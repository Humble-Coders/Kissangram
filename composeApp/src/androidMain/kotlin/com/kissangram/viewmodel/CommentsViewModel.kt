package com.kissangram.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.Comment
import com.kissangram.model.Post
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.AndroidSpeechRepository
import com.kissangram.repository.FirestorePostRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.usecase.AddCommentUseCase
import com.kissangram.usecase.DeleteCommentUseCase
import com.kissangram.usecase.DeletePostUseCase
import com.kissangram.usecase.GetCommentsUseCase
import com.kissangram.usecase.LikePostUseCase
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommentsUiState(
    val post: Post? = null,
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMoreComments: Boolean = true,
    val newCommentText: String = "",
    val isPostingComment: Boolean = false,
    val replyingToComment: Comment? = null,
    val showDeleteDialog: Boolean = false,
    val selectedCommentForDelete: Comment? = null,
    val deleteReason: String = "",
    val expandedReplies: Set<String> = emptySet(),
    val repliesByParentId: Map<String, List<Comment>> = emptyMap(),
    val loadingRepliesFor: Set<String> = emptySet(),
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val showDeletePostDialog: Boolean = false,
    val isDeletingPost: Boolean = false,
    val deletePostError: String? = null
)

class CommentsViewModel(
    application: Application,
    private val postId: String,
    private val initialPost: Post? = null
) : AndroidViewModel(application) {

    val authRepository = AndroidAuthRepository(
        context = application,
        activity = null,
        preferencesRepository = AndroidPreferencesRepository(application)
    )
    private val userRepository = FirestoreUserRepository(authRepository = authRepository)
    private val postRepository = FirestorePostRepository(
        authRepository = authRepository,
        userRepository = userRepository
    )
    private val speechRepository = AndroidSpeechRepository(application)
    
    // Use cases
    private val getCommentsUseCase = GetCommentsUseCase(postRepository)
    private val addCommentUseCase = AddCommentUseCase(postRepository)
    private val deleteCommentUseCase = DeleteCommentUseCase(postRepository)
    private val likePostUseCase = LikePostUseCase(postRepository)
    private val deletePostUseCase = DeletePostUseCase(postRepository)
    
    private val _uiState = MutableStateFlow(
        CommentsUiState(post = initialPost)
    )
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()
    
    private var currentPage = 0
    private val pageSize = 20
    private val postsBeingProcessed = mutableSetOf<String>() // Track ongoing like operations
    
    init {
        Log.d(TAG, "init: CommentsViewModel created for postId=$postId, hasInitialPost=${initialPost != null}")
        if (initialPost == null) {
            loadPost()
        }
        loadComments()
    }
    
    suspend fun getCurrentUserId(): String? {
        return authRepository.getCurrentUserId()
    }
    
    private fun loadPost() {
        viewModelScope.launch {
            try {
                val post = postRepository.getPost(postId)
                _uiState.value = _uiState.value.copy(post = post)
            } catch (e: Exception) {
                Log.e(TAG, "loadPost: failed", e)
                _uiState.value = _uiState.value.copy(error = "Failed to load post: ${e.message}")
            }
        }
    }
    
    fun loadComments() {
        viewModelScope.launch {
            Log.d(TAG, "loadComments: start, setting isLoading=true")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentPage = 0
            
            try {
                val comments = getCommentsUseCase(postId, page = 0, pageSize = pageSize)
                Log.d(TAG, "loadComments: loaded ${comments.size} comments")
                _uiState.value = _uiState.value.copy(
                    comments = comments,
                    isLoading = false,
                    hasMoreComments = comments.size >= pageSize
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadComments: failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load comments: ${e.message}"
                )
            }
        }
    }
    
    fun loadMoreComments() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreComments) {
            return
        }
        
        viewModelScope.launch {
            Log.d(TAG, "loadMoreComments: loading page ${currentPage + 1}")
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            
            try {
                val nextPage = currentPage + 1
                val comments = getCommentsUseCase(postId, page = nextPage, pageSize = pageSize)
                Log.d(TAG, "loadMoreComments: loaded ${comments.size} comments")
                
                if (comments.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        comments = _uiState.value.comments + comments,
                        isLoadingMore = false,
                        hasMoreComments = comments.size >= pageSize
                    )
                    currentPage = nextPage
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        hasMoreComments = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMoreComments: failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = "Failed to load more comments: ${e.message}"
                )
            }
        }
    }
    
    fun onCommentTextChange(text: String) {
        _uiState.value = _uiState.value.copy(newCommentText = text)
    }
    
    fun startSpeechRecognition() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isListening = true, error = null)
            
            val hasPermission = ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                _uiState.value = _uiState.value.copy(
                    isListening = false,
                    error = "Microphone permission is required for voice comments"
                )
                return@launch
            }
            
            speechRepository.setOnTextUpdate { recognizedText ->
                _uiState.value = _uiState.value.copy(
                    newCommentText = recognizedText.trim(),
                    error = null
                )
            }
            
            launch {
                try {
                    speechRepository.startListening()
                } catch (e: Exception) {
                    if (_uiState.value.isListening) {
                        _uiState.value = _uiState.value.copy(
                            isListening = false,
                            error = e.message ?: "Speech recognition error"
                        )
                    }
                }
            }
        }
    }
    
    fun stopSpeechRecognition() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isListening = false, isProcessing = true)
            speechRepository.stopListening()
            kotlinx.coroutines.delay(3500)
            val finalText = speechRepository.getAccumulatedText()
            if (finalText.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(newCommentText = finalText.trim())
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }
    
    fun postComment() {
        val text = _uiState.value.newCommentText.trim()
        if (text.isEmpty() || _uiState.value.isPostingComment) {
            return
        }
        
        val replyingTo = _uiState.value.replyingToComment
        val parentCommentId = replyingTo?.id
        
        viewModelScope.launch {
            Log.d(TAG, "postComment: posting comment, text=${text.take(50)}..., parentCommentId=$parentCommentId")
            _uiState.value = _uiState.value.copy(isPostingComment = true, error = null)
            
            val optimisticComment = Comment(
                    id = "temp_${System.currentTimeMillis()}",
                    postId = postId,
                    authorId = authRepository.getCurrentUserId() ?: "",
                    authorName = userRepository.getCurrentUser()?.name ?: "",
                    authorUsername = userRepository.getCurrentUser()?.username ?: "",
                    authorProfileImageUrl = userRepository.getCurrentUser()?.profileImageUrl,
                    authorRole = userRepository.getCurrentUser()?.role ?: com.kissangram.model.UserRole.FARMER,
                    authorVerificationStatus = userRepository.getCurrentUser()?.verificationStatus ?: com.kissangram.model.VerificationStatus.UNVERIFIED,
                    text = text,
                    voiceComment = null,
                    parentCommentId = parentCommentId,
                    repliesCount = 0,
                    likesCount = 0,
                    isLikedByMe = false,
                    isExpertAnswer = false,
                    isBestAnswer = false,
                    createdAt = System.currentTimeMillis()
            )
            
            try {
                val updatedComments: List<Comment>
                val updatedReplies: Map<String, List<Comment>>
                val updatedExpanded: Set<String>
                
                if (parentCommentId == null) {
                    updatedComments = listOf(optimisticComment) + _uiState.value.comments
                    updatedReplies = _uiState.value.repliesByParentId
                    updatedExpanded = _uiState.value.expandedReplies
                } else {
                    updatedComments = _uiState.value.comments
                    val existingReplies = _uiState.value.repliesByParentId[parentCommentId] ?: emptyList()
                    updatedReplies = _uiState.value.repliesByParentId + (parentCommentId to (existingReplies + optimisticComment))
                    updatedExpanded = _uiState.value.expandedReplies + parentCommentId
                }
                
                _uiState.value = _uiState.value.copy(
                    comments = if (parentCommentId == null) updatedComments else _uiState.value.comments.map { c ->
                        if (c.id == parentCommentId) c.copy(repliesCount = c.repliesCount + 1) else c
                    },
                    repliesByParentId = if (parentCommentId != null) updatedReplies else _uiState.value.repliesByParentId,
                    expandedReplies = if (parentCommentId != null) updatedExpanded else _uiState.value.expandedReplies,
                    newCommentText = "",
                    replyingToComment = null
                )
                
                // Actually post the comment
                val createdComment = addCommentUseCase(postId, text, parentCommentId)
                Log.d(TAG, "postComment: comment created with id=${createdComment.id}")
                
                // Replace optimistic comment with real one
                if (parentCommentId == null) {
                    val finalComments = updatedComments.map { c ->
                        if (c.id == optimisticComment.id) createdComment else c
                    }
                    _uiState.value = _uiState.value.copy(comments = finalComments, isPostingComment = false)
                } else {
                    val parentReplies = _uiState.value.repliesByParentId[parentCommentId] ?: emptyList()
                    val finalReplies = parentReplies.map { c ->
                        if (c.id == optimisticComment.id) createdComment else c
                    }
                    _uiState.value = _uiState.value.copy(
                        repliesByParentId = _uiState.value.repliesByParentId + (parentCommentId to finalReplies),
                        isPostingComment = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "postComment: failed", e)
                // Revert optimistic update
                if (parentCommentId == null) {
                    _uiState.value = _uiState.value.copy(
                        comments = _uiState.value.comments.filter { it.id != optimisticComment.id },
                        isPostingComment = false,
                        error = "Failed to post comment: ${e.message}",
                        newCommentText = text
                    )
                } else {
                    val parentReplies = (_uiState.value.repliesByParentId[parentCommentId] ?: emptyList())
                        .filter { it.id != optimisticComment.id }
                    _uiState.value = _uiState.value.copy(
                        repliesByParentId = if (parentReplies.size == 0) {
                            _uiState.value.repliesByParentId - parentCommentId
                        } else {
                            _uiState.value.repliesByParentId + (parentCommentId to parentReplies)
                        },
                        expandedReplies = _uiState.value.expandedReplies,
                        comments = _uiState.value.comments.map { c ->
                            if (c.id == parentCommentId) c.copy(repliesCount = (c.repliesCount - 1).coerceAtLeast(0)) else c
                        },
                        isPostingComment = false,
                        error = "Failed to post comment: ${e.message}",
                        newCommentText = text
                    )
                }
            }
        }
    }
    
    fun loadReplies(parentCommentId: String) {
        if (_uiState.value.loadingRepliesFor.contains(parentCommentId)) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingRepliesFor = _uiState.value.loadingRepliesFor + parentCommentId
            )
            try {
                val replies = postRepository.getReplies(postId, parentCommentId, page = 0, pageSize = 50)
                val currentReplies = _uiState.value.repliesByParentId[parentCommentId] ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    repliesByParentId = _uiState.value.repliesByParentId + (parentCommentId to (currentReplies + replies).distinctBy { it.id }),
                    expandedReplies = _uiState.value.expandedReplies + parentCommentId,
                    loadingRepliesFor = _uiState.value.loadingRepliesFor - parentCommentId
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadReplies: failed for parentCommentId=$parentCommentId", e)
                _uiState.value = _uiState.value.copy(
                    loadingRepliesFor = _uiState.value.loadingRepliesFor - parentCommentId,
                    error = "Failed to load replies: ${e.message}"
                )
            }
        }
    }
    
    fun toggleReplies(parentCommentId: String) {
        val state = _uiState.value
        if (state.expandedReplies.contains(parentCommentId)) {
            _uiState.value = state.copy(expandedReplies = state.expandedReplies - parentCommentId)
        } else {
            loadReplies(parentCommentId)
        }
    }
    
    fun startReply(comment: Comment) {
        _uiState.value = _uiState.value.copy(replyingToComment = comment)
    }
    
    fun cancelReply() {
        _uiState.value = _uiState.value.copy(replyingToComment = null)
    }
    
    fun showDeleteConfirmation(comment: Comment) {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = true,
            selectedCommentForDelete = comment,
            deleteReason = ""
        )
    }
    
    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = false,
            selectedCommentForDelete = null,
            deleteReason = ""
        )
    }
    
    fun onDeleteReasonChange(reason: String) {
        _uiState.value = _uiState.value.copy(deleteReason = reason)
    }
    
    fun deleteComment() {
        val comment = _uiState.value.selectedCommentForDelete ?: return
        val reason = _uiState.value.deleteReason.trim()
        
        if (reason.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Please provide a reason for deletion")
            return
        }
        
        viewModelScope.launch {
            Log.d(TAG, "deleteComment: deleting commentId=${comment.id}, reason=${reason.take(50)}...")
            _uiState.value = _uiState.value.copy(error = null)
            
            try {
                // Optimistic update: remove comment from list immediately
                val updatedComments = _uiState.value.comments.filter { it.id != comment.id }
                _uiState.value = _uiState.value.copy(
                    comments = updatedComments,
                    showDeleteDialog = false,
                    selectedCommentForDelete = null,
                    deleteReason = ""
                )
                
                // Actually delete the comment
                deleteCommentUseCase(postId, comment.id, reason)
                Log.d(TAG, "deleteComment: comment deleted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "deleteComment: failed", e)
                // Revert optimistic update
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete comment: ${e.message}",
                    comments = _uiState.value.comments + comment // Restore comment
                )
            }
        }
    }
    
    /**
     * Like or unlike a post. Returns true if the request was accepted, false if already processing.
     * Uses the same pattern as HomeViewModel for consistency.
     */
    fun onLikePost(postId: String): Boolean {
        // Prevent multiple simultaneous requests for the same post
        if (postsBeingProcessed.contains(postId)) {
            Log.d(TAG, "onLikePost: post $postId is already being processed, ignoring")
            return false
        }
        
        val currentPost = _uiState.value.post
        if (currentPost == null || currentPost.id != postId) {
            Log.w(TAG, "onLikePost: post not found or mismatch, postId=$postId")
            return false
        }
        
        val newLikedState = !currentPost.isLikedByMe
        val newLikesCount = if (newLikedState) currentPost.likesCount + 1 else maxOf(0, currentPost.likesCount - 1)
        
        // Mark as being processed
        postsBeingProcessed.add(postId)
        
        // ⚡ INSTAGRAM APPROACH: Update UI IMMEDIATELY (synchronous, main thread)
        // This happens before any async work, so user sees instant feedback
        val updatedPost = currentPost.copy(
            isLikedByMe = newLikedState,
            likesCount = newLikesCount
        )
        _uiState.value = _uiState.value.copy(post = updatedPost)
        
        // Fire network request in background (non-blocking)
        viewModelScope.launch {
            try {
                likePostUseCase(postId, currentPost.isLikedByMe)
                Log.d(TAG, "onLikePost: SUCCESS - postId=$postId, isLiked=$newLikedState")
            } catch (e: Exception) {
                Log.e(TAG, "onLikePost: FAILED for postId=$postId", e)
                // Revert on failure
                _uiState.value = _uiState.value.copy(post = currentPost)
            } finally {
                // Always remove from processing set
                postsBeingProcessed.remove(postId)
            }
        }
        return true
    }
    
    fun showDeletePostConfirmation() {
        _uiState.value = _uiState.value.copy(showDeletePostDialog = true, deletePostError = null)
    }
    
    fun dismissDeletePostDialog() {
        _uiState.value = _uiState.value.copy(showDeletePostDialog = false, deletePostError = null)
    }
    
    fun deletePost(onSuccess: () -> Unit) {
        if (_uiState.value.isDeletingPost) {
            return
        }
        
        viewModelScope.launch {
            Log.d(TAG, "deletePost: deleting postId=$postId")
            _uiState.value = _uiState.value.copy(isDeletingPost = true, deletePostError = null)
            
            try {
                deletePostUseCase(postId)
                Log.d(TAG, "deletePost: post deleted successfully")
                _uiState.value = _uiState.value.copy(
                    isDeletingPost = false,
                    showDeletePostDialog = false
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "deletePost: failed", e)
                _uiState.value = _uiState.value.copy(
                    isDeletingPost = false,
                    deletePostError = e.message ?: "Failed to delete post"
                )
            }
        }
    }
    
    companion object {
        private const val TAG = "CommentsViewModel"
    }
}
