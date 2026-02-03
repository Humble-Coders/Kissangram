package com.kissangram.repository

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Android implementation of StorageRepository using Firebase Storage.
 * Handles uploading profile images, post media, and verification documents.
 */
class AndroidStorageRepository : com.kissangram.repository.StorageRepository {

    private val storage: FirebaseStorage by lazy {
        // Use the "kissangram" database app instance if available, otherwise default
        try {
            FirebaseStorage.getInstance(FirebaseApp.getInstance())
        } catch (e: Exception) {
            Log.w(TAG, "Using default Firebase Storage instance", e)
            FirebaseStorage.getInstance()
        }
    }
    
    override suspend fun uploadProfileImage(userId: String, imageData: ByteArray): String {
        Log.d(TAG, "uploadProfileImage: Starting upload for user $userId, size=${imageData.size} bytes")
        
        // Create reference: profile_images/{userId}/profile_{timestamp}.jpg
        val timestamp = System.currentTimeMillis()
        val fileName = "profile_$timestamp.jpg"
        val storageRef = storage.reference
            .child(FOLDER_PROFILE_IMAGES)
            .child(userId)
            .child(fileName)
        
        return try {
            // Upload the image
            val uploadTask = storageRef.putBytes(imageData)
            uploadTask.await()
            
            // Get the download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "uploadProfileImage: SUCCESS - URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "uploadProfileImage: FAILED", e)
            throw Exception("Failed to upload profile image: ${e.message}", e)
        }
    }
    
    override suspend fun deleteProfileImage(userId: String) {
        Log.d(TAG, "deleteProfileImage: Deleting profile image for user $userId")
        
        try {
            // List all files in the user's profile_images folder and delete them
            val folderRef = storage.reference
                .child(FOLDER_PROFILE_IMAGES)
                .child(userId)
            
            val listResult = folderRef.listAll().await()
            for (item in listResult.items) {
                item.delete().await()
                Log.d(TAG, "deleteProfileImage: Deleted ${item.path}")
            }
            
            Log.d(TAG, "deleteProfileImage: SUCCESS - Deleted ${listResult.items.size} files")
        } catch (e: Exception) {
            Log.e(TAG, "deleteProfileImage: FAILED", e)
            throw Exception("Failed to delete profile image: ${e.message}", e)
        }
    }
    
    override suspend fun uploadPostMedia(
        postId: String,
        mediaIndex: Int,
        mediaData: ByteArray,
        contentType: String
    ): String {
        Log.d(TAG, "uploadPostMedia: Starting upload for post $postId, index=$mediaIndex, size=${mediaData.size} bytes")
        
        // Determine file extension from content type
        val extension = when {
            contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
            contentType.contains("png") -> "png"
            contentType.contains("gif") -> "gif"
            contentType.contains("webp") -> "webp"
            contentType.contains("mp4") -> "mp4"
            contentType.contains("video") -> "mp4"
            else -> "jpg"
        }
        
        // Create reference: posts/{postId}/media_{index}_{uuid}.{ext}
        val uuid = UUID.randomUUID().toString().take(8)
        val fileName = "media_${mediaIndex}_$uuid.$extension"
        val storageRef = storage.reference
            .child(FOLDER_POSTS)
            .child(postId)
            .child(fileName)
        
        return try {
            // Create metadata
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(contentType)
                .build()
            
            // Upload with metadata
            val uploadTask = storageRef.putBytes(mediaData, metadata)
            uploadTask.await()
            
            // Get the download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "uploadPostMedia: SUCCESS - URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "uploadPostMedia: FAILED", e)
            throw Exception("Failed to upload post media: ${e.message}", e)
        }
    }
    
    override suspend fun uploadVerificationDocument(
        userId: String,
        documentData: ByteArray,
        contentType: String
    ): String {
        Log.d(TAG, "uploadVerificationDocument: Starting upload for user $userId, size=${documentData.size} bytes")
        
        // Determine file extension from content type
        val extension = when {
            contentType.contains("pdf") -> "pdf"
            contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
            contentType.contains("png") -> "png"
            else -> "pdf"
        }
        
        // Create reference: verification_docs/{userId}/doc_{timestamp}.{ext}
        val timestamp = System.currentTimeMillis()
        val fileName = "doc_$timestamp.$extension"
        val storageRef = storage.reference
            .child(FOLDER_VERIFICATION_DOCS)
            .child(userId)
            .child(fileName)
        
        return try {
            // Create metadata
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(contentType)
                .build()
            
            // Upload with metadata
            val uploadTask = storageRef.putBytes(documentData, metadata)
            uploadTask.await()
            
            // Get the download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "uploadVerificationDocument: SUCCESS - URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "uploadVerificationDocument: FAILED", e)
            throw Exception("Failed to upload verification document: ${e.message}", e)
        }
    }
    
    companion object {
        private const val TAG = "AndroidStorageRepo"
        private const val FOLDER_PROFILE_IMAGES = "profile_images"
        private const val FOLDER_POSTS = "posts"
        private const val FOLDER_VERIFICATION_DOCS = "verification_docs"
    }
}
