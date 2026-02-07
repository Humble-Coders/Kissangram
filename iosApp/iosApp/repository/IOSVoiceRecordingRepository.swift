import Foundation
import AVFoundation
import Shared

/**
 * iOS implementation of VoiceRecordingRepository using AVAudioRecorder.
 * Records audio in M4A format (AAC codec) for good quality and compatibility.
 */
@MainActor
final class IOSVoiceRecordingRepository: VoiceRecordingRepository {
    
    private var audioRecorder: AVAudioRecorder?
    private var currentFilePath: String?
    private var recordingStartTime: Date?
    private var isCurrentlyRecording = false
    private var durationTimer: Timer?
    private var onDurationUpdate: ((Int) -> Void)?
    
    // MARK: - VoiceRecordingRepository Protocol
    
    func isAvailable() -> Bool {
        // Check if device has microphone capability
        let audioSession = AVAudioSession.sharedInstance()
        return audioSession.isInputAvailable
    }
    
    func hasPermission() -> Bool {
        return AVAudioSession.sharedInstance().recordPermission == .granted
    }
    
    func requestPermission() async throws -> KotlinBoolean {
        let audioSession = AVAudioSession.sharedInstance()
        
        let granted = await withCheckedContinuation { continuation in
            audioSession.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
        
        return KotlinBoolean(value: granted)
    }
    
    func startRecording(outputFilePath: String) async throws {
        if isCurrentlyRecording {
            return
        }
        
        if !hasPermission() {
            throw NSError(
                domain: "VoiceRecordingRepository",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Microphone permission is required"]
            )
        }
        
        do {
            // Configure audio session
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try audioSession.setActive(true)
            
            // Ensure parent directory exists
            let fileURL = URL(fileURLWithPath: outputFilePath)
            let parentDir = fileURL.deletingLastPathComponent()
            try FileManager.default.createDirectory(at: parentDir, withIntermediateDirectories: true)
            
            // Delete existing file if any
            if FileManager.default.fileExists(atPath: outputFilePath) {
                try FileManager.default.removeItem(atPath: outputFilePath)
            }
            
            currentFilePath = outputFilePath
            
            // Configure recording settings (AAC in M4A container)
            let settings: [String: Any] = [
                AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
                AVSampleRateKey: 44100,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue,
                AVEncoderBitRateKey: 128000
            ]
            
            // Create recorder
            let recorder = try AVAudioRecorder(url: fileURL, settings: settings)
            recorder.isMeteringEnabled = true
            
            // Prepare and start recording
            guard recorder.prepareToRecord() else {
                throw NSError(
                    domain: "VoiceRecordingRepository",
                    code: 2,
                    userInfo: [NSLocalizedDescriptionKey: "Failed to prepare recorder"]
                )
            }
            
            guard recorder.record() else {
                throw NSError(
                    domain: "VoiceRecordingRepository",
                    code: 3,
                    userInfo: [NSLocalizedDescriptionKey: "Failed to start recording"]
                )
            }
            
            audioRecorder = recorder
            recordingStartTime = Date()
            isCurrentlyRecording = true
            
            // Start duration update timer
            startDurationUpdates()
            
        } catch {
            cleanupRecorder()
            throw NSError(
                domain: "VoiceRecordingRepository",
                code: 4,
                userInfo: [NSLocalizedDescriptionKey: "Failed to start recording: \(error.localizedDescription)"]
            )
        }
    }
    
    func stopRecording() async throws -> KotlinInt {
        if !isCurrentlyRecording {
            return KotlinInt(value: 0)
        }
        
        let duration = getCurrentDuration()
        
        audioRecorder?.stop()
        
        // Deactivate audio session
        do {
            try AVAudioSession.sharedInstance().setActive(false)
        } catch {
            // Ignore deactivation errors
        }
        
        cleanupRecorder()
        return KotlinInt(value: Int32(duration))
    }
    
    func cancelRecording() async throws {
        if !isCurrentlyRecording {
            return
        }
        
        audioRecorder?.stop()
        
        // Delete the file
        if let path = currentFilePath {
            do {
                try FileManager.default.removeItem(atPath: path)
            } catch {
                // Ignore deletion errors
            }
        }
        
        // Deactivate audio session
        do {
            try AVAudioSession.sharedInstance().setActive(false)
        } catch {
            // Ignore deactivation errors
        }
        
        cleanupRecorder()
    }
    
    func isRecording() -> Bool {
        return isCurrentlyRecording
    }
    
    func getCurrentDuration() -> Int32 {
        guard isCurrentlyRecording, let startTime = recordingStartTime else {
            return 0
        }
        let elapsed = Date().timeIntervalSince(startTime)
        return Int32(elapsed)
    }
    
    func deleteRecording(filePath: String) async throws {
        do {
            if FileManager.default.fileExists(atPath: filePath) {
                try FileManager.default.removeItem(atPath: filePath)
            }
        } catch {
            // Ignore deletion errors
        }
    }
    
    func getAudioDuration(filePath: String) async throws -> KotlinInt {
        do {
            let fileURL = URL(fileURLWithPath: filePath)
            let audioAsset = AVURLAsset(url: fileURL)
            let duration = try await audioAsset.load(.duration)
            let seconds = CMTimeGetSeconds(duration)
            return KotlinInt(value: Int32(seconds.isNaN ? 0 : seconds))
        } catch {
            return KotlinInt(value: 0)
        }
    }
    
    // MARK: - Helper Methods
    
    private func cleanupRecorder() {
        stopDurationUpdates()
        
        audioRecorder = nil
        currentFilePath = nil
        recordingStartTime = nil
        isCurrentlyRecording = false
    }
    
    private func startDurationUpdates() {
        durationTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self = self, self.isCurrentlyRecording else { return }
                self.onDurationUpdate?(Int(self.getCurrentDuration()))
            }
        }
    }
    
    private func stopDurationUpdates() {
        durationTimer?.invalidate()
        durationTimer = nil
    }
    
    // MARK: - Public Helper Methods
    
    /**
     * Set a callback to receive duration updates while recording.
     * Called every second with the current duration in seconds.
     */
    func setOnDurationUpdate(_ callback: ((Int) -> Void)?) {
        onDurationUpdate = callback
    }
    
    /**
     * Get the file path for a new voice caption recording.
     * Creates a unique filename in the app's cache directory.
     */
    func generateVoiceCaptionPath() -> String {
        let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let voiceCaptionsDir = cacheDir.appendingPathComponent("voice_captions")
        
        // Create directory if needed
        try? FileManager.default.createDirectory(at: voiceCaptionsDir, withIntermediateDirectories: true)
        
        let fileName = "voice_caption_\(Int(Date().timeIntervalSince1970 * 1000)).m4a"
        return voiceCaptionsDir.appendingPathComponent(fileName).path
    }
}
