import Foundation
import Speech
import AVFoundation
import Shared

@MainActor
final class IOSSpeechRepository: SpeechRepository {
    private var audioEngine: AVAudioEngine?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var speechRecognizer: SFSpeechRecognizer?
    private var accumulatedText: String = ""
    private var onTextUpdate: ((String) -> Void)?
    private var shouldStopAfterCurrent = false
    private var stopTimeoutTask: Task<Void, Never>?
    
    init() {
        self.speechRecognizer = SFSpeechRecognizer()
    }
    
    func isAvailable() -> Bool {
        return speechRecognizer?.isAvailable ?? false
    }
    
    func hasPermission() -> Bool {
        return SFSpeechRecognizer.authorizationStatus() == .authorized
    }
    
    func requestPermission() async throws -> KotlinBoolean {
        let result = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
        return KotlinBoolean(value: result)
    }
    
    func startListening() async throws -> String {
        guard let recognizer = speechRecognizer, recognizer.isAvailable else {
            throw NSError(domain: "SpeechRepository", code: 1, userInfo: [NSLocalizedDescriptionKey: "Speech recognition not available"])
        }
        
        if !hasPermission() {
            let granted = try await requestPermission()
            guard granted.boolValue else {
                throw NSError(domain: "SpeechRepository", code: 2, userInfo: [NSLocalizedDescriptionKey: "Speech recognition permission denied"])
            }
        }
        
        // Request microphone permission
        let audioSession = AVAudioSession.sharedInstance()
        let micPermissionGranted = await withCheckedContinuation { continuation in
            audioSession.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
        guard micPermissionGranted else {
            throw NSError(domain: "SpeechRepository", code: 3, userInfo: [NSLocalizedDescriptionKey: "Microphone permission denied"])
        }
        
        // Reset accumulated text
        accumulatedText = ""
        shouldStopAfterCurrent = false
        stopTimeoutTask?.cancel()
        stopTimeoutTask = nil
        
        // Configure audio session
        try audioSession.setCategory(.record, mode: .measurement, options: [])
        try audioSession.setActive(true)
        
        // Cancel previous task if any
        recognitionTask?.cancel()
        recognitionTask = nil
        
        // Create recognition request
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        recognitionRequest = request
        
        // Create and configure audio engine
        let engine = AVAudioEngine()
        audioEngine = engine
        
        let inputNode = engine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        
        // Install tap on input node
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
            request.append(buffer)
        }
        
        // Prepare and start audio engine
        engine.prepare()
        do {
            try engine.start()
        } catch {
            inputNode.removeTap(onBus: 0)
            throw NSError(domain: "SpeechRepository", code: 5, userInfo: [NSLocalizedDescriptionKey: "Failed to start audio engine: \(error.localizedDescription)"])
        }
        
        // Start recognition task
        self.recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
            guard let self = self else { return }
            
            if let error = error {
                let nsError = error as NSError
                if nsError.code == 216 { // SFSpeechRecognizerErrorCode.cancelled
                    return
                }
                
                // If we should stop and got an error, stop now
                if self.shouldStopAfterCurrent {
                    Task { @MainActor in
                        self.actuallyStopListening()
                    }
                }
                return
            }
            
            if let result = result {
                let recognizedText = result.bestTranscription.formattedString
                
                if !recognizedText.isEmpty {
                    // Update accumulated text with the latest recognition
                    Task { @MainActor in
                        self.accumulatedText = recognizedText
                        self.onTextUpdate?(recognizedText)
                    }
                }
                
                // If this is a final result and we should stop, stop now
                if result.isFinal && self.shouldStopAfterCurrent {
                    Task { @MainActor in
                        self.actuallyStopListening()
                    }
                }
            }
        }
        
        return ""
    }
    
    func stopListening() async throws {
        // Set flag to stop after current recognition finishes
        shouldStopAfterCurrent = true
        
        // Cancel any pending timeout
        stopTimeoutTask?.cancel()
        
        // Set a timeout to force stop if final results don't come within 3 seconds
        stopTimeoutTask = Task {
            try? await Task.sleep(nanoseconds: 3_000_000_000) // 3 seconds
            if shouldStopAfterCurrent {
                await MainActor.run {
                    actuallyStopListening()
                }
            }
        }
    }
    
    private func actuallyStopListening() {
        shouldStopAfterCurrent = false
        stopTimeoutTask?.cancel()
        stopTimeoutTask = nil
        
        // Cancel recognition task
        recognitionTask?.cancel()
        recognitionTask = nil
        
        // Stop audio engine
        audioEngine?.stop()
        if let inputNode = audioEngine?.inputNode {
            inputNode.removeTap(onBus: 0)
        }
        
        // End recognition request
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        audioEngine = nil
        
        // Deactivate audio session
        try? AVAudioSession.sharedInstance().setActive(false)
        
        onTextUpdate = nil
    }
    
    // MARK: - Helper methods for continuous updates
    
    func startListeningWithUpdates(onTextUpdate: @escaping (String) -> Void) async throws {
        self.onTextUpdate = onTextUpdate
        _ = try await startListening()
    }
    
    func stopListeningSync() -> String {
        // Set flag to stop after current recognition finishes
        shouldStopAfterCurrent = true
        
        // Cancel any pending timeout
        stopTimeoutTask?.cancel()
        
        // Set a timeout to force stop if final results don't come within 3 seconds
        stopTimeoutTask = Task {
            try? await Task.sleep(nanoseconds: 3_000_000_000) // 3 seconds
            if shouldStopAfterCurrent {
                await MainActor.run {
                    actuallyStopListening()
                }
            }
        }
        
        // Return current accumulated text immediately (will be updated if final result comes)
        return accumulatedText
    }
    
    func getAccumulatedText() -> String {
        return accumulatedText
    }
}
