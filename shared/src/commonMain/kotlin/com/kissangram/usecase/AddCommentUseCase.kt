package com.kissangram.usecase

import com.kissangram.model.Comment
import com.kissangram.repository.PostRepository

/**
 * Use case for adding a comment to a post
 */
class AddCommentUseCase(
    private val postRepository: PostRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        postId: String, 
        text: String, 
        parentCommentId: String? = null
    ): Comment {
        require(postId.isNotBlank()) { "Post ID cannot be empty" }
        require(text.isNotBlank()) { "Comment text cannot be empty" }
        require(text.length <= 1000) { "Comment text cannot exceed 1000 characters" }
        
        return postRepository.addComment(postId, text.trim(), parentCommentId)
    }
}
