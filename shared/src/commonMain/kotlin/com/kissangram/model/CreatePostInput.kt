package com.kissangram.model

/**
 * Input model for creating a new post.
 * This captures data from the Create Post screen before submission.
 */
data class CreatePostInput(
    // Post Type
    val type: PostType = PostType.NORMAL,
    
    // Content
    val text: String = "",
    
    // Media (file data before upload)
    val mediaItems: List<MediaItem> = emptyList(),
    
    // Voice Caption (audio data before upload)
    val voiceCaptionData: ByteArray? = null,
    val voiceCaptionDurationSeconds: Int = 0,
    
    // Crops & Hashtags
    val crops: List<String> = emptyList(),
    val hashtags: List<String> = emptyList(),
    
    // Location (optional)
    val location: CreatePostLocation? = null,
    
    // Visibility
    val visibility: PostVisibility = PostVisibility.PUBLIC,
    
    // Question-specific (only if type == QUESTION)
    val targetExpertise: List<String> = emptyList()
)

/**
 * Media item for create post (before upload)
 */
data class MediaItem(
    val mediaData: ByteArray,
    val type: MediaType,
    val thumbnailData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaItem) return false
        
        if (!mediaData.contentEquals(other.mediaData)) return false
        if (type != other.type) return false
        if (thumbnailData != null) {
            if (other.thumbnailData == null) return false
            if (!thumbnailData.contentEquals(other.thumbnailData)) return false
        } else if (other.thumbnailData != null) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = mediaData.contentHashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (thumbnailData?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Location for create post
 */
data class CreatePostLocation(
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Post visibility options
 */
enum class PostVisibility {
    PUBLIC,
    FOLLOWERS
}
