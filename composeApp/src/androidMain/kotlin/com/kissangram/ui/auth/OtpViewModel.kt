package com.kissangram.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.auth.AndroidAuthRepository
import com.kissangram.auth.usecase.VerifyOtpUseCase
import com.kissangram.speech.AndroidSpeechRecognizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OtpViewModel(
    application: Application,
    val phoneNumber: String
) : AndroidViewModel(application) {
    
    private val authRepository = AndroidAuthRepository(context = application)
    private val verifyOtpUseCase = VerifyOtpUseCase(authRepository)
    private val speechRecognizer = AndroidSpeechRecognizer(application)
    
    private val _uiState = MutableStateFlow(OtpUiState())
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()
    
    fun updateOtp(otp: String) {
        _uiState.value = _uiState.value.copy(
            otp = otp,
            error = null
        )
    }
    
    fun verifyOtp(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val otp = _uiState.value.otp
        if (otp.length != 6) {
            _uiState.value = _uiState.value.copy(
                error = "Please enter 6 digit OTP"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                verifyOtpUseCase(otp)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "Invalid OTP"
                )
                onError(exception.message ?: "Invalid OTP")
            }
        }
    }
    
    fun resendOtp(onResend: () -> Unit) {
        onResend()
    }
    
    fun startSpeechRecognition() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isListening = true, error = null)
            
            // Set up callback for continuous updates BEFORE starting to listen
            (speechRecognizer as? AndroidSpeechRecognizer)?.setOnTextUpdate { recognizedText ->
                // Extract digits from recognized text and update continuously
                // Limit to 6 digits for OTP
                val digits = recognizedText.filter { it.isDigit() }.take(6)
                if (digits.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        otp = digits,
                        error = null
                    )
                }
            }
            
            // Start listening in background - it will continue until stopped
            try {
                speechRecognizer.startListening()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isListening = false,
                    error = e.message ?: "Speech recognition error"
                )
            }
        }
    }
    
    fun stopSpeechRecognition() {
        android.util.Log.d("OtpViewModel", "stopSpeechRecognition called")
        viewModelScope.launch {
            // Set processing state
            _uiState.value = _uiState.value.copy(isListening = false, isProcessing = true)
            android.util.Log.d("OtpViewModel", "Calling speechRecognizer.stopListening()")
            speechRecognizer.stopListening()
            // Wait a bit for final results to come in (up to 3 seconds)
            delay(3500) // Wait 3.5 seconds to ensure final results are processed
            // Get final accumulated text
            val finalText = (speechRecognizer as? AndroidSpeechRecognizer)?.getAccumulatedText() ?: ""
            val digits = finalText.filter { it.isDigit() }.take(6)
            if (digits.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(otp = digits)
            }
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }
}

data class OtpUiState(
    val otp: String = "",
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null
)
