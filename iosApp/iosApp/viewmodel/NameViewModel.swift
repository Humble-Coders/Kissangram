import Foundation
import SwiftUI
import Shared

@MainActor
class NameViewModel: ObservableObject {
    @Published var name: String = ""
    @Published var isLoading: Bool = false
    @Published var isListening: Bool = false
    @Published var isProcessing: Bool = false
    @Published var error: String?
    
    private let authRepository: AuthRepository
    private let updateUserProfileUseCase: UpdateUserProfileUseCase
    private let speechRecognizer: IOSSpeechRecognizerWrapper
    
    init() {
        self.authRepository = IOSAuthRepository()
        self.updateUserProfileUseCase = UpdateUserProfileUseCase(authRepository: authRepository)
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
                        // Update name continuously
                        self.name = recognizedText.trimmingCharacters(in: CharacterSet.whitespaces)
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
        // Update with initial text (will be updated via callback if final result comes)
        name = initialText.trimmingCharacters(in: CharacterSet.whitespaces)
        
        // Wait a bit for final results to come in (up to 3 seconds)
        try? await Task.sleep(nanoseconds: 3_500_000_000) // 3.5 seconds
        
        // Get final accumulated text after waiting
        let finalText = speechRecognizer.getAccumulatedText()
        name = finalText.trimmingCharacters(in: CharacterSet.whitespaces)
        
        isProcessing = false
    }
    
    func saveName(onSuccess: @escaping () -> Void, onError: @escaping (String) -> Void) async {
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        guard trimmedName.count >= 2 else {
            error = "Name must be at least 2 characters"
            return
        }
        
        isLoading = true
        error = nil
        
        do {
            try await updateUserProfileUseCase.invoke(name: trimmedName)
            isLoading = false
            onSuccess()
        } catch {
            isLoading = false
            let errorMessage = (error as? Error)?.localizedDescription ?? "Failed to save name"
            self.error = errorMessage
            onError(errorMessage)
        }
    }
}
