import Foundation
import FirebaseStorage
import Cloudinary
import Shared

/// iOS implementation of StorageRepository using Firebase Storage and Cloudinary.
/// Handles uploading profile images, post media, and verification documents.
public final class IOSStorageRepository: StorageRepository {
    
    private let storage: Storage
    private let cloudinary: CLDCloudinary
    
    // Storage folder paths
    private let folderProfileImages = "profile_images"
    private let folderPosts = "posts"
    private let folderVerificationDocs = "verification_docs"
    
    public init() {
        self.storage = Storage.storage()
        
        // Initialize Cloudinary
        // Get these from: https://console.cloudinary.com/settings/api-keys
        let config = CLDConfiguration(
            cloudName: "ddjgu0mng",
            apiKey: "358269548668498",
            apiSecret: "GfgbZyDrqjwwaKVmiP8UaYtQQfU"
        )
        self.cloudinary = CLDCloudinary(configuration: config)
    }
    
    /// Upload a profile image for the given user to Firebase Storage.
    /// - Parameters:
    ///   - userId: The user's ID
    ///   - imageData: The image data as KotlinByteArray
    /// - Returns: The Firebase Storage HTTPS URL of the uploaded image
    public func uploadProfileImage(userId: String, imageData: KotlinByteArray) async throws -> String {
        let data = imageData.toData()
        
        print("📤 IOSStorageRepository: Starting profile image upload to Firebase Storage")
        print("   - User ID: \(userId)")
        print("   - Image Data Size: \(data.count) bytes")
        
        // Compress image before upload (max 1MB, 800x800px)
        var compressedImageData = MediaCompressor.compressImage(data)
        
        // Further compress if still too large (target: 1MB for profile images)
        if compressedImageData.count > 1024 * 1024 { // 1MB
            // Re-compress with lower quality if still too large
            if let image = UIImage(data: compressedImageData) {
                var quality: CGFloat = 0.75
                var finalData = image.jpegData(compressionQuality: quality)
                while let data = finalData, data.count > 1024 * 1024 && quality > 0.5 {
                    quality -= 0.1
                    finalData = image.jpegData(compressionQuality: quality)
                }
                if let final = finalData {
                    compressedImageData = final
                }
            }
        }
        
        print("📤 IOSStorageRepository: Image compressed: \(data.count) -> \(compressedImageData.count) bytes")
        
        // Create reference: profile_images/{userId}/profile_{timestamp}.jpg
        let timestamp = Int(Date().timeIntervalSince1970 * 1000)
        let fileName = "profile_\(timestamp).jpg"
        let storageRef = storage.reference()
            .child(folderProfileImages)
            .child(userId)
            .child(fileName)
        
        // Create metadata with cache control
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        metadata.cacheControl = "public, max-age=31536000" // 1 year cache
        
        // Upload with metadata
        _ = try await storageRef.putDataAsync(compressedImageData, metadata: metadata)
        
        // Get the download URL
        let downloadUrl = try await storageRef.downloadURL()
        print("✅ IOSStorageRepository: Profile image uploaded successfully")
        print("   - URL: \(downloadUrl.absoluteString)")
        
        return downloadUrl.absoluteString
    }
    
    /// Delete a profile image for the given user.
    /// - Parameter userId: The user's ID
    public func deleteProfileImage(userId: String) async throws {
        let folderRef = storage.reference()
            .child(folderProfileImages)
            .child(userId)
        
        // List all files in the user's profile_images folder
        let listResult = try await folderRef.listAll()
        
        // Delete each file
        for item in listResult.items {
            try await item.delete()
        }
    }
    
    /// Upload post media (image or video thumbnail).
    /// - Parameters:
    ///   - postId: The post's ID
    ///   - mediaIndex: The index of the media in the post
    ///   - mediaData: The media data as KotlinByteArray
    ///   - contentType: The MIME type of the media
    /// - Returns: The download URL of the uploaded media
    public func uploadPostMedia(postId: String, mediaIndex: Int32, mediaData: KotlinByteArray, contentType: String) async throws -> String {
        // Convert KotlinByteArray to Data
        let data = mediaData.toData()
        
        // Determine file extension from content type
        let ext: String
        if contentType.contains("jpeg") || contentType.contains("jpg") {
            ext = "jpg"
        } else if contentType.contains("png") {
            ext = "png"
        } else if contentType.contains("gif") {
            ext = "gif"
        } else if contentType.contains("webp") {
            ext = "webp"
        } else if contentType.contains("mp4") || contentType.contains("video") {
            ext = "mp4"
        } else {
            ext = "jpg"
        }
        
        // Create reference: posts/{postId}/media_{index}_{uuid}.{ext}
        let uuid = UUID().uuidString.prefix(8)
        let fileName = "media_\(mediaIndex)_\(uuid).\(ext)"
        let storageRef = storage.reference()
            .child(folderPosts)
            .child(postId)
            .child(fileName)
        
        // Create metadata
        let metadata = StorageMetadata()
        metadata.contentType = contentType
        
        // Upload with metadata
        _ = try await storageRef.putDataAsync(data, metadata: metadata)
        
        // Get the download URL
        let downloadUrl = try await storageRef.downloadURL()
        return downloadUrl.absoluteString
    }
    
    /// Upload expert verification document.
    /// - Parameters:
    ///   - userId: The user's ID
    ///   - documentData: The document data as KotlinByteArray
    ///   - contentType: The MIME type of the document
    /// - Returns: The download URL of the uploaded document
    public func uploadVerificationDocument(userId: String, documentData: KotlinByteArray, contentType: String) async throws -> String {
        // Convert KotlinByteArray to Data
        let data = documentData.toData()
        
        // Determine file extension from content type
        let ext: String
        if contentType.contains("pdf") {
            ext = "pdf"
        } else if contentType.contains("jpeg") || contentType.contains("jpg") {
            ext = "jpg"
        } else if contentType.contains("png") {
            ext = "png"
        } else {
            ext = "pdf"
        }
        
        // Create reference: verification_docs/{userId}/doc_{timestamp}.{ext}
        let timestamp = Int(Date().timeIntervalSince1970 * 1000)
        let fileName = "doc_\(timestamp).\(ext)"
        let storageRef = storage.reference()
            .child(folderVerificationDocs)
            .child(userId)
            .child(fileName)
        
        // Create metadata
        let metadata = StorageMetadata()
        metadata.contentType = contentType
        
        // Upload with metadata
        _ = try await storageRef.putDataAsync(data, metadata: metadata)
        
        // Get the download URL
        let downloadUrl = try await storageRef.downloadURL()
        return downloadUrl.absoluteString
    }
    
    // MARK: - Cloudinary Upload Methods
    
    /// Ensure URL uses HTTPS (required for network security)
    /// Converts http:// to https://
    private func ensureHttps(_ url: String) -> String {
        if url.hasPrefix("http://") {
            return url.replacingOccurrences(of: "http://", with: "https://")
        }
        return url
    }
    
    /// Upload post media (image or video) to Cloudinary.
    /// - Parameters:
    ///   - mediaData: The media file data as KotlinByteArray
    ///   - mediaType: The type of media (IMAGE or VIDEO)
    ///   - thumbnailData: Optional thumbnail data for videos as KotlinByteArray
    /// - Returns: MediaUploadResult containing mediaUrl and optional thumbnailUrl
    public func uploadPostMediaToCloudinary(
        mediaData: KotlinByteArray,
        mediaType: Shared.MediaType,
        thumbnailData: KotlinByteArray?
    ) async throws -> Shared.MediaUploadResult {
        print("📤 IOSStorageRepository: Starting Cloudinary upload")
        print("   - Media Data Size: \(mediaData.size) bytes")
        print("   - Media Type: \(mediaType == Shared.MediaType.video ? "video" : "image")")
        print("   - Thumbnail Data Size: \(thumbnailData?.size ?? 0) bytes")
        
        // Convert KotlinByteArray to Data
        let mediaDataObj = mediaData.toData()
        print("   - Converted to Data: \(mediaDataObj.count) bytes")
        
        // Upload main media file
        let params = CLDUploadRequestParams()
        params.setFolder("posts")
        // Cloudinary requires resource_type for video uploads; defaults to "image" which causes 400 for video data
        let resourceType: CLDUrlResourceType = mediaType == Shared.MediaType.video ? .video : .image
        params.setResourceType(resourceType)
        print("   - Resource Type: \(resourceType == .video ? "video" : "image")")
        
        let mediaUrl = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            print("📤 IOSStorageRepository: Starting Cloudinary signed upload...")
            cloudinary.createUploader().signedUpload(
                data: mediaDataObj,
                params: params,
                progress: { progress in
                    // Log progress for debugging
                    let progressPercent = Int(progress.fractionCompleted * 100)
                    print("📤 IOSStorageRepository: Upload progress: \(progressPercent)%")
                },
                completionHandler: { result, error in
                    if let error = error {
                        print("❌ IOSStorageRepository: Upload failed - \(error.localizedDescription)")
                        continuation.resume(throwing: error)
                    } else if let secureUrl = result?.secureUrl {
                        print("✅ IOSStorageRepository: Media uploaded successfully")
                        print("   - Media URL: \(secureUrl)")
                        continuation.resume(returning: self.ensureHttps(secureUrl))
                    } else if let url = result?.url {
                        print("✅ IOSStorageRepository: Media uploaded successfully (non-secure URL)")
                        print("   - Media URL: \(url)")
                        continuation.resume(returning: self.ensureHttps(url))
                    } else {
                        print("❌ IOSStorageRepository: Upload succeeded but no URL returned")
                        continuation.resume(throwing: NSError(domain: "IOSStorageRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "Upload succeeded but no URL returned"]))
                    }
                }
            )
        }
        
        // Upload thumbnail if provided and media is video
        let thumbnailUrl: String?
        if mediaType == Shared.MediaType.video, let thumbData = thumbnailData {
            do {
                print("📤 IOSStorageRepository: Starting thumbnail upload...")
                // Convert KotlinByteArray to Data
                let thumbDataObj = thumbData.toData()
                print("   - Thumbnail Data: \(thumbDataObj.count) bytes")
                
                let thumbParams = CLDUploadRequestParams()
                thumbParams.setFolder("posts/thumbnails")
                
                thumbnailUrl = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String?, Error>) in
                    cloudinary.createUploader().signedUpload(
                        data: thumbDataObj,
                        params: thumbParams,
                        progress: { progress in
                            // Log progress for debugging
                            let progressPercent = Int(progress.fractionCompleted * 100)
                            print("📤 IOSStorageRepository: Thumbnail upload progress: \(progressPercent)%")
                        },
                        completionHandler: { result, error in
                            if let error = error {
                                print("⚠️ IOSStorageRepository: Thumbnail upload failed (continuing without thumbnail): \(error.localizedDescription)")
                                continuation.resume(returning: nil)
                            } else if let secureUrl = result?.secureUrl {
                                print("✅ IOSStorageRepository: Thumbnail uploaded successfully")
                                print("   - Thumbnail URL: \(secureUrl)")
                                continuation.resume(returning: self.ensureHttps(secureUrl))
                            } else if let url = result?.url {
                                print("✅ IOSStorageRepository: Thumbnail uploaded successfully (non-secure URL)")
                                print("   - Thumbnail URL: \(url)")
                                continuation.resume(returning: self.ensureHttps(url))
                            } else {
                                print("⚠️ IOSStorageRepository: Thumbnail upload succeeded but no URL returned")
                                continuation.resume(returning: nil)
                            }
                        }
                    )
                }
            } catch {
                print("⚠️ IOSStorageRepository: Thumbnail upload error (continuing without thumbnail): \(error.localizedDescription)")
                thumbnailUrl = nil
            }
        } else {
            thumbnailUrl = nil
        }
        
        print("✅ IOSStorageRepository: Media upload completed")
        print("   - Media URL: \(mediaUrl)")
        print("   - Thumbnail URL: \(thumbnailUrl ?? "none")")
        
        return Shared.MediaUploadResult(
            mediaUrl: mediaUrl,
            thumbnailUrl: thumbnailUrl
        )
    }
    
    /// Upload voice caption audio file to Cloudinary.
    /// - Parameter audioData: The audio file data as KotlinByteArray
    /// - Returns: The Cloudinary URL of the uploaded audio file
    public func uploadVoiceCaptionToCloudinary(audioData: KotlinByteArray) async throws -> String {
        // Convert KotlinByteArray to Data
        let data = audioData.toData()
        
        let params = CLDUploadRequestParams()
        params.setFolder("posts/voice_captions")
        params.setResourceType("video") // Cloudinary treats audio as video resource type
        
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            cloudinary.createUploader().signedUpload(
                data: data,
                params: params,
                progress: { _ in },
                completionHandler: { result, error in
                    if let error = error {
                        continuation.resume(throwing: error)
                    } else if let secureUrl = result?.secureUrl {
                        continuation.resume(returning: self.ensureHttps(secureUrl))
                    } else if let url = result?.url {
                        continuation.resume(returning: self.ensureHttps(url))
                    } else {
                        continuation.resume(throwing: NSError(domain: "IOSStorageRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "Upload succeeded but no URL returned"]))
                    }
                }
            )
        }
    }
    
    // MARK: - Firebase Storage Upload Methods
    
    /// Upload post media (image or video) to Firebase Storage.
    /// - Parameters:
    ///   - mediaData: The media file data as KotlinByteArray
    ///   - mediaType: The type of media (IMAGE or VIDEO)
    ///   - thumbnailData: Optional thumbnail data for videos as KotlinByteArray
    /// - Returns: MediaUploadResult containing mediaUrl and optional thumbnailUrl
    public func uploadPostMediaToFirebase(
        mediaData: KotlinByteArray,
        mediaType: Shared.MediaType,
        thumbnailData: KotlinByteArray?
    ) async throws -> Shared.MediaUploadResult {
        print("📤 IOSStorageRepository: Starting Firebase Storage upload")
        print("   - Media Data Size: \(mediaData.size) bytes")
        print("   - Media Type: \(mediaType == Shared.MediaType.video ? "video" : "image")")
        print("   - Thumbnail Data Size: \(thumbnailData?.size ?? 0) bytes")
        
        // Convert KotlinByteArray to Data
        let mediaDataObj = mediaData.toData()
        
        // Compress media before upload
        let compressedMediaData: Data
        if mediaType == Shared.MediaType.image {
            compressedMediaData = MediaCompressor.compressImage(mediaDataObj)
        } else {
            compressedMediaData = MediaCompressor.compressVideo(mediaDataObj)
        }
        
        print("📤 IOSStorageRepository: Media compressed: \(mediaDataObj.count) -> \(compressedMediaData.count) bytes")
        
        // Generate thumbnail for videos if not provided
        let finalThumbnailData: Data?
        if mediaType == Shared.MediaType.video {
            if let thumbData = thumbnailData {
                finalThumbnailData = thumbData.toData()
            } else {
                print("📤 IOSStorageRepository: Generating thumbnail for video")
                // Save compressed video to temp file for thumbnail generation
                let tempDir = FileManager.default.temporaryDirectory
                let tempVideoURL = tempDir.appendingPathComponent("temp_video_\(UUID().uuidString).mp4")
                try compressedMediaData.write(to: tempVideoURL)
                finalThumbnailData = VideoThumbnailGenerator.generateThumbnailFromURL(tempVideoURL)
                try? FileManager.default.removeItem(at: tempVideoURL)
            }
        } else {
            finalThumbnailData = nil
        }
        
        // Determine file extension and content type
        let (fileExtension, contentType): (String, String)
        if mediaType == Shared.MediaType.image {
            fileExtension = "jpg"
            contentType = "image/jpeg"
        } else {
            fileExtension = "mp4"
            contentType = "video/mp4"
        }
        
        // Generate unique file name
        let uuid = UUID().uuidString.prefix(8)
        let fileName = "media_\(Int(Date().timeIntervalSince1970 * 1000))_\(uuid).\(fileExtension)"
        let storageRef = storage.reference()
            .child(folderPosts)
            .child(fileName)
        
        // Create metadata with cache control
        let metadata = StorageMetadata()
        metadata.contentType = contentType
        metadata.cacheControl = "public, max-age=31536000" // 1 year cache
        
        // Upload media
        _ = try await storageRef.putDataAsync(compressedMediaData, metadata: metadata)
        
        // Get download URL
        let mediaUrl = try await storageRef.downloadURL()
        let mediaUrlString = mediaUrl.absoluteString
        print("✅ IOSStorageRepository: Media uploaded successfully")
        print("   - Media URL: \(mediaUrlString)")
        
        // Upload thumbnail if available
        let thumbnailUrl: String?
        if let thumbData = finalThumbnailData, mediaType == Shared.MediaType.video {
            do {
                let thumbUuid = UUID().uuidString.prefix(8)
                let thumbFileName = "thumb_\(Int(Date().timeIntervalSince1970 * 1000))_\(thumbUuid).jpg"
                let thumbStorageRef = storage.reference()
                    .child(folderPosts)
                    .child("thumbnails")
                    .child(thumbFileName)
                
                let thumbMetadata = StorageMetadata()
                thumbMetadata.contentType = "image/jpeg"
                thumbMetadata.cacheControl = "public, max-age=31536000" // 1 year cache
                
                _ = try await thumbStorageRef.putDataAsync(thumbData, metadata: thumbMetadata)
                let thumbUrl = try await thumbStorageRef.downloadURL()
                thumbnailUrl = thumbUrl.absoluteString
                print("✅ IOSStorageRepository: Thumbnail uploaded successfully")
                print("   - Thumbnail URL: \(thumbnailUrl ?? "none")")
            } catch {
                print("⚠️ IOSStorageRepository: Failed to upload thumbnail, continuing without thumbnail: \(error.localizedDescription)")
                thumbnailUrl = nil
            }
        } else {
            thumbnailUrl = nil
        }
        
        print("✅ IOSStorageRepository: Firebase Storage upload completed")
        print("   - Media URL: \(mediaUrlString)")
        print("   - Thumbnail URL: \(thumbnailUrl ?? "none")")
        
        return Shared.MediaUploadResult(
            mediaUrl: mediaUrlString,
            thumbnailUrl: thumbnailUrl
        )
    }
    
    /// Upload voice caption audio file to Firebase Storage.
    /// - Parameter audioData: The audio file data as KotlinByteArray
    /// - Returns: The Firebase Storage URL of the uploaded audio file
    public func uploadVoiceCaptionToFirebase(audioData: KotlinByteArray) async throws -> String {
        print("📤 IOSStorageRepository: Starting voice caption upload to Firebase Storage")
        print("   - Audio Data Size: \(audioData.size) bytes")
        
        // Convert KotlinByteArray to Data
        let data = audioData.toData()
        
        // Compress audio before upload
        let compressedAudioData = MediaCompressor.compressAudio(data)
        print("📤 IOSStorageRepository: Audio compressed: \(data.count) -> \(compressedAudioData.count) bytes")
        
        // Generate unique file name
        let uuid = UUID().uuidString.prefix(8)
        let fileName = "voice_\(Int(Date().timeIntervalSince1970 * 1000))_\(uuid).m4a"
        let storageRef = storage.reference()
            .child(folderPosts)
            .child("voice_captions")
            .child(fileName)
        
        // Create metadata with cache control
        let metadata = StorageMetadata()
        metadata.contentType = "audio/mp4"
        metadata.cacheControl = "public, max-age=31536000" // 1 year cache
        
        // Upload audio
        _ = try await storageRef.putDataAsync(compressedAudioData, metadata: metadata)
        
        // Get download URL
        let downloadUrl = try await storageRef.downloadURL()
        print("✅ IOSStorageRepository: Voice caption uploaded to Firebase Storage successfully")
        print("   - Voice URL: \(downloadUrl.absoluteString)")
        
        return downloadUrl.absoluteString
    }
}

// MARK: - Helper Extension
extension KotlinByteArray {
    func toData() -> Data {
        var bytes: [UInt8] = []
        for i in 0..<Int(self.size) {
            bytes.append(UInt8(bitPattern: self.get(index: Int32(i))))
        }
        return Data(bytes)
    }
}
