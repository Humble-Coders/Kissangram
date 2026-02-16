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
     * Get comments for a post (top-level only; parentCommentId == null)
     */
    @Throws(Exception::class)
    suspend fun getComments(postId: String, page: Int, pageSize: Int): List<Comment>
    
    /**
     * Get replies for a parent comment.
     * @param postId The post ID
     * @param parentCommentId The parent comment ID
     * @param page Page number (0-based)
     * @param pageSize Page size
     */
    @Throws(Exception::class)
    suspend fun getReplies(postId: String, parentCommentId: String, page: Int, pageSize: Int): List<Comment>
    
    /**
     * Add a comment to a post
     * @param postId The ID of the post
     * @param text The comment text
     * @param parentCommentId Optional parent comment ID for replies. If null, creates a top-level comment.
     * @return The created Comment object
     */
    @Throws(Exception::class)
    suspend fun addComment(postId: String, text: String, parentCommentId: String? = null): Comment
    
    /**
     * Delete a comment from a post
     * @param postId The ID of the post
     * @param commentId The ID of the comment to delete
     * @param reason The reason for deletion
     */
    @Throws(Exception::class)
    suspend fun deleteComment(postId: String, commentId: String, reason: String)
    
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
    
    /**
     * Delete a post. This will trigger a Cloud Function to remove the post
     * from all follower feeds and the posts collection.
     * @param postId The ID of the post to delete
     */
    @Throws(Exception::class)
    suspend fun deletePost(postId: String)
}
