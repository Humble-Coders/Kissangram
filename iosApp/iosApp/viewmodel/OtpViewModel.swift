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
    
    private let authRepository: AuthRepository
    private let verifyOtpUseCase: VerifyOtpUseCase
    private let speechRecognizer: IOSSpeechRecognizerWrapper
    
    init(phoneNumber: String) {
        self.phoneNumber = phoneNumber
        self.authRepository = IOSAuthRepository()
        self.verifyOtpUseCase = VerifyOtpUseCase(authRepository: authRepository)
        self.speechRecognizer = IOSSpeechRecognizerWrapper()
    }
    
    func verifyOtp(onSuccess: @escaping () -> Void, onError: @escaping (String) -> Void) async {
        guard otp.count == 6 else {
            error = "Please enter 6 digit OTP"
            return
        }
        
        isLoading = true
        error = nil
        
        do {
            try await verifyOtpUseCase.invoke(otp: otp)
            isLoading = false
            onSuccess()
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
            if !speechRecognizer.hasPermission() {
                let granted = try await speechRecognizer.requestPermission()
                if !granted {
                    isListening = false
                    error = "Speech recognition permission is required"
                    return
                }
            }
            
            // Start listening with continuous updates
            Task {
                do {
                    try await speechRecognizer.startListening { [weak self] recognizedText in
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
        
        do {
            let finalText = try await speechRecognizer.stopListening()
            // Extract digits and limit to 6 digits for OTP
            let digits = String(finalText.filter { $0.isNumber }.prefix(6))
            if !digits.isEmpty {
                otp = digits
            }
            isProcessing = false
        } catch let recognitionError {
            if !recognitionError.localizedDescription.contains("cancelled") {
                self.error = recognitionError.localizedDescription
            }
            isProcessing = false
        }
    }
}
