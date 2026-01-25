package com.kissangram.ui.auth

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.auth.AndroidAuthRepository
import com.kissangram.auth.usecase.UpdateUserProfileUseCase
import com.kissangram.speech.AndroidSpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NameViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    private val authRepository = AndroidAuthRepository(context = application)
    private val updateUserProfileUseCase = UpdateUserProfileUseCase(authRepository)
    private val speechRecognizer = AndroidSpeechRecognizer(application)
    
    private val _uiState = MutableStateFlow(NameUiState())
    val uiState: StateFlow<NameUiState> = _uiState.asStateFlow()
    
    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            error = null
        )
    }
    
    fun startSpeechRecognition() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isListening = true, error = null)
            
            // Check permission
            val hasPermission = ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                _uiState.value = _uiState.value.copy(
                    isListening = false,
                    error = "Microphone permission is required for speech recognition"
                )
                return@launch
            }
            
            // Set up callback for continuous updates
            (speechRecognizer as? AndroidSpeechRecognizer)?.setOnTextUpdate { recognizedText ->
                // Update name continuously
                _uiState.value = _uiState.value.copy(
                    name = recognizedText.trim(),
                    error = null
                )
            }
            
            // Start listening in background - it will continue until stopped
            launch {
                try {
                    speechRecognizer.startListening()
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
            // Set processing state
            _uiState.value = _uiState.value.copy(isListening = false, isProcessing = true)
            speechRecognizer.stopListening()
            // Wait a bit for final results to come in (up to 3 seconds)
            kotlinx.coroutines.delay(3500) // Wait 3.5 seconds to ensure final results are processed
            // Get final accumulated text
            val finalText = (speechRecognizer as? AndroidSpeechRecognizer)?.getAccumulatedText() ?: ""
            if (finalText.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(name = finalText.trim())
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }
    
    fun saveName(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val name = _uiState.value.name.trim()
        if (name.length < 2) {
            _uiState.value = _uiState.value.copy(
                error = "Name must be at least 2 characters"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                updateUserProfileUseCase(name)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "Failed to save name"
                )
                onError(exception.message ?: "Failed to save name")
            }
        }
    }
}

data class NameUiState(
    val name: String = "",
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null
)
