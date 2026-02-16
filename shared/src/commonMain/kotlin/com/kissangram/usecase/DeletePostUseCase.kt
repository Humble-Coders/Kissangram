package com.kissangram.usecase

import com.kissangram.repository.PostRepository

/**
 * Use case for deleting a post.
 * This will trigger a Cloud Function to remove the post from all follower feeds
 * and the posts collection.
 */
class DeletePostUseCase(
    private val postRepository: PostRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: String) {
        require(postId.isNotBlank()) { "Post ID cannot be empty" }
        
        postRepository.deletePost(postId)
    }
}
