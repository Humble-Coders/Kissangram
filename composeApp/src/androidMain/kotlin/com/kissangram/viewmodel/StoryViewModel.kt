package com.kissangram.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.Story
import com.kissangram.model.UserStories
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestoreStoryRepository
import com.kissangram.usecase.GetStoryBarUseCase
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

class StoryViewModel(
    application: Application,
    initialUserId: String
) : AndroidViewModel(application) {

    private val authRepository = AndroidAuthRepository(
        context = application,
        activity = null,
        preferencesRepository = AndroidPreferencesRepository(application)
    )
    private val storyRepository = FirestoreStoryRepository(authRepository = authRepository)
    private val getStoryBarUseCase = GetStoryBarUseCase(storyRepository)

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    private var autoAdvanceJob: Job? = null
    private val STORY_DURATION_MS = 5000L // 5 seconds per story

    init {
        loadStories(initialUserId)
    }

    fun loadStories(initialUserId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val allUserStories = getStoryBarUseCase()
                Log.d(TAG, "loadStories: loaded ${allUserStories.size} user stories")
                
                if (allUserStories.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No stories available"
                    )
                    return@launch
                }

                // Find initial user index
                val initialIndex = allUserStories.indexOfFirst { it.userId == initialUserId }
                val startIndex = if (initialIndex >= 0) initialIndex else 0

                _uiState.value = _uiState.value.copy(
                    userStories = allUserStories,
                    currentUserIndex = startIndex,
                    currentStoryIndex = 0,
                    isLoading = false
                )

                // Start auto-advance for first story
                startAutoAdvance()
                markCurrentStoryAsViewed()
            } catch (e: Exception) {
                Log.e(TAG, "loadStories: failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load stories"
                )
            }
        }
    }

    fun nextStory() {
        val currentState = _uiState.value
        val currentUserStories = currentState.getCurrentUserStories() ?: return
        
        if (currentState.currentStoryIndex < currentUserStories.stories.size - 1) {
            // Move to next story in current user
            _uiState.value = currentState.copy(currentStoryIndex = currentState.currentStoryIndex + 1)
            startAutoAdvance()
            markCurrentStoryAsViewed()
        } else {
            // Move to next user
            nextUser()
        }
    }

    fun previousStory() {
        val currentState = _uiState.value
        val currentUserStories = currentState.getCurrentUserStories() ?: return
        
        if (currentState.currentStoryIndex > 0) {
            // Move to previous story in current user
            _uiState.value = currentState.copy(currentStoryIndex = currentState.currentStoryIndex - 1)
            startAutoAdvance()
            markCurrentStoryAsViewed()
        } else {
            // Move to previous user
            previousUser()
        }
    }

    fun nextUser() {
        val currentState = _uiState.value
        if (currentState.currentUserIndex < currentState.userStories.size - 1) {
            _uiState.value = currentState.copy(
                currentUserIndex = currentState.currentUserIndex + 1,
                currentStoryIndex = 0
            )
            startAutoAdvance()
            markCurrentStoryAsViewed()
        }
    }

    fun previousUser() {
        val currentState = _uiState.value
        if (currentState.currentUserIndex > 0) {
            val previousUserStories = currentState.userStories[currentState.currentUserIndex - 1]
            _uiState.value = currentState.copy(
                currentUserIndex = currentState.currentUserIndex - 1,
                currentStoryIndex = previousUserStories.stories.size - 1 // Start at last story
            )
            startAutoAdvance()
            markCurrentStoryAsViewed()
        }
    }

    fun pauseAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
    }

    fun resumeAutoAdvance() {
        startAutoAdvance()
    }

    private fun startAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = viewModelScope.launch {
            delay(STORY_DURATION_MS)
            nextStory()
        }
    }

    private fun markCurrentStoryAsViewed() {
        val currentState = _uiState.value
        val currentStory = currentState.getCurrentStory() ?: return
        
        if (!currentStory.isViewedByMe) {
            viewModelScope.launch {
                try {
                    storyRepository.markStoryAsViewed(currentStory.id)
                    // Update local state
                    val updatedUserStories = currentState.userStories.toMutableList()
                    val updatedUserStory = updatedUserStories[currentState.currentUserIndex]
                    val updatedStories = updatedUserStory.stories.toMutableList()
                    val storyIndex = updatedStories.indexOfFirst { it.id == currentStory.id }
                    if (storyIndex >= 0) {
                        updatedStories[storyIndex] = currentStory.copy(isViewedByMe = true)
                        updatedUserStories[currentState.currentUserIndex] = updatedUserStory.copy(
                            stories = updatedStories,
                            hasUnviewedStories = updatedStories.any { !it.isViewedByMe }
                        )
                        _uiState.value = currentState.copy(userStories = updatedUserStories)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "markCurrentStoryAsViewed: failed", e)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoAdvanceJob?.cancel()
    }

    companion object {
        private const val TAG = "StoryViewModel"
    }
}

data class StoryUiState(
    val userStories: List<UserStories> = emptyList(),
    val currentUserIndex: Int = 0,
    val currentStoryIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    fun getCurrentUserStories(): UserStories? {
        return if (currentUserIndex in userStories.indices) {
            userStories[currentUserIndex]
        } else {
            null
        }
    }

    fun getCurrentStory(): Story? {
        val userStories = getCurrentUserStories() ?: return null
        return if (currentStoryIndex in userStories.stories.indices) {
            userStories.stories[currentStoryIndex]
        } else {
            null
        }
    }
}
