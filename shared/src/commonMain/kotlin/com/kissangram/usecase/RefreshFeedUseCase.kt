package com.kissangram.usecase

import com.kissangram.model.Post
import com.kissangram.repository.FeedRepository

/**
 * Use case for refreshing the home feed
 */
class RefreshFeedUseCase(
    private val feedRepository: FeedRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<Post> {
        return feedRepository.refreshFeed()
    }
}
