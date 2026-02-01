package com.kissangram.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.Post
import com.kissangram.model.UserStories
import com.kissangram.repository.MockFeedRepository
import com.kissangram.repository.MockPostRepository
import com.kissangram.repository.MockStoryRepository
import com.kissangram.usecase.GetHomeFeedUseCase
import com.kissangram.usecase.GetStoryBarUseCase
import com.kissangram.usecase.LikePostUseCase
import com.kissangram.usecase.SavePostUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    // Repositories (using mock for now)
    private val feedRepository = MockFeedRepository()
    private val postRepository = MockPostRepository()
    private val storyRepository = MockStoryRepository()
    
    // Use cases
    private val getHomeFeedUseCase = GetHomeFeedUseCase(feedRepository)
    private val getStoryBarUseCase = GetStoryBarUseCase(storyRepository)
    private val likePostUseCase = LikePostUseCase(postRepository)
    private val savePostUseCase = SavePostUseCase(postRepository)
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadContent()
    }
    
    fun loadContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Load stories and posts in parallel
                val stories = getStoryBarUseCase()
                val posts = getHomeFeedUseCase(page = 0)
                
                _uiState.value = _uiState.value.copy(
                    stories = stories,
                    posts = posts,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load content"
                )
            }
        }
    }
    
    fun refreshFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            
            try {
                val stories = getStoryBarUseCase()
                val posts = getHomeFeedUseCase(page = 0)
                
                _uiState.value = _uiState.value.copy(
                    stories = stories,
                    posts = posts,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.message
                )
            }
        }
    }
    
    fun loadMorePosts() {
        if (_uiState.value.isLoadingMore) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            
            try {
                val currentPage = _uiState.value.currentPage
                val newPosts = getHomeFeedUseCase(page = currentPage + 1)
                
                if (newPosts.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        posts = _uiState.value.posts + newPosts,
                        currentPage = currentPage + 1,
                        isLoadingMore = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        hasMorePosts = false,
                        isLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }
    
    fun onLikePost(postId: String) {
        viewModelScope.launch {
            val currentPosts = _uiState.value.posts
            val postIndex = currentPosts.indexOfFirst { it.id == postId }
            
            if (postIndex != -1) {
                val post = currentPosts[postIndex]
                val newLikedState = !post.isLikedByMe
                val newLikesCount = if (newLikedState) post.likesCount + 1 else post.likesCount - 1
                
                // Optimistic update
                val updatedPost = post.copy(
                    isLikedByMe = newLikedState,
                    likesCount = newLikesCount
                )
                val updatedPosts = currentPosts.toMutableList()
                updatedPosts[postIndex] = updatedPost
                _uiState.value = _uiState.value.copy(posts = updatedPosts)
                
                try {
                    likePostUseCase(postId, post.isLikedByMe)
                } catch (e: Exception) {
                    // Revert on failure
                    val revertedPosts = currentPosts.toMutableList()
                    _uiState.value = _uiState.value.copy(posts = revertedPosts)
                }
            }
        }
    }
    
    fun onSavePost(postId: String) {
        viewModelScope.launch {
            val currentPosts = _uiState.value.posts
            val postIndex = currentPosts.indexOfFirst { it.id == postId }
            
            if (postIndex != -1) {
                val post = currentPosts[postIndex]
                val newSavedState = !post.isSavedByMe
                
                // Optimistic update
                val updatedPost = post.copy(isSavedByMe = newSavedState)
                val updatedPosts = currentPosts.toMutableList()
                updatedPosts[postIndex] = updatedPost
                _uiState.value = _uiState.value.copy(posts = updatedPosts)
                
                try {
                    savePostUseCase(postId, post.isSavedByMe)
                } catch (e: Exception) {
                    // Revert on failure
                    val revertedPosts = currentPosts.toMutableList()
                    _uiState.value = _uiState.value.copy(posts = revertedPosts)
                }
            }
        }
    }
}

data class HomeUiState(
    val stories: List<UserStories> = emptyList(),
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePosts: Boolean = true,
    val currentPage: Int = 0,
    val error: String? = null
)
