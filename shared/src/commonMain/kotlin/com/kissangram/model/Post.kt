package com.kissangram.model

/**
 * Post data model matching Firestore schema
 */
data class Post(
    val id: String,
    
    // Author (denormalized)
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorProfileImageUrl: String?,
    val authorRole: UserRole,
    val authorVerificationStatus: VerificationStatus,
    
    // Post Type
    val type: PostType,
    
    // Content
    val text: String,
    
    // Media
    val media: List<PostMedia>,
    
    // Voice Caption (optional)
    val voiceCaption: VoiceContent?,
    
    // Crops & Hashtags
    val crops: List<String>,
    val hashtags: List<String>,
    
    // Location (optional)
    val location: PostLocation?,
    
    // Question-specific (only if type == QUESTION)
    val question: QuestionData?,
    
    // Engagement Counts
    val likesCount: Int,
    val commentsCount: Int,
    val savesCount: Int,
    
    // User interaction state (computed for current user)
    val isLikedByMe: Boolean,
    val isSavedByMe: Boolean,
    
    // Metadata
    val createdAt: Long,
    val updatedAt: Long?
)

enum class PostType {
    NORMAL,
    QUESTION
}

data class PostMedia(
    val url: String,
    val type: MediaType,
    val thumbnailUrl: String?
)

enum class MediaType {
    IMAGE,
    VIDEO
}

data class VoiceContent(
    val url: String,
    val durationSeconds: Int
)

data class PostLocation(
    val name: String,
    val latitude: Double?,
    val longitude: Double?
)

data class QuestionData(
    val targetExpertise: List<String>,
    val targetExpertIds: List<String>,
    val targetExperts: List<UserInfo>,
    val isAnswered: Boolean,
    val bestAnswerCommentId: String?
)
