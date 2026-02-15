package com.kissangram.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.Cloudinary
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.kissangram.model.MediaType
import com.kissangram.model.MediaUploadResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.net.toUri

/**
 * Android implementation of StorageRepository using Firebase Storage and Cloudinary.
 * Handles uploading profile images, post media, and verification documents.
 */
class AndroidStorageRepository(
    private val context: Context
) : com.kissangram.repository.StorageRepository {

    private val storage: FirebaseStorage by lazy {
        // Use the "kissangram" database app instance if available, otherwise default
        try {
            FirebaseStorage.getInstance(FirebaseApp.getInstance())
        } catch (e: Exception) {
            Log.w(TAG, "Using default Firebase Storage instance", e)
            FirebaseStorage.getInstance()
        }
    }
    
    private val cloudinary: Cloudinary by lazy {
        // Initialize Cloudinary - config should be set via MediaManager.init() in Application class
        // For now, we'll use MediaManager.get() which should be initialized elsewhere
        try {
            MediaManager.get().cloudinary
        } catch (e: Exception) {
            Log.e(TAG, "Cloudinary not initialized. Please call MediaManager.init() in Application class", e)
            throw IllegalStateException("Cloudinary not initialized. Please configure Cloudinary in Application class.")
        }
    }
    
    override suspend fun uploadProfileImage(userId: String, imageData: ByteArray): String {
        Log.d(TAG, "uploadProfileImage: Starting Cloudinary upload for user $userId, size=${imageData.size} bytes")
        
        return try {
            // Write ByteArray to temporary file for Cloudinary upload
            val tempFile = File(context.cacheDir, "profile_${UUID.randomUUID()}.tmp")
            tempFile.outputStream().use { it.write(imageData) }
            
            suspendCancellableCoroutine<String> { continuation ->
                val requestId = MediaManager.get().upload(tempFile.absolutePath)
                    .option("resource_type", "image")
                    .option("folder", FOLDER_PROFILE_IMAGES)
                    .option("public_id", "profile_${userId}_${System.currentTimeMillis()}")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d(TAG, "uploadProfileImage: Upload started")
                        }
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                        override fun onSuccess(requestId: String, resultData: Map<*, *>?) {
                            val url = (resultData?.get("secure_url") as? String
                                ?: resultData?.get("url") as? String)
                                ?.let { ensureHttps(it) }
                                ?: throw IllegalStateException("No URL in upload result")
                            Log.d(TAG, "uploadProfileImage: SUCCESS - URL: $url")
                            continuation.resume(url)
                        }
                        override fun onError(requestId: String, error: ErrorInfo) {
                            Log.e(TAG, "uploadProfileImage: FAILED - ${error.description}")
                            continuation.resumeWithException(
                                Exception("Cloudinary upload failed: ${error.description}")
                            )
                        }
                        override fun onReschedule(requestId: String, error: ErrorInfo) {}
                    })
                    .dispatch()
                
                continuation.invokeOnCancellation {
                    MediaManager.get().cancelRequest(requestId)
                }
            }.also {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadProfileImage: FAILED", e)
            throw Exception("Failed to upload profile image to Cloudinary: ${e.message}", e)
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
    
    override suspend fun uploadPostMediaToCloudinary(
        mediaData: ByteArray,
        mediaType: MediaType,
        thumbnailData: ByteArray?
    ): MediaUploadResult {
        Log.d(TAG, "📤 AndroidStorageRepository: Starting Cloudinary upload")
        Log.d(TAG, "   - Media Data Size: ${mediaData.size} bytes")
        Log.d(TAG, "   - Media Type: $mediaType")
        Log.d(TAG, "   - Thumbnail Data Size: ${thumbnailData?.size ?: 0} bytes")
        
        return try {
            // Write ByteArray to temporary file for Cloudinary upload
            val tempFile = File(context.cacheDir, "upload_${UUID.randomUUID()}.tmp")
            tempFile.outputStream().use { it.write(mediaData) }
            
            val fileSize = tempFile.length()
            Log.d(TAG, "📤 AndroidStorageRepository: File created")
            Log.d(TAG, "   - File path: ${tempFile.absolutePath}")
            Log.d(TAG, "   - File size: ${fileSize / 1024} KB")
            
            // Upload main media file
            val mediaUrl = suspendCancellableCoroutine<String> { continuation ->
                val requestId = MediaManager.get().upload(tempFile.absolutePath)
                    .option("resource_type", when (mediaType) {
                        MediaType.IMAGE -> "image"
                        MediaType.VIDEO -> "video"
                    })
                    .option("folder", "posts")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d(TAG, "uploadPostMediaToCloudinary: Upload started, requestId=$requestId")
                        }
                        
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            val progress = (bytes * 100 / totalBytes).toInt()
                            Log.d(TAG, "uploadPostMediaToCloudinary: Progress $progress%")
                        }


                        override fun onSuccess(requestId: String, resultData: Map<*, *>?) {
                            val url = (resultData?.get("secure_url") as? String
                                ?: resultData?.get("url") as? String)
                                ?.let { ensureHttps(it) }
                                ?: throw IllegalStateException("No URL in upload result")
                            Log.d(TAG, "✅ AndroidStorageRepository: Post media uploaded to Cloudinary successfully")
                            Log.d(TAG, "   - Media URL: $url")
                            continuation.resume(url)
                        }
                        
                        override fun onError(requestId: String, error: ErrorInfo) {
                            Log.e(TAG, "uploadPostMediaToCloudinary: FAILED - ${error.description}")
                            continuation.resumeWithException(
                                Exception("Cloudinary upload failed: ${error.description}")
                            )
                        }
                        
                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            Log.w(TAG, "uploadPostMediaToCloudinary: Rescheduling - ${error.description}")
                        }
                    })
                    .dispatch()
                
                continuation.invokeOnCancellation {
                    MediaManager.get().cancelRequest(requestId)
                }
            }
            
            // Upload thumbnail if provided and media is video
            var thumbFile: File? = null
            val thumbnailUrl = if (mediaType == MediaType.VIDEO && thumbnailData != null) {
                try {
                    // Write thumbnail ByteArray to temporary file
                    thumbFile = File(context.cacheDir, "thumb_${UUID.randomUUID()}.tmp")
                    thumbFile.outputStream().use { it.write(thumbnailData) }
                    
                    if (thumbFile.exists()) {
                        suspendCancellableCoroutine<String?> { continuation ->
                            MediaManager.get().upload(thumbFile!!.absolutePath)
                                .option("resource_type", "image")
                                .option("folder", "posts/thumbnails")
                                .callback(object : UploadCallback {
                                    override fun onStart(requestId: String) {}
                                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                                    override fun onSuccess(requestId: String, resultData: Map<*, *>?) {
                                        val url = (resultData?.get("secure_url") as? String
                                            ?: resultData?.get("url") as? String)
                                            ?.let { ensureHttps(it) }
                                        continuation.resume(url)
                                    }
                                    override fun onError(requestId: String, error: ErrorInfo) {
                                        Log.w(TAG, "Thumbnail upload failed, continuing without thumbnail")
                                        continuation.resume(null)
                                    }
                                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                                        continuation.resume(null)
                                    }
                                })
                                .dispatch()
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to upload thumbnail, continuing without thumbnail", e)
                    null
                }
            } else {
                null
            }
            
            Log.d(TAG, "✅ AndroidStorageRepository: Media upload completed")
            Log.d(TAG, "   - Media URL: $mediaUrl")
            Log.d(TAG, "   - Thumbnail URL: ${thumbnailUrl ?: "none"}")
            
            // Clean up temporary files
            tempFile.delete()
            thumbFile?.delete()
            
            MediaUploadResult(mediaUrl = mediaUrl, thumbnailUrl = thumbnailUrl)
        } catch (e: Exception) {
            Log.e(TAG, "❌ AndroidStorageRepository: Media upload FAILED", e)
            throw Exception("Failed to upload post media to Cloudinary: ${e.message}", e)
        }
    }
    
    override suspend fun uploadVoiceCaptionToCloudinary(audioData: ByteArray): String {
        Log.d(TAG, "📤 AndroidStorageRepository: Starting voice caption upload to Cloudinary")
        Log.d(TAG, "   - Audio Data Size: ${audioData.size} bytes")
        
        return try {
            // Write ByteArray to temporary file for Cloudinary upload
            val tempFile = File(context.cacheDir, "voice_${UUID.randomUUID()}.tmp")
            tempFile.outputStream().use { it.write(audioData) }
            
            val fileSize = tempFile.length()
            Log.d(TAG, "📤 AndroidStorageRepository: Voice caption file created")
            Log.d(TAG, "   - File path: ${tempFile.absolutePath}")
            Log.d(TAG, "   - File size: ${fileSize / 1024} KB")
            
            suspendCancellableCoroutine<String> { continuation ->
                val requestId = MediaManager.get().upload(tempFile.absolutePath)
                    .option("resource_type", "video") // Cloudinary treats audio as video resource type
                    .option("folder", "posts/voice_captions")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d(TAG, "uploadVoiceCaptionToCloudinary: Upload started, requestId=$requestId")
                        }
                        
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            val progress = (bytes * 100 / totalBytes).toInt()
                            Log.d(TAG, "uploadVoiceCaptionToCloudinary: Progress $progress%")
                        }
                        
                        override fun onSuccess(requestId: String, resultData: Map<*, *>?) {
                            val url = (resultData?.get("secure_url") as? String
                                ?: resultData?.get("url") as? String)
                                ?.let { ensureHttps(it) }
                                ?: throw IllegalStateException("No URL in upload result")
                            Log.d(TAG, "✅ AndroidStorageRepository: Voice caption uploaded to Cloudinary successfully")
                            Log.d(TAG, "   - Voice URL: $url")
                            continuation.resume(url)
                        }
                        
                        override fun onError(requestId: String, error: ErrorInfo) {
                            Log.e(TAG, "uploadVoiceCaptionToCloudinary: FAILED - ${error.description}")
                            continuation.resumeWithException(
                                Exception("Cloudinary upload failed: ${error.description}")
                            )
                        }
                        
                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            Log.w(TAG, "uploadVoiceCaptionToCloudinary: Rescheduling - ${error.description}")
                        }
                    })
                    .dispatch()
                
                continuation.invokeOnCancellation {
                    MediaManager.get().cancelRequest(requestId)
                }
            }.also {
                // Clean up temporary file
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadVoiceCaptionToCloudinary: FAILED", e)
            throw Exception("Failed to upload voice caption to Cloudinary: ${e.message}", e)
        }
    }
    
    /**
     * Ensure URL uses HTTPS (required for Android network security)
     * Converts http:// to https://
     */
    private fun ensureHttps(url: String): String {
        return if (url.startsWith("http://")) {
            url.replace("http://", "https://")
        } else {
            url
        }
    }
    
    companion object {
        private const val TAG = "AndroidStorageRepo"
        private const val FOLDER_PROFILE_IMAGES = "profile_images"
        private const val FOLDER_POSTS = "posts"
        private const val FOLDER_VERIFICATION_DOCS = "verification_docs"
    }
}
