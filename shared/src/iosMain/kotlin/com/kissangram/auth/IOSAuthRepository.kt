package com.kissangram.auth

import cocoapods.FirebaseAuth.*
import com.kissangram.preferences.PreferencesRepositoryFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
public class IOSAuthRepository : com.kissangram.auth.AuthRepository {
    private val auth: FIRAuth
    private val preferencesRepository: com.kissangram.preferences.PreferencesRepository = PreferencesRepositoryFactory().create()
    
    public constructor() {
        // Default constructor for Swift
        // Initialize auth immediately to ensure Firebase is ready
        // This will throw if Firebase isn't configured
        auth = try {
            FIRAuth.auth() ?: throw IllegalStateException("Firebase Auth not initialized. Call FirebaseApp.configure() in iOSApp.init()")
        } catch (e: Exception) {
            throw IllegalStateException("Firebase Auth initialization failed: ${e.message}. Make sure FirebaseApp.configure() is called in iOSApp.init()")
        }
    }
    
    private fun getPhoneAuthProvider(): FIRPhoneAuthProvider {
        // Ensure auth is initialized first
        val authInstance = auth
        // Use default provider which uses the default auth instance
        val provider = FIRPhoneAuthProvider.provider()
        if (provider == null) {
            throw IllegalStateException("Failed to get PhoneAuthProvider. Firebase Auth may not be initialized.")
        }
        return provider
    }
    
    override suspend fun sendOtp(phoneNumber: String) {
        suspendCancellableCoroutine { continuation ->
            try {
                // Log initialization state
                println("[IOSAuthRepository] Starting sendOtp for phone: $phoneNumber")
                
                // Ensure auth is initialized before getting provider
                val authInstance = auth
                println("[IOSAuthRepository] Auth instance obtained: ${authInstance != null}")
                
                // Use providerWithAuth to ensure we use the same auth instance
                // This is critical - using provider() might get a different instance without bundle info
                val provider = FIRPhoneAuthProvider.providerWithAuth(authInstance)
                    ?: throw IllegalStateException("Failed to get PhoneAuthProvider. Firebase Auth may not be initialized.")
                
                println("[IOSAuthRepository] Provider obtained: ${provider != null}")
                println("[IOSAuthRepository] Calling verifyPhoneNumber...")
                
                // Call verifyPhoneNumber - ensure all parameters are non-null where required
                provider.verifyPhoneNumber(
                    phoneNumber = phoneNumber,
                    UIDelegate = null
                ) { verificationID: String?, error: NSError? ->
                    println("[IOSAuthRepository] verifyPhoneNumber callback called")
                    println("[IOSAuthRepository] verificationID: ${verificationID != null}")
                    println("[IOSAuthRepository] error: ${error?.localizedDescription()}")
                    
                    if (error != null) {
                        if (!continuation.isCompleted) {
                            continuation.resumeWithException(
                                Exception(error.localizedDescription() ?: "Verification failed")
                            )
                        }
                    } else {
                        // Store verification ID in preferences so it persists across navigation
                        // Store asynchronously to avoid blocking the callback
                        if (verificationID != null) {
                            println("[IOSAuthRepository] Storing verification ID asynchronously")
                            CoroutineScope(Dispatchers.Default).launch {
                                try {
                                    preferencesRepository.setVerificationId(verificationID)
                                    println("[IOSAuthRepository] Verification ID stored successfully")
                                } catch (e: Exception) {
                                    println("[IOSAuthRepository] Failed to store verification ID: ${e.message}")
                                    // If storage fails, continue anyway
                                }
                            }
                        }
                        // Resume continuation immediately - don't wait for storage
                        if (!continuation.isCompleted) {
                            println("[IOSAuthRepository] Resuming continuation with success")
                            continuation.resume(Unit)
                            println("[IOSAuthRepository] Continuation resumed")
                        } else {
                            println("[IOSAuthRepository] WARNING: Continuation already completed!")
                        }
                    }
                }
            } catch (e: Exception) {
                println("[IOSAuthRepository] Exception in sendOtp: ${e.message}")
                println("[IOSAuthRepository] Exception stack: ${e.stackTraceToString()}")
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
    
    override suspend fun verifyOtp(otp: String) {
        // Check if user is already signed in (auto-verification for test numbers)
        val currentUser = auth.currentUser()
        if (currentUser != null) {
            // User is already authenticated (test number auto-verification)
            // Clear verification ID asynchronously
            CoroutineScope(Dispatchers.Default).launch {
                preferencesRepository.clearVerificationId()
            }
            return
        }
        
        // Get verification ID from preferences (persists across navigation)
        val id = preferencesRepository.getVerificationId()
            ?: throw IllegalStateException("No verification ID. Please request OTP again.")
        
        val provider = getPhoneAuthProvider()
        val credential = provider.credentialWithVerificationID(id, verificationCode = otp)
        
        suspendCancellableCoroutine { continuation ->
            auth.signInWithCredential(credential) { result: FIRAuthDataResult?, error: NSError? ->
                if (error != null) {
                    continuation.resumeWithException(
                        Exception(error.localizedDescription() ?: "Verification failed")
                    )
                } else {
                    // Clear verification ID after successful verification
                    // Clear verification ID asynchronously
                    CoroutineScope(Dispatchers.Default).launch {
                        preferencesRepository.clearVerificationId()
                    }
                    continuation.resume(Unit)
                }
            }
        }
    }
    
    override suspend fun updateUserProfile(name: String) {
        val user = auth.currentUser()
            ?: throw IllegalStateException("No authenticated user")
        
        val changeRequest = user.profileChangeRequest()
        changeRequest.setDisplayName(name)
        
        suspendCancellableCoroutine { continuation ->
            changeRequest.commitChangesWithCompletion { error: NSError? ->
                if (error != null) {
                    continuation.resumeWithException(
                        Exception(error.localizedDescription() ?: "Failed to update profile")
                    )
                } else {
                    continuation.resume(Unit)
                }
            }
        }
    }
    
    override suspend fun getCurrentUserId(): String? {
        return auth.currentUser()?.uid()
    }
    
    override suspend fun isUserAuthenticated(): Boolean {
        return auth.currentUser() != null
    }
    
    override suspend fun signOut() {
        try {
            auth.signOut(null)
        } catch (e: Exception) {
            // Ignore sign out errors
        }
    }
}
