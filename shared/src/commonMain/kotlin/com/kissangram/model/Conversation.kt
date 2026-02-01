package com.kissangram.model

/**
 * Direct Message Conversation data model matching Firestore schema
 */
data class Conversation(
    val id: String,
    
    // Participants
    val participantIds: List<String>,
    val participants: Map<String, UserInfo>,
    
    // Other participant (convenience for UI)
    val otherParticipant: UserInfo?,
    
    // Last message preview
    val lastMessage: MessagePreview?,
    
    // Unread count for current user
    val unreadCount: Int,
    
    // Metadata
    val createdAt: Long,
    val updatedAt: Long
)

data class MessagePreview(
    val senderId: String,
    val senderName: String,
    val type: MessageType,
    val preview: String,
    val sentAt: Long
)

/**
 * Chat Message data model
 */
data class Message(
    val id: String,
    
    // Sender
    val senderId: String,
    val senderName: String,
    val senderProfileImageUrl: String?,
    
    // Message Type
    val type: MessageType,
    
    // Content (based on type)
    val text: String?,
    val imageContent: ImageContent?,
    val videoContent: VideoContent?,
    val voiceContent: VoiceContent?,
    val sharedPost: SharedPostContent?,
    
    // Read status
    val isReadByMe: Boolean,
    
    // Metadata
    val sentAt: Long,
    val isDeleted: Boolean
)

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    VOICE,
    POST
}

data class ImageContent(
    val url: String,
    val thumbnailUrl: String?
)

data class VideoContent(
    val url: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int
)

data class SharedPostContent(
    val postId: String,
    val postText: String,
    val postImageUrl: String?,
    val authorName: String,
    val authorUsername: String
)
