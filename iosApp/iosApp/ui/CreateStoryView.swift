import SwiftUI
import PhotosUI
import AVFoundation
import AVKit
import UniformTypeIdentifiers
import Photos
import UIKit
import Shared

// MARK: - Story Text Overlay
struct StoryTextOverlay: Identifiable {
    let id = UUID()
    var text: String
    var positionX: Float
    var positionY: Float
    var fontSize: Float = 24 // Larger default size
    var textColor: UInt64 = 0xFFFFFFFF // ARGB color (default white)
    var rotation: Float = 0 // Rotation angle in degrees
    var scale: Float = 1.0 // Scale factor (1.0 = normal)
}

// MARK: - Post Visibility Enum (for stories)
enum StoryVisibility {
    case `public`
    case followers
}

// MARK: - Create Story View
struct CreateStoryView: View {
    // ViewModel - handles all business logic and repository management
    @StateObject private var viewModel = CreateStoryViewModel()
    
    // State
    @State private var selectedMediaUrl: URL? = nil
    @State private var mediaType: MediaType? = nil
    @State private var textOverlays: [StoryTextOverlay] = []
    @State private var location: CreateStoryLocation? = nil
    @State private var visibility: StoryVisibility = .public
    @State private var showTextInputSheet = false
    @State private var showLocationPicker = false
    @State private var selectedTextOverlayIndex: Int? = nil
    @State private var selectedOverlayId: UUID? = nil // For Instagram-style selection
    
    // Media picker state
    @State private var showCamera = false
    @State private var showImagePicker = false
    @State private var showVisibilityDialog = false
    @State private var showVideoPicker = false
    @State private var showGalleryPicker = false
    @State private var imagePickerSourceType: UIImagePickerController.SourceType = .camera
    @State private var selectedPhotos: [PhotosPickerItem] = []
    @State private var showCameraUnavailableAlert = false
    @State private var isVideoFromCamera = false
    @State private var locationPositionX: Float = 0.5
    @State private var locationPositionY: Float = 0.5
    @State private var isLocationOverlaySelected = false
    @State private var showSavedToPhotosConfirmation = false
    @State private var saveToPhotosError: String? = nil
    
    var onBackClick: () -> Void = {}
    var onStoryCreated: () -> Void = {}
    
    private var isStoryEnabled: Bool {
        selectedMediaUrl != nil && !viewModel.isCreatingStory
    }
    
    var body: some View {
        Group {
            if let mediaUrl = selectedMediaUrl {
                CreateStoryMediaSection(
                    mediaUrl: mediaUrl,
                    mediaType: mediaType,
                    isVideoFromCamera: isVideoFromCamera,
                    textOverlays: textOverlays,
                    selectedOverlayId: selectedOverlayId,
                    location: location,
                    locationPositionX: locationPositionX,
                    locationPositionY: locationPositionY,
                    isLocationOverlaySelected: isLocationOverlaySelected,
                    onLocationOverlaySelect: { selectedOverlayId = nil; isLocationOverlaySelected = true },
                    onLocationPositionChange: { locationPositionX = $0; locationPositionY = $1 },
                    onRemoveLocation: { location = nil; isLocationOverlaySelected = false },
                    onBackgroundTap: { selectedOverlayId = nil; isLocationOverlaySelected = false },
                    onPositionChange: { index, newX, newY in
                        guard textOverlays.indices.contains(index) else { return }
                        var updated = textOverlays
                        updated[index].positionX = newX
                        updated[index].positionY = newY
                        textOverlays = updated
                    },
                    onSizeChange: { index, newSize in
                        guard textOverlays.indices.contains(index) else { return }
                        var updated = textOverlays
                        updated[index].fontSize = newSize
                        textOverlays = updated
                    },
                    onRotationChange: { index, newRotation in
                        guard textOverlays.indices.contains(index) else { return }
                        var updated = textOverlays
                        updated[index].rotation = newRotation
                        textOverlays = updated
                    },
                    onScaleChange: { index, newScale in
                        guard textOverlays.indices.contains(index) else { return }
                        var updated = textOverlays
                        updated[index].scale = newScale
                        textOverlays = updated
                    },
                    onSelect: { index in
                        guard textOverlays.indices.contains(index) else { return }
                        selectedOverlayId = textOverlays[index].id
                    },
                    onEdit: { index in
                        selectedTextOverlayIndex = index
                        showTextInputSheet = true
                    },
                    onDelete: { index in
                        let overlayId = textOverlays[index].id
                        textOverlays.remove(at: index)
                        if selectedOverlayId == overlayId {
                            selectedOverlayId = nil
                        }
                    }
                )
            } else {
                VStack(spacing: 32) {
                    Spacer()
                    Text("Create Your Story")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                    HStack(spacing: 16) {
                        StoryMediaSelectionButton(
                            icon: "camera.fill",
                            label: "Camera",
                            onClick: {
                                if UIImagePickerController.isSourceTypeAvailable(.camera) {
                                    imagePickerSourceType = .camera
                                    showCamera = true
                                } else {
                                    showCameraUnavailableAlert = true
                                }
                            }
                        )
                        StoryMediaSelectionButton(
                            icon: "photo.fill",
                            label: "Gallery",
                            onClick: { showGalleryPicker = true }
                        )
                    }
                    Spacer()
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .safeAreaInset(edge: .top, spacing: 0) {
            CreateStoryTopBar(
                onBackClick: onBackClick,
                showTextAndLocation: selectedMediaUrl != nil,
                location: location,
                onAddText: {
                    showTextInputSheet = true
                    selectedTextOverlayIndex = nil
                },
                onLocation: { showLocationPicker = true }
            )
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if selectedMediaUrl != nil {
                CreateStoryBottomSection(
                    onShareTap: { showVisibilityDialog = true },
                    onSaveTap: { saveComposedStoryToPhotos() },
                    isShareEnabled: isStoryEnabled
                )
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .overlay {
            if viewModel.isCreatingStory {
                Color.black.opacity(0.5)
                    .ignoresSafeArea()
                VStack(spacing: 16) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(1.5)
                    Text("Creating story...")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white)
                }
                .padding(24)
                .background(Color.black.opacity(0.7))
                .cornerRadius(12)
            }
        }
        .alert("Error", isPresented: $viewModel.showError) {
            Button("OK") {
                viewModel.errorMessage = nil
            }
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error occurred")
        }
        .overlay {
            if showSavedToPhotosConfirmation {
                VStack {
                    Spacer()
                    Text("Saved to Photos")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(Color.black.opacity(0.75))
                        .cornerRadius(8)
                    Spacer().frame(height: 120)
                }
                .allowsHitTesting(false)
            }
        }
        .onChange(of: showSavedToPhotosConfirmation) { newValue in
            if newValue {
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                    showSavedToPhotosConfirmation = false
                }
            }
        }
        .alert("Save failed", isPresented: .init(get: { saveToPhotosError != nil }, set: { if !$0 { saveToPhotosError = nil } })) {
            Button("OK") { saveToPhotosError = nil }
        } message: {
            Text(saveToPhotosError ?? "")
        }
        .fullScreenCover(isPresented: $showCamera) {
            // Theme: black background so status bar and bottom match (no white bars)
            // Photo is used as captured (no in-app crop). Native camera may support aspect ratio on some devices.
            ZStack {
                Color.black.ignoresSafeArea()
                MediaPicker(
                    sourceType: imagePickerSourceType,
                    mediaTypes: [UTType.image.identifier, UTType.movie.identifier],
                    allowsEditing: false,
                    onImagePicked: { image in
                        saveImageToTempFile(image: image) { url in
                            if let url = url {
                                selectedMediaUrl = url
                                mediaType = .image
                            }
                            showCamera = false
                        }
                    },
                    onVideoPicked: { url in
                        saveVideoToTempFile(sourceURL: url) { copiedURL in
                            if let u = copiedURL {
                                selectedMediaUrl = u
                                mediaType = .video
                                isVideoFromCamera = true
                            }
                            showCamera = false
                        }
                    }
                )
            }
            .preferredColorScheme(.dark)
        }
        .sheet(isPresented: $showImagePicker) {
            // Image picker: Allow editing for videos (trim screen), but not for photos
            MediaPicker(
                sourceType: imagePickerSourceType,
                mediaTypes: [UTType.image.identifier, UTType.movie.identifier],
                allowsEditing: true, // Videos will show trim screen, photos won't show edit screen (iOS handles this)
                onImagePicked: { image in
                    saveImageToTempFile(image: image) { url in
                        if let url = url {
                            selectedMediaUrl = url
                            mediaType = .image
                        }
                    }
                },
                onVideoPicked: { url in
                    selectedMediaUrl = url
                    mediaType = .video
                    isVideoFromCamera = false
                }
            )
        }
        .photosPicker(
            isPresented: $showGalleryPicker,
            selection: $selectedPhotos,
            maxSelectionCount: 1,
            matching: .any(of: [.images, .videos])
        )
        .sheet(isPresented: $showTextInputSheet) {
            TextOverlayInputSheet(
                initialText: selectedTextOverlayIndex.flatMap { textOverlays.indices.contains($0) ? textOverlays[$0].text : nil } ?? "",
                initialColor: selectedTextOverlayIndex.flatMap { textOverlays.indices.contains($0) ? textOverlays[$0].textColor : nil } ?? 0xFFFFFFFF,
                onDismiss: {
                    showTextInputSheet = false
                    selectedTextOverlayIndex = nil
                },
                onConfirm: { text, color in
                    if let index = selectedTextOverlayIndex, textOverlays.indices.contains(index) {
                        textOverlays[index].text = text
                        textOverlays[index].textColor = color
                    } else {
                        let newOverlay = StoryTextOverlay(
                            text: text,
                            positionX: 0.5,
                            positionY: 0.5, // Center of screen
                            fontSize: 28, // Larger default size to match Android
                            textColor: color,
                            rotation: 0,
                            scale: 1.0
                        )
                        textOverlays.append(newOverlay)
                        // DON'T auto-select - let user tap to select
                        selectedOverlayId = nil
                    }
                    showTextInputSheet = false
                    selectedTextOverlayIndex = nil
                }
            )
        }
        .alert("Camera Unavailable", isPresented: $showCameraUnavailableAlert) {
            Button("OK", role: .cancel) { }
        } message: {
            Text("Camera is not available on this device.")
        }
        .confirmationDialog("Who can see your story?", isPresented: $showVisibilityDialog, titleVisibility: .visible) {
            Button("Public") {
                visibility = .public
                showVisibilityDialog = false  // Dismiss dialog first
                // Small delay to ensure dialog dismisses before loader appears
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    handleShareClick()
                }
            }
            Button("My Followers") {
                visibility = .followers
                showVisibilityDialog = false  // Dismiss dialog first
                // Small delay to ensure dialog dismisses before loader appears
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    handleShareClick()
                }
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("Choose visibility for this story.")
        }
        .onChange(of: selectedPhotos) { newItems in
            Task {
                for item in newItems {
                    await loadMediaItem(from: item)
                }
                selectedPhotos = []
            }
        }
        .sheet(isPresented: $showLocationPicker) {
            StoryLocationSelectionSheet(
                onSelected: { loc in
                    location = loc
                    locationPositionX = 0.5
                    locationPositionY = 0.5
                    showLocationPicker = false
                },
                onDismiss: { showLocationPicker = false }
            )
        }
    }
    
    // MARK: - Helper Functions
    @MainActor
    private func loadMediaItem(from item: PhotosPickerItem) async {
        // Try to load as video first
        if let movie = try? await item.loadTransferable(type: StoryMovie.self) {
            selectedMediaUrl = movie.url
            mediaType = .video
            isVideoFromCamera = false
            return
        }
        
        // Try to load as image data
        if let data = try? await item.loadTransferable(type: Data.self),
           let uiImage = UIImage(data: data) {
            saveImageToTempFile(image: uiImage) { url in
                if let url = url {
                    selectedMediaUrl = url
                    mediaType = .image
                }
            }
        }
    }
    
    private func saveImageToTempFile(image: UIImage, completion: @escaping (URL?) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            if let imageData = image.jpegData(compressionQuality: 0.8) {
                let tempURL = FileManager.default.temporaryDirectory
                    .appendingPathComponent(UUID().uuidString)
                    .appendingPathExtension("jpg")
                
                do {
                    try imageData.write(to: tempURL)
                    DispatchQueue.main.async {
                        completion(tempURL)
                    }
                } catch {
                    DispatchQueue.main.async {
                        completion(nil)
                    }
                }
            } else {
                DispatchQueue.main.async {
                    completion(nil)
                }
            }
        }
    }
    
    /// Copy video to app-owned temp file so playback is stable (avoids picker lifecycle and "stops in the middle" for camera recordings).
    private func saveVideoToTempFile(sourceURL: URL, completion: @escaping (URL?) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            let tempURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("story_video_\(UUID().uuidString)")
                .appendingPathExtension("mp4")
            do {
                if FileManager.default.fileExists(atPath: tempURL.path) {
                    try FileManager.default.removeItem(at: tempURL)
                }
                try FileManager.default.copyItem(at: sourceURL, to: tempURL)
                DispatchQueue.main.async {
                    completion(tempURL)
                }
            } catch {
                DispatchQueue.main.async {
                    completion(sourceURL)
                }
            }
        }
    }
    
    // MARK: - Story Creation
    @MainActor
    private func handleShareClick() {
        guard let mediaUrl = selectedMediaUrl,
              let mediaTypeValue = mediaType else {
            return
        }
        
        // Set loading state immediately to show loader right away
        viewModel.isCreatingStory = true
        
        Task {
            do {
                // Convert local MediaType to Shared.MediaType
                let sharedMediaType: Shared.MediaType = mediaTypeValue == .image ? .image : .video
                
                // Convert iOS text overlays to Shared text overlays
                let sharedTextOverlays = textOverlays.map { overlay in
                    Shared.StoryTextOverlay(
                        id: overlay.id.uuidString,
                        text: overlay.text,
                        positionX: overlay.positionX,
                        positionY: overlay.positionY,
                        fontSize: overlay.fontSize,
                        textColor: Int64(overlay.textColor),
                        rotation: overlay.rotation,
                        scale: overlay.scale
                    )
                }
                
                // Convert iOS location to Shared location
                let sharedLocation = location.map { loc in
                    Shared.CreateStoryLocation(
                        name: loc.name,
                        latitude: loc.latitude != nil ? KotlinDouble(value: loc.latitude!) : nil,
                        longitude: loc.longitude != nil ? KotlinDouble(value: loc.longitude!) : nil
                    )
                }
                
                // Use ViewModel to build story input with proper media handling
                let storyInput = try await viewModel.buildStoryInput(
                    mediaUrl: mediaUrl,
                    mediaType: sharedMediaType,
                    textOverlays: sharedTextOverlays,
                    location: sharedLocation,
                    visibility: visibility
                )
                
                // Use ViewModel to create the story
                await viewModel.createStory(input: storyInput)
                
                // If successful, call the callback
                if !viewModel.isCreatingStory && viewModel.errorMessage == nil {
                    onStoryCreated()
                }
            } catch {
                // Error is handled by ViewModel and shown via errorMessage
                print("Failed to create story: \(error.localizedDescription)")
            }
        }
    }
    
    // MARK: - Save composed story to photo library
    private func saveComposedStoryToPhotos() {
        guard let mediaUrl = selectedMediaUrl else { return }
        let mediaTypeValue = mediaType
        let overlays = textOverlays
        let loc = location
        let locX = locationPositionX
        let locY = locationPositionY
        
        Task {
            let image: UIImage? = await Task.detached(priority: .userInitiated) {
                CreateStoryView.buildComposedImage(
                    mediaUrl: mediaUrl,
                    mediaType: mediaTypeValue,
                    textOverlays: overlays,
                    location: loc,
                    locationPositionX: locX,
                    locationPositionY: locY
                )
            }.value
            
            await MainActor.run {
                guard let image = image else {
                    saveToPhotosError = "Could not create image"
                    return
                }
                
                // Convert image to data safely
                guard let imageData = image.pngData() else {
                    saveToPhotosError = "Could not convert image to data"
                    return
                }
                
                PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                    DispatchQueue.main.async {
                        guard status == .authorized || status == .limited else {
                            self.saveToPhotosError = "Photo library access is required to save. Please enable it in Settings."
                            return
                        }
                        
                        PHPhotoLibrary.shared().performChanges({
                            let request = PHAssetCreationRequest.forAsset()
                            request.addResource(with: .photo, data: imageData, options: nil)
                        }, completionHandler: { success, error in
                            DispatchQueue.main.async {
                                if success {
                                    self.showSavedToPhotosConfirmation = true
                                } else {
                                    self.saveToPhotosError = error?.localizedDescription ?? "Failed to save to Photos"
                                }
                            }
                        })
                    }
                }
            }
        }
    }
    
    /// Builds a single composited UIImage from media + text overlays + location (for save to Photos).
    private static func buildComposedImage(
        mediaUrl: URL,
        mediaType: MediaType?,
        textOverlays: [StoryTextOverlay],
        location: CreateStoryLocation?,
        locationPositionX: Float,
        locationPositionY: Float
    ) -> UIImage? {
        let baseImage: UIImage?
        if mediaType == .video {
            let asset = AVAsset(url: mediaUrl)
            let generator = AVAssetImageGenerator(asset: asset)
            generator.appliesPreferredTrackTransform = true
            generator.maximumSize = CGSize(width: 1080, height: 1920)
            var time = CMTime.zero
            guard let cgImage = try? generator.copyCGImage(at: time, actualTime: nil) else { return nil }
            baseImage = UIImage(cgImage: cgImage)
        } else {
            if let data = try? Data(contentsOf: mediaUrl), let img = UIImage(data: data) {
                baseImage = img
            } else if let img = UIImage(contentsOfFile: mediaUrl.path) {
                baseImage = img
            } else {
                return nil
            }
        }
        guard let base = baseImage else { return nil }
        let size = base.size
        let screenW = Float(UIScreen.main.bounds.width)
        let screenH = Float(UIScreen.main.bounds.height)
        let scaleX = size.width / CGFloat(screenW)
        let scaleY = size.height / CGFloat(screenH)
        
        let renderer = UIGraphicsImageRenderer(size: size)
        let image = renderer.image { ctx in
            base.draw(at: .zero)
            let c = ctx.cgContext
            
            for overlay in textOverlays {
                let px = CGFloat(overlay.positionX) * size.width
                let py = CGFloat(overlay.positionY) * size.height
                let font = UIFont.boldSystemFont(ofSize: CGFloat(overlay.fontSize * Float(scaleX)))
                let color = uiColorFromHex(overlay.textColor)
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: font,
                    .foregroundColor: color
                ]
                let textSize = (overlay.text as NSString).size(withAttributes: attrs)
                let rotation = CGFloat(overlay.rotation) * .pi / 180
                c.saveGState()
                c.translateBy(x: px + textSize.width / 2, y: py + textSize.height / 2)
                c.rotate(by: rotation)
                c.translateBy(x: -textSize.width / 2, y: -textSize.height / 2)
                (overlay.text as NSString).draw(at: .zero, withAttributes: attrs)
                c.restoreGState()
            }
            
            if let loc = location {
                let px = CGFloat(locationPositionX) * size.width
                let py = CGFloat(locationPositionY) * size.height
                let name = loc.name as NSString
                let font = UIFont.systemFont(ofSize: 14 * scaleX)
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: font,
                    .foregroundColor: UIColor.white
                ]
                let textSize = name.size(withAttributes: attrs)
                let pillW = textSize.width + 24 * scaleX
                let pillH = textSize.height + 16 * scaleY
                let rect = CGRect(x: px, y: py, width: pillW, height: pillH)
                let green = UIColor(red: 0.176, green: 0.416, blue: 0.310, alpha: 0.85)
                let path = UIBezierPath(roundedRect: rect, cornerRadius: min(rect.width, rect.height) / 2)
                green.setFill()
                path.fill()
                name.draw(at: CGPoint(x: px + 12 * scaleX, y: py + 8 * scaleY), withAttributes: attrs)
            }
        }
        return image
    }
}

// MARK: - Create Story Top Bar (Back, title, Text + Location icons)
private struct CreateStoryTopBar: View {
    let onBackClick: () -> Void
    let showTextAndLocation: Bool
    let location: CreateStoryLocation?
    let onAddText: () -> Void
    let onLocation: () -> Void
    
    var body: some View {
        HStack {
            Button(action: onBackClick) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(12)
                    .background(Color.black.opacity(0.3))
                    .clipShape(Circle())
            }
            Spacer()
            Text("Your Story")
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(.white)
            Spacer()
            if showTextAndLocation {
                HStack(spacing: 20) {
                    Button(action: onAddText) {
                        Image(systemName: "textformat")
                            .font(.system(size: 22))
                            .foregroundColor(.white)
                    }
                    Button(action: onLocation) {
                        Image(systemName: "location.fill")
                            .font(.system(size: 22))
                            .foregroundColor(location != nil ? Color(red: 0.176, green: 0.416, blue: 0.310) : .white)
                    }
                }
                .frame(width: 88, height: 44)
            } else {
                Color.clear.frame(width: 88, height: 44)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(Color.black.opacity(0.4))
    }
}

// MARK: - Looping Video Player (start only when view is visible; support recorded-video mode with play button)
private final class LoopingVideoPlayerHolder: ObservableObject {
    let player: AVPlayer
    private var item: AVPlayerItem?
    private var endObserver: NSObjectProtocol?
    private var timeObserver: Any?
    private let isRecordedFromCamera: Bool
    
    @Published var didPlayToEnd = false
    
    init(url: URL, isRecordedFromCamera: Bool = false) {
        self.isRecordedFromCamera = isRecordedFromCamera
        let playerItem = AVPlayerItem(url: url)
        item = playerItem
        player = AVPlayer(playerItem: playerItem)
        player.isMuted = true
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: playerItem,
            queue: .main
        ) { [weak self] _ in
            guard let self = self else { return }
            if self.isRecordedFromCamera {
                self.player.seek(to: .zero)
                self.didPlayToEnd = true
            } else {
                self.player.seek(to: .zero)
                self.player.play()
            }
        }
        if !isRecordedFromCamera {
            let interval = CMTime(seconds: 0.25, preferredTimescale: 600)
            timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
                guard let self = self, let item = self.player.currentItem else { return }
                let duration = item.duration
                guard duration.seconds.isFinite, duration.seconds > 0.5 else { return }
                if time.seconds >= duration.seconds - 0.3 {
                    self.player.seek(to: .zero)
                    self.player.play()
                }
            }
        }
    }
    
    func startPlayback() {
        didPlayToEnd = false
        player.play()
    }
    
    func pausePlayback() {
        player.pause()
    }
    
    deinit {
        if let o = endObserver { NotificationCenter.default.removeObserver(o) }
        if let t = timeObserver { player.removeTimeObserver(t) }
        player.pause()
    }
}

private struct CreateStoryLoopingVideoView: View {
    let url: URL
    let isRecordedFromCamera: Bool
    @StateObject private var holder: LoopingVideoPlayerHolder
    @State private var showPlayButton: Bool
    
    init(url: URL, isRecordedFromCamera: Bool) {
        self.url = url
        self.isRecordedFromCamera = isRecordedFromCamera
        _holder = StateObject(wrappedValue: LoopingVideoPlayerHolder(url: url, isRecordedFromCamera: isRecordedFromCamera))
        _showPlayButton = State(initialValue: isRecordedFromCamera)
    }
    
    var body: some View {
        ZStack {
            VideoPlayer(player: holder.player)
                .onAppear {
                    if !isRecordedFromCamera {
                        DispatchQueue.main.async { holder.startPlayback() }
                    }
                }
                .onDisappear {
                    holder.pausePlayback()
                }
                .onChange(of: holder.didPlayToEnd) { newValue in
                    if newValue { showPlayButton = true }
                }
            if showPlayButton {
                Color.black.opacity(0.3)
                    .ignoresSafeArea()
                Button(action: {
                    showPlayButton = false
                    holder.startPlayback()
                }) {
                    Image(systemName: "play.circle.fill")
                        .font(.system(size: 72))
                        .foregroundColor(.white)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Create Story Media Section (image/video + text overlays in one block)
private struct CreateStoryMediaSection: View {
    let mediaUrl: URL
    let mediaType: MediaType?
    let isVideoFromCamera: Bool
    let textOverlays: [StoryTextOverlay]
    let selectedOverlayId: UUID?
    let location: CreateStoryLocation?
    let locationPositionX: Float
    let locationPositionY: Float
    let isLocationOverlaySelected: Bool
    let onLocationOverlaySelect: () -> Void
    let onLocationPositionChange: (Float, Float) -> Void
    let onRemoveLocation: () -> Void
    let onBackgroundTap: () -> Void
    let onPositionChange: (Int, Float, Float) -> Void
    let onSizeChange: (Int, Float) -> Void
    let onRotationChange: (Int, Float) -> Void
    let onScaleChange: (Int, Float) -> Void
    let onSelect: (Int) -> Void
    let onEdit: (Int) -> Void
    let onDelete: (Int) -> Void
    
    var body: some View {
        ZStack {
            if mediaType == .image {
                // Use a layout-neutral container so AsyncImage never drives parent layout
                // (AsyncImage can report intrinsic size from image dimensions and cause "zoom").
                // VideoPlayer fills the proposed frame natively; image branch must do the same.
                Color.clear
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .overlay {
                        AsyncImage(url: mediaUrl) { phase in
                            switch phase {
                            case .empty:
                                Color.gray.opacity(0.3)
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            case .failure:
                                Color.gray.opacity(0.3)
                            @unknown default:
                                Color.gray.opacity(0.3)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()
                    }
            } else {
                CreateStoryLoopingVideoView(url: mediaUrl, isRecordedFromCamera: isVideoFromCamera)
                    .id(mediaUrl)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
            }
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture { onBackgroundTap() }
            ForEach(Array(textOverlays.enumerated()), id: \.element.id) { index, overlay in
                DraggableTextOverlayView(
                    overlay: overlay,
                    isSelected: overlay.id == selectedOverlayId,
                    onPositionChange: { newX, newY in onPositionChange(index, newX, newY) },
                    onSizeChange: { newSize in onSizeChange(index, newSize) },
                    onRotationChange: { newRotation in onRotationChange(index, newRotation) },
                    onScaleChange: { newScale in onScaleChange(index, newScale) },
                    onSelect: { onSelect(index) },
                    onEdit: { onEdit(index) },
                    onDelete: { onDelete(index) }
                )
                .id(overlay.id)
            }
            if let loc = location {
                DraggableLocationOverlayView(
                    location: loc,
                    positionX: locationPositionX,
                    positionY: locationPositionY,
                    isSelected: isLocationOverlaySelected,
                    onPositionChange: onLocationPositionChange,
                    onSelect: onLocationOverlaySelect,
                    onDelete: onRemoveLocation
                )
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Create Story Bottom Section (Share + Save)
private struct CreateStoryBottomSection: View {
    let onShareTap: () -> Void
    let onSaveTap: () -> Void
    let isShareEnabled: Bool
    
    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                Button(action: onShareTap) {
                    Text("Share")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(isShareEnabled ? Color(red: 0.176, green: 0.416, blue: 0.310) : Color.gray)
                        .cornerRadius(12)
                }
                .disabled(!isShareEnabled)
                Button(action: onSaveTap) {
                    Text("Save")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color(red: 0.176, green: 0.416, blue: 0.310))
                        .cornerRadius(12)
                }
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 20)
        .background(Color.black.opacity(0.6))
    }
}

// MARK: - Story Media Selection Button
struct StoryMediaSelectionButton: View {
    let icon: String
    let label: String
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 48))
                    .foregroundColor(.white)
                
                Text(label)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white)
            }
            .frame(width: 120, height: 120)
            .background(Color(red: 0.176, green: 0.416, blue: 0.310))
            .cornerRadius(12)
        }
    }
}

// MARK: - Draggable Text Overlay View
struct DraggableTextOverlayView: View {
    let overlay: StoryTextOverlay  // Remove @State - use the overlay directly from parent
    let isSelected: Bool
    let onPositionChange: (Float, Float) -> Void
    let onSizeChange: (Float) -> Void
    let onRotationChange: (Float) -> Void
    let onScaleChange: (Float) -> Void
    let onSelect: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void
    
    /* ---------- Screen px ---------- */
    private let screenWidthPx = UIScreen.main.bounds.width
    private let screenHeightPx = UIScreen.main.bounds.height
    
    /* ---------- Stable base values ---------- */
    @State private var basePositionPx: CGPoint = .zero
    @State private var baseFontSize: Float = 28
    
    /* ---------- Live gesture state ---------- */
    @State private var localOffset: CGSize = .zero
    @State private var scale: CGFloat = 1.0
    @State private var rotation: Double = 0
    @State private var isTransforming: Bool = false
    @State private var lastRotationValue: Angle = .zero
    
    // Computed properties
    private var absoluteX: CGFloat {
        basePositionPx.x + localOffset.width
    }
    
    private var absoluteY: CGFloat {
        basePositionPx.y + localOffset.height
    }
    
    private var liveFontSize: Float {
        // Match Android: font size is multiplied by scale
        (baseFontSize * Float(scale)).clamped(to: 12...72)
    }
    
    var body: some View {
        // Text container (matches Android Row)
        HStack {
            Text(overlay.text)
                .font(.system(size: CGFloat(liveFontSize), weight: .bold))
                .foregroundColor(colorFromHex(overlay.textColor))
                .lineLimit(5)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Color.black.opacity(0.6))
        .cornerRadius(8)
        .overlay(
            // White border when selected (matches Android: 2dp white border)
            Group {
                if isSelected {
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.white, lineWidth: 2)
                }
            }
        )
        .frame(maxWidth: screenWidthPx * 0.85, alignment: .leading) // Match Android: 85% of screen width
        .overlay(
            // Delete button positioned relative to text container (matches Android)
            Group {
                if isSelected {
                    Button(action: onDelete) {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                    }
                    .frame(width: 32, height: 32)
                    .background(Color.red)
                    .clipShape(Circle())
                    .offset(x: 12, y: -12) // Match Android: offset(12.dp, (-12).dp)
                }
            },
            alignment: .topTrailing
        )
        .offset(
            x: absoluteX - screenWidthPx / 2,
            y: absoluteY - screenHeightPx / 2
        )
        .rotationEffect(.degrees(rotation))
        .scaleEffect(scale)
        .gesture(
            isSelected ? createTransformGesture() : nil
        )
        .simultaneousGesture(
            !isTransforming ? createTapGesture() : nil
        )
        .onTapGesture(count: 2) {
            // Double tap always opens edit
            if !isSelected {
                onSelect()
            }
            onEdit()
        }
        .onAppear {
            // Initialize base values (matches Android initialization)
            basePositionPx = CGPoint(
                x: CGFloat(overlay.positionX) * screenWidthPx,
                y: CGFloat(overlay.positionY) * screenHeightPx
            )
            baseFontSize = overlay.fontSize // Match Android: use fontSize directly
            scale = max(CGFloat(overlay.scale), 1.0)
            rotation = Double(overlay.rotation)
        }
        .onChange(of: overlay.positionX) { _ in
            syncBasePosition()
        }
        .onChange(of: overlay.positionY) { _ in
            syncBasePosition()
        }
        .onChange(of: overlay.fontSize) { newValue in
            if !isTransforming {
                baseFontSize = newValue
            }
        }
        .onChange(of: overlay.scale) { newValue in
            if !isTransforming {
                scale = CGFloat(newValue)
            }
        }
        .onChange(of: overlay.rotation) { newValue in
            if !isTransforming {
                rotation = Double(newValue)
            }
        }
        .onChange(of: overlay.text) { _ in
            // Force view update when text changes
            // The Text view will automatically update since overlay is not @State
        }
        .onChange(of: overlay.textColor) { _ in
            // Force view update when text color changes
        }
    }
    
    // MARK: - Gesture Helpers
    
    private func createTransformGesture() -> some Gesture {
        // Update both local state (smooth UI) and parent (model in sync). Never sync from parent during gesture so overlay stays stable.
        let magnification = MagnificationGesture()
            .onChanged { value in
                isTransforming = true
                if value != 1.0 {
                    let damped = 1.0 + (value - 1.0) * 0.45
                    scale = (scale * damped).clamped(to: 0.5...3.0)
                    onScaleChange(Float(scale))
                    onSizeChange(liveFontSize)
                }
            }
            .onEnded { _ in
                isTransforming = false
            }
        
        let rotationGesture = RotationGesture()
            .onChanged { value in
                isTransforming = true
                let rotationDeltaRadians = value.radians - lastRotationValue.radians
                lastRotationValue = value
                let rotationDeltaDegrees = (rotationDeltaRadians * 180.0 / .pi) * 0.5
                rotation = (rotation + rotationDeltaDegrees).truncatingRemainder(dividingBy: 360.0)
                onRotationChange(Float(rotation))
            }
            .onEnded { _ in
                lastRotationValue = .zero
                isTransforming = false
            }
        
        let drag = DragGesture()
            .onChanged { value in
                isTransforming = true
                localOffset = CGSize(
                    width: value.translation.width / scale,
                    height: value.translation.height / scale
                )
                let livePx = CGPoint(
                    x: basePositionPx.x + localOffset.width,
                    y: basePositionPx.y + localOffset.height
                )
                onPositionChange(
                    Float(livePx.x / screenWidthPx).clamped(to: 0...1),
                    Float(livePx.y / screenHeightPx).clamped(to: 0...1)
                )
            }
            .onEnded { _ in
                basePositionPx = CGPoint(
                    x: basePositionPx.x + localOffset.width,
                    y: basePositionPx.y + localOffset.height
                )
                localOffset = .zero
                isTransforming = false
            }
        
        return magnification
            .simultaneously(with: rotationGesture)
            .simultaneously(with: drag)
    }
    
    private func createTapGesture() -> some Gesture {
        TapGesture()
            .onEnded {
                if !isSelected {
                    onSelect()
                } else {
                    onEdit()
                }
            }
    }
    
    // MARK: - Helper Methods
    
    private func syncBasePosition() {
        if !isTransforming {
            basePositionPx = CGPoint(
                x: CGFloat(overlay.positionX) * screenWidthPx,
                y: CGFloat(overlay.positionY) * screenHeightPx
            )
            localOffset = .zero
        }
    }
}

// MARK: - Draggable Location Overlay View
private struct DraggableLocationOverlayView: View {
    let location: CreateStoryLocation
    let positionX: Float
    let positionY: Float
    let isSelected: Bool
    let onPositionChange: (Float, Float) -> Void
    let onSelect: () -> Void
    let onDelete: () -> Void
    
    private let screenWidthPx = UIScreen.main.bounds.width
    private let screenHeightPx = UIScreen.main.bounds.height
    @State private var basePositionPx: CGPoint = .zero
    @State private var localOffset: CGSize = .zero
    
    private var absoluteX: CGFloat {
        basePositionPx.x + localOffset.width
    }
    private var absoluteY: CGFloat {
        basePositionPx.y + localOffset.height
    }
    
    var body: some View {
        ZStack(alignment: .topTrailing) {
            HStack(spacing: 6) {
                Image(systemName: "location.fill")
                    .font(.system(size: 14))
                    .foregroundColor(Color(red: 0.176, green: 0.416, blue: 0.310))
                Text(location.name)
                    .font(.system(size: 14))
                    .foregroundColor(.white)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color(red: 0.176, green: 0.416, blue: 0.310).opacity(0.85))
            .cornerRadius(20)
            .overlay(
                Group {
                    if isSelected {
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color.white, lineWidth: 2)
                    }
                }
            )
            if isSelected {
                Button(action: onDelete) {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 28, height: 28)
                        .background(Color.red)
                        .clipShape(Circle())
                }
                .offset(x: 8, y: -8)
            }
        }
        .offset(
            x: absoluteX - screenWidthPx / 2,
            y: absoluteY - screenHeightPx / 2
        )
        .gesture(
            DragGesture()
                .onChanged { value in
                    localOffset = value.translation
                    let livePx = CGPoint(x: basePositionPx.x + localOffset.width, y: basePositionPx.y + localOffset.height)
                    onPositionChange(
                        Float(livePx.x / screenWidthPx).clamped(to: 0...1),
                        Float(livePx.y / screenHeightPx).clamped(to: 0...1)
                    )
                }
                .onEnded { _ in
                    basePositionPx = CGPoint(x: basePositionPx.x + localOffset.width, y: basePositionPx.y + localOffset.height)
                    localOffset = .zero
                }
        )
        .onTapGesture {
            onSelect()
        }
        .onAppear {
            basePositionPx = CGPoint(
                x: CGFloat(positionX) * screenWidthPx,
                y: CGFloat(positionY) * screenHeightPx
            )
        }
        .onChange(of: positionX) { _ in syncBasePosition() }
        .onChange(of: positionY) { _ in syncBasePosition() }
    }
    
    private func syncBasePosition() {
        basePositionPx = CGPoint(
            x: CGFloat(positionX) * screenWidthPx,
            y: CGFloat(positionY) * screenHeightPx
        )
        localOffset = .zero
    }
}

// Helper extension for clamping Double values
extension Double {
    func clamped(to range: ClosedRange<Double>) -> Double {
        return Swift.max(range.lowerBound, Swift.min(range.upperBound, self))
    }
}

// Helper extension for clamping Float values
extension Float {
    func clamped(to range: ClosedRange<Float>) -> Float {
        return Swift.max(range.lowerBound, Swift.min(range.upperBound, self))
    }
}

// Helper extension for clamping CGFloat values
extension CGFloat {
    func clamped(to range: ClosedRange<CGFloat>) -> CGFloat {
        return Swift.max(range.lowerBound, Swift.min(range.upperBound, self))
    }
}

// MARK: - Bottom Action Bar
struct BottomActionBarView: View {
    let onAddTextClick: () -> Void
    let onLocationClick: () -> Void
    let location: CreateStoryLocation?
    let onRemoveLocation: () -> Void
    @Binding var visibility: StoryVisibility
    let onShareClick: () -> Void
    let isShareEnabled: Bool
    
    var body: some View {
        VStack(spacing: 16) {
            // Action Buttons
            HStack(spacing: 40) {
                // Add Text Button
                Button(action: onAddTextClick) {
                    VStack(spacing: 4) {
                        Image(systemName: "textformat")
                            .font(.system(size: 28))
                            .foregroundColor(.white)
                        Text("Text")
                            .font(.system(size: 12))
                            .foregroundColor(.white)
                    }
                }
                
                // Location Button
                Button(action: onLocationClick) {
                    VStack(spacing: 4) {
                        Image(systemName: "location.fill")
                            .font(.system(size: 28))
                            .foregroundColor(location != nil ? Color(red: 0.176, green: 0.416, blue: 0.310) : .white)
                        Text("Location")
                            .font(.system(size: 12))
                            .foregroundColor(location != nil ? Color(red: 0.176, green: 0.416, blue: 0.310) : .white)
                    }
                }
            }
            
            // Location Display
            if let location = location {
                Button(action: onRemoveLocation) {
                    HStack {
                        Image(systemName: "location.fill")
                            .font(.system(size: 20))
                            .foregroundColor(Color(red: 0.176, green: 0.416, blue: 0.310))
                        
                        Text(location.name)
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                        
                        Spacer()
                        
                        Image(systemName: "xmark")
                            .font(.system(size: 16))
                            .foregroundColor(.white)
                    }
                    .padding(12)
                    .background(Color(red: 0.176, green: 0.416, blue: 0.310).opacity(0.2))
                    .cornerRadius(8)
                }
            }
            
            // Visibility Selection
            StoryVisibilitySelectionSection(selectedVisibility: $visibility)
            
            // Share Button
            Button(action: onShareClick) {
                Text("Share Story")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(isShareEnabled ? Color(red: 0.176, green: 0.416, blue: 0.310) : Color.gray)
                    .cornerRadius(12)
            }
            .disabled(!isShareEnabled)
        }
        .padding(16)
        .background(Color.black.opacity(0.7))
        .cornerRadius(20, corners: [.topLeft, .topRight])
    }
}

// MARK: - Text Overlay Input Sheet
struct TextOverlayInputSheet: View {
    @State private var text: String
    @State private var selectedColor: UInt64
    let onDismiss: () -> Void
    let onConfirm: (String, UInt64) -> Void
    
    init(initialText: String, initialColor: UInt64, onDismiss: @escaping () -> Void, onConfirm: @escaping (String, UInt64) -> Void) {
        _text = State(initialValue: initialText)
        _selectedColor = State(initialValue: initialColor)
        self.onDismiss = onDismiss
        self.onConfirm = onConfirm
    }
    
    let colorOptions: [(UInt64, String)] = [
        (0xFFFFFFFF, "White"),
        (0xFF000000, "Black"),
        (0xFFFF0000, "Red"),
        (0xFF0000FF, "Blue"),
        (0xFF00FF00, "Green"),
        (0xFFFFFF00, "Yellow"),
        (0xFFFF00FF, "Magenta"),
        (0xFF00FFFF, "Cyan")
    ]
    
    var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                TextField("Enter text", text: $text, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .lineLimit(3...5)
                    .padding()
                
                // Color Picker
                VStack(alignment: .leading, spacing: 8) {
                    Text("Text Color")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Color(red: 0.106, green: 0.106, blue: 0.106))
                    
                    HStack(spacing: 8) {
                        ForEach(colorOptions, id: \.0) { color, label in
                            ColorOptionView(
                                color: colorFromHex(color),
                                isSelected: selectedColor == color,
                                onClick: { selectedColor = color }
                            )
                        }
                    }
                }
                .padding(.horizontal)
                
                Spacer()
            }
            .navigationTitle("Add Text")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        onDismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Add") {
                        onConfirm(text, selectedColor)
                    }
                    .disabled(text.isEmpty)
                }
            }
        }
    }
}

// MARK: - Color Option View
struct ColorOptionView: View {
    let color: Color
    let isSelected: Bool
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            Circle()
                .fill(color)
                .frame(width: 40, height: 40)
                .overlay(
                    Circle()
                        .stroke(isSelected ? Color(red: 0.176, green: 0.416, blue: 0.310) : Color.gray, lineWidth: isSelected ? 3 : 1)
                )
        }
    }
}

// MARK: - Helper Extensions
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners
    
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}

// MARK: - Story Location Selection Sheet (Current + Manual, same as Create Post)
private struct StoryLocationSelectionSheet: View {
    @Environment(\.dismiss) var dismiss
    @State private var selectedTab: StoryLocationTab = .current
    @State private var allStates: [String] = []
    @State private var districtsForSelectedState: [String] = []
    @State private var selectedState: String? = nil
    @State private var selectedDistrict: String? = nil
    @State private var villageName: String = ""
    @State private var isLoadingStates = false
    @State private var isLoadingDistricts = false
    @State private var isLoadingLocation = false
    @State private var locationError: String? = nil
    
    private let locationRepository = IOSLocationRepository()
    let onSelected: (CreateStoryLocation) -> Void
    let onDismiss: () -> Void
    
    private let primaryGreen = Color(red: 0.176, green: 0.416, blue: 0.310)
    
    enum StoryLocationTab { case current, manual }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                HStack(spacing: 0) {
                    StoryTabButton(title: "Current Location", isSelected: selectedTab == .current, color: primaryGreen) { selectedTab = .current }
                    StoryTabButton(title: "Manual", isSelected: selectedTab == .manual, color: primaryGreen) { selectedTab = .manual }
                }
                .padding(.horizontal, 18)
                .padding(.top, 16)
                if selectedTab == .current {
                    StoryCurrentLocationTab(
                        locationRepository: locationRepository,
                        isLoading: $isLoadingLocation,
                        error: $locationError,
                        primaryGreen: primaryGreen,
                        onSuccess: { loc in onSelected(loc); dismiss() }
                    )
                } else {
                    StoryManualLocationTab(
                        locationRepository: locationRepository,
                        allStates: $allStates,
                        districts: $districtsForSelectedState,
                        selectedState: $selectedState,
                        selectedDistrict: $selectedDistrict,
                        villageName: $villageName,
                        isLoadingStates: $isLoadingStates,
                        isLoadingDistricts: $isLoadingDistricts,
                        isLoadingLocation: $isLoadingLocation,
                        error: $locationError,
                        primaryGreen: primaryGreen,
                        onLoadStates: { loadStates() },
                        onStateSelected: { state in selectState(state) },
                        onDistrictSelected: { district in selectedDistrict = district },
                        onSave: { saveManualLocation() }
                    )
                }
            }
            .navigationTitle("Add Location")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { onDismiss(); dismiss() }
                }
            }
        }
        .onAppear { loadStates() }
    }
    
    private func loadStates() {
        isLoadingStates = true
        locationError = nil
        Task {
            do {
                let states = try await locationRepository.getStates()
                await MainActor.run {
                    allStates = states.sorted()
                    isLoadingStates = false
                }
            } catch {
                await MainActor.run {
                    isLoadingStates = false
                    locationError = "Failed to load states: \(error.localizedDescription)"
                }
            }
        }
    }
    
    private func selectState(_ state: String) {
        selectedState = state
        selectedDistrict = nil
        districtsForSelectedState = []
        isLoadingDistricts = true
        Task {
            do {
                let districts = try await locationRepository.getDistricts(state: state)
                await MainActor.run {
                    districtsForSelectedState = districts.sorted()
                    isLoadingDistricts = false
                }
            } catch {
                await MainActor.run {
                    isLoadingDistricts = false
                    locationError = "Failed to load districts: \(error.localizedDescription)"
                }
            }
        }
    }
    
    private func saveManualLocation() {
        guard let state = selectedState, let district = selectedDistrict else {
            locationError = "Please select state and district"
            return
        }
        let village = villageName.trimmingCharacters(in: .whitespaces)
        let locationName = village.isEmpty ? "\(district), \(state)" : "\(village), \(district), \(state)"
        isLoadingLocation = true
        locationError = nil
        Task {
            do {
                let coordinates = try await locationRepository.forwardGeocode(locationName: locationName)
                await MainActor.run {
                    onSelected(CreateStoryLocation(
                        name: locationName,
                        latitude: coordinates?.latitude,
                        longitude: coordinates?.longitude
                    ))
                    isLoadingLocation = false
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    onSelected(CreateStoryLocation(name: locationName, latitude: nil, longitude: nil))
                    isLoadingLocation = false
                    dismiss()
                }
            }
        }
    }
}

private struct StoryTabButton: View {
    let title: String
    let isSelected: Bool
    let color: Color
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Text(title).font(.system(size: 16, weight: isSelected ? .semibold : .regular)).foregroundColor(isSelected ? color : .secondary)
                Rectangle().fill(isSelected ? color : Color.clear).frame(height: 2)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct StoryCurrentLocationTab: View {
    let locationRepository: IOSLocationRepository
    @Binding var isLoading: Bool
    @Binding var error: String?
    let primaryGreen: Color
    let onSuccess: (CreateStoryLocation) -> Void
    
    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            if let err = error {
                Text(err).font(.system(size: 14)).foregroundColor(.red).padding(.horizontal, 18).padding(.bottom, 8)
            }
            Image(systemName: "location.fill").font(.system(size: 64)).foregroundColor(primaryGreen)
            Text("Use Current Location").font(.system(size: 20, weight: .semibold)).foregroundColor(.primary)
            Text("We'll use your GPS to find your location").font(.system(size: 13)).foregroundColor(.secondary).multilineTextAlignment(.center).padding(.horizontal, 32)
            Button(action: useCurrent) {
                HStack {
                    if isLoading {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white)).scaleEffect(0.8)
                        Text("Detecting location...").font(.system(size: 16, weight: .semibold)).foregroundColor(.white)
                    } else {
                        Image(systemName: "location.fill").font(.system(size: 24))
                        Text("Use Current Location").font(.system(size: 16, weight: .semibold)).foregroundColor(.white)
                    }
                }
                .frame(maxWidth: .infinity).frame(height: 56).background(primaryGreen).cornerRadius(18)
            }
            .disabled(isLoading)
            .padding(.horizontal, 18)
            Spacer()
        }
        .padding(.top, 32)
    }
    
    private func useCurrent() {
        Task {
            if !locationRepository.hasLocationPermission() {
                do {
                    let granted = try await locationRepository.requestLocationPermission()
                    if !granted.boolValue {
                        await MainActor.run { error = "Location permission is required to use current location"; isLoading = false }
                        return
                    }
                } catch let requestError {
                    await MainActor.run { isLoading = false; error = requestError.localizedDescription }
                    return
                }
            }
            await MainActor.run { isLoading = true; error = nil }
            do {
                guard let coordinates = try await locationRepository.getCurrentLocation() else {
                    await MainActor.run { isLoading = false; error = "Unable to get current location. Please try again." }
                    return
                }
                let name = try await locationRepository.reverseGeocode(latitude: coordinates.latitude, longitude: coordinates.longitude)
                await MainActor.run {
                    isLoading = false
                    onSuccess(CreateStoryLocation(name: name ?? "\(coordinates.latitude), \(coordinates.longitude)", latitude: coordinates.latitude, longitude: coordinates.longitude))
                }
            } catch _ {
                await MainActor.run { isLoading = false; error = "Unable to get location. Please try again." }
            }
        }
    }
}

private struct StoryManualLocationTab: View {
    let locationRepository: IOSLocationRepository
    @Binding var allStates: [String]
    @Binding var districts: [String]
    @Binding var selectedState: String?
    @Binding var selectedDistrict: String?
    @Binding var villageName: String
    @Binding var isLoadingStates: Bool
    @Binding var isLoadingDistricts: Bool
    @Binding var isLoadingLocation: Bool
    @Binding var error: String?
    let primaryGreen: Color
    let onLoadStates: () -> Void
    let onStateSelected: (String) -> Void
    let onDistrictSelected: (String) -> Void
    let onSave: () -> Void
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if let err = error { Text(err).font(.system(size: 14)).foregroundColor(.red).padding(.horizontal, 18) }
                VStack(alignment: .leading, spacing: 8) {
                    Text("State").font(.system(size: 14, weight: .medium)).foregroundColor(.secondary)
                    if isLoadingStates {
                        HStack { ProgressView().scaleEffect(0.8); Text("Loading states...").foregroundColor(.secondary) }
                            .frame(maxWidth: .infinity, alignment: .leading).padding().background(Color(.systemGray6)).cornerRadius(22)
                    } else {
                        Menu {
                            ForEach(allStates, id: \.self) { state in Button(state) { onStateSelected(state) } }
                        } label: {
                            HStack {
                                Text(selectedState ?? "Select State").font(.system(size: 16)).foregroundColor(selectedState != nil ? .primary : .secondary)
                                Spacer()
                                Image(systemName: "chevron.down").font(.system(size: 14)).foregroundColor(.secondary)
                            }
                            .padding().frame(maxWidth: .infinity).background(Color(.systemGray6)).cornerRadius(22)
                        }
                    }
                }
                .padding(.horizontal, 18)
                VStack(alignment: .leading, spacing: 8) {
                    Text("District").font(.system(size: 14, weight: .medium)).foregroundColor(.secondary)
                    if isLoadingDistricts {
                        HStack { ProgressView().scaleEffect(0.8); Text("Loading districts...").foregroundColor(.secondary) }
                            .frame(maxWidth: .infinity, alignment: .leading).padding().background(Color(.systemGray6)).cornerRadius(22)
                    } else {
                        Menu {
                            ForEach(districts, id: \.self) { district in Button(district) { onDistrictSelected(district) } }
                        } label: {
                            HStack {
                                Text(selectedDistrict ?? (selectedState == nil ? "Select state first" : "Select District")).font(.system(size: 16)).foregroundColor(selectedDistrict != nil ? .primary : .secondary)
                                Spacer()
                                Image(systemName: "chevron.down").font(.system(size: 14)).foregroundColor(.secondary)
                            }
                            .padding().frame(maxWidth: .infinity).background(Color(.systemGray6)).cornerRadius(22)
                        }
                        .disabled(selectedState == nil)
                    }
                }
                .padding(.horizontal, 18)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Village (Optional)").font(.system(size: 14, weight: .medium)).foregroundColor(.secondary)
                    TextField("Enter village name", text: $villageName)
                        .font(.system(size: 16)).padding().background(Color(.systemGray6)).cornerRadius(22)
                }
                .padding(.horizontal, 18)
                Button(action: onSave) {
                    HStack {
                        if isLoadingLocation {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white)).scaleEffect(0.8)
                            Text("Confirming...").font(.system(size: 16, weight: .semibold)).foregroundColor(.white)
                        } else { Text("Save Location").font(.system(size: 16, weight: .semibold)).foregroundColor(.white) }
                    }
                    .frame(maxWidth: .infinity).frame(height: 56).background((selectedState != nil && selectedDistrict != nil && !isLoadingLocation) ? primaryGreen : primaryGreen.opacity(0.5)).cornerRadius(18)
                }
                .disabled(selectedState == nil || selectedDistrict == nil || isLoadingLocation)
                .padding(.horizontal, 18)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
        }
    }
}

// MARK: - Create Story Location
struct CreateStoryLocation {
    let name: String
    let latitude: Double?
    let longitude: Double?
}

// MARK: - Create Story Input (placeholder - will match Kotlin model)
struct CreateStoryInput {
    let mediaData: Data?
    let mediaType: MediaType?
    let thumbnailData: Data?
    let textOverlays: [StoryTextOverlay]
    let location: CreateStoryLocation?
    let visibility: StoryVisibility
}

enum MediaType {
    case image
    case video
}

// MARK: - Story Visibility Selection Section
struct StoryVisibilitySelectionSection: View {
    @Binding var selectedVisibility: StoryVisibility
    
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Who can see this?")
                .font(.system(size: 16.875, weight: .semibold))
                .foregroundColor(.white)
            
            HStack(spacing: 13.5) {
                StoryVisibilityButton(
                    label: "Public",
                    isSelected: selectedVisibility == .public,
                    action: { selectedVisibility = .public }
                )
                
                StoryVisibilityButton(
                    label: "My Followers",
                    isSelected: selectedVisibility == .followers,
                    action: { selectedVisibility = .followers }
                )
            }
            
            Text("Public stories are visible to all farmers")
                .font(.system(size: 13.5))
                .foregroundColor(.white.opacity(0.7))
        }
    }
}

// MARK: - Custom Button Style for Visibility Buttons
struct VisibilityButtonStyle: ButtonStyle {
    let isSelected: Bool
    
    func makeBody(configuration: Configuration) -> some View {
        ZStack {
            // Background
            RoundedRectangle(cornerRadius: 22)
                .fill(isSelected ? Color(red: 0.176, green: 0.416, blue: 0.310) : Color.black.opacity(0.5))
                .overlay(
                    RoundedRectangle(cornerRadius: 22)
                        .stroke(
                            isSelected ? Color.clear : Color(red: 0.176, green: 0.416, blue: 0.310).opacity(0.7),
                            lineWidth: 2
                        )
                )
                .opacity(configuration.isPressed ? 0.8 : 1.0)
            
            // Content with forced white text
            configuration.label
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 56)
    }
}

// MARK: - Story Visibility Button
struct StoryVisibilityButton: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 15.75, weight: .semibold))
                .foregroundColor(.white) // Explicit white color
        }
        .buttonStyle(VisibilityButtonStyle(isSelected: isSelected))
        .tint(.white) // Force button tint
    }
}

// MARK: - Story Movie Transferable
struct StoryMovie: Transferable {
    let url: URL
    
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { movie in
            SentTransferredFile(movie.url)
        } importing: { received in
            let copy = URL(fileURLWithPath: NSTemporaryDirectory())
                .appendingPathComponent(UUID().uuidString)
                .appendingPathExtension("mp4")
            
            if FileManager.default.fileExists(atPath: copy.path) {
                try FileManager.default.removeItem(at: copy)
            }
            
            try FileManager.default.copyItem(at: received.file, to: copy)
            return StoryMovie(url: copy)
        }
    }
}

// MARK: - Helper Function for Hex Color Conversion
private func colorFromHex(_ hex: UInt64) -> Color {
    // Check if hex includes alpha (8-digit: AARRGGBB)
    let hasAlpha = hex > 0xFFFFFF
    
    if hasAlpha {
        // 8-digit hex with alpha: AARRGGBB
        let actualAlpha = Double((hex >> 24) & 0xFF) / 255.0
        return Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: actualAlpha
        )
    } else {
        // 6-digit hex without alpha: RRGGBB
        return Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1.0
        )
    }
}

private func uiColorFromHex(_ hex: UInt64) -> UIColor {
    let hasAlpha = hex > 0xFFFFFF
    let r = CGFloat((hex >> 16) & 0xFF) / 255.0
    let g = CGFloat((hex >> 8) & 0xFF) / 255.0
    let b = CGFloat(hex & 0xFF) / 255.0
    let a: CGFloat = hasAlpha ? CGFloat((hex >> 24) & 0xFF) / 255.0 : 1.0
    return UIColor(red: r, green: g, blue: b, alpha: a)
}

// Note: MediaPicker is already defined in CreatePostView.swift and can be reused
