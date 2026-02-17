package com.kissangram.util

/**
 * Utility for transforming Cloudinary URLs with optimizations
 * for different display contexts (feed, thumbnail, full size)
 */
object CloudinaryUrlTransformer {
    
    /**
     * Ensure Cloudinary URL uses HTTPS (required for iOS App Transport Security)
     */
    fun ensureHttps(url: String): String {
        return if (url.startsWith("http://")) {
            url.replace("http://", "https://")
        } else {
            url
        }
    }
    
    /**
     * Transform URL for feed display (optimized size)
     * Uses: w_800,h_800,c_limit,q_auto,f_auto for Cloudinary
     * Delegates to FirebaseStorageUrlTransformer for Firebase Storage URLs
     */
    fun transformForFeed(url: String): String {
        // Check if Firebase Storage URL first
        if (FirebaseStorageUrlTransformer.isFirebaseStorageUrl(url)) {
            return FirebaseStorageUrlTransformer.transformForFeed(url)
        }
        
        if (!isCloudinaryUrl(url)) {
            return url
        }
        return addTransformations(ensureHttps(url), "w_800,h_800,c_limit,q_auto,f_auto")
    }
    
    /**
     * Transform URL for thumbnail display (small size)
     * Uses: w_300,h_300,c_fill,q_auto,f_auto for Cloudinary
     * Delegates to FirebaseStorageUrlTransformer for Firebase Storage URLs
     */
    fun transformForThumbnail(url: String, thumbnailUrl: String? = null): String {
        // Check if Firebase Storage URL first
        if (FirebaseStorageUrlTransformer.isFirebaseStorageUrl(url)) {
            return FirebaseStorageUrlTransformer.transformForThumbnail(url, thumbnailUrl)
        }
        
        if (!isCloudinaryUrl(url)) return url
        return addTransformations(ensureHttps(url), "w_300,h_300,c_fill,q_auto,f_auto")
    }
    
    /**
     * Generate thumbnail URL from a video URL.
     * For Cloudinary videos, adds thumbnail transformations to extract a frame.
     * For Firebase Storage videos, delegates to FirebaseStorageUrlTransformer.
     * Format: /video/upload/w_300,h_300,c_fill/so_0.3/v123/folder/file.jpg
     * The .jpg extension at the end tells Cloudinary to render the frame as an image.
     */
    fun generateVideoThumbnailUrl(videoUrl: String): String {
        // Check if Firebase Storage URL first
        if (FirebaseStorageUrlTransformer.isFirebaseStorageUrl(videoUrl)) {
            return FirebaseStorageUrlTransformer.generateVideoThumbnailUrl(videoUrl)
        }
        
        if (!isCloudinaryUrl(videoUrl)) {
            return videoUrl
        }
        
        val secureUrl = ensureHttps(videoUrl)
        
        // Remove any existing query parameters
        val baseUrl = secureUrl.split("?")[0]
        
        // Change file extension from video format to .jpg
        val urlWithoutExtension = baseUrl.replace(Regex("\\.(mp4|mov|avi|webm|mkv)$"), "")
        val urlWithJpg = "$urlWithoutExtension.jpg"
        
        // Insert transformations and so_ parameter right after /upload/
        // Format: https://res.cloudinary.com/cloud/video/upload/w_300,h_300,c_fill/so_0.3/v123/folder/file.jpg
        val uploadIndex = urlWithJpg.indexOf("/upload/")
        if (uploadIndex != -1) {
            val afterUpload = urlWithJpg.substring(uploadIndex + "/upload/".length)
            
            // Insert transformations and so_ right after /upload/
            val transformations = "w_300,h_300,c_fill/so_0.3"
            return urlWithJpg.substring(0, uploadIndex + "/upload/".length) + 
                   "$transformations/" + afterUpload
        }
        
        return urlWithJpg
    }
    
    /**
     * Get original URL without transformations
     */
    fun getOriginal(url: String): String {
        // Check if Firebase Storage URL first
        if (FirebaseStorageUrlTransformer.isFirebaseStorageUrl(url)) {
            return FirebaseStorageUrlTransformer.getOriginal(url)
        }
        
        if (!isCloudinaryUrl(url)) return url
        // Remove any existing transformations and ensure HTTPS
        val baseUrl = ensureHttps(url).split("?")[0]
        return baseUrl
    }
    
    /**
     * Add custom transformations to a Cloudinary URL
     * Cloudinary supports transformations as query parameters
     * Format: https://res.cloudinary.com/cloud_name/image/upload/v123/folder/file.jpg?w_800,h_800
     */
    fun addTransformations(url: String, transformations: String): String {
        if (!isCloudinaryUrl(url)) return url
        val secureUrl = ensureHttps(url)
        
        // Split URL and query parameters
        val parts = secureUrl.split("?", limit = 2)
        val baseUrl = parts[0]
        val existingParams = parts.getOrNull(1)
        
        // Add transformations as query parameter (baseUrl is already HTTPS from ensureHttps)
        // If there are existing params, combine them
        return if (existingParams != null && existingParams.isNotEmpty()) {
            "$baseUrl?$transformations,$existingParams"
        } else {
            "$baseUrl?$transformations"
        }
    }
    
    /**
     * Check if URL is a Cloudinary URL
     */
    private fun isCloudinaryUrl(url: String): Boolean {
        return url.contains("cloudinary.com") || url.contains("res.cloudinary.com")
    }
}
