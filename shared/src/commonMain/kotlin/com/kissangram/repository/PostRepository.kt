package com.kissangram.repository

import com.kissangram.model.Post
import com.kissangram.model.Comment

/**
 * Repository interface for post operations
 */
interface PostRepository {
    /**
     * Get a single post by ID
     */
    @Throws(Exception::class)
    suspend fun getPost(postId: String): Post?
    
    /**
     * Like a post
     */
    @Throws(Exception::class)
    suspend fun likePost(postId: String)
    
    /**
     * Unlike a post
     */
    @Throws(Exception::class)
    suspend fun unlikePost(postId: String)
    
    /**
     * Save a post to user's saved posts
     */
    @Throws(Exception::class)
    suspend fun savePost(postId: String)
    
    /**
     * Remove a post from user's saved posts
     */
    @Throws(Exception::class)
    suspend fun unsavePost(postId: String)
    
    /**
     * Get comments for a post
     */
    @Throws(Exception::class)
    suspend fun getComments(postId: String, page: Int, pageSize: Int): List<Comment>
    
    /**
     * Add a comment to a post
     */
    @Throws(Exception::class)
    suspend fun addComment(postId: String, text: String): Comment
    
    /**
     * Get posts by user
     */
    @Throws(Exception::class)
    suspend fun getPostsByUser(userId: String, page: Int, pageSize: Int): List<Post>
    
    /**
     * Get posts by crop tag
     */
    @Throws(Exception::class)
    suspend fun getPostsByCrop(crop: String, page: Int, pageSize: Int): List<Post>
    
    /**
     * Get posts by hashtag
     */
    @Throws(Exception::class)
    suspend fun getPostsByHashtag(hashtag: String, page: Int, pageSize: Int): List<Post>
    
    /**
     * Create a new post in Firestore.
     * @param postData Map of post fields to be written to Firestore
     * @return The created Post object
     */
    @Throws(Exception::class)
    suspend fun createPost(postData: Map<String, Any?>): Post
}
