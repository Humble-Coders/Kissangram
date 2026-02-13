package com.kissangram.usecase

import com.kissangram.repository.PostRepository

/**
 * Use case for deleting a comment from a post
 */
class DeleteCommentUseCase(
    private val postRepository: PostRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(postId: String, commentId: String, reason: String) {
        require(postId.isNotBlank()) { "Post ID cannot be empty" }
        require(commentId.isNotBlank()) { "Comment ID cannot be empty" }
        require(reason.isNotBlank()) { "Reason cannot be empty" }
        require(reason.length <= 200) { "Reason cannot exceed 200 characters" }
        
        postRepository.deleteComment(postId, commentId, reason)
    }
}
