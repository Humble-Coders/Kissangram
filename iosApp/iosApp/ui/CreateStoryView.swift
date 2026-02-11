import SwiftUI
import PhotosUI
import AVFoundation
import AVKit
import UniformTypeIdentifiers
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
    @State private var showVideoPicker = false
    @State private var showGalleryPicker = false
    @State private var imagePickerSourceType: UIImagePickerController.SourceType = .camera
    @State private var selectedPhotos: [PhotosPickerItem] = []
    @State private var showCameraUnavailableAlert = false
    
    var onBackClick: () -> Void = {}
    var onStoryCreated: () -> Void = {}
    
    private var isStoryEnabled: Bool {
        selectedMediaUrl != nil && !viewModel.isCreatingStory
    }
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            if let mediaUrl = selectedMediaUrl {
                // Media Preview (Full Screen)
                ZStack {
                    if mediaType == .image {
                        AsyncImage(url: mediaUrl) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .clipped()
                        } placeholder: {
                            Color.gray.opacity(0.3)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                    } else {
                        VideoPlayer(player: AVPlayer(url: mediaUrl))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .clipped()
                    }
                    
                    // Background tap detection
                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture {
                            // Background tap - deselect all overlays
                            selectedOverlayId = nil
                        }
                    
                    // Text Overlays
                    ForEach(textOverlays.indices, id: \.self) { index in
                        DraggableTextOverlayView(
                            overlay: textOverlays[index],
                            isSelected: textOverlays[index].id == selectedOverlayId,
                            onPositionChange: { newX, newY in
                                textOverlays[index].positionX = newX
                                textOverlays[index].positionY = newY
                            },
                            onSizeChange: { newSize in
                                textOverlays[index].fontSize = newSize
                            },
                            onRotationChange: { newRotation in
                                textOverlays[index].rotation = newRotation
                            },
                            onScaleChange: { newScale in
                                textOverlays[index].scale = newScale
                            },
                            onSelect: {
                                selectedOverlayId = textOverlays[index].id
                            },
                            onEdit: {
                                selectedTextOverlayIndex = index
                                showTextInputSheet = true
                            },
                            onDelete: {
                                let overlayId = textOverlays[index].id
                                textOverlays.remove(at: index)
                                if selectedOverlayId == overlayId {
                                    selectedOverlayId = nil
                                }
                            }
                        )
                    }
                }
                .ignoresSafeArea()
            } else {
                // Empty state - show media selection options
                VStack(spacing: 32) {
                    Text("Create Your Story")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                    
                    HStack(spacing: 16) {
                        // Camera Button - Opens picker that supports both photos and videos
                        // Videos will show edit screen, photos will go directly to final screen
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
                        
                        // Gallery Button - Uses PhotosPicker (no edit screen, goes directly to final screen)
                        StoryMediaSelectionButton(
                            icon: "photo.fill",
                            label: "Gallery",
                            onClick: {
                                showGalleryPicker = true
                            }
                        )
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            
            // Top Bar
            VStack {
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
                    
                    // Spacer for balance
                    Color.clear
                        .frame(width: 44, height: 44)
                }
                .padding(.horizontal, 18)
                .padding(.top, 8)
                
                Spacer()
            }
            
            // Bottom Action Bar - Always mounted, visibility controlled by opacity
            VStack {
                Spacer()
                BottomActionBarView(
                    onAddTextClick: {
                        showTextInputSheet = true
                        selectedTextOverlayIndex = nil
                    },
                    onLocationClick: {
                        showLocationPicker = true
                    },
                    location: location,
                    onRemoveLocation: {
                        location = nil
                    },
                    visibility: $visibility,
                    onShareClick: {
                        handleShareClick()
                    },
                    isShareEnabled: isStoryEnabled
                )
                .opacity(selectedMediaUrl != nil ? 1.0 : 0.0)
                .offset(y: selectedMediaUrl != nil ? 0 : 100) // Slide down when hidden
                .allowsHitTesting(selectedMediaUrl != nil) // Disable interaction when hidden
                .animation(.easeInOut(duration: 0.2), value: selectedMediaUrl != nil)
            }
            
            // Loading Overlay
            if viewModel.isCreatingStory {
                ZStack {
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
        }
        .alert("Error", isPresented: $viewModel.showError) {
            Button("OK") {
                viewModel.errorMessage = nil
            }
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error occurred")
        }
        .sheet(isPresented: $showCamera) {
            // Camera picker: Allow editing for videos (trim screen), but not for photos
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
                }
            )
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
        .onChange(of: selectedPhotos) { newItems in
            Task {
                for item in newItems {
                    await loadMediaItem(from: item)
                }
                selectedPhotos = []
            }
        }
    }
    
    // MARK: - Helper Functions
    @MainActor
    private func loadMediaItem(from item: PhotosPickerItem) async {
        // Try to load as video first
        if let movie = try? await item.loadTransferable(type: StoryMovie.self) {
            selectedMediaUrl = movie.url
            mediaType = .video
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
    
    // MARK: - Story Creation
    @MainActor
    private func handleShareClick() {
        guard let mediaUrl = selectedMediaUrl,
              let mediaTypeValue = mediaType else {
            return
        }
        
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
        ZStack(alignment: .topTrailing) {
            // Text container with border when selected
            HStack(spacing: 8) {
                Text(overlay.text)
                    .font(.system(size: max(CGFloat(liveFontSize), 24), weight: .bold))
                    .foregroundColor(colorFromHex(overlay.textColor))
                    .fixedSize()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Color.black.opacity(0.6))
            .cornerRadius(8)
            .overlay(
                // White border when selected
                Group {
                    if isSelected {
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.white, lineWidth: 2)
                    }
                }
            )
            
            // Delete button - only visible when selected
            if isSelected {
                Button(action: onDelete) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 32, height: 32)
                        .background(Color.red)
                        .clipShape(Circle())
                }
                .offset(x: 12, y: -12)
            }
        }
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
            // Initialize base values
            basePositionPx = CGPoint(
                x: CGFloat(overlay.positionX) * screenWidthPx,
                y: CGFloat(overlay.positionY) * screenHeightPx
            )
            // Ensure font size is at least 24 for visibility
            baseFontSize = max(overlay.fontSize, 24)
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
        // Combined gesture using simultaneous gestures
        let magnification = MagnificationGesture()
            .onChanged { value in
                isTransforming = true
                
                /* ---------- Scale + Size ---------- */
                if value != 1.0 {
                    scale = (scale * value).clamped(to: 0.5...3.0)
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
                
                /* ---------- Rotation ---------- */
                // Calculate delta from last rotation value (Angle type)
                let rotationDeltaRadians = value.radians - lastRotationValue.radians
                lastRotationValue = value
                let rotationDeltaDegrees = rotationDeltaRadians * 180.0 / .pi
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
                
                /* ---------- Drag (normalized) ---------- */
                // Divide by scale to account for scaling
                localOffset = CGSize(
                    width: value.translation.width / scale,
                    height: value.translation.height / scale
                )
                
                // Calculate live position in pixels
                let livePx = CGPoint(
                    x: basePositionPx.x + localOffset.width,
                    y: basePositionPx.y + localOffset.height
                )
                
                // Update position (normalized)
                onPositionChange(
                    Float(livePx.x / screenWidthPx).clamped(to: 0...1),
                    Float(livePx.y / screenHeightPx).clamped(to: 0...1)
                )
            }
            .onEnded { _ in
                /* ---------- Commit position on gesture end ---------- */
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

// Note: MediaPicker is already defined in CreatePostView.swift and can be reused
