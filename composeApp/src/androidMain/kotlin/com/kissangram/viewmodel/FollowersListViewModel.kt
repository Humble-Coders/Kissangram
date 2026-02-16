package com.kissangram.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.UserInfo
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestoreUserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FollowersListType {
    FOLLOWERS,
    FOLLOWING
}

data class FollowersListUiState(
    val users: List<UserInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 0,
    val isRefreshing: Boolean = false
)

class FollowersListViewModel(
    application: Application,
    private val userId: String,
    private val type: FollowersListType
) : AndroidViewModel(application) {

    private val prefs = AndroidPreferencesRepository(application.applicationContext)
    private val authRepository = AndroidAuthRepository(
        context = application.applicationContext,
        activity = null,
        preferencesRepository = prefs
    )
    private val userRepository = FirestoreUserRepository(authRepository = authRepository)

    private val _uiState = MutableStateFlow(FollowersListUiState())
    val uiState: StateFlow<FollowersListUiState> = _uiState.asStateFlow()

    private val pageSize = 20

    init {
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentPage = 0)
            try {
                val users = when (type) {
                    FollowersListType.FOLLOWERS -> userRepository.getFollowers(userId, page = 0, pageSize = pageSize)
                    FollowersListType.FOLLOWING -> userRepository.getFollowing(userId, page = 0, pageSize = pageSize)
                }
                _uiState.value = _uiState.value.copy(
                    users = users,
                    isLoading = false,
                    hasMore = users.size >= pageSize,
                    currentPage = 0
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load ${type.name.lowercase()}"
                )
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return

        viewModelScope.launch {
            val nextPage = _uiState.value.currentPage + 1
            _uiState.value = _uiState.value.copy(isLoadingMore = true, error = null)
            try {
                val newUsers = when (type) {
                    FollowersListType.FOLLOWERS -> userRepository.getFollowers(userId, page = nextPage, pageSize = pageSize)
                    FollowersListType.FOLLOWING -> userRepository.getFollowing(userId, page = nextPage, pageSize = pageSize)
                }
                _uiState.value = _uiState.value.copy(
                    users = _uiState.value.users + newUsers,
                    isLoadingMore = false,
                    hasMore = newUsers.size >= pageSize,
                    currentPage = nextPage
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.message ?: "Failed to load more ${type.name.lowercase()}"
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null, currentPage = 0)
            try {
                val users = when (type) {
                    FollowersListType.FOLLOWERS -> userRepository.getFollowers(userId, page = 0, pageSize = pageSize)
                    FollowersListType.FOLLOWING -> userRepository.getFollowing(userId, page = 0, pageSize = pageSize)
                }
                _uiState.value = _uiState.value.copy(
                    users = users,
                    isRefreshing = false,
                    hasMore = users.size >= pageSize,
                    currentPage = 0
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.message ?: "Failed to refresh ${type.name.lowercase()}"
                )
            }
        }
    }
}
