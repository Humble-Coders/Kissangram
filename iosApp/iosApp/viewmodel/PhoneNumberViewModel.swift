import Foundation
import SwiftUI
import Shared

@MainActor
class PhoneNumberViewModel: ObservableObject {
    @Published var phoneNumber: String = ""
    @Published var countryCode: String = "+91"
    @Published var isLoading: Bool = false
    @Published var isListening: Bool = false
    @Published var isProcessing: Bool = false
    @Published var error: String?
    
    private let authRepository: AuthRepository
    private let sendOtpUseCase: SendOtpUseCase
    private let speechRecognizer: IOSSpeechRecognizerWrapper
    
    init() {
        self.authRepository = IOSAuthRepository()
        self.sendOtpUseCase = SendOtpUseCase(authRepository: authRepository)
        self.speechRecognizer = IOSSpeechRecognizerWrapper()
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
                        // Extract digits from recognized text and update continuously
                        let digits = recognizedText.filter { $0.isNumber }
                        if !digits.isEmpty {
                            self.phoneNumber = digits
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
        
        let initialText = speechRecognizer.stopListening()
        // Extract digits from initial text (will be updated via callback if final result comes)
        let digits = initialText.filter { $0.isNumber }
        if !digits.isEmpty {
            phoneNumber = digits
        }
        
        // Wait a bit for final results to come in (up to 3 seconds)
        try? await Task.sleep(nanoseconds: 3_500_000_000) // 3.5 seconds
        
        // Get final accumulated text after waiting
        let finalText = speechRecognizer.getAccumulatedText()
        let finalDigits = finalText.filter { $0.isNumber }
        if !finalDigits.isEmpty {
            phoneNumber = finalDigits
        }
        
        isProcessing = false
    }
    
    func sendOtp(onSuccess: @escaping (String) -> Void, onError: @escaping (String) -> Void) async {
        guard phoneNumber.count >= 10 else {
            error = "Please enter a valid phone number"
            return
        }
        
        let fullPhoneNumber = "\(countryCode)\(phoneNumber)"
        isLoading = true
        error = nil
        
        do {
            print("[PhoneNumberViewModel] Calling sendOtpUseCase for: \(fullPhoneNumber)")
            try await sendOtpUseCase.invoke(phoneNumber: fullPhoneNumber)
            print("✅ [PhoneNumberViewModel] OTP sent successfully, calling onSuccess")
            isLoading = false
            onSuccess(fullPhoneNumber)
            print("✅ [PhoneNumberViewModel] onSuccess callback completed")
        } catch {
            print("❌ [PhoneNumberViewModel] Error sending OTP: \(error)")
            isLoading = false
            let errorMessage = error.localizedDescription
            self.error = errorMessage
            onError(errorMessage)
        }
    }
}
