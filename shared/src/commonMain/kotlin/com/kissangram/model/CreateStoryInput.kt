package com.kissangram.model

import kotlin.random.Random

/**
 * Input model for creating a new story.
 * This captures data from the Create Story screen before submission.
 */
data class CreateStoryInput(
    // Media (file data before upload) - single item for stories
    val mediaData: ByteArray? = null,
    val mediaType: MediaType? = null,
    val thumbnailData: ByteArray? = null,
    
    // Text overlay (optional)
    val textOverlays: List<StoryTextOverlay> = emptyList(),
    
    // Location (optional)
    val location: CreateStoryLocation? = null,
    
    // Visibility
    val visibility: PostVisibility = PostVisibility.PUBLIC
)

/**
 * Text overlay for story (editable before upload)
 */
data class StoryTextOverlay(
    val id: String = generateId(),
    val text: String,
    val positionX: Float,  // 0.0 to 1.0 (normalized)
    val positionY: Float,  // 0.0 to 1.0 (normalized)
    val fontSize: Float = 24f,  // Font size in sp/dp (larger default)
    val textColor: Long = 0xFFFFFFFF,  // ARGB color (default white)
    val rotation: Float = 0f,  // Rotation angle in degrees
    val scale: Float = 1f  // Scale factor (1.0 = normal)
) {
    companion object {
        private var idCounter = 0L
        private fun generateId(): String {
            // Use counter + random for uniqueness in multiplatform
            val random = Random.nextInt(10000, 99999)
            return "overlay_${++idCounter}_$random"
        }
    }
}

/**
 * Location for create story
 */
data class CreateStoryLocation(
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)
