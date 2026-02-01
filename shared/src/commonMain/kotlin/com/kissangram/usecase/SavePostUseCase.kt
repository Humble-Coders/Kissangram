package com.kissangram.usecase

import com.kissangram.repository.PostRepository

/**
 * Use case for saving/unsaving a post
 */
class SavePostUseCase(
    private val postRepository: PostRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: String, isCurrentlySaved: Boolean) {
        require(postId.isNotBlank()) { "Post ID cannot be empty" }
        
        if (isCurrentlySaved) {
            postRepository.unsavePost(postId)
        } else {
            postRepository.savePost(postId)
        }
    }
}
