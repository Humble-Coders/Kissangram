package com.kissangram.usecase

import com.kissangram.model.Post
import com.kissangram.repository.FeedRepository

/**
 * Use case for getting the home feed
 */
class GetHomeFeedUseCase(
    private val feedRepository: FeedRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(page: Int = 0, pageSize: Int = 20): List<Post> {
        require(page >= 0) { "Page must be non-negative" }
        require(pageSize in 1..50) { "Page size must be between 1 and 50" }
        
        return feedRepository.getHomeFeed(page, pageSize)
    }
}
