import Foundation
import SwiftUI
import AVFoundation
import Shared

/**
 * ViewModel for Create Post screen on iOS.
 * Handles voice recording and post creation logic.
 */
@MainActor
class CreatePostViewModel: ObservableObject {
    
    // MARK: - Published State
    
    @Published var postType: PostType = .normal
    @Published var caption: String = ""
    @Published var mediaItems: [MediaItem] = []
    @Published var selectedCrops: Set<String> = []
    @Published var visibility: PostVisibility = .public
    @Published var locationName: String? = nil
    @Published var locationLatitude: Double? = nil
    @Published var locationLongitude: Double? = nil
    @Published var hashtags: [String] = []
    @Published var hashtagInput: String = ""
    @Published var targetExpertise: Set<String> = []
    
    // Crops state
    @Published var allCrops: [String] = []
    @Published var cropSearchQuery: String = ""
    @Published var isLoadingCrops: Bool = false
    @Published var showAllCrops: Bool = false
    
    // Voice caption state
    @Published var voiceCaptionUri: String? = nil
    @Published var voiceCaptionDuration: Int = 0
    @Published var isRecordingVoice: Bool = false
    @Published var recordingDuration: Int = 0
    
    // Voice playback state
    @Published var isPlayingVoice: Bool = false
    @Published var playbackProgress: Int = 0
    
    // Permission state
    @Published var needsAudioPermission: Bool = false
    @Published var showPermissionDeniedAlert: Bool = false
    
    // Error state
    @Published var errorMessage: String? = nil
    @Published var showError: Bool = false
    
    // MARK: - Computed Properties
    
    var isPostEnabled: Bool {
        switch postType {
        case .question:
            return !caption.isEmpty
        case .normal:
            return !mediaItems.isEmpty && !caption.isEmpty
        }
    }
    
    // Computed properties for filtered and limited crops
    var visibleCrops: [String] {
        let unselectedCrops = allCrops.filter { !selectedCrops.contains($0) }
        let filteredCrops: [String]
        if cropSearchQuery.isEmpty {
            filteredCrops = unselectedCrops
        } else {
            filteredCrops = unselectedCrops.filter { $0.localizedCaseInsensitiveContains(cropSearchQuery) }
        }
        if showAllCrops || !cropSearchQuery.isEmpty {
            return filteredCrops
        } else {
            return Array(filteredCrops.prefix(10))
        }
    }
    
    var hasMoreCrops: Bool {
        if !cropSearchQuery.isEmpty { return false }
        let unselectedCrops = allCrops.filter { !selectedCrops.contains($0) }
        return !showAllCrops && unselectedCrops.count > 10
    }
    
    var remainingCropsCount: Int {
        let unselectedCrops = allCrops.filter { !selectedCrops.contains($0) }
        return max(unselectedCrops.count - 10, 0)
    }
    
    // MARK: - Private Properties
    
    private let voiceRecordingRepository = IOSVoiceRecordingRepository()
    private let cropsRepository = IOSCropsRepository()
    private var currentVoiceCaptionPath: String?
    
    // Audio playback
    private var audioPlayer: AVAudioPlayer?
    private var playbackTimer: Timer?
    
    // MARK: - Initialization
    
    init() {
        // Set up duration update callback
        voiceRecordingRepository.setOnDurationUpdate { [weak self] duration in
            Task { @MainActor [weak self] in
                self?.recordingDuration = duration
            }
        }
        
        // Load crops
        loadCrops()
    }
    
    // MARK: - Crops
    
    private func loadCrops() {
        isLoadingCrops = true
        Task {
            do {
                let crops = try await cropsRepository.getAllCrops()
                await MainActor.run {
                    self.allCrops = crops
                    self.isLoadingCrops = false
                }
            } catch {
                await MainActor.run {
                    self.isLoadingCrops = false
                    self.errorMessage = "Failed to load crops: \(error.localizedDescription)"
                    self.showError = true
                }
            }
        }
    }
    
    func setCropSearchQuery(_ query: String) {
        cropSearchQuery = query
    }
    
    func toggleShowAllCrops() {
        showAllCrops.toggle()
    }
    
    func toggleCrop(_ crop: String) {
        if selectedCrops.contains(crop) {
            selectedCrops.remove(crop)
        } else {
            selectedCrops.insert(crop)
        }
    }
    
    // MARK: - Post Type
    
    func setPostType(_ type: PostType) {
        postType = type
    }
    
    // MARK: - Caption
    
    func setCaption(_ text: String) {
        caption = text
    }
    
    // MARK: - Media Items
    
    func addMediaItem(localUri: String, type: MediaItem.MediaType) {
        let item = MediaItem(localUri: localUri, type: type)
        mediaItems.append(item)
    }
    
    func removeMediaItem(_ item: MediaItem) {
        mediaItems.removeAll { $0.id == item.id }
    }
    
    // MARK: - Crops

    
    // MARK: - Visibility
    
    func setVisibility(_ visibility: PostVisibility) {
        self.visibility = visibility
    }
    
    // MARK: - Location
    
    func setLocation(name: String?, latitude: Double?, longitude: Double?) {
        locationName = name
        locationLatitude = latitude
        locationLongitude = longitude
    }
    
    func clearLocation() {
        locationName = nil
        locationLatitude = nil
        locationLongitude = nil
    }
    
    // MARK: - Hashtags
    
    func setHashtagInput(_ input: String) {
        hashtagInput = input
    }
    
    func addHashtag(_ tag: String) {
        let cleanTag = tag.replacingOccurrences(of: "#", with: "").trimmingCharacters(in: .whitespaces)
        if !cleanTag.isEmpty && !hashtags.contains(cleanTag) {
            hashtags.append(cleanTag)
            hashtagInput = ""
        }
    }
    
    func removeHashtag(_ tag: String) {
        hashtags.removeAll { $0 == tag }
    }
    
    // MARK: - Target Expertise (for questions)
    
    func toggleExpertise(_ expertise: String) {
        if targetExpertise.contains(expertise) {
            targetExpertise.remove(expertise)
        } else {
            targetExpertise.insert(expertise)
        }
    }
    
    // MARK: - Voice Recording
    
    func hasAudioPermission() -> Bool {
        return voiceRecordingRepository.hasPermission()
    }
    
    func startVoiceRecording() {
        Task {
            // Check permission first
            if !voiceRecordingRepository.hasPermission() {
                do {
                    let granted = try await voiceRecordingRepository.requestPermission()
                    if !granted.boolValue {
                        await MainActor.run {
                            showPermissionDeniedAlert = true
                        }
                        return
                    }
                } catch {
                    await MainActor.run {
                        errorMessage = "Failed to request permission: \(error.localizedDescription)"
                        showError = true
                    }
                    return
                }
            }
            
            do {
                // Generate a new file path
                let filePath = voiceRecordingRepository.generateVoiceCaptionPath()
                currentVoiceCaptionPath = filePath
                
                try await voiceRecordingRepository.startRecording(outputFilePath: filePath)
                
                await MainActor.run {
                    isRecordingVoice = true
                    recordingDuration = 0
                    errorMessage = nil
                }
            } catch {
                await MainActor.run {
                    isRecordingVoice = false
                    errorMessage = "Failed to start recording: \(error.localizedDescription)"
                    showError = true
                }
            }
        }
    }
    
    func stopVoiceRecording() {
        Task {
            do {
                let durationResult = try await voiceRecordingRepository.stopRecording()
                let duration = Int(durationResult.int32Value)
                let filePath = currentVoiceCaptionPath
                
                await MainActor.run {
                    isRecordingVoice = false
                    voiceCaptionUri = filePath
                    voiceCaptionDuration = duration
                    recordingDuration = 0
                    errorMessage = nil
                }
            } catch {
                await MainActor.run {
                    isRecordingVoice = false
                    errorMessage = "Failed to stop recording: \(error.localizedDescription)"
                    showError = true
                }
            }
        }
    }
    
    func cancelVoiceRecording() {
        Task {
            try? await voiceRecordingRepository.cancelRecording()
            currentVoiceCaptionPath = nil
            
            await MainActor.run {
                isRecordingVoice = false
                recordingDuration = 0
            }
        }
    }
    
    func removeVoiceCaption() {
        // Stop playback first
        stopVoicePlayback()
        
        Task {
            if let uri = voiceCaptionUri {
                try? await voiceRecordingRepository.deleteRecording(filePath: uri)
            }
            
            await MainActor.run {
                voiceCaptionUri = nil
                voiceCaptionDuration = 0
                isPlayingVoice = false
                playbackProgress = 0
            }
        }
    }
    
    func toggleVoiceRecording() {
        if isRecordingVoice {
            stopVoiceRecording()
        } else {
            startVoiceRecording()
        }
    }
    
    // MARK: - Voice Playback
    
    func playVoiceCaption() {
        guard let uri = voiceCaptionUri else { return }
        
        // Stop any existing playback
        stopVoicePlayback()
        
        do {
            let url = URL(fileURLWithPath: uri)
            audioPlayer = try AVAudioPlayer(contentsOf: url)
            audioPlayer?.delegate = nil // We'll use timer instead
            
            // Configure audio session for playback
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
            
            audioPlayer?.play()
            isPlayingVoice = true
            playbackProgress = 0
            
            // Start progress timer
            startPlaybackProgressUpdates()
            
        } catch {
            errorMessage = "Failed to play: \(error.localizedDescription)"
            showError = true
            stopVoicePlayback()
        }
    }
    
    func stopVoicePlayback() {
        // Stop timer
        playbackTimer?.invalidate()
        playbackTimer = nil
        
        // Stop player
        audioPlayer?.stop()
        audioPlayer = nil
        
        isPlayingVoice = false
        playbackProgress = 0
        
        // Deactivate audio session
        try? AVAudioSession.sharedInstance().setActive(false)
    }
    
    func toggleVoicePlayback() {
        if isPlayingVoice {
            stopVoicePlayback()
        } else {
            playVoiceCaption()
        }
    }
    
    private func startPlaybackProgressUpdates() {
        playbackTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self = self, let player = self.audioPlayer else { return }
                
                if player.isPlaying {
                    self.playbackProgress = Int(player.currentTime)
                } else {
                    // Playback finished
                    self.stopVoicePlayback()
                }
            }
        }
    }
    
    // MARK: - Build Post Input
    
    func buildPostInput() -> CreatePostInput {
        return CreatePostInput(
            type: postType,
            text: caption,
            mediaItems: mediaItems,
            voiceCaptionUri: voiceCaptionUri,
            voiceCaptionDurationSeconds: voiceCaptionDuration,
            crops: Array(selectedCrops),
            hashtags: hashtags,
            locationName: locationName,
            locationLatitude: locationLatitude,
            locationLongitude: locationLongitude,
            visibility: visibility,
            targetExpertise: postType == .question ? Array(targetExpertise) : []
        )
    }
    
    // MARK: - Clear Error
    
    func clearError() {
        errorMessage = nil
        showError = false
    }
    
    // MARK: - Cleanup
    
    func cleanup() {
        // Stop playback
        stopVoicePlayback()
        
        // Cancel any ongoing recording
        if voiceRecordingRepository.isRecording() {
            Task {
                try? await voiceRecordingRepository.cancelRecording()
            }
        }
    }
    
    deinit {
        // Note: Can't call async cleanup in deinit
        // The UI should call cleanup() when dismissing
    }
}
