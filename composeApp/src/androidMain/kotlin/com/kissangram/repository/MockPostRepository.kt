package com.kissangram.repository

import com.kissangram.model.*
import com.kissangram.repository.PostRepository
import kotlinx.coroutines.delay

/**
 * Mock implementation of PostRepository with dummy data for development
 */
class MockPostRepository : PostRepository {
    
    // In-memory state for likes and saves (would be persisted in real implementation)
    private val likedPosts = mutableSetOf<String>()
    private val savedPosts = mutableSetOf<String>()
    
    override suspend fun getPost(postId: String): Post? {
        delay(300)
        // Return a sample post
        return null
    }
    
    override suspend fun likePost(postId: String) {
        delay(200)
        likedPosts.add(postId)
    }
    
    override suspend fun unlikePost(postId: String) {
        delay(200)
        likedPosts.remove(postId)
    }
    
    override suspend fun savePost(postId: String) {
        delay(200)
        savedPosts.add(postId)
    }
    
    override suspend fun unsavePost(postId: String) {
        delay(200)
        savedPosts.remove(postId)
    }
    
    override suspend fun getComments(postId: String, page: Int, pageSize: Int): List<Comment> {
        delay(400)
        
        val currentTime = System.currentTimeMillis()
        
        return listOf(
            Comment(
                id = "comment1",
                postId = postId,
                authorId = "user10",
                authorName = "Dr. Sharma",
                authorUsername = "dr_sharma",
                authorProfileImageUrl = "https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?w=150",
                authorRole = UserRole.EXPERT,
                authorVerificationStatus = VerificationStatus.VERIFIED,
                text = "Great progress! Make sure to check soil moisture before next irrigation.",
                voiceComment = null,
                parentCommentId = null,
                repliesCount = 2,
                likesCount = 15,
                isLikedByMe = false,
                isExpertAnswer = true,
                isBestAnswer = false,
                createdAt = currentTime - 1800000
            ),
            Comment(
                id = "comment2",
                postId = postId,
                authorId = "user11",
                authorName = "Anil Kumar",
                authorUsername = "anil_farmer",
                authorProfileImageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                authorRole = UserRole.FARMER,
                authorVerificationStatus = VerificationStatus.UNVERIFIED,
                text = "Which variety is this? I want to try next season.",
                voiceComment = null,
                parentCommentId = null,
                repliesCount = 1,
                likesCount = 5,
                isLikedByMe = true,
                isExpertAnswer = false,
                isBestAnswer = false,
                createdAt = currentTime - 3600000
            ),
            Comment(
                id = "comment3",
                postId = postId,
                authorId = "user12",
                authorName = "Ravi Singh",
                authorUsername = "ravi_agri",
                authorProfileImageUrl = null,
                authorRole = UserRole.FARMER,
                authorVerificationStatus = VerificationStatus.UNVERIFIED,
                text = "Very nice! What's your irrigation schedule?",
                voiceComment = null,
                parentCommentId = null,
                repliesCount = 0,
                likesCount = 3,
                isLikedByMe = false,
                isExpertAnswer = false,
                isBestAnswer = false,
                createdAt = currentTime - 5400000
            )
        )
    }
    
    override suspend fun addComment(postId: String, text: String): Comment {
        delay(500)
        
        return Comment(
            id = "comment_new_${System.currentTimeMillis()}",
            postId = postId,
            authorId = "current_user",
            authorName = "You",
            authorUsername = "current_user",
            authorProfileImageUrl = null,
            authorRole = UserRole.FARMER,
            authorVerificationStatus = VerificationStatus.UNVERIFIED,
            text = text,
            voiceComment = null,
            parentCommentId = null,
            repliesCount = 0,
            likesCount = 0,
            isLikedByMe = false,
            isExpertAnswer = false,
            isBestAnswer = false,
            createdAt = System.currentTimeMillis()
        )
    }
    
    override suspend fun getPostsByUser(userId: String, page: Int, pageSize: Int): List<Post> {
        delay(400)
        return emptyList()
    }
    
    override suspend fun getPostsByCrop(crop: String, page: Int, pageSize: Int): List<Post> {
        delay(400)
        return emptyList()
    }
    
    override suspend fun getPostsByHashtag(hashtag: String, page: Int, pageSize: Int): List<Post> {
        delay(400)
        return emptyList()
    }
    
    override suspend fun createPost(postData: Map<String, Any?>): Post {
        delay(500) // Simulate network delay
        
        val postId = "mock_post_${System.currentTimeMillis()}"
        val authorId = postData["authorId"] as? String ?: "mock_user"
        val authorName = postData["authorName"] as? String ?: "Mock User"
        val authorUsername = postData["authorUsername"] as? String ?: "mock_user"
        val authorProfileImageUrl = postData["authorProfileImageUrl"] as? String
        val text = postData["text"] as? String ?: ""
        
        // Parse media array
        val mediaList = (postData["media"] as? List<Map<String, Any>>)?.mapNotNull { mediaMap ->
            val url = mediaMap["url"] as? String ?: return@mapNotNull null
            val typeStr = mediaMap["type"] as? String ?: "image"
            val thumbnailUrl = mediaMap["thumbnailUrl"] as? String
            
            PostMedia(
                url = url,
                type = when (typeStr) {
                    "video" -> MediaType.VIDEO
                    else -> MediaType.IMAGE
                },
                thumbnailUrl = thumbnailUrl?.takeIf { it.isNotEmpty() }
            )
        } ?: emptyList()
        
        // Parse voice caption
        val voiceCaptionMap = postData["voiceCaption"] as? Map<String, Any>
        val voiceCaption = voiceCaptionMap?.let {
            VoiceContent(
                url = it["url"] as? String ?: return@let null,
                durationSeconds = (it["durationSeconds"] as? Number)?.toInt() ?: 0
            )
        }
        
        // Parse crops and hashtags
        val crops = (postData["crops"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val hashtags = (postData["hashtags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        
        // Parse location
        val locationMap = postData["location"] as? Map<String, Any>
        val location = locationMap?.let {
            PostLocation(
                name = it["name"] as? String ?: "",
                latitude = it["latitude"] as? Double,
                longitude = it["longitude"] as? Double
            )
        }
        
        // Parse question data
        val questionMap = postData["question"] as? Map<String, Any>
        val question = questionMap?.let {
            QuestionData(
                targetExpertise = (it["targetExpertise"] as? List<*>)?.mapNotNull { e -> e as? String } ?: emptyList(),
                targetExpertIds = (it["targetExpertIds"] as? List<*>)?.mapNotNull { e -> e as? String } ?: emptyList(),
                targetExperts = emptyList(), // Mock doesn't populate this
                isAnswered = it["isAnswered"] as? Boolean ?: false,
                bestAnswerCommentId = it["bestAnswerCommentId"] as? String
            )
        }
        
        val typeStr = postData["type"] as? String ?: "normal"
        val roleStr = postData["authorRole"] as? String ?: "farmer"
        val verificationStatusStr = postData["authorVerificationStatus"] as? String ?: "unverified"
        
        return Post(
            id = postId,
            authorId = authorId,
            authorName = authorName,
            authorUsername = authorUsername,
            authorProfileImageUrl = authorProfileImageUrl,
            authorRole = when (roleStr) {
                "expert" -> UserRole.EXPERT
                "agripreneur" -> UserRole.AGRIPRENEUR
                "input_seller" -> UserRole.INPUT_SELLER
                "agri_lover" -> UserRole.AGRI_LOVER
                else -> UserRole.FARMER
            },
            authorVerificationStatus = when (verificationStatusStr) {
                "pending" -> VerificationStatus.PENDING
                "verified" -> VerificationStatus.VERIFIED
                "rejected" -> VerificationStatus.REJECTED
                else -> VerificationStatus.UNVERIFIED
            },
            type = when (typeStr) {
                "question" -> PostType.QUESTION
                else -> PostType.NORMAL
            },
            text = text,
            media = mediaList,
            voiceCaption = voiceCaption,
            crops = crops,
            hashtags = hashtags,
            location = location,
            question = question,
            likesCount = 0,
            commentsCount = 0,
            savesCount = 0,
            isLikedByMe = false,
            isSavedByMe = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = null
        )
    }
}
