package com.kissangram.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.Post
import com.kissangram.model.User
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidFollowRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestorePostRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.usecase.FollowUserUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OtherUserProfileUiState(
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingPosts: Boolean = false,
    val isFollowLoading: Boolean = false,
    val isFollowing: Boolean = false,
    val error: String? = null
)

class OtherUserProfileViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = AndroidPreferencesRepository(application.applicationContext)
    private val authRepository = AndroidAuthRepository(
        context = application.applicationContext,
        activity = null,
        preferencesRepository = prefs
    )
    private val userRepository = FirestoreUserRepository(authRepository = authRepository)
    private val postRepository = FirestorePostRepository(
        authRepository = authRepository,
        userRepository = userRepository
    )
    private val followRepository = AndroidFollowRepository(
        authRepository = authRepository,
        userRepository = userRepository
    )
    private val followUserUseCase = FollowUserUseCase(followRepository)

    private val _uiState = MutableStateFlow(OtherUserProfileUiState())
    val uiState: StateFlow<OtherUserProfileUiState> = _uiState.asStateFlow()

    /**
     * Load user profile by userId and check follow status
     */
    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Load user profile
                val user = userRepository.getUser(userId)
                
                if (user == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "User not found"
                    )
                    return@launch
                }

                // Check if current user is following this user
                val isFollowing = followRepository.isFollowing(userId)

                _uiState.value = _uiState.value.copy(
                    user = user,
                    isFollowing = isFollowing,
                    isLoading = false
                )

                // Load user's posts
                loadPosts(userId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load profile"
                )
            }
        }
    }

    /**
     * Toggle follow/unfollow status.
     * Keeps the loader visible (and button disabled) until the cloud function
     * completes, preventing rapid clicks from firing multiple API calls.
     */
    fun toggleFollow() {
        // Guard: ignore clicks while a follow/unfollow is already in flight
        if (_uiState.value.isFollowLoading) return

        val userId = _uiState.value.user?.id ?: return
        val isCurrentlyFollowing = _uiState.value.isFollowing

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFollowLoading = true, error = null)

            try {
                // Optimistic UI update — keeps isFollowLoading = true so the
                // button stays disabled while the cloud function runs.
                val currentUser = _uiState.value.user
                val newFollowingState = !isCurrentlyFollowing
                val newFollowersCount = if (newFollowingState) {
                    (currentUser?.followersCount ?: 0) + 1
                } else {
                    maxOf(0, (currentUser?.followersCount ?: 0) - 1)
                }

                _uiState.value = _uiState.value.copy(
                    isFollowing = newFollowingState,
                    user = currentUser?.copy(followersCount = newFollowersCount)
                )

                // Perform actual follow/unfollow (cloud function)
                followUserUseCase(userId, isCurrentlyFollowing)

                // Reload user to get the authoritative counts from Firestore
                val updatedUser = userRepository.getUser(userId)
                _uiState.value = _uiState.value.copy(
                    user = updatedUser,
                    isFollowing = followRepository.isFollowing(userId),
                    isFollowLoading = false
                )
            } catch (e: Exception) {
                // Rollback optimistic update on error
                _uiState.value = _uiState.value.copy(
                    isFollowing = isCurrentlyFollowing,
                    isFollowLoading = false,
                    error = e.message ?: "Failed to ${if (isCurrentlyFollowing) "unfollow" else "follow"} user"
                )
            }
        }
    }

    private fun loadPosts(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPosts = true)
            try {
                val posts = postRepository.getPostsByUser(userId, page = 0, pageSize = 30)
                _uiState.value = _uiState.value.copy(posts = posts, isLoadingPosts = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPosts = false,
                    posts = emptyList()
                )
            }
        }
    }
}
