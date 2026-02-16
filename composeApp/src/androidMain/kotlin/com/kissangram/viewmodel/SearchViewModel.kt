package com.kissangram.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.Post
import com.kissangram.model.UserInfo
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidFollowRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestorePostRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.usecase.FollowUserUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SuggestionSection {
    data class UserRow(val users: List<UserInfo>) : SuggestionSection()
    data class PostGrid(val posts: List<Post>) : SuggestionSection()
}

data class SearchUiState(
    val query: String = "",
    val results: List<UserInfo> = emptyList(),
    val suggestedPosts: List<Post> = emptyList(),
    val suggestedUsers: List<UserInfo> = emptyList(),
    val suggestionSections: List<SuggestionSection> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshingSuggestions: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
)

class SearchViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = AndroidPreferencesRepository(application.applicationContext)
    private val authRepository = AndroidAuthRepository(
        context = application.applicationContext,
        activity = null,
        preferencesRepository = prefs
    )
    private val userRepository = FirestoreUserRepository(authRepository = authRepository)
    private val followRepository = AndroidFollowRepository(
        authRepository = authRepository,
        userRepository = userRepository
    )
    private val postRepository = FirestorePostRepository(
        authRepository = authRepository,
        userRepository = userRepository
    )
    private val followUserUseCase = FollowUserUseCase(followRepository)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    
    init {
        // Load suggestions when ViewModel is created
        loadSuggestions()
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query, error = null)
        
        // Cancel previous search
        searchJob?.cancel()
        
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                results = emptyList(),
                hasSearched = false,
                isLoading = false
            )
            // Reload suggestions when query is cleared
            loadSuggestions()
            return
        }

        // Debounce search by 500ms
        searchJob = viewModelScope.launch {
            delay(500)
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        // Allow single character searches for live incremental search
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        try {
            val results = userRepository.searchUsers(query, limit = 20)
            _uiState.value = _uiState.value.copy(
                results = results,
                isLoading = false,
                hasSearched = true
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = e.message ?: "Failed to search users",
                hasSearched = true
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = SearchUiState()
        loadSuggestions()
    }
    
    private fun createSuggestionSections(posts: List<Post>, users: List<UserInfo>): List<SuggestionSection> {
        val sections = mutableListOf<SuggestionSection>()
        
        // Shuffle posts and users separately
        val shuffledPosts = posts.shuffled()
        val shuffledUsers = users.shuffled()
        
        // Split posts into chunks of 6 (3 rows of 2-column grid)
        val postChunks = shuffledPosts.chunked(6)
        
        // Split users into chunks of 5-7 users per row
        val userChunks = shuffledUsers.chunked(6)
        
        // Create alternating sections: user rows and post grids
        val maxSections = maxOf(postChunks.size, userChunks.size)
        
        for (i in 0 until maxSections) {
            // Add user row if available
            if (i < userChunks.size && userChunks[i].isNotEmpty()) {
                sections.add(SuggestionSection.UserRow(userChunks[i]))
            }
            
            // Add post grid if available
            if (i < postChunks.size && postChunks[i].isNotEmpty()) {
                sections.add(SuggestionSection.PostGrid(postChunks[i]))
            }
        }
        
        // Shuffle the order of sections but keep users grouped and posts grouped
        return sections.shuffled()
    }
    
    fun loadSuggestions() {
        viewModelScope.launch {
            try {
                val posts = postRepository.getRandomPosts(limit = 20)
                val users = userRepository.getSuggestedUsers(limit = 10)
                val sections = createSuggestionSections(posts, users)
                _uiState.value = _uiState.value.copy(
                    suggestedPosts = posts,
                    suggestedUsers = users,
                    suggestionSections = sections,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load suggestions"
                )
            }
        }
    }
    
    fun refreshSuggestions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshingSuggestions = true, error = null)
            try {
                val posts = postRepository.getRandomPosts(limit = 20)
                val users = userRepository.getSuggestedUsers(limit = 10)
                val sections = createSuggestionSections(posts, users)
                _uiState.value = _uiState.value.copy(
                    suggestedPosts = posts,
                    suggestedUsers = users,
                    suggestionSections = sections,
                    isRefreshingSuggestions = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshingSuggestions = false,
                    error = e.message ?: "Failed to refresh suggestions"
                )
            }
        }
    }
    
    fun followUser(userId: String) {
        viewModelScope.launch {
            try {
                // Follow the user (isCurrentlyFollowing = false since these are suggestions)
                followUserUseCase(userId, isCurrentlyFollowing = false)
                // Remove from suggestions and refresh
                val updatedUsers = _uiState.value.suggestedUsers.filter { it.id != userId }
                val sections = createSuggestionSections(_uiState.value.suggestedPosts, updatedUsers)
                _uiState.value = _uiState.value.copy(
                    suggestedUsers = updatedUsers,
                    suggestionSections = sections
                )
                // Optionally reload one more suggestion
                if (updatedUsers.size < 10) {
                    val newUsers = userRepository.getSuggestedUsers(limit = 1)
                    val finalUsers = updatedUsers + newUsers
                    val finalSections = createSuggestionSections(_uiState.value.suggestedPosts, finalUsers)
                    _uiState.value = _uiState.value.copy(
                        suggestedUsers = finalUsers,
                        suggestionSections = finalSections
                    )
                }
            } catch (e: Exception) {
                // Silently fail - user can follow from profile if needed
            }
        }
    }
}
