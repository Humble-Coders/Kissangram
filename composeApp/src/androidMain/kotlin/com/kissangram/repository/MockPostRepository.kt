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
}
