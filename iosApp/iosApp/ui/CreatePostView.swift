import SwiftUI
import PhotosUI
import AVFoundation
import UniformTypeIdentifiers

// MARK: - Enums
enum PostType {
    case normal
    case question
}

enum PostVisibility {
    case `public`
    case followers
}

// MARK: - Media Item
struct MediaItem: Identifiable {
    let id = UUID()
    let localUri: String
    let type: MediaType
    var thumbnailUri: String? = nil
    enum MediaType {
        case image
        case video
    }
}

// MARK: - Create Post Input
struct CreatePostInput {
    var type: PostType = .normal
    var text: String = ""
    var mediaItems: [MediaItem] = []
    var voiceCaptionUri: String? = nil
    var voiceCaptionDurationSeconds: Int = 0
    var crops: [String] = []
    var hashtags: [String] = []
    var locationName: String? = nil
    var locationLatitude: Double? = nil
    var locationLongitude: Double? = nil
    var visibility: PostVisibility = .public
    var targetExpertise: [String] = []
}

// MARK: - Create Post View
struct CreatePostView: View {
    // ViewModel
    @StateObject private var viewModel = CreatePostViewModel()
    
    // State
    @State private var postType: PostType = .normal
    @State private var caption: String = ""
    @State private var mediaItems: [MediaItem] = []
    // selectedCrops is now managed by ViewModel (viewModel.selectedCrops)
    @State private var visibility: PostVisibility = .public
    // locationName is now managed by ViewModel (viewModel.locationName)
    @State private var hashtags: [String] = []
    @State private var hashtagInput: String = ""
    
    // Question-specific state
    @State private var targetExpertise: Set<String> = []
    
    // Media picker state
    @State private var showCamera = false
    @State private var showImagePicker = false
    @State private var showVideoPicker = false
    @State private var showGalleryPicker = false
    @State private var imagePickerSourceType: UIImagePickerController.SourceType = .camera
    @State private var selectedPhotos: [PhotosPickerItem] = []
    @State private var showCameraUnavailableAlert = false
    
    
    var onBackClick: () -> Void = {}
    var onPostClick: (CreatePostInput) -> Void = { _ in }
    
    private var isPostEnabled: Bool {
        switch postType {
        case .question:
            return !caption.isEmpty
        case .normal:
            return !mediaItems.isEmpty && !caption.isEmpty
        }
    }
    
    private func buildPostInput() -> CreatePostInput {
        return CreatePostInput(
            type: postType,
            text: caption,
            mediaItems: mediaItems,
            voiceCaptionUri: viewModel.voiceCaptionUri,
            voiceCaptionDurationSeconds: viewModel.voiceCaptionDuration,
            crops: Array(viewModel.selectedCrops),
            hashtags: hashtags,
            locationName: viewModel.locationName,
            locationLatitude: viewModel.locationLatitude,
            locationLongitude: viewModel.locationLongitude,
            visibility: visibility,
            targetExpertise: postType == .question ? Array(targetExpertise) : []
        )
    }
    
    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground
                
                VStack(spacing: 0) {
                    contentScrollView
                    bottomPostButton
                }
            }
            .navigationTitle("Create Post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onBackClick) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.textPrimary)
                    }
                }
            }
        }
        .sheet(isPresented: $showCamera) {
            cameraSheet
        }
        .sheet(isPresented: $showVideoPicker) {
            videoSheet
        }
        .sheet(isPresented: $showImagePicker) {
            imagePickerSheet
        }
        .photosPicker(
            isPresented: $showGalleryPicker,
            selection: $selectedPhotos,
            maxSelectionCount: 10,
            matching: .any(of: [.images, .videos])
        )
        .sheet(isPresented: Binding(
            get: { viewModel.showLocationSheet },
            set: { if !$0 { viewModel.hideLocationSelectionSheet() } }
        )) {
            LocationSelectionSheet(viewModel: viewModel)
        }
        .onChange(of: selectedPhotos) { newItems in
            Task {
                for item in newItems {
                    await loadMediaItem(from: item)
                }
                selectedPhotos = []
            }
        }
        .onDisappear {
            viewModel.cleanup()
        }
    }
    
    // MARK: - Content Scroll View
    private var contentScrollView: some View {
        ScrollView {
            VStack(spacing: 27) {
                PostTypeToggle(selectedType: $postType)
                
                mediaSelectionButtons
                
                if !mediaItems.isEmpty {
                    MediaPreviewSection(mediaItems: $mediaItems)
                }
                
                captionTextArea
                
                voiceCaptionContent
                
                HashtagsSection(hashtags: $hashtags, hashtagInput: $hashtagInput)
                
                CropSelectionSection(viewModel: viewModel)
                
                if postType == .question {
                    TargetExpertiseSection(selectedExpertise: $targetExpertise)
                }
                
                VisibilitySelectionSection(selectedVisibility: $visibility)
                
                LocationButton(
                    location: viewModel.locationName,
                    onLocationClick: {
                        viewModel.showLocationSelectionSheet()
                    },
                    onRemoveLocation: {
                        viewModel.clearLocation()
                    }
                )
            }
            .padding(.horizontal, 18)
            .padding(.top, 24)
            .padding(.bottom, 20)
        }
        .frame(maxHeight: .infinity)
    }
    
    // MARK: - Media Selection Buttons
    private var mediaSelectionButtons: some View {
        HStack(spacing: 13.5) {
            MediaSelectionButton(icon: "camera.fill", label: "Camera") {
                if UIImagePickerController.isSourceTypeAvailable(.camera) {
                    imagePickerSourceType = .camera
                    showCamera = true
                } else {
                    showCameraUnavailableAlert = true
                }
            }
            
            MediaSelectionButton(icon: "photo.fill", label: "Gallery") {
                if #available(iOS 16.0, *) {
                    showGalleryPicker = true
                } else {
                    imagePickerSourceType = .photoLibrary
                    showImagePicker = true
                }
            }
            
            MediaSelectionButton(icon: "video.fill", label: "Video") {
                if UIImagePickerController.isSourceTypeAvailable(.camera) {
                    imagePickerSourceType = .camera
                    showVideoPicker = true
                } else if UIImagePickerController.isSourceTypeAvailable(.photoLibrary) {
                    imagePickerSourceType = .photoLibrary
                    showVideoPicker = true
                } else {
                    showCameraUnavailableAlert = true
                }
            }
        }
        .alert("Camera Unavailable", isPresented: $showCameraUnavailableAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Camera is not available on this device. Please use the Gallery option instead.")
        }
    }
    
    // MARK: - Caption Text Area
    private var captionTextArea: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 18)
                .fill(Color.appBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(Color.primaryGreen.opacity(0.13), lineWidth: 1.18)
                )
                .frame(height: 140)
            
            if caption.isEmpty {
                Text(postType == .question ? "Ask your question..." : "Enter caption")
                    .font(.system(size: 16.875))
                    .foregroundColor(.textPrimary.opacity(0.5))
                    .padding(.horizontal, 18)
                    .padding(.vertical, 18)
                    .allowsHitTesting(false)
            }
            
            TextEditor(text: $caption)
                .font(.system(size: 16.875))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 14)
                .background(Color.clear)
                .scrollContentBackground(.hidden)
                .frame(height: 140)
                .clipped()
        }
    }
    
    // MARK: - Voice Caption Content
    private var voiceCaptionContent: some View {
        VoiceCaptionSection(
            voiceCaptionUri: viewModel.voiceCaptionUri,
            voiceCaptionDuration: viewModel.isRecordingVoice ? viewModel.recordingDuration : viewModel.voiceCaptionDuration,
            isRecording: viewModel.isRecordingVoice,
            isPlaying: viewModel.isPlayingVoice,
            playbackProgress: viewModel.playbackProgress,
            onRecordClick: {
                viewModel.toggleVoiceRecording()
            },
            onPlayClick: {
                viewModel.toggleVoicePlayback()
            },
            onRemoveVoiceCaption: {
                viewModel.removeVoiceCaption()
            }
        )
        .alert("Permission Denied", isPresented: $viewModel.showPermissionDeniedAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Microphone permission is required to record voice captions. Please enable it in Settings.")
        }
        .alert("Error", isPresented: $viewModel.showError) {
            Button("OK", role: .cancel) {
                viewModel.clearError()
            }
        } message: {
            Text(viewModel.errorMessage ?? "An error occurred")
        }
    }
    
    // MARK: - Bottom Post Button
    private var bottomPostButton: some View {
        VStack(spacing: 0) {
            Divider()
                .background(Color.black.opacity(0.05))
            
            Button(action: {
                // Sync View's state to ViewModel before creating post
                viewModel.postType = postType
                viewModel.caption = caption
                viewModel.mediaItems = mediaItems
                viewModel.hashtags = hashtags
                viewModel.visibility = visibility
                viewModel.targetExpertise = targetExpertise
                
                viewModel.createPost(
                    onSuccess: {
                        // Navigate back on success
                        onBackClick()
                    },
                    onError: { error in
                        // Error is already shown in UI state, but we can show an alert here if needed
                        print("Failed to create post: \(error)")
                    }
                )
            }) {
                if viewModel.isCreatingPost {
                    HStack {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                        Text("Posting...")
                            .font(.system(size: 19.125, weight: .semibold))
                            .foregroundColor(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 65)
                    .background(Color.primaryGreen)
                    .cornerRadius(18)
                } else {
                    Text(postType == .question ? "Ask Question" : "Post")
                        .font(.system(size: 19.125, weight: .semibold))
                        .foregroundColor(isPostEnabled ? .white : Color(red: 0.608, green: 0.608, blue: 0.608))
                        .frame(maxWidth: .infinity)
                        .frame(height: 65)
                        .background(isPostEnabled ? Color.primaryGreen : Color(red: 0.898, green: 0.898, blue: 0.898))
                        .cornerRadius(18)
                }
            }
            .disabled(!isPostEnabled || viewModel.isCreatingPost)
            .padding(.horizontal, 18)
            .padding(.top, 19)
        }
        .background(Color.appBackground)
    }
    
    // MARK: - Camera Sheet (supports both image and video, like Story)
    private var cameraSheet: some View {
        MediaPicker(
            sourceType: imagePickerSourceType,
            mediaTypes: [UTType.image.identifier, UTType.movie.identifier],
            allowsEditing: false,
            onImagePicked: { image in
                saveImageToTempFile(image: image) { url in
                    if let url = url {
                        mediaItems.append(MediaItem(localUri: url.absoluteString, type: .image))
                    }
                }
            },
            onVideoPicked: { url in
                saveVideoToTempFile(sourceURL: url) { copiedURL in
                    if let u = copiedURL {
                        mediaItems.append(MediaItem(localUri: u.absoluteString, type: .video))
                    }
                }
            }
        )
    }
    
    // MARK: - Video Sheet (camera recording)
    private var videoSheet: some View {
        MediaPicker(
            sourceType: imagePickerSourceType,
            mediaTypes: [UTType.movie.identifier],
            allowsEditing: true,
            onImagePicked: nil,
            onVideoPicked: { url in
                saveVideoToTempFile(sourceURL: url) { copiedURL in
                    if let u = copiedURL {
                        mediaItems.append(MediaItem(localUri: u.absoluteString, type: .video))
                    }
                }
            }
        )
    }
    
    // MARK: - Image Picker Sheet (iOS < 16 fallback for gallery)
    private var imagePickerSheet: some View {
        MediaPicker(
            sourceType: imagePickerSourceType,
            mediaTypes: [UTType.image.identifier, UTType.movie.identifier],
            allowsEditing: true,
            onImagePicked: { image in
                saveImageToTempFile(image: image) { url in
                    if let url = url {
                        mediaItems.append(MediaItem(localUri: url.absoluteString, type: .image))
                    }
                }
            },
            onVideoPicked: { url in
                saveVideoToTempFile(sourceURL: url) { copiedURL in
                    if let u = copiedURL {
                        mediaItems.append(MediaItem(localUri: u.absoluteString, type: .video))
                    }
                }
            }
        )
    }
    
    // Helper function to save image to temp file
    private func saveImageToTempFile(image: UIImage, completion: @escaping (URL?) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            guard let imageData = image.jpegData(compressionQuality: 0.8) else {
                DispatchQueue.main.async {
                    completion(nil)
                }
                return
            }
            
            let tempDirectory = FileManager.default.temporaryDirectory
            let fileName = "post_image_\(UUID().uuidString).jpg"
            let fileURL = tempDirectory.appendingPathComponent(fileName)
            
            do {
                try imageData.write(to: fileURL)
                DispatchQueue.main.async {
                    completion(fileURL)
                }
            } catch {
                print("Error saving image: \(error.localizedDescription)")
                DispatchQueue.main.async {
                    completion(nil)
                }
            }
        }
    }
    
    /// Copy video to app-owned temp file so playback/upload is stable (avoids picker lifecycle and "stops in the middle" for camera recordings). Matches Story behavior.
    private func saveVideoToTempFile(sourceURL: URL, completion: @escaping (URL?) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            let tempURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("post_video_\(UUID().uuidString)")
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
    
    // Helper function to load media from PhotosPickerItem
    @MainActor
    private func loadMediaItem(from item: PhotosPickerItem) async {
        // Try to load as video first
        if let movie = try? await item.loadTransferable(type: Movie.self) {
            mediaItems.append(MediaItem(
                localUri: movie.url.absoluteString,
                type: .video
            ))
            return
        }
        
        // Try to load as image data
        if let data = try? await item.loadTransferable(type: Data.self),
           let uiImage = UIImage(data: data) {
            saveImageToTempFile(image: uiImage) { url in
                if let url = url {
                    mediaItems.append(MediaItem(
                        localUri: url.absoluteString,
                        type: .image
                    ))
                }
            }
        }
    }
}

// MARK: - Movie Transferable
struct Movie: Transferable {
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
            return Self.init(url: copy)
        }
    }
}

// MARK: - Post Type Toggle
struct PostTypeToggle: View {
    @Binding var selectedType: PostType
    
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Post Type")
                .font(.system(size: 16.875, weight: .semibold))
                .foregroundColor(.textPrimary)
            
            HStack(spacing: 13.5) {
                PostTypeButton(
                    label: "Normal Post",
                    isSelected: selectedType == .normal,
                    action: { selectedType = .normal }
                )
                
                PostTypeButton(
                    label: "Ask Question",
                    isSelected: selectedType == .question,
                    action: { selectedType = .question }
                )
            }
        }
    }
}

struct PostTypeButton: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 14.625, weight: .semibold))
                .foregroundColor(isSelected ? .white : .textPrimary)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(isSelected ? Color.primaryGreen : Color.appBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.primaryGreen.opacity(isSelected ? 1.0 : 0.13), lineWidth: 1.18)
                )
                .cornerRadius(14)
        }
    }
}

// MARK: - Media Selection Button
struct MediaSelectionButton: View {
    let icon: String
    let label: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 9) {
                Image(systemName: icon)
                    .font(.system(size: 31.5))
                    .foregroundColor(.primaryGreen)
                
                Text(label)
                    .font(.system(size: 14.625, weight: .semibold))
                    .foregroundColor(.primaryGreen)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 101)
            .background(Color.appBackground)
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(Color.primaryGreen.opacity(0.13), lineWidth: 1.18)
            )
            .cornerRadius(18)
        }
    }
}

// MARK: - Media Preview Section
struct MediaPreviewSection: View {
    @Binding var mediaItems: [MediaItem]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Selected Media (\(mediaItems.count))")
                .font(.system(size: 14.625, weight: .medium))
                .foregroundColor(.textSecondary)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 9) {
                    ForEach(mediaItems) { item in
                        MediaPreviewItem(
                            item: item,
                            onRemove: {
                                mediaItems.removeAll { $0.id == item.id }
                            }
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Media Preview Item
struct MediaPreviewItem: View {
    let item: MediaItem
    let onRemove: () -> Void
    
    private var mediaURL: URL {
        URL(string: item.localUri) ?? URL(fileURLWithPath: item.localUri)
    }
    
    var body: some View {
        ZStack(alignment: .topTrailing) {
            // Media thumbnail
            mediaThumbnail
                .frame(width: 90, height: 90)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            
            // Video indicator overlay
            if item.type == .video {
                Circle()
                    .fill(Color.black.opacity(0.5))
                    .frame(width: 32, height: 32)
                    .overlay(
                        Image(systemName: "play.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                    )
                    .offset(x: -27, y: -27)
            }
            
            // Remove button
            Button(action: onRemove) {
                Circle()
                    .fill(Color.black.opacity(0.5))
                    .frame(width: 24, height: 24)
                    .overlay(
                        Image(systemName: "xmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                    )
            }
            .offset(x: -4, y: 4)
        }
    }
    
    @ViewBuilder
    private var mediaThumbnail: some View {
        if item.type == .video {
            // For videos, show a placeholder with play icon
            ZStack {
                Color.gray.opacity(0.2)
                Image(systemName: "play.fill")
                    .font(.system(size: 24))
                    .foregroundColor(.white)
            }
        } else {
            // For images
            AsyncImage(url: mediaURL) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                case .failure(_), .empty:
                    Color.gray.opacity(0.2)
                @unknown default:
                    Color.gray.opacity(0.2)
                }
            }
        }
    }
}

// MARK: - Voice Caption Section
struct VoiceCaptionSection: View {
    let voiceCaptionUri: String?
    let voiceCaptionDuration: Int
    let isRecording: Bool
    var isPlaying: Bool = false
    var playbackProgress: Int = 0
    let onRecordClick: () -> Void
    var onPlayClick: () -> Void = {}
    let onRemoveVoiceCaption: () -> Void
    
    var body: some View {
        if let _ = voiceCaptionUri {
            // Show recorded voice caption with play button
            HStack {
                HStack(spacing: 10) {
                    // Play/Stop button
                    Button(action: onPlayClick) {
                        Circle()
                            .fill(isPlaying ? Color(red: 1.0, green: 0.42, blue: 0.42) : Color.primaryGreen)
                            .frame(width: 40.5, height: 40.5)
                            .overlay(
                                Image(systemName: isPlaying ? "stop.fill" : "play.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white)
                            )
                    }
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(isPlaying ? "Playing..." : "Voice Caption")
                            .font(.system(size: 14.625, weight: .medium))
                            .foregroundColor(.textPrimary)
                        
                        Text(isPlaying ? "\(playbackProgress)s / \(voiceCaptionDuration)s" : "\(voiceCaptionDuration)s")
                            .font(.system(size: 12))
                            .foregroundColor(isPlaying ? .primaryGreen : .textSecondary)
                    }
                }
                
                Spacer()
                
                Button(action: onRemoveVoiceCaption) {
                    Image(systemName: "xmark")
                        .font(.system(size: 18))
                        .foregroundColor(.textSecondary)
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 74)
            .background(Color.primaryGreen.opacity(0.1))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(Color.primaryGreen.opacity(0.3), lineWidth: 1.18)
            )
            .cornerRadius(18)
        } else {
            // Show record button
            Button(action: onRecordClick) {
                HStack {
                    HStack(spacing: 13.5) {
                        Circle()
                            .fill(isRecording ? Color.primaryGreen : Color.primaryGreen.opacity(0.08))
                            .frame(width: 40.5, height: 40.5)
                            .overlay(
                                Image(systemName: isRecording ? "stop.fill" : "mic.fill")
                                    .font(.system(size: 20.25))
                                    .foregroundColor(isRecording ? .white : .primaryGreen)
                            )
                        
                        Text(isRecording ? "Recording..." : "Add voice caption")
                            .font(.system(size: 16.875, weight: .medium))
                            .foregroundColor(.textPrimary)
                    }
                    
                    Spacer()
                    
                    Text(isRecording ? "Tap to stop" : "Tap to record")
                        .font(.system(size: 14.625, weight: .semibold))
                        .foregroundColor(isRecording ? .primaryGreen : .textSecondary)
                }
                .padding(.horizontal, 19)
                .frame(height: 74)
                .background(isRecording ? Color.primaryGreen.opacity(0.15) : Color.appBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(isRecording ? Color.primaryGreen : Color.primaryGreen.opacity(0.08), lineWidth: 1.18)
                )
                .cornerRadius(18)
            }
        }
    }
}

// MARK: - Hashtags Section
struct HashtagsSection: View {
    @Binding var hashtags: [String]
    @Binding var hashtagInput: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Hashtags")
                .font(.system(size: 16.875, weight: .semibold))
                .foregroundColor(.textPrimary)
            
            // Hashtag input
            HStack {
                Text("#")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.primaryGreen)
                
                TextField("Type and press return to add", text: $hashtagInput)
                    .font(.system(size: 14.625))
                    .foregroundColor(.textPrimary)
                    .onSubmit {
                        addHashtag()
                    }
            }
            .padding(.horizontal, 16)
            .frame(height: 50)
            .background(Color.appBackground)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.primaryGreen.opacity(0.13), lineWidth: 1.18)
            )
            .cornerRadius(14)
            
            // Display added hashtags
            if !hashtags.isEmpty {
                FlowLayout(spacing: 8) {
                    ForEach(hashtags, id: \.self) { tag in
                        HashtagChip(
                            hashtag: tag,
                            onRemove: {
                                hashtags.removeAll { $0 == tag }
                            }
                        )
                    }
                }
            }
        }
    }
    
    private func addHashtag() {
        let tag = hashtagInput.replacingOccurrences(of: "#", with: "").trimmingCharacters(in: .whitespaces)
        if !tag.isEmpty && !hashtags.contains(tag) {
            hashtags.append(tag)
            hashtagInput = ""
        }
    }
}

struct HashtagChip: View {
    let hashtag: String
    let onRemove: () -> Void
    
    var body: some View {
        HStack(spacing: 6) {
            Text("#\(hashtag)")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.primaryGreen)
            
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.primaryGreen)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color.primaryGreen.opacity(0.1))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.primaryGreen.opacity(0.3), lineWidth: 1)
        )
        .cornerRadius(16)
    }
}

// MARK: - Crop Selection Section
struct CropSelectionSection: View {
    @ObservedObject var viewModel: CreatePostViewModel
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("What crop is this?")
                .font(.system(size: 16.875, weight: .semibold))
                .foregroundColor(.textPrimary)
            
            // Search Bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.textSecondary)
                    .font(.system(size: 16))
                
                TextField("Search crops...", text: Binding(
                    get: { viewModel.cropSearchQuery },
                    set: { viewModel.setCropSearchQuery($0) }
                ))
                .font(.system(size: 16))
                .foregroundColor(.textPrimary)
                .disabled(viewModel.isLoadingCrops)
                
                if !viewModel.cropSearchQuery.isEmpty {
                    Button(action: { viewModel.setCropSearchQuery("") }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.textSecondary)
                            .font(.system(size: 16))
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 13.5)
            .background(Color.appBackground)
            .cornerRadius(22)
            .overlay(
                RoundedRectangle(cornerRadius: 22)
                    .stroke(Color.black.opacity(0.1), lineWidth: 1.18)
            )
            
            // Selected Crops Section
            if !viewModel.selectedCrops.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Selected Crops")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.textSecondary)
                    
                    FlowLayout(spacing: 8) {
                        ForEach(Array(viewModel.selectedCrops).sorted(), id: \.self) { crop in
                            CropChip(
                                crop: crop,
                                isSelected: true,
                                onClick: { viewModel.toggleCrop(crop) }
                            )
                        }
                    }
                }
            }
            
            // Available Crops Section
            if viewModel.isLoadingCrops {
                HStack {
                    Spacer()
                    ProgressView()
                        .scaleEffect(0.8)
                    Spacer()
                }
                .padding(.vertical, 16)
            } else if !viewModel.cropSearchQuery.isEmpty && viewModel.visibleCrops.isEmpty {
                Text("No crops found")
                    .font(.system(size: 14))
                    .foregroundColor(.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 16)
            } else if !viewModel.visibleCrops.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    if !viewModel.cropSearchQuery.isEmpty {
                        Text("Search Results")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.textSecondary)
                    }
                    
                    FlowLayout(spacing: 8) {
                        ForEach(viewModel.visibleCrops, id: \.self) { crop in
                            CropChip(
                                crop: crop,
                                isSelected: false,
                                onClick: { viewModel.toggleCrop(crop) }
                            )
                        }
                    }
                    
                    // Show More / Show Less Button
                    if viewModel.hasMoreCrops || viewModel.showAllCrops {
                        Button(action: { viewModel.toggleShowAllCrops() }) {
                            HStack(spacing: 4) {
                                Text(viewModel.showAllCrops ? "Show Less" : "+\(viewModel.remainingCropsCount) more crops")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(.primaryGreen)
                                
                                Image(systemName: viewModel.showAllCrops ? "chevron.up" : "chevron.down")
                                    .font(.system(size: 12))
                                    .foregroundColor(.primaryGreen)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .center)
                    }
                }
            }
        }
    }
}

// MARK: - Target Expertise Section (for Questions)
struct TargetExpertiseSection: View {
    @Binding var selectedExpertise: Set<String>
    
    private let expertiseOptions = [
        "Crop Doctor",
        "Soil Expert",
        "Irrigation Specialist",
        "Pest Control",
        "Organic Farming",
        "Seeds & Varieties",
        "Farm Equipment",
        "Market & Pricing"
    ]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Who should answer? (Optional)")
                .font(.system(size: 16.875, weight: .semibold))
                .foregroundColor(.textPrimary)
            
            Text("Select expertise areas to get relevant answers")
                .font(.system(size: 13.5))
                .foregroundColor(.textSecondary)
            
            FlowLayout(spacing: 9) {
                ForEach(expertiseOptions, id: \.self) { expertise in
                    ExpertiseChip(
                        expertise: expertise,
                        isSelected: selectedExpertise.contains(expertise),
                        onClick: {
                            if selectedExpertise.contains(expertise) {
                                selectedExpertise.remove(expertise)
                            } else {
                                selectedExpertise.insert(expertise)
                            }
                        }
                    )
                }
            }
        }
    }
}

struct ExpertiseChip: View {
    let expertise: String
    let isSelected: Bool
    let onClick: () -> Void
    
    private let accentColor = Color(red: 1.0, green: 0.654, blue: 0.149) // #FFA726
    
    var body: some View {
        Button(action: onClick) {
            Text(expertise)
                .font(.system(size: 13.5, weight: .medium))
                .foregroundColor(isSelected ? accentColor : .textPrimary)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(isSelected ? accentColor.opacity(0.15) : Color.appBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(isSelected ? accentColor : Color.primaryGreen.opacity(0.13), lineWidth: 1.18)
                )
                .cornerRadius(18)
        }
    }
}

// MARK: - Visibility Selection Section
struct VisibilitySelectionSection: View {
    @Binding var selectedVisibility: PostVisibility
    
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Who can see this?")
                .font(.system(size: 16.875, weight: .semibold))
                .foregroundColor(.textPrimary)
            
            HStack(spacing: 13.5) {
                VisibilityButton(
                    label: "Public",
                    isSelected: selectedVisibility == .public,
                    action: { selectedVisibility = .public }
                )
                
                VisibilityButton(
                    label: "My Followers",
                    isSelected: selectedVisibility == .followers,
                    action: { selectedVisibility = .followers }
                )
            }
            
            Text("Public posts are visible to all farmers")
                .font(.system(size: 13.5))
                .foregroundColor(.textSecondary)
        }
    }
}

// MARK: - Visibility Button
struct VisibilityButton: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 15.75, weight: .semibold))
                .foregroundColor(isSelected ? .white : .textPrimary)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(isSelected ? Color.primaryGreen : Color.appBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 22)
                        .stroke(Color.primaryGreen.opacity(isSelected ? 1.0 : 0.13), lineWidth: 1.18)
                )
                .cornerRadius(22)
        }
    }
}

// MARK: - Location Button
struct LocationButton: View {
    let location: String?
    let onLocationClick: () -> Void
    let onRemoveLocation: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 13.5) {
            Text("Add location")
                .font(.system(size: 16.875, weight: .semibold))
                .foregroundColor(.textPrimary)
            
            Button(action: onLocationClick) {
                HStack {
                    HStack(spacing: 13.5) {
                        Circle()
                            .fill(location != nil ? Color.primaryGreen : Color.primaryGreen.opacity(0.08))
                            .frame(width: 40.5, height: 40.5)
                            .overlay(
                                Image(systemName: "location.fill")
                                    .font(.system(size: 20.25))
                                    .foregroundColor(location != nil ? .white : .primaryGreen)
                            )
                        
                        Text(location ?? "Select location")
                            .font(.system(size: 16.875, weight: .medium))
                            .foregroundColor(location != nil ? .textPrimary : .textPrimary.opacity(0.5))
                    }
                    
                    Spacer()
                    
                    if location != nil {
                        Button(action: onRemoveLocation) {
                            Image(systemName: "xmark")
                                .font(.system(size: 20))
                                .foregroundColor(.textSecondary)
                        }
                    } else {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 22.5))
                            .foregroundColor(.textSecondary)
                    }
                }
                .padding(.horizontal, 19)
                .frame(height: 74)
                .background(Color.appBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(Color.primaryGreen.opacity(0.08), lineWidth: 1.18)
                )
                .cornerRadius(18)
            }
        }
    }
}

// MARK: - Media Picker
struct MediaPicker: UIViewControllerRepresentable {
    let sourceType: UIImagePickerController.SourceType
    let mediaTypes: [String]
    let onImagePicked: ((UIImage) -> Void)?
    let onVideoPicked: ((URL) -> Void)?
    let allowsEditing: Bool // Allow editing to be configurable
    
    init(
        sourceType: UIImagePickerController.SourceType,
        mediaTypes: [String],
        allowsEditing: Bool = true,
        onImagePicked: ((UIImage) -> Void)? = nil,
        onVideoPicked: ((URL) -> Void)? = nil
    ) {
        self.sourceType = sourceType
        self.mediaTypes = mediaTypes
        self.allowsEditing = allowsEditing
        self.onImagePicked = onImagePicked
        self.onVideoPicked = onVideoPicked
    }
    
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        
        // Use the requested source type if available, otherwise fall back to photo library
        let actualSourceType: UIImagePickerController.SourceType
        if UIImagePickerController.isSourceTypeAvailable(sourceType) {
            actualSourceType = sourceType
        } else if UIImagePickerController.isSourceTypeAvailable(.photoLibrary) {
            actualSourceType = .photoLibrary
        } else {
            actualSourceType = .savedPhotosAlbum
        }
        
        picker.sourceType = actualSourceType
        
        // Get available media types for this source and filter to requested types
        let availableTypes = UIImagePickerController.availableMediaTypes(for: actualSourceType) ?? []
        let filteredTypes = mediaTypes.filter { availableTypes.contains($0) }
        
        // Use filtered types if any match, otherwise use all available types
        if !filteredTypes.isEmpty {
            picker.mediaTypes = filteredTypes
        } else if !availableTypes.isEmpty {
            picker.mediaTypes = availableTypes
        }
        
        picker.delegate = context.coordinator
        
        // Configure editing behavior
        // Note: UIImagePickerController doesn't support selective editing per media type
        // When both image and video types are available:
        // - Videos will show trim screen if allowsEditing = true
        // - Images will show crop screen if allowsEditing = true
        // Since we want videos to have edit screen but not photos, we need to handle this carefully
        // For story creation: We want video edit screen but no photo edit screen
        // Solution: Set allowsEditing based on the allowsEditing parameter
        // The caller should set allowsEditing = true only when videos are expected
        picker.allowsEditing = allowsEditing
        
        // Check if video is available
        let hasVideo = picker.mediaTypes.contains(UTType.movie.identifier)
        
        // Only set video settings if video is available
        if hasVideo {
            picker.cameraCaptureMode = .video
            picker.videoQuality = .typeHigh
            picker.videoMaximumDuration = 60 // 60 seconds max
        }
        
        return picker
    }
    
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(onImagePicked: onImagePicked, onVideoPicked: onVideoPicked)
    }
    
    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onImagePicked: ((UIImage) -> Void)?
        let onVideoPicked: ((URL) -> Void)?
        
        init(onImagePicked: ((UIImage) -> Void)?, onVideoPicked: ((URL) -> Void)?) {
            self.onImagePicked = onImagePicked
            self.onVideoPicked = onVideoPicked
        }
        
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            // Check if it's a video
            if let videoURL = info[.mediaURL] as? URL {
                // It's a video - copy to temp directory
                let tempDirectory = FileManager.default.temporaryDirectory
                let fileName = "post_video_\(UUID().uuidString).mp4"
                let tempURL = tempDirectory.appendingPathComponent(fileName)
                
                do {
                    if FileManager.default.fileExists(atPath: tempURL.path) {
                        try FileManager.default.removeItem(at: tempURL)
                    }
                    try FileManager.default.copyItem(at: videoURL, to: tempURL)
                    onVideoPicked?(tempURL)
                } catch {
                    print("Error copying video: \(error.localizedDescription)")
                }
            } else if let editedImage = info[.editedImage] as? UIImage {
                onImagePicked?(editedImage)
            } else if let originalImage = info[.originalImage] as? UIImage {
                onImagePicked?(originalImage)
            }
            
            picker.dismiss(animated: true)
        }
        
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
        }
    }
}

// MARK: - Location Selection Sheet
struct LocationSelectionSheet: View {
    @ObservedObject var viewModel: CreatePostViewModel
    @Environment(\.dismiss) var dismiss
    @State private var selectedTab: LocationTab = .current
    
    enum LocationTab {
        case current
        case manual
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Tab Selector
                HStack(spacing: 0) {
                    TabButton(
                        title: "Current Location",
                        isSelected: selectedTab == .current,
                        action: { selectedTab = .current }
                    )
                    TabButton(
                        title: "Manual",
                        isSelected: selectedTab == .manual,
                        action: { selectedTab = .manual }
                    )
                }
                .padding(.horizontal, 18)
                .padding(.top, 16)
                
                // Content
                if selectedTab == .current {
                    CurrentLocationTab(viewModel: viewModel, onDismiss: { dismiss() })
                } else {
                    ManualLocationTab(viewModel: viewModel, onDismiss: { dismiss() })
                }
            }
            .navigationTitle("Add Location")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct TabButton: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Text(title)
                    .font(.system(size: 16, weight: isSelected ? .semibold : .regular))
                    .foregroundColor(isSelected ? .primaryGreen : .textSecondary)
                
                Rectangle()
                    .fill(isSelected ? Color.primaryGreen : Color.clear)
                    .frame(height: 2)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

struct CurrentLocationTab: View {
    @ObservedObject var viewModel: CreatePostViewModel
    let onDismiss: () -> Void
    
    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            
            if let error = viewModel.locationError {
                Text(error)
                    .font(.system(size: 14))
                    .foregroundColor(.red)
                    .padding(.horizontal, 18)
                    .padding(.bottom, 8)
            }
            
            Image(systemName: "location.fill")
                .font(.system(size: 64))
                .foregroundColor(.primaryGreen)
            
            Text("Use Current Location")
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(.textPrimary)
            
            Text("We'll use your GPS to find your location")
                .font(.system(size: 13))
                .foregroundColor(.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            
            Button(action: {
                viewModel.useCurrentLocation()
                // The ViewModel will set showLocationSheet = false when location is set
            }) {
                HStack {
                    if viewModel.isLoadingLocation {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                        Text("Detecting location...")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                    } else {
                        Image(systemName: "location.fill")
                            .font(.system(size: 24))
                        Text("Use Current Location")
                            .font(.system(size: 16, weight: .semibold))
                    }
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(Color.primaryGreen)
                .cornerRadius(18)
            }
            .disabled(viewModel.isLoadingLocation)
            .padding(.horizontal, 18)
            
            Spacer()
        }
        .padding(.top, 32)
    }
}

struct ManualLocationTab: View {
    @ObservedObject var viewModel: CreatePostViewModel
    let onDismiss: () -> Void
    
    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                if let error = viewModel.locationError {
                    Text(error)
                        .font(.system(size: 14))
                        .foregroundColor(.red)
                        .padding(.horizontal, 18)
                }
                
                // State Dropdown
                VStack(alignment: .leading, spacing: 8) {
                    Text("State")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.textSecondary)
                    
                    if viewModel.isLoadingStates {
                        HStack {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                                .scaleEffect(0.8)
                            Text("Loading states...")
                                .font(.system(size: 16))
                                .foregroundColor(.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(Color.appBackground)
                        .cornerRadius(22)
                    } else {
                        Menu {
                            ForEach(viewModel.allStates, id: \.self) { state in
                                Button(state) {
                                    viewModel.selectState(state)
                                }
                            }
                        } label: {
                            HStack {
                                Text(viewModel.selectedState ?? "Select State")
                                    .font(.system(size: 16))
                                    .foregroundColor(viewModel.selectedState != nil ? .textPrimary : .textPrimary.opacity(0.5))
                                Spacer()
                                Image(systemName: "chevron.down")
                                    .font(.system(size: 14))
                                    .foregroundColor(.textSecondary)
                            }
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.appBackground)
                            .cornerRadius(22)
                            .overlay(
                                RoundedRectangle(cornerRadius: 22)
                                    .stroke(Color.primaryGreen.opacity(0.1), lineWidth: 1)
                            )
                        }
                    }
                }
                .padding(.horizontal, 18)
                
                // District Dropdown
                VStack(alignment: .leading, spacing: 8) {
                    Text("District")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.textSecondary)
                    
                    if viewModel.isLoadingDistricts {
                        HStack {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .primaryGreen))
                                .scaleEffect(0.8)
                            Text("Loading districts...")
                                .font(.system(size: 16))
                                .foregroundColor(.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(Color.appBackground)
                        .cornerRadius(22)
                    } else {
                        Menu {
                            ForEach(viewModel.districtsForSelectedState, id: \.self) { district in
                                Button(district) {
                                    viewModel.selectDistrict(district)
                                }
                            }
                        } label: {
                            HStack {
                                Text(viewModel.selectedDistrict ?? (viewModel.selectedState == nil ? "Select state first" : "Select District"))
                                    .font(.system(size: 16))
                                    .foregroundColor(viewModel.selectedDistrict != nil ? .textPrimary : .textPrimary.opacity(0.5))
                                Spacer()
                                Image(systemName: "chevron.down")
                                    .font(.system(size: 14))
                                    .foregroundColor(.textSecondary)
                            }
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.appBackground)
                            .cornerRadius(22)
                            .overlay(
                                RoundedRectangle(cornerRadius: 22)
                                    .stroke(Color.primaryGreen.opacity(0.1), lineWidth: 1)
                            )
                        }
                        .disabled(viewModel.selectedState == nil)
                    }
                }
                .padding(.horizontal, 18)
                
                // Village Text Field
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 4) {
                        Text("Village")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.textSecondary)
                        Text("(Optional)")
                            .font(.system(size: 12))
                            .foregroundColor(.textSecondary.opacity(0.6))
                            .italic()
                    }
                    
                    TextField("Enter village name", text: $viewModel.villageName)
                        .font(.system(size: 16))
                        .foregroundColor(.textPrimary)
                        .padding()
                        .background(Color.appBackground)
                        .cornerRadius(22)
                        .overlay(
                            RoundedRectangle(cornerRadius: 22)
                                .stroke(Color.primaryGreen.opacity(0.1), lineWidth: 1)
                        )
                }
                .padding(.horizontal, 18)
                
                // Save Button
                Button(action: {
                    viewModel.saveManualLocation()
                    // The ViewModel will set showLocationSheet = false when location is set
                }) {
                    HStack {
                        if viewModel.isLoadingLocation {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.8)
                            Text("Confirming location...")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                        } else {
                            Text("Save Location")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(
                        (viewModel.selectedState != nil && viewModel.selectedDistrict != nil && !viewModel.isLoadingLocation) ?
                        Color.primaryGreen : Color.primaryGreen.opacity(0.5)
                    )
                    .cornerRadius(18)
                }
                .disabled(viewModel.selectedState == nil || viewModel.selectedDistrict == nil || viewModel.isLoadingLocation)
                .padding(.horizontal, 18)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
        }
    }
}

// MARK: - Preview
struct CreatePostView_Previews: PreviewProvider {
    static var previews: some View {
        CreatePostView()
    }
}
