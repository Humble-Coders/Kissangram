package com.kissangram.usecase

import com.kissangram.repository.FollowRepository

/**
 * Use case for following/unfollowing a user
 */
class FollowUserUseCase(
    private val followRepository: FollowRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(userId: String, isCurrentlyFollowing: Boolean) {
        require(userId.isNotBlank()) { "User ID cannot be empty" }
        
        if (isCurrentlyFollowing) {
            followRepository.unfollowUser(userId)
        } else {
            followRepository.followUser(userId)
        }
    }
}
