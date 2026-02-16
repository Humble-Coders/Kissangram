package com.kissangram.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.usecase.DeletePostUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OwnPostDetailUiState(
    val showDeleteDialog: Boolean = false,
    val isDeletingPost: Boolean = false,
    val deleteError: String? = null
)

class OwnPostDetailViewModel(
    application: Application,
    private val postId: String,
    private val commentsViewModel: CommentsViewModel,
    private val deletePostUseCase: DeletePostUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OwnPostDetailUiState())
    val uiState: StateFlow<OwnPostDetailUiState> = _uiState.asStateFlow()

    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = true, deleteError = null)
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false, deleteError = null)
    }

    fun deletePost(onSuccess: () -> Unit) {
        if (_uiState.value.isDeletingPost) {
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "deletePost: deleting postId=$postId")
            _uiState.value = _uiState.value.copy(isDeletingPost = true, deleteError = null)

            try {
                deletePostUseCase(postId)
                Log.d(TAG, "deletePost: post deleted successfully")
                _uiState.value = _uiState.value.copy(
                    isDeletingPost = false,
                    showDeleteDialog = false
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "deletePost: failed", e)
                _uiState.value = _uiState.value.copy(
                    isDeletingPost = false,
                    deleteError = e.message ?: "Failed to delete post"
                )
            }
        }
    }

    companion object {
        private const val TAG = "OwnPostDetailViewModel"
    }
}
