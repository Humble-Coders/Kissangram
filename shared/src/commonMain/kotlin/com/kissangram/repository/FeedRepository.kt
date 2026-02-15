package com.kissangram.repository

import com.kissangram.model.Post

/**
 * Repository interface for home feed operations
 */
interface FeedRepository {
    /**
     * Get home feed posts for the current user
     * @param page Page number (0-indexed)
     * @param pageSize Number of posts per page
     * @param forceRefresh If true, bypass cache and fetch from Firestore. If false and page==0, may return cached data.
     * @return List of posts for the feed
     */
    @Throws(Exception::class)
    suspend fun getHomeFeed(page: Int, pageSize: Int, forceRefresh: Boolean = false): List<Post>
    
    /**
     * Refresh the home feed (fetch latest posts)
     * @return List of latest posts
     */
    @Throws(Exception::class)
    suspend fun refreshFeed(): List<Post>
}
