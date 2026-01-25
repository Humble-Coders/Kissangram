package com.kissangram.speech

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Clean implementation of Android Speech Recognition using the system API
 * with improved error handling, compatibility for older Android versions,
 * and continuous listening support.
 */
public class AndroidSpeechRecognizer(
    private val context: Context
) : com.kissangram.speech.SpeechRecognizer {
    
    private var speechRecognizer: android.speech.SpeechRecognizer? = null
    private var accumulatedText: String = ""
    private var onTextUpdate: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var isListening = false
    @Volatile
    private var shouldContinueListening = false
    @Volatile
    private var shouldStopAfterCurrent = false
    private var retryCount = 0
    private val maxRetries = 3
    private var stopTimeoutRunnable: Runnable? = null
    private var restartRunnable: Runnable? = null
    private val lock = Any()
    
    override fun isAvailable(): Boolean {
        // Check if speech recognition is available on this device
        if (!hasPermission()) {
            return false
        }
        
        // Check if SpeechRecognizer is available (may not be on all devices)
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
            android.util.Log.w("AndroidSpeechRecognizer", "Speech recognition not available on this device")
            return false
        }
        
        // Check if there's an activity that can handle speech recognition intent
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val activities = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (activities.isEmpty()) {
            android.util.Log.w("AndroidSpeechRecognizer", "No speech recognition activities found")
            return false
        }
        
        return true
    }
    
    override fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    override suspend fun requestPermission(): Boolean {
        return hasPermission()
    }
    
    override suspend fun startListening(): String {
        if (!hasPermission()) {
            throw Exception("RECORD_AUDIO permission is required")
        }
        
        if (!isAvailable()) {
            throw Exception("Speech recognition is not available on this device")
        }
        
        // If already listening, don't start again
        if (isListening) {
            android.util.Log.d("AndroidSpeechRecognizer", "Already listening, skipping start")
            return ""
        }
        
        // Reset accumulated text and set listening state
        accumulatedText = ""
        shouldContinueListening = true
        shouldStopAfterCurrent = false
        isListening = true
        retryCount = 0
        stopTimeoutRunnable = null
        
        // Use Android's SpeechRecognizer with improved error handling
        return useImprovedSpeechRecognizer()
    }
    
    private suspend fun useImprovedSpeechRecognizer(): String = suspendCancellableCoroutine { continuation ->
        try {
            // Clean up existing recognizer if any
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            
            // Add a small delay for older Android versions to ensure system is ready
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                mainHandler.postDelayed({
                    startRecognizer(continuation)
                }, 100)
            } else {
                startRecognizer(continuation)
            }
            
            // Return empty string immediately
            continuation.resume("")
            
        } catch (e: Exception) {
            android.util.Log.e("AndroidSpeechRecognizer", "Exception: ${e.message}", e)
            try {
                continuation.resumeWithException(e)
            } catch (ex: Exception) {
                // Continuation already completed
            }
        }
    }
    
    private fun startRecognizer(continuation: kotlin.coroutines.Continuation<String>) {
        try {
            val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
            if (recognizer == null) {
                android.util.Log.e("AndroidSpeechRecognizer", "Failed to create SpeechRecognizer")
                try {
                    continuation.resumeWithException(Exception("Speech recognition not available"))
                } catch (e: Exception) {
                    // Continuation already completed
                }
                return
            }
            
            speechRecognizer = recognizer
            
            val listener = object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    android.util.Log.d("AndroidSpeechRecognizer", "Ready for speech")
                }
                
                override fun onBeginningOfSpeech() {
                    android.util.Log.d("AndroidSpeechRecognizer", "Beginning of speech")
                }
                
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    android.util.Log.d("AndroidSpeechRecognizer", "End of speech")
                    // Don't auto-restart here, let the user control it
                }
                
                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        android.speech.SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        android.speech.SpeechRecognizer.ERROR_CLIENT -> {
                            // Client error - try to recover on older Android versions
                            android.util.Log.e("AndroidSpeechRecognizer", "Client error (SDK: ${Build.VERSION.SDK_INT})")
                            
                            if (retryCount < maxRetries && shouldContinueListening && isListening) {
                                retryCount++
                                android.util.Log.d("AndroidSpeechRecognizer", "Retrying after client error (attempt $retryCount/$maxRetries)")
                                mainHandler.postDelayed({
                                    if (shouldContinueListening && isListening) {
                                        try {
                                            // Destroy and recreate recognizer
                                            recognizer.destroy()
                                            val newRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
                                            if (newRecognizer != null) {
                                                speechRecognizer = newRecognizer
                                                newRecognizer.setRecognitionListener(this)
                                                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                    putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                                    putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                                }
                                                newRecognizer.startListening(intent)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("AndroidSpeechRecognizer", "Error retrying: ${e.message}")
                                        }
                                    }
                                }, 500L * retryCount) // Exponential backoff
                            } else if (!shouldContinueListening) {
                                try {
                                    continuation.resume("")
                                } catch (e: Exception) {
                                    // Continuation already completed
                                }
                            }
                            return
                        }
                        android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                        android.speech.SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        android.speech.SpeechRecognizer.ERROR_NO_MATCH -> {
                            // No match - continue listening if we should
                            retryCount = 0 // Reset retry count on successful error handling
                            val shouldContinue: Boolean
                            synchronized(lock) {
                                shouldContinue = shouldContinueListening && isListening && !shouldStopAfterCurrent
                            }
                            if (shouldContinue) {
                                restartListening(recognizer, this)
                            } else {
                                android.util.Log.d("AndroidSpeechRecognizer", "No match but stopping - shouldStop: $shouldStopAfterCurrent")
                                try {
                                    continuation.resume("")
                                } catch (e: Exception) {
                                    // Continuation already completed
                                }
                            }
                            return
                        }
                        android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            // Busy - try again after delay with exponential backoff
                            if (shouldContinueListening && isListening) {
                                val delay = 500L * (retryCount + 1)
                                mainHandler.postDelayed({
                                    if (shouldContinueListening && isListening) {
                                        restartListening(recognizer, this)
                                    }
                                }, delay)
                            }
                            return
                        }
                        android.speech.SpeechRecognizer.ERROR_SERVER -> "Server error"
                        android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // Timeout - continue if we should
                            retryCount = 0 // Reset retry count
                            val shouldContinue: Boolean
                            synchronized(lock) {
                                shouldContinue = shouldContinueListening && isListening && !shouldStopAfterCurrent
                            }
                            if (shouldContinue) {
                                restartListening(recognizer, this)
                            } else {
                                android.util.Log.d("AndroidSpeechRecognizer", "Timeout but stopping - shouldStop: $shouldStopAfterCurrent")
                                try {
                                    continuation.resume("")
                                } catch (e: Exception) {
                                    // Continuation already completed
                                }
                            }
                            return
                        }
                        else -> "Unknown error: $error"
                    }
                    android.util.Log.e("AndroidSpeechRecognizer", "Error: $errorMsg")
                }
                
                override fun onResults(results: android.os.Bundle?) {
                    // Synchronized check of flags to prevent race conditions
                    val shouldStop: Boolean
                    synchronized(lock) {
                        shouldStop = shouldStopAfterCurrent || !shouldContinueListening || !isListening
                        android.util.Log.d("AndroidSpeechRecognizer", "=== ON RESULTS CALLED ===")
                        android.util.Log.d("AndroidSpeechRecognizer", "Flags check: shouldStop=$shouldStop, shouldStopAfterCurrent=$shouldStopAfterCurrent, shouldContinue=$shouldContinueListening, isListening=$isListening")
                    }
                    
                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        val recognizedText = matches[0]
                        android.util.Log.d("AndroidSpeechRecognizer", "Recognized: $recognizedText")
                        
                        // Reset retry count on successful recognition
                        retryCount = 0
                        
                        // Accumulate text
                        if (recognizedText.isNotEmpty()) {
                            if (accumulatedText.isNotEmpty()) {
                                accumulatedText += " "
                            }
                            accumulatedText += recognizedText
                            
                            // Update callback
                            val textToUpdate = accumulatedText
                            onTextUpdate?.let { callback ->
                                mainHandler.post {
                                    callback(textToUpdate)
                                }
                            }
                        }
                    }
                    
                    // Double-check flags after processing (in case they changed)
                    val shouldStopFinal: Boolean
                    synchronized(lock) {
                        shouldStopFinal = shouldStopAfterCurrent || !shouldContinueListening || !isListening
                    }
                    
                    // If we should stop, don't restart - just process the final results
                    if (shouldStop || shouldStopFinal) {
                        android.util.Log.d("AndroidSpeechRecognizer", "Final results received, NOT restarting (shouldStop: $shouldStopAfterCurrent, shouldContinue: $shouldContinueListening, isListening: $isListening)")
                        android.util.Log.d("AndroidSpeechRecognizer", "Final accumulated text: $accumulatedText")
                        // Don't try to resume continuation - it's already been resumed in startListening()
                        // The accumulated text is already stored and can be retrieved via getAccumulatedText()
                        // Clean up after a delay to ensure ViewModel can get accumulated text
                        mainHandler.postDelayed({
                            actuallyStopListening()
                        }, 500)
                        return
                    }
                    
                    // Final check right before restarting - this is critical
                    val finalCheck: Boolean
                    synchronized(lock) {
                        finalCheck = shouldStopAfterCurrent || !shouldContinueListening || !isListening
                    }
                    
                    if (finalCheck) {
                        android.util.Log.d("AndroidSpeechRecognizer", "Final check before restart - stopping instead")
                        android.util.Log.d("AndroidSpeechRecognizer", "Final accumulated text: $accumulatedText")
                        // Don't try to resume continuation - it's already been resumed
                        // Clean up after a delay
                        mainHandler.postDelayed({
                            actuallyStopListening()
                        }, 500)
                        return
                    }
                    
                    // Only restart if we should continue
                    android.util.Log.d("AndroidSpeechRecognizer", "Final results received, continuing to listen")
                    restartListening(recognizer, this)
                }
                
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        val partialText = matches[0]
                        android.util.Log.d("AndroidSpeechRecognizer", "Partial: $partialText")
                        
                        // Update with partial results
                        val currentText = if (accumulatedText.isNotEmpty()) {
                            "$accumulatedText $partialText"
                        } else {
                            partialText
                        }
                        
                        onTextUpdate?.let { callback ->
                            mainHandler.post {
                                callback(currentText)
                            }
                        }
                    }
                }
                
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            }
            
            recognizer.setRecognitionListener(listener)
            
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Add these for better compatibility on older devices
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
            }
            
            try {
                recognizer.startListening(intent)
                android.util.Log.d("AndroidSpeechRecognizer", "Started listening (SDK: ${Build.VERSION.SDK_INT})")
            } catch (e: Exception) {
                android.util.Log.e("AndroidSpeechRecognizer", "Error starting: ${e.message}", e)
                recognizer.destroy()
                speechRecognizer = null
                try {
                    continuation.resumeWithException(Exception("Failed to start listening: ${e.message}"))
                } catch (ex: Exception) {
                    // Continuation already completed
                }
            }
            
            // Note: invokeOnCancellation is only available on CancellableContinuation
            // We'll handle cleanup in stopListening() instead
            
        } catch (e: Exception) {
            android.util.Log.e("AndroidSpeechRecognizer", "Exception: ${e.message}", e)
            try {
                continuation.resumeWithException(e)
            } catch (ex: Exception) {
                // Continuation already completed
            }
        }
    }
    
    private fun restartListening(recognizer: android.speech.SpeechRecognizer, listener: android.speech.RecognitionListener) {
        // Synchronized check - don't restart if we should stop after current
        val shouldStop: Boolean
        synchronized(lock) {
            shouldStop = shouldStopAfterCurrent || !shouldContinueListening || !isListening
        }
        
        if (shouldStop) {
            android.util.Log.d("AndroidSpeechRecognizer", "Skipping restart - shouldStop: $shouldStopAfterCurrent, shouldContinue: $shouldContinueListening, isListening: $isListening")
            return
        }
        
        // Cancel any pending restart
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        
        // Longer delay for older Android versions
        val delay = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) 500L else 300L
        
        restartRunnable = Runnable {
            // Double-check flags before restarting (synchronized)
            val shouldStopNow: Boolean
            synchronized(lock) {
                shouldStopNow = shouldStopAfterCurrent || !shouldContinueListening || !isListening
            }
            
            if (shouldStopNow) {
                android.util.Log.d("AndroidSpeechRecognizer", "Cancelled restart - flags changed")
                return@Runnable
            }
            
            try {
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // Add for better compatibility on older devices
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    }
                }
                recognizer.startListening(intent)
                android.util.Log.d("AndroidSpeechRecognizer", "Restarted listening")
            } catch (e: Exception) {
                android.util.Log.e("AndroidSpeechRecognizer", "Error restarting: ${e.message}")
                // Don't try to recover if we should stop (synchronized check)
                val shouldStopRecovery: Boolean
                synchronized(lock) {
                    shouldStopRecovery = shouldStopAfterCurrent || !shouldContinueListening || !isListening
                }
                if (shouldStopRecovery) {
                    return@Runnable
                }
                // Try to recover by recreating recognizer
                if (retryCount < maxRetries) {
                    retryCount++
                    try {
                        recognizer.destroy()
                        val newRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
                        if (newRecognizer != null) {
                            speechRecognizer = newRecognizer
                            newRecognizer.setRecognitionListener(listener)
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                                }
                            }
                            newRecognizer.startListening(intent)
                        }
                    } catch (recoveryError: Exception) {
                        android.util.Log.e("AndroidSpeechRecognizer", "Error recovering: ${recoveryError.message}")
                        isListening = false
                        shouldContinueListening = false
                    }
                } else {
                    isListening = false
                    shouldContinueListening = false
                }
            }
        }
        
        mainHandler.postDelayed(restartRunnable!!, delay)
    }
    
    override suspend fun stopListening() {
        android.util.Log.d("AndroidSpeechRecognizer", "=== STOP LISTENING CALLED ===")
        android.util.Log.d("AndroidSpeechRecognizer", "Before: shouldStop=$shouldStopAfterCurrent, shouldContinue=$shouldContinueListening, isListening=$isListening")
        
        // Synchronized flag setting to prevent race conditions
        synchronized(lock) {
            shouldStopAfterCurrent = true
            shouldContinueListening = false
            android.util.Log.d("AndroidSpeechRecognizer", "After setting flags: shouldStop=$shouldStopAfterCurrent, shouldContinue=$shouldContinueListening, isListening=$isListening")
        }
        
        // Cancel any pending restart callbacks immediately
        restartRunnable?.let { 
            mainHandler.removeCallbacks(it)
            restartRunnable = null
            android.util.Log.d("AndroidSpeechRecognizer", "Cancelled pending restart")
        }
        
        // Cancel any pending timeout
        stopTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        
        // Stop the recognizer immediately to prevent it from listening further
        // But don't destroy it yet - wait for final results
        val recognizer = speechRecognizer
        if (recognizer != null) {
            try {
                // Stop listening but keep the recognizer alive to receive final results
                recognizer.stopListening()
                android.util.Log.d("AndroidSpeechRecognizer", "Stopped recognizer (waiting for final results)")
            } catch (e: Exception) {
                android.util.Log.e("AndroidSpeechRecognizer", "Error stopping: ${e.message}")
                // If stop fails, cancel it
                try {
                    recognizer.cancel()
                } catch (cancelEx: Exception) {
                    android.util.Log.e("AndroidSpeechRecognizer", "Error cancelling: ${cancelEx.message}")
                }
            }
        }
        
        // Set a timeout to force cleanup if final results don't come within 1.5 seconds
        stopTimeoutRunnable = Runnable {
            synchronized(lock) {
                if (shouldStopAfterCurrent && isListening) {
                    android.util.Log.d("AndroidSpeechRecognizer", "Timeout reached, forcing cleanup")
                    actuallyStopListening()
                }
            }
        }
        mainHandler.postDelayed(stopTimeoutRunnable!!, 1500)
    }
    
    private fun actuallyStopListening() {
        android.util.Log.d("AndroidSpeechRecognizer", "Actually stopping listening")
        
        // Synchronized flag setting
        synchronized(lock) {
            isListening = false
            shouldStopAfterCurrent = false
            shouldContinueListening = false
        }
        
        // Cancel any pending restart callbacks
        restartRunnable?.let { 
            mainHandler.removeCallbacks(it)
            restartRunnable = null
        }
        
        // Cancel timeout
        stopTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        stopTimeoutRunnable = null
        
        val recognizer = speechRecognizer
        speechRecognizer = null
        
        if (recognizer != null) {
            try {
                recognizer.cancel()
            } catch (e: Exception) {
                android.util.Log.e("AndroidSpeechRecognizer", "Error cancelling: ${e.message}")
            }
            
            try {
                recognizer.stopListening()
            } catch (e: Exception) {
                android.util.Log.e("AndroidSpeechRecognizer", "Error stopping: ${e.message}")
            }
            
            try {
                recognizer.destroy()
            } catch (e: Exception) {
                android.util.Log.e("AndroidSpeechRecognizer", "Error destroying: ${e.message}")
            }
        }
        
        onTextUpdate = null
    }
    
    fun setOnTextUpdate(callback: (String) -> Unit) {
        onTextUpdate = callback
    }
    
    fun getAccumulatedText(): String {
        return accumulatedText
    }
}


