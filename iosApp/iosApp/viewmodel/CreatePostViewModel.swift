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
    @Published var needsLocationPermission: Bool = false
    
    // Post creation state
    @Published var isCreatingPost: Bool = false
    @Published var postCreationError: String? = nil
    
    // Location selection state
    @Published var showLocationSheet: Bool = false
    @Published var selectedState: String? = nil
    @Published var selectedDistrict: String? = nil
    @Published var villageName: String = ""
    @Published var allStates: [String] = []
    @Published var districtsForSelectedState: [String] = []
    @Published var isLoadingStates: Bool = false
    @Published var isLoadingDistricts: Bool = false
    @Published var isLoadingLocation: Bool = false
    @Published var locationError: String? = nil
    
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
    private let locationRepository = IOSLocationRepository()
    
    // Repositories for post creation
    private let storageRepository = IOSStorageRepository()
    private let postRepository = FirestorePostRepository()
    private let preferencesRepository = IOSPreferencesRepository()
    private lazy var authRepository: AuthRepository = {
        IOSAuthRepository(preferencesRepository: preferencesRepository)
    }()
    private lazy var userRepository: UserRepository = {
        FirestoreUserRepository(authRepository: authRepository)
    }()
    
    // Use case for creating posts
    private lazy var createPostUseCase: CreatePostUseCase = {
        CreatePostUseCase(
            storageRepository: storageRepository,
            postRepository: postRepository,
            authRepository: authRepository,
            userRepository: userRepository
        )
    }()
    
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
        // Store URI for now - will convert to ByteArray when creating post
        let item = MediaItem(localUri: localUri, type: type)
        mediaItems.append(item)
    }
    
    nonisolated private func filePathToKotlinByteArray(filePath: String) throws -> KotlinByteArray {
        let url: URL
        if filePath.hasPrefix("file://") {
            guard let fileURL = URL(string: filePath) else {
                throw NSError(domain: "CreatePostViewModel", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid file URI: \(filePath)"])
            }
            url = fileURL
        } else {
            url = URL(fileURLWithPath: filePath)
        }
        
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw NSError(domain: "CreatePostViewModel", code: 404, userInfo: [NSLocalizedDescriptionKey: "File does not exist: \(filePath)"])
        }
        
        let data = try Data(contentsOf: url)
        
        // Convert Data to KotlinByteArray
        let kotlinByteArray = KotlinByteArray(size: Int32(data.count))
        data.withUnsafeBytes { bytes in
            for (i, b) in bytes.enumerated() {
                kotlinByteArray.set(index: Int32(i), value: Int8(bitPattern: b))
            }
        }
        
        return kotlinByteArray
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
    
    func showLocationSelectionSheet() {
        showLocationSheet = true
        loadStatesAndDistricts()
    }
    
    func hideLocationSelectionSheet() {
        showLocationSheet = false
    }
    
    func loadStatesAndDistricts() {
        isLoadingStates = true
        Task {
            do {
                let states = try await locationRepository.getStates()
                await MainActor.run {
                    self.allStates = states.sorted()
                    self.isLoadingStates = false
                }
            } catch {
                await MainActor.run {
                    self.isLoadingStates = false
                    self.locationError = "Failed to load states: \(error.localizedDescription)"
                }
            }
        }
    }
    
    func selectState(_ state: String) {
        selectedState = state
        selectedDistrict = nil // Reset district when state changes
        districtsForSelectedState = []
        loadDistricts(for: state)
    }
    
    func selectDistrict(_ district: String) {
        selectedDistrict = district
    }
    
    func setVillageName(_ name: String) {
        villageName = name
    }
    
    private func loadDistricts(for state: String) {
        isLoadingDistricts = true
        Task {
            do {
                let districts = try await locationRepository.getDistricts(state: state)
                await MainActor.run {
                    self.districtsForSelectedState = districts.sorted()
                    self.isLoadingDistricts = false
                }
            } catch {
                await MainActor.run {
                    self.isLoadingDistricts = false
                    self.locationError = "Failed to load districts: \(error.localizedDescription)"
                }
            }
        }
    }
    
    func useCurrentLocation() {
        Task {
            // Check permission
            if !locationRepository.hasLocationPermission() {
                do {
                    let granted = try await locationRepository.requestLocationPermission()
                    if !granted.boolValue {
                        await MainActor.run {
                            needsLocationPermission = true
                            locationError = "Location permission is required to use current location"
                        }
                        return
                    }
                } catch {
                    await MainActor.run {
                        isLoadingLocation = false
                        locationError = "Failed to request location permission: \(error.localizedDescription)"
                    }
                    return
                }
            }
            
            await MainActor.run {
                isLoadingLocation = true
                locationError = nil
            }
            
            do {
                // Get GPS coordinates
                guard let coordinates = try await locationRepository.getCurrentLocation() else {
                    await MainActor.run {
                        isLoadingLocation = false
                        locationError = "Unable to get current location. Please try again."
                    }
                    return
                }
                
                // Reverse geocode to get location name
                let locationName = try await locationRepository.reverseGeocode(
                    latitude: coordinates.latitude,
                    longitude: coordinates.longitude
                )
                
                if let name = locationName {
                    // Log coordinates for debugging
                    print("CreatePostViewModel: Current Location Selected:")
                    print("  Name: \(name)")
                    print("  Latitude: \(coordinates.latitude)")
                    print("  Longitude: \(coordinates.longitude)")
                    
                    await MainActor.run {
                        self.locationName = name
                        self.locationLatitude = coordinates.latitude
                        self.locationLongitude = coordinates.longitude
                        self.isLoadingLocation = false
                        self.showLocationSheet = false
                        self.locationError = nil
                    }
                } else {
                    await MainActor.run {
                        isLoadingLocation = false
                        locationError = "Unable to get location name. Please try manual selection."
                    }
                }
            } catch {
                await MainActor.run {
                    isLoadingLocation = false
                    locationError = "Failed to get location: \(error.localizedDescription)"
                }
            }
        }
    }
    
    func saveManualLocation() {
        guard let state = selectedState, let district = selectedDistrict else {
            locationError = "Please select state and district"
            return
        }
        
        let village = villageName.trimmingCharacters(in: .whitespaces)
        
        // Build location name
        let locationName: String
        if !village.isEmpty {
            locationName = "\(village), \(district), \(state)"
        } else {
            locationName = "\(district), \(state)"
        }
        
        isLoadingLocation = true
        locationError = nil
        
        Task {
            do {
                // Forward geocode to get coordinates
                let coordinates = try await locationRepository.forwardGeocode(locationName: locationName)
                
                // Log coordinates for debugging
                if let coords = coordinates {
                    print("CreatePostViewModel: Manual Location Selected:")
                    print("  Name: \(locationName)")
                    print("  Latitude: \(coords.latitude)")
                    print("  Longitude: \(coords.longitude)")
                } else {
                    print("CreatePostViewModel: Manual Location Selected but geocoding failed:")
                    print("  Name: \(locationName)")
                    print("  Coordinates: nil")
                }
                
                await MainActor.run {
                    self.locationName = locationName
                    self.locationLatitude = coordinates?.latitude
                    self.locationLongitude = coordinates?.longitude
                    self.isLoadingLocation = false
                    self.showLocationSheet = false
                    self.locationError = nil
                }
            } catch {
                // Even if geocoding fails, save the location name
                await MainActor.run {
                    self.locationName = locationName
                    self.locationLatitude = nil
                    self.locationLongitude = nil
                    self.isLoadingLocation = false
                    self.showLocationSheet = false
                    self.locationError = nil
                }
            }
        }
    }
    
    func removeLocation() {
        locationName = nil
        locationLatitude = nil
        locationLongitude = nil
    }
    
    func onLocationPermissionResult(_ granted: Bool) {
        needsLocationPermission = false
        if granted {
            useCurrentLocation()
        } else {
            locationError = "Location permission is required to use current location"
        }
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
    
    // MARK: - Create Post
    
    func createPost(
        onSuccess: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        isCreatingPost = true
        postCreationError = nil
        
        Task {
            do {
                // Build Kotlin CreatePostInput from Swift state
                let location: CreatePostLocation? = {
                    guard let name = locationName else { return nil }
                    return CreatePostLocation(
                        name: name,
                        latitude: locationLatitude != nil ? KotlinDouble(value: locationLatitude!) : nil,
                        longitude: locationLongitude != nil ? KotlinDouble(value: locationLongitude!) : nil
                    )
                }()
                
                // Convert Swift MediaItems to Kotlin MediaItems with ByteArray
                let kotlinMediaItems = try await withThrowingTaskGroup(of: Shared.MediaItem.self) { group in
                    var items: [Shared.MediaItem] = []
                    
                    for item in mediaItems {
                        group.addTask {
                            let mediaData = try self.filePathToKotlinByteArray(filePath: item.localUri)
                            let thumbnailData = try item.thumbnailUri.map { try self.filePathToKotlinByteArray(filePath: $0) }
                            
                            return Shared.MediaItem(
                                mediaData: mediaData,
                                type: item.type == .image ? .image : .video,
                                thumbnailData: thumbnailData
                            )
                        }
                    }
                    
                    for try await item in group {
                        items.append(item)
                    }
                    
                    return items
                }
                
                // Convert voice caption URI to ByteArray if present
                let voiceCaptionData = try voiceCaptionUri.map { try filePathToKotlinByteArray(filePath: $0) }
                
                // Convert PostType
                let kotlinPostType: Shared.PostType = postType == .normal ? .normal : .question
                
                // Convert PostVisibility (Swift enum to Kotlin enum)
                let kotlinVisibility: Shared.PostVisibility = visibility == .public ? .public_ : .followers
                
                // Build Kotlin CreatePostInput
                let kotlinInput = Shared.CreatePostInput(
                    type: kotlinPostType,
                    text: caption,
                    mediaItems: kotlinMediaItems,
                    voiceCaptionData: voiceCaptionData,
                    voiceCaptionDurationSeconds: Int32(voiceCaptionDuration),
                    crops: Array(selectedCrops),
                    hashtags: hashtags,
                    location: location,
                    visibility: kotlinVisibility,
                    targetExpertise: postType == .question ? Array(targetExpertise) : []
                )
                
                print("CreatePostViewModel: Starting post creation with \(kotlinMediaItems.count) media items")
                
                // Call use case
                let post = try await createPostUseCase.invoke(input: kotlinInput)
                
                print("CreatePostViewModel: SUCCESS - Post created with ID: \(post.id)")
                
                await MainActor.run {
                    isCreatingPost = false
                    postCreationError = nil
                }
                
                onSuccess()
            } catch {
                print("CreatePostViewModel: FAILED - \(error.localizedDescription)")
                let errorMessage = (error as NSError).localizedDescription
                
                await MainActor.run {
                    isCreatingPost = false
                    postCreationError = errorMessage
                }
                
                onError(errorMessage)
            }
        }
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
