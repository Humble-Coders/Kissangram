package com.kissangram.repository

/**
 * Repository interface for follow/unfollow operations
 */
interface FollowRepository {
    /**
     * Follow a user
     */
    @Throws(Exception::class)
    suspend fun followUser(userId: String)
    
    /**
     * Unfollow a user
     */
    @Throws(Exception::class)
    suspend fun unfollowUser(userId: String)
    
    /**
     * Check if current user is following a specific user
     */
    @Throws(Exception::class)
    suspend fun isFollowing(userId: String): Boolean
    
    /**
     * Check if a specific user is following the current user
     */
    @Throws(Exception::class)
    suspend fun isFollowedBy(userId: String): Boolean
}
