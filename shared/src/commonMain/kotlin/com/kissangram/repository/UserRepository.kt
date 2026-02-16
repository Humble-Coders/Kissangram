package com.kissangram.repository

import com.kissangram.model.User
import com.kissangram.model.UserInfo
import com.kissangram.model.VerificationStatus

/**
 * Repository interface for user operations
 */
interface UserRepository {
    /**
     * Create or overwrite user profile in Firestore (e.g. after onboarding).
     * Called after role selection (non-expert) or after expert doc upload (skip/complete).
     */
    //@Throws(Exception::class)
    suspend fun createUserProfile(
        userId: String,
        phoneNumber: String,
        name: String,
        role: com.kissangram.model.UserRole,
        language: String,
        verificationDocUrl: String? = null,
        verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED
    )

    /**
     * Get current user's profile (from Firestore)
     */
    @Throws(Exception::class)
    suspend fun getCurrentUser(): User?
    
    /**
     * Get user by ID
     */
    @Throws(Exception::class)
    suspend fun getUser(userId: String): User?
    
    /**
     * Get user info (simplified) by ID
     */
    @Throws(Exception::class)
    suspend fun getUserInfo(userId: String): UserInfo?
    
    /**
     * Update current user's profile with basic fields
     */
    @Throws(Exception::class)
    suspend fun updateProfile(
        name: String? = null,
        username: String? = null,
        bio: String? = null,
        profileImageUrl: String? = null
    )
    
    /**
     * Update current user's full profile including location, role, and crops
     * Per FIRESTORE_SCHEMA.md:
     * - location: { district, state, country }
     * - role: farmer | expert | agripreneur | input_seller | agri_lover
     * - expertise: List of crops (used as crops for all users, expertise for experts)
     */
    @Throws(Exception::class)
    suspend fun updateFullProfile(
        name: String? = null,
        bio: String? = null,
        profileImageUrl: String? = null,
        role: com.kissangram.model.UserRole? = null,
        state: String? = null,
        district: String? = null,
        village: String? = null,
        crops: List<String>? = null
    )
    
    /**
     * Search users by name or username
     */
    @Throws(Exception::class)
    suspend fun searchUsers(query: String, limit: Int = 20): List<UserInfo>
    
    /**
     * Check if username is available
     */
    @Throws(Exception::class)
    suspend fun isUsernameAvailable(username: String): Boolean
    
    /**
     * Get followers of a user
     */
    @Throws(Exception::class)
    suspend fun getFollowers(userId: String, page: Int, pageSize: Int): List<UserInfo>
    
    /**
     * Get users that a user is following
     */
    @Throws(Exception::class)
    suspend fun getFollowing(userId: String, page: Int, pageSize: Int): List<UserInfo>
    
    /**
     * Get suggested users to follow (excluding current user and already followed users)
     * @param limit Number of suggestions
     * @return List of suggested users
     */
    @Throws(Exception::class)
    suspend fun getSuggestedUsers(limit: Int = 10): List<UserInfo>
}
