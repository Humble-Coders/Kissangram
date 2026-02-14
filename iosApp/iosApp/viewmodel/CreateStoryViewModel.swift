import Foundation
import SwiftUI
import AVFoundation
import Shared

/**
 * ViewModel for Create Story screen on iOS.
 * Handles story creation logic, media handling, and repository management.
 */
@MainActor
class CreateStoryViewModel: ObservableObject {
    
    // MARK: - Published State
    
    @Published var isCreatingStory: Bool = false
    @Published var errorMessage: String? = nil
    @Published var showError: Bool = false
    
    // MARK: - Private Properties (Repositories and Use Case)
    
    // Repositories - instantiated once and reused
    private let storageRepository = IOSStorageRepository()
    private let preferencesRepository = IOSPreferencesRepository()
    
    // Lazy repositories that depend on others
    private lazy var authRepository: AuthRepository = {
        IOSAuthRepository(preferencesRepository: preferencesRepository)
    }()
    
    private lazy var storyRepository: StoryRepository = {
        FirestoreStoryRepository(authRepository: authRepository)
    }()
    
    private lazy var userRepository: UserRepository = {
        FirestoreUserRepository(authRepository: authRepository)
    }()
    
    // Use case - instantiated once and reused
    private lazy var createStoryUseCase: CreateStoryUseCase = {
        CreateStoryUseCase(
            storageRepository: storageRepository,
            storyRepository: storyRepository,
            authRepository: authRepository,
            userRepository: userRepository
        )
    }()
    
    // MARK: - Initialization
    
    init() {
        // ViewModel is ready - repositories and use case will be lazily initialized when needed
    }
    
    // MARK: - Story Creation
    
    /**
     * Create a story from the provided input.
     * This method handles the async upload and creation logic.
     */
    func createStory(input: Shared.CreateStoryInput) async {
        guard !isCreatingStory else {
            return // Prevent duplicate submissions
        }
        
        isCreatingStory = true
        errorMessage = nil
        showError = false
        
        do {
            try await createStoryUseCase.invoke(input: input)
            // Success - caller should handle navigation
        } catch {
            errorMessage = error.localizedDescription
            showError = true
            print("Failed to create story: \(error.localizedDescription)")
        }
        
        isCreatingStory = false
    }
    
    /**
     * Build CreateStoryInput from media URL and story data.
     * This method ensures:
     * - Images: Upload original file data without recompression
     * - Videos: Upload original video file data unchanged
     * - Video thumbnails: Generated at CMTime(seconds: 0.3, preferredTimescale: 600)
     * - Same media data used for preview is used for upload
     * 
     * @param mediaUrl The URL of the media file
     * @param mediaType The type of media (image or video)
     * @param textOverlays Array of Shared.StoryTextOverlay objects
     * @param location Optional Shared.CreateStoryLocation
     * @param visibility StoryVisibility enum (will be converted to Shared.PostVisibility)
     */
    func buildStoryInput(
        mediaUrl: URL,
        mediaType: Shared.MediaType,
        textOverlays: [Shared.StoryTextOverlay],
        location: Shared.CreateStoryLocation?,
        visibility: StoryVisibility
    ) async throws -> Shared.CreateStoryInput {
        
        // Read media data - use original file data without recompression
        let mediaData: Data
        do {
            mediaData = try Data(contentsOf: mediaUrl)
        } catch {
            throw NSError(
                domain: "CreateStoryViewModel",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Failed to read media file: \(error.localizedDescription)"]
            )
        }
        
        // Generate thumbnail based on media type
        var thumbnailData: Data? = nil
        
        if mediaType == Shared.MediaType.image {
            // For images, thumbnail is the same as the image (no recompression)
            thumbnailData = mediaData
        } else {
            // For videos, generate thumbnail at 0.3 seconds
            thumbnailData = try await generateVideoThumbnail(
                videoUrl: mediaUrl,
                at: CMTime(seconds: 0.3, preferredTimescale: 600)
            )
        }
        
        // Convert Data to KotlinByteArray
        let kotlinMediaData = dataToKotlinByteArray(mediaData)
        let kotlinThumbnailData = thumbnailData.map { dataToKotlinByteArray($0) }
        
        // Text overlays and location are already Shared types, use them directly
        let sharedTextOverlays = textOverlays
        let sharedLocation = location
        
        // Media type is already Shared.MediaType, use it directly
        let sharedMediaType: Shared.MediaType = mediaType
        
        // Convert visibility
        let sharedVisibility: Shared.PostVisibility = visibility == .public ? .public_ : .followers
        
        // Create Shared.CreateStoryInput
        return Shared.CreateStoryInput(
            mediaData: kotlinMediaData,
            mediaType: sharedMediaType,
            thumbnailData: kotlinThumbnailData,
            textOverlays: sharedTextOverlays,
            location: sharedLocation,
            visibility: sharedVisibility
        )
    }
    
    // MARK: - Private Helper Methods
    
    /**
     * Generate video thumbnail at the specified time.
     * Uses CMTime(seconds: 0.3, preferredTimescale: 600) for better frame selection.
     */
    private func generateVideoThumbnail(videoUrl: URL, at time: CMTime) async throws -> Data {
        let asset = AVAsset(url: videoUrl)
        let imageGenerator = AVAssetImageGenerator(asset: asset)
        imageGenerator.appliesPreferredTrackTransform = true
        imageGenerator.requestedTimeToleranceAfter = .zero
        imageGenerator.requestedTimeToleranceBefore = .zero
        
        do {
            let cgImage = try await imageGenerator.image(at: time).image
            
            // Convert CGImage to Data without recompression for PNG, or with minimal compression for JPEG
            // Use PNG to preserve quality, or JPEG with high quality
            guard let imageData = UIImage(cgImage: cgImage).pngData() ?? 
                  UIImage(cgImage: cgImage).jpegData(compressionQuality: 0.95) else {
                throw NSError(
                    domain: "CreateStoryViewModel",
                    code: -2,
                    userInfo: [NSLocalizedDescriptionKey: "Failed to convert thumbnail to data"]
                )
            }
            
            return imageData
        } catch {
            throw NSError(
                domain: "CreateStoryViewModel",
                code: -3,
                userInfo: [NSLocalizedDescriptionKey: "Failed to generate video thumbnail: \(error.localizedDescription)"]
            )
        }
    }
    
    /**
     * Convert Swift Data to KotlinByteArray.
     */
    private func dataToKotlinByteArray(_ data: Data) -> KotlinByteArray {
        let kotlinArray = KotlinByteArray(size: Int32(data.count))
        data.withUnsafeBytes { bytes in
            for (i, b) in bytes.enumerated() {
                kotlinArray.set(index: Int32(i), value: Int8(bitPattern: b))
            }
        }
        return kotlinArray
    }
}
