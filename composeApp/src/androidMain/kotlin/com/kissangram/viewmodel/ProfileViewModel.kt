package com.kissangram.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.User
import com.kissangram.model.Post
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.repository.FirestorePostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val posts: List<Post> = emptyList(),
    val isLoadingPosts: Boolean = false
)

class ProfileViewModel(
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

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val user = userRepository.getCurrentUser()
                _uiState.value = _uiState.value.copy(user = user, isLoading = false)
                // Load posts after profile is loaded
                user?.id?.let { userId ->
                    loadUserPosts(userId)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load profile"
                )
            }
        }
    }
    
    fun loadUserPosts(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPosts = true)
            try {
                val posts = postRepository.getPostsByUser(userId, page = 0, pageSize = 30)
                _uiState.value = _uiState.value.copy(
                    posts = posts,
                    isLoadingPosts = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPosts = false,
                    error = e.message ?: "Failed to load posts"
                )
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.signOut()
                onSignedOut()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
