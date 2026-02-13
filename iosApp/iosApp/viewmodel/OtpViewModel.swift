import Foundation
import SwiftUI
import Shared

@MainActor
class OtpViewModel: ObservableObject {
    @Published var otp: String = ""
    @Published var isLoading: Bool = false
    @Published var isListening: Bool = false
    @Published var isProcessing: Bool = false
    @Published var error: String?
    
    let phoneNumber: String
    
    private let preferencesRepository: PreferencesRepository
    private let authRepository: AuthRepository
    private let userRepository: UserRepository
    private let verifyOtpUseCase: VerifyOtpUseCase
    private let speechRepository: IOSSpeechRepository
    
    init(phoneNumber: String) {
        self.phoneNumber = phoneNumber
        self.preferencesRepository = IOSPreferencesRepository()
        self.authRepository = IOSAuthRepository(preferencesRepository: preferencesRepository)
        self.userRepository = FirestoreUserRepository(authRepository: authRepository)
        self.verifyOtpUseCase = VerifyOtpUseCase(authRepository: authRepository)
        self.speechRepository = IOSSpeechRepository()
    }
    
    func verifyOtp(
        onExistingUser: @escaping (String) -> Void, // userName callback for existing users
        onNewUser: @escaping () -> Void, // callback for new users
        onError: @escaping (String) -> Void
    ) async {
        guard otp.count == 6 else {
            error = "Please enter 6 digit OTP"
            return
        }
        
        isLoading = true
        error = nil
        
        do {
            try await verifyOtpUseCase.invoke(otp: otp)
            
            // Check if user exists in Firestore
            if let currentUser = try await userRepository.getCurrentUser() {
                // Existing user - show welcome back screen
                isLoading = false
                onExistingUser(currentUser.name)
            } else {
                // New user - continue to name screen
                isLoading = false
                onNewUser()
            }
        } catch {
            isLoading = false
            let errorMessage = (error as? Error)?.localizedDescription ?? "Invalid OTP"
            self.error = errorMessage
            onError(errorMessage)
        }
    }
    
    func resendOtp(onResend: @escaping () -> Void) {
        onResend()
    }
    
    func startSpeechRecognition() async {
        guard !isListening else { return }
        
        isListening = true
        error = nil
        
        // Haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
        
        do {
            // Request permission if needed
            if !speechRepository.hasPermission() {
                let granted = try await speechRepository.requestPermission()
                if !granted.boolValue {
                    isListening = false
                    error = "Speech recognition permission is required"
                    return
                }
            }
            
            // Start listening with continuous updates
            Task {
                do {
                    try await speechRepository.startListeningWithUpdates { [weak self] recognizedText in
                        guard let self = self else { return }
                        // Extract digits from recognized text and limit to 6 digits for OTP
                        let digits = String(recognizedText.filter { $0.isNumber }.prefix(6))
                        if !digits.isEmpty {
                            self.otp = digits
                        }
                    }
                } catch {
                    if !error.localizedDescription.contains("cancelled") {
                        self.error = (error as? Error)?.localizedDescription ?? "Speech recognition error"
                    }
                    self.isListening = false
                }
            }
        } catch {
            self.error = (error as? Error)?.localizedDescription ?? "Speech recognition error"
            isListening = false
        }
    }
    
    func stopSpeechRecognition() async {
        guard isListening else { return }
        
        // Haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .light)
        impactFeedback.impactOccurred()
        
        // Set processing state
        isListening = false
        isProcessing = true
        
        let initialText = speechRepository.stopListeningSync()
        // Extract digits and limit to 6 digits for OTP
        let digits = String(initialText.filter { $0.isNumber }.prefix(6))
        if !digits.isEmpty {
            otp = digits
        }
        
        // Wait a bit for final results to come in (up to 3 seconds)
        try? await Task.sleep(nanoseconds: 3_500_000_000) // 3.5 seconds
        
        // Get final accumulated text after waiting
        let finalText = speechRepository.getAccumulatedText()
        let finalDigits = String(finalText.filter { $0.isNumber }.prefix(6))
        if !finalDigits.isEmpty {
            otp = finalDigits
        }
        
        isProcessing = false
    }
}
