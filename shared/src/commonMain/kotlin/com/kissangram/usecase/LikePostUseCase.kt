package com.kissangram.usecase

import com.kissangram.repository.PostRepository

/**
 * Use case for liking/unliking a post
 */
class LikePostUseCase(
    private val postRepository: PostRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: String, isCurrentlyLiked: Boolean) {
        require(postId.isNotBlank()) { "Post ID cannot be empty" }
        
        if (isCurrentlyLiked) {
            postRepository.unlikePost(postId)
        } else {
            postRepository.likePost(postId)
        }
    }
}
