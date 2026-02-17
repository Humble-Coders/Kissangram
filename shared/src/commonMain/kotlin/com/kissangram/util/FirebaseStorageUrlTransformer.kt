package com.kissangram.util

/**
 * Utility for transforming Firebase Storage URLs.
 * Firebase Storage doesn't support URL transformations like Cloudinary,
 * so this utility mainly handles URL validation and thumbnail URL resolution.
 */
object FirebaseStorageUrlTransformer {
    
    /**
     * Ensure Firebase Storage URL uses HTTPS (required for network security)
     */
    fun ensureHttps(url: String): String {
        return if (url.startsWith("http://")) {
            url.replace("http://", "https://")
        } else {
            url
        }
    }
    
    /**
     * Transform URL for feed display.
     * Firebase Storage doesn't support URL transformations, so return original URL.
     * @param url Firebase Storage URL
     * @return Original URL (no transformations applied)
     */
    fun transformForFeed(url: String): String {
        if (!isFirebaseStorageUrl(url)) {
            return url
        }
        return ensureHttps(url)
    }
    
    /**
     * Transform URL for thumbnail display.
     * If a thumbnail URL is provided separately, use it.
     * Otherwise, return original URL (Firebase Storage doesn't generate thumbnails on-the-fly).
     * @param url Firebase Storage URL
     * @param thumbnailUrl Optional separate thumbnail URL
     * @return Thumbnail URL if available, otherwise original URL
     */
    fun transformForThumbnail(url: String, thumbnailUrl: String? = null): String {
        if (!isFirebaseStorageUrl(url)) {
            return url
        }
        
        // If thumbnail URL is provided, use it
        if (thumbnailUrl != null && thumbnailUrl.isNotBlank()) {
            return ensureHttps(thumbnailUrl)
        }
        
        // Otherwise return original URL (no on-the-fly thumbnail generation)
        return ensureHttps(url)
    }
    
    /**
     * Generate thumbnail URL from a video URL.
     * For Firebase Storage, thumbnails are stored separately, so this returns
     * the expected thumbnail path based on the video URL pattern.
     * 
     * Pattern: posts/{postId}/media_{index}_{uuid}.mp4
     * Thumbnail: posts/{postId}/thumbnails/thumb_{uuid}.jpg
     * 
     * @param videoUrl Firebase Storage video URL
     * @return Expected thumbnail URL pattern, or original URL if pattern doesn't match
     */
    fun generateVideoThumbnailUrl(videoUrl: String): String {
        if (!isFirebaseStorageUrl(videoUrl)) {
            return videoUrl
        }
        
        val secureUrl = ensureHttps(videoUrl)
        
        // Try to extract UUID from video URL and construct thumbnail URL
        // Pattern: .../posts/{postId}/media_{index}_{uuid}.mp4
        // Thumbnail: .../posts/{postId}/thumbnails/thumb_{uuid}.jpg
        val uuidPattern = Regex("media_\\d+_([a-fA-F0-9-]+)\\.[^.]+$")
        val match = uuidPattern.find(secureUrl)
        
        if (match != null) {
            val uuid = match.groupValues[1]
            // Replace media file path with thumbnail path
            val thumbnailUrl = secureUrl.replace(
                Regex("media_\\d+_$uuid\\.[^.]+$"),
                "thumbnails/thumb_$uuid.jpg"
            )
            return thumbnailUrl
        }
        
        // If pattern doesn't match, return original URL
        return secureUrl
    }
    
    /**
     * Check if URL is a Firebase Storage URL
     */
    fun isFirebaseStorageUrl(url: String): Boolean {
        return url.contains("firebasestorage.googleapis.com") ||
               url.contains("firebase.storage") ||
               url.startsWith("gs://")
    }
    
    /**
     * Get original URL without any transformations.
     * For Firebase Storage, this just ensures HTTPS.
     */
    fun getOriginal(url: String): String {
        if (!isFirebaseStorageUrl(url)) {
            return url
        }
        return ensureHttps(url)
    }
}
