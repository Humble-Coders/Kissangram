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
    
    /// Upload a profile image for the given user.
    /// - Parameters:
    ///   - userId: The user's ID
    ///   - imageData: The image data as KotlinByteArray
    /// - Returns: The download URL of the uploaded image
    public func uploadProfileImage(userId: String, imageData: KotlinByteArray) async throws -> String {
        // Convert KotlinByteArray to Data
        let data = imageData.toData()
        
        // Create reference: profile_images/{userId}/profile_{timestamp}.jpg
        let timestamp = Int(Date().timeIntervalSince1970 * 1000)
        let fileName = "profile_\(timestamp).jpg"
        let storageRef = storage.reference()
            .child(folderProfileImages)
            .child(userId)
            .child(fileName)
        
        // Create metadata
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        
        // Upload the image
        _ = try await storageRef.putDataAsync(data, metadata: metadata)
        
        // Get the download URL
        let downloadUrl = try await storageRef.downloadURL()
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
        // Convert KotlinByteArray to Data
        let mediaDataObj = mediaData.toData()
        
        // Upload main media file
        let params = CLDUploadRequestParams()
        params.setFolder("posts")
        
        let mediaUrl = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            cloudinary.createUploader().signedUpload(
                data: mediaDataObj,
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
        
        // Upload thumbnail if provided and media is video
        let thumbnailUrl: String?
        if mediaType == Shared.MediaType.video, let thumbData = thumbnailData {
            do {
                // Convert KotlinByteArray to Data
                let thumbDataObj = thumbData.toData()
                
                let thumbParams = CLDUploadRequestParams()
                thumbParams.setFolder("posts/thumbnails")
                
                thumbnailUrl = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String?, Error>) in
                    cloudinary.createUploader().signedUpload(
                        data: thumbDataObj,
                        params: thumbParams,
                        progress: { _ in },
                        completionHandler: { result, error in
                            if let error = error {
                                continuation.resume(returning: nil)
                            } else if let secureUrl = result?.secureUrl {
                                continuation.resume(returning: self.ensureHttps(secureUrl))
                            } else if let url = result?.url {
                                continuation.resume(returning: self.ensureHttps(url))
                            } else {
                                continuation.resume(returning: nil)
                            }
                        }
                    )
                }
            } catch {
                thumbnailUrl = nil
            }
        } else {
            thumbnailUrl = nil
        }
        
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
