package com.kissangram.model

/**
 * Story data model matching Firestore schema
 */
data class Story(
    val id: String,
    
    // Author (denormalized)
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorProfileImageUrl: String?,
    val authorRole: UserRole,
    val authorVerificationStatus: VerificationStatus,
    
    // Content
    val media: StoryMedia,
    
    // Text overlay (optional)
    val textOverlay: TextOverlay?,
    
    // Location (optional)
    val locationName: String?,
    
    // Engagement
    val viewsCount: Int,
    
    // User interaction state
    val isViewedByMe: Boolean,
    
    // Metadata
    val createdAt: Long,
    val expiresAt: Long
)

data class StoryMedia(
    val url: String,
    val type: MediaType,
    val thumbnailUrl: String?
)

data class TextOverlay(
    val text: String,
    val positionX: Float,
    val positionY: Float
)

/**
 * Grouped stories by user for story bar display
 */
data class UserStories(
    val userId: String,
    val userName: String,
    val userProfileImageUrl: String?,
    val userRole: UserRole,
    val userVerificationStatus: VerificationStatus,
    val stories: List<Story>,
    val hasUnviewedStories: Boolean,
    val latestStoryTime: Long
)
