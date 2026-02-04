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
    
    // Media (local URIs before upload)
    val mediaItems: List<MediaItem> = emptyList(),
    
    // Voice Caption (local URI before upload)
    val voiceCaptionUri: String? = null,
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
    val localUri: String,
    val type: MediaType,
    val thumbnailUri: String? = null
)

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
