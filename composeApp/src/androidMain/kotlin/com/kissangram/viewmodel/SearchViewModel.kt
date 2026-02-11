package com.kissangram.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.UserInfo
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestoreUserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<UserInfo> = emptyList(),
    val isLoading: Boolean = false,
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

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

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
    }
}
