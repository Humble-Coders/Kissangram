package com.kissangram.repository

/**
 * Repository interface for Firebase Storage operations.
 * Used for uploading profile images, post media, etc.
 */
interface StorageRepository {
    /**
     * Upload a profile image for the given user.
     * @param userId The user's ID
     * @param imageData The image data as ByteArray
     * @return The download URL of the uploaded image
     */
    @Throws(Exception::class)
    suspend fun uploadProfileImage(userId: String, imageData: ByteArray): String
    
    /**
     * Delete a profile image for the given user.
     * @param userId The user's ID
     */
    @Throws(Exception::class)
    suspend fun deleteProfileImage(userId: String)
    
    /**
     * Upload post media (image or video thumbnail).
     * @param postId The post's ID
     * @param mediaIndex The index of the media in the post
     * @param mediaData The media data as ByteArray
     * @param contentType The MIME type of the media (e.g., "image/jpeg")
     * @return The download URL of the uploaded media
     */
    @Throws(Exception::class)
    suspend fun uploadPostMedia(postId: String, mediaIndex: Int, mediaData: ByteArray, contentType: String): String
    
    /**
     * Upload expert verification document.
     * @param userId The user's ID
     * @param documentData The document data as ByteArray
     * @param contentType The MIME type of the document
     * @return The download URL of the uploaded document
     */
    @Throws(Exception::class)
    suspend fun uploadVerificationDocument(userId: String, documentData: ByteArray, contentType: String): String
}
