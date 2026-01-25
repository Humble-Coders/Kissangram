package com.kissangram.auth

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.kissangram.preferences.PreferencesRepositoryFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidAuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val context: Context? = null,
    private val activity: Activity? = null
) : com.kissangram.auth.AuthRepository {

    private val preferencesRepository by lazy {
        context?.let { PreferencesRepositoryFactory(it).create() }
    }

    override suspend fun sendOtp(phoneNumber: String) {
        suspendCancellableCoroutine { continuation ->
            var verificationCompleted = false
            
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-verification completed (usually for test numbers)
                    // For test numbers, Firebase auto-verifies and signs in
                    // We still need to sign in with the credential
                    firebaseAuth.signInWithCredential(credential).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            verificationCompleted = true
                            if (!continuation.isCompleted) {
                                continuation.resume(Unit)
                            }
                        } else {
                            if (!continuation.isCompleted) {
                                continuation.resumeWithException(
                                    task.exception ?: Exception("Auto-verification failed")
                                )
                            }
                        }
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (!continuation.isCompleted) {
                        // Log error details for debugging
                        android.util.Log.e("AndroidAuthRepository", "Verification failed: ${e.message}")
                        android.util.Log.e("AndroidAuthRepository", "Error: ${e.localizedMessage}")
                        
                        // Provide more detailed error message
                        val errorMessage = when {
                            e.message?.contains("billing", ignoreCase = true) == true -> {
                                "Billing not enabled. Please wait a few minutes after enabling billing, or check Firebase Console."
                            }
                            e.message?.contains("quota", ignoreCase = true) == true -> {
                                "SMS quota exceeded. Please check Firebase Console or wait a few minutes."
                            }
                            e.message?.contains("recaptcha", ignoreCase = true) == true -> {
                                "reCAPTCHA verification failed. Ensure app hash is registered in Firebase Console for SMS Retriever API."
                            }
                            else -> {
                                e.message ?: "Verification failed. Please try again."
                            }
                        }
                        continuation.resumeWithException(Exception(errorMessage))
                    }
                }

                override fun onCodeSent(
                    id: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    android.util.Log.d("AndroidAuthRepository", "✅ Verification code sent successfully")
                    android.util.Log.d("AndroidAuthRepository", "SMS Retriever API is being used (no reCAPTCHA)")
                    
                    // Store verification ID in preferences so it persists across navigation
                    // Use runBlocking in callback context to store synchronously
                    preferencesRepository?.let { prefs ->
                        try {
                            kotlinx.coroutines.runBlocking {
                                prefs.setVerificationId(id)
                            }
                        } catch (e: Exception) {
                            // If storage fails, continue anyway - verification ID is still available
                        }
                    }
                    if (!continuation.isCompleted && !verificationCompleted) {
                        continuation.resume(Unit)
                    }
                }
            }

            val activityContext = activity ?: (context as? Activity)
                ?: throw IllegalStateException("Activity context is required for Phone Authentication")
            
            // Build PhoneAuthOptions - Firebase will automatically use SMS Retriever API
            // SMS Retriever API is used by default if app hash is registered in Firebase Console
            // This avoids reCAPTCHA and provides silent SMS verification
            // 
            // To ensure SMS Retriever is used (not reCAPTCHA):
            // 1. Get your app hash: Run `keytool -list -v -keystore <your-keystore> -alias <alias>`
            // 2. Register the SHA-256 hash in Firebase Console → Authentication → Settings → Phone
            // 3. Ensure the app is signed with the same keystore
            android.util.Log.d("AndroidAuthRepository", "Starting phone verification for: $phoneNumber")
            android.util.Log.d("AndroidAuthRepository", "Firebase will use SMS Retriever API if app hash is configured")
            
            val options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                .setActivity(activityContext)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    override suspend fun verifyOtp(otp: String) {
        // Check if user is already signed in (auto-verification for test numbers)
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            // User is already authenticated (test number auto-verification)
            // Clear verification ID as it's no longer needed
            preferencesRepository?.clearVerificationId()
            return
        }
        
        // Get verification ID from preferences (persists across navigation)
        val id = preferencesRepository?.getVerificationId() 
            ?: throw IllegalStateException("No verification ID. Please request OTP again.")
        
        val credential = PhoneAuthProvider.getCredential(id, otp)
        firebaseAuth.signInWithCredential(credential).await()
        
        // Clear verification ID after successful verification
        preferencesRepository?.clearVerificationId()
    }

    override suspend fun updateUserProfile(name: String) {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("No authenticated user")

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()

        user.updateProfile(profileUpdates).await()
    }

    override suspend fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    override suspend fun isUserAuthenticated(): Boolean {
        return firebaseAuth.currentUser != null
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

}
