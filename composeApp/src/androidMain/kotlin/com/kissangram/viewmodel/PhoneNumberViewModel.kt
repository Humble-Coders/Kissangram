package com.kissangram.viewmodel

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.AndroidSpeechRepository
import com.kissangram.usecase.SendOtpUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhoneNumberViewModel(
    application: Application,
    private val activity: Activity? = null
) : AndroidViewModel(application) {
    
    private val preferencesRepository = AndroidPreferencesRepository(application)
    private val authRepository = AndroidAuthRepository(
        context = application,
        activity = activity,
        preferencesRepository = preferencesRepository
    )
    private val sendOtpUseCase = SendOtpUseCase(authRepository)
    private val speechRepository = AndroidSpeechRepository(application)
    
    private val _uiState = MutableStateFlow(PhoneNumberUiState())
    val uiState: StateFlow<PhoneNumberUiState> = _uiState.asStateFlow()
    
    fun updatePhoneNumber(phoneNumber: String) {
        _uiState.value = _uiState.value.copy(
            phoneNumber = phoneNumber,
            error = null
        )
    }
    
    fun updateCountryCode(countryCode: String) {
        _uiState.value = _uiState.value.copy(countryCode = countryCode)
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
            
            // Set up callback for continuous updates BEFORE starting to listen
            speechRepository.setOnTextUpdate { recognizedText ->
                // Extract digits from recognized text and update continuously
                // Callback is already on main thread via Handler
                val digits = recognizedText.filter { it.isDigit() }
                if (digits.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        phoneNumber = digits,
                        error = null
                    )
                }
            }
            
            // Start listening in background - it will continue until stopped
            try {
                speechRepository.startListening()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isListening = false,
                    error = e.message ?: "Speech recognition error"
                )
            }
        }
    }
    
    fun stopSpeechRecognition() {
        android.util.Log.d("PhoneNumberViewModel", "stopSpeechRecognition called")
        viewModelScope.launch {
            // Set processing state
            _uiState.value = _uiState.value.copy(isListening = false, isProcessing = true)
            android.util.Log.d("PhoneNumberViewModel", "Calling speechRepository.stopListening()")
            speechRepository.stopListening()
            // Wait a bit for final results to come in (up to 3 seconds)
            kotlinx.coroutines.delay(3500) // Wait 3.5 seconds to ensure final results are processed
            // Get final accumulated text
            val finalText = speechRepository.getAccumulatedText()
            val digits = finalText.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(phoneNumber = digits)
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }
    
    fun sendOtp(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val phone = _uiState.value.phoneNumber
        if (phone.isBlank() || phone.length < 10) {
            _uiState.value = _uiState.value.copy(
                error = "Please enter a valid phone number"
            )
            return
        }
        
        val fullPhoneNumber = "${_uiState.value.countryCode}$phone"
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                sendOtpUseCase(fullPhoneNumber)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess(fullPhoneNumber)
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "Failed to send OTP"
                )
                onError(exception.message ?: "Failed to send OTP")
            }
        }
    }
}

data class PhoneNumberUiState(
    val phoneNumber: String = "",
    val countryCode: String = "+91",
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null
)
