package com.kissangram.model

/**
 * Notification data model matching Firestore schema
 */
data class Notification(
    val id: String,
    
    // Type
    val type: NotificationType,
    
    // Actor (who triggered this)
    val actorId: String,
    val actorName: String,
    val actorUsername: String,
    val actorProfileImageUrl: String?,
    val actorRole: UserRole,
    val actorVerificationStatus: VerificationStatus,
    
    // Related content
    val postId: String?,
    val commentId: String?,
    val postImageUrl: String?,
    
    // State
    val isRead: Boolean,
    
    // Metadata
    val createdAt: Long
)

enum class NotificationType {
    LIKE,
    COMMENT,
    FOLLOW,
    MENTION,
    QUESTION,
    ANSWER,
    BEST_ANSWER,
    GROUP_INVITE
}
