import Foundation
import FirebaseAuth
import Shared

final class IOSAuthRepository: AuthRepository {
    private let auth: Auth
    private let preferencesRepository: PreferencesRepository
    
    init(preferencesRepository: PreferencesRepository) {
        self.auth = Auth.auth()
        self.preferencesRepository = preferencesRepository
    }
    
    func sendOtp(phoneNumber: String) async throws {
        print("[IOSAuthRepository] Starting sendOtp for phone: \(phoneNumber)")
        
        return try await withCheckedThrowingContinuation { continuation in
            PhoneAuthProvider.provider().verifyPhoneNumber(phoneNumber, uiDelegate: nil) { [weak self] verificationID, error in
                print("[IOSAuthRepository] verifyPhoneNumber callback called")
                print("[IOSAuthRepository] verificationID: \(verificationID != nil)")
                print("[IOSAuthRepository] error: \(error?.localizedDescription ?? "none")")
                
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                
                // Store verification ID in preferences so it persists across navigation
                if let verificationID = verificationID {
                    print("[IOSAuthRepository] Storing verification ID asynchronously")
                    Task {
                        do {
                            try await self?.preferencesRepository.setVerificationId(id: verificationID)
                            print("[IOSAuthRepository] Verification ID stored successfully")
                        } catch {
                            print("[IOSAuthRepository] Failed to store verification ID: \(error.localizedDescription)")
                            // If storage fails, continue anyway
                        }
                    }
                }
                
                print("[IOSAuthRepository] Resuming continuation with success")
                continuation.resume(returning: ())
            }
        }
    }
    
    func verifyOtp(otp: String) async throws {
        // Check if user is already signed in (auto-verification for test numbers)
        if auth.currentUser != nil {
            // User is already authenticated (test number auto-verification)
            // Clear verification ID asynchronously
            Task {
                try? await preferencesRepository.clearVerificationId()
            }
            return
        }
        
        // Get verification ID from preferences (persists across navigation)
        guard let verificationID = try await preferencesRepository.getVerificationId() else {
            throw NSError(domain: "IOSAuthRepository", code: 1, userInfo: [NSLocalizedDescriptionKey: "No verification ID. Please request OTP again."])
        }
        
        let credential = PhoneAuthProvider.provider().credential(withVerificationID: verificationID, verificationCode: otp)
        
        return try await withCheckedThrowingContinuation { continuation in
            self.auth.signIn(with: credential) { [weak self] result, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                
                // Clear verification ID after successful verification
                Task {
                    try? await self?.preferencesRepository.clearVerificationId()
                }
                
                continuation.resume(returning: ())
            }
        }
    }
    
    func updateUserProfile(name: String) async throws {
        guard let user = auth.currentUser else {
            throw NSError(domain: "IOSAuthRepository", code: 2, userInfo: [NSLocalizedDescriptionKey: "No authenticated user"])
        }
        
        let changeRequest = user.createProfileChangeRequest()
        changeRequest.displayName = name
        
        return try await withCheckedThrowingContinuation { continuation in
            changeRequest.commitChanges { error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
    }
    
    func getCurrentUserId() async throws -> String? {
        return auth.currentUser?.uid
    }
    
    func getCurrentUserPhoneNumber() async throws -> String? {
        return auth.currentUser?.phoneNumber
    }
    
    func getCurrentUserDisplayName() async throws -> String? {
        return auth.currentUser?.displayName
    }
    
    func isUserAuthenticated() async throws -> KotlinBoolean {
        return KotlinBoolean(value: auth.currentUser != nil)
    }
    
    func signOut() async throws {
        try? auth.signOut()
        try? await preferencesRepository.clearSession()
    }
}
