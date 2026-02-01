package com.kissangram.repository

import com.kissangram.model.Story
import com.kissangram.model.UserStories

/**
 * Repository interface for story operations
 */
interface StoryRepository {
    /**
     * Get stories for the story bar (stories from followed users)
     * @return List of user stories grouped by user
     */
    @Throws(Exception::class)
    suspend fun getStoryBar(): List<UserStories>
    
    /**
     * Get stories for a specific user
     */
    @Throws(Exception::class)
    suspend fun getStoriesForUser(userId: String): List<Story>
    
    /**
     * Mark a story as viewed
     */
    @Throws(Exception::class)
    suspend fun markStoryAsViewed(storyId: String)
    
    /**
     * Get current user's own stories
     */
    @Throws(Exception::class)
    suspend fun getMyStories(): List<Story>
}
