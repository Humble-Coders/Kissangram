import Foundation
import FirebaseStorage

/// iOS implementation of StorageRepository using Firebase Storage.
/// Handles uploading profile images, post media, and verification documents.
final class IOSStorageRepository {
    
    private let storage: Storage
    
    // Storage folder paths
    private let folderProfileImages = "profile_images"
    private let folderPosts = "posts"
    private let folderVerificationDocs = "verification_docs"
    
    init() {
        self.storage = Storage.storage()
    }
    
    /// Upload a profile image for the given user.
    /// - Parameters:
    ///   - userId: The user's ID
    ///   - imageData: The image data
    /// - Returns: The download URL of the uploaded image
    func uploadProfileImage(userId: String, imageData: Data) async throws -> String {
        print("📤 IOSStorageRepository: Uploading profile image for user \(userId), size=\(imageData.count) bytes")
        
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
        
        do {
            // Upload the image
            _ = try await storageRef.putDataAsync(imageData, metadata: metadata)
            
            // Get the download URL
            let downloadUrl = try await storageRef.downloadURL()
            print("✅ IOSStorageRepository: Profile image uploaded, URL: \(downloadUrl.absoluteString)")
            return downloadUrl.absoluteString
        } catch {
            print("❌ IOSStorageRepository: Profile image upload failed - \(error.localizedDescription)")
            throw error
        }
    }
    
    /// Delete a profile image for the given user.
    /// - Parameter userId: The user's ID
    func deleteProfileImage(userId: String) async throws {
        print("🗑️ IOSStorageRepository: Deleting profile image for user \(userId)")
        
        let folderRef = storage.reference()
            .child(folderProfileImages)
            .child(userId)
        
        do {
            // List all files in the user's profile_images folder
            let listResult = try await folderRef.listAll()
            
            // Delete each file
            for item in listResult.items {
                try await item.delete()
                print("🗑️ IOSStorageRepository: Deleted \(item.fullPath)")
            }
            
            print("✅ IOSStorageRepository: Deleted \(listResult.items.count) profile image files")
        } catch {
            print("❌ IOSStorageRepository: Delete profile image failed - \(error.localizedDescription)")
            throw error
        }
    }
    
    /// Upload post media (image or video thumbnail).
    /// - Parameters:
    ///   - postId: The post's ID
    ///   - mediaIndex: The index of the media in the post
    ///   - mediaData: The media data
    ///   - contentType: The MIME type of the media
    /// - Returns: The download URL of the uploaded media
    func uploadPostMedia(postId: String, mediaIndex: Int, mediaData: Data, contentType: String) async throws -> String {
        print("📤 IOSStorageRepository: Uploading post media for post \(postId), index=\(mediaIndex), size=\(mediaData.count) bytes")
        
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
        
        do {
            // Upload with metadata
            _ = try await storageRef.putDataAsync(mediaData, metadata: metadata)
            
            // Get the download URL
            let downloadUrl = try await storageRef.downloadURL()
            print("✅ IOSStorageRepository: Post media uploaded, URL: \(downloadUrl.absoluteString)")
            return downloadUrl.absoluteString
        } catch {
            print("❌ IOSStorageRepository: Post media upload failed - \(error.localizedDescription)")
            throw error
        }
    }
    
    /// Upload expert verification document.
    /// - Parameters:
    ///   - userId: The user's ID
    ///   - documentData: The document data
    ///   - contentType: The MIME type of the document
    /// - Returns: The download URL of the uploaded document
    func uploadVerificationDocument(userId: String, documentData: Data, contentType: String) async throws -> String {
        print("📤 IOSStorageRepository: Uploading verification document for user \(userId), size=\(documentData.count) bytes")
        
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
        
        do {
            // Upload with metadata
            _ = try await storageRef.putDataAsync(documentData, metadata: metadata)
            
            // Get the download URL
            let downloadUrl = try await storageRef.downloadURL()
            print("✅ IOSStorageRepository: Verification document uploaded, URL: \(downloadUrl.absoluteString)")
            return downloadUrl.absoluteString
        } catch {
            print("❌ IOSStorageRepository: Verification document upload failed - \(error.localizedDescription)")
            throw error
        }
    }
}
