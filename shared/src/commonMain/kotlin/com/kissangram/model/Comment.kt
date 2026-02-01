package com.kissangram.model

/**
 * Comment data model matching Firestore schema
 */
data class Comment(
    val id: String,
    
    // Post reference
    val postId: String,
    
    // Author (denormalized)
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorProfileImageUrl: String?,
    val authorRole: UserRole,
    val authorVerificationStatus: VerificationStatus,
    
    // Content
    val text: String,
    
    // Voice Comment (optional)
    val voiceComment: VoiceContent?,
    
    // For nested replies
    val parentCommentId: String?,
    val repliesCount: Int,
    
    // Engagement
    val likesCount: Int,
    val isLikedByMe: Boolean,
    
    // Question-specific
    val isExpertAnswer: Boolean,
    val isBestAnswer: Boolean,
    
    // Metadata
    val createdAt: Long
)
