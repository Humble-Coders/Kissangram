package com.kissangram.usecase

import com.kissangram.model.Comment
import com.kissangram.repository.PostRepository

/**
 * Use case for getting comments on a post
 */
class GetCommentsUseCase(
    private val postRepository: PostRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: String, page: Int = 0, pageSize: Int = 20): List<Comment> {
        require(postId.isNotBlank()) { "Post ID cannot be empty" }
        require(page >= 0) { "Page must be non-negative" }
        require(pageSize in 1..50) { "Page size must be between 1 and 50" }
        
        return postRepository.getComments(postId, page, pageSize)
    }
}
