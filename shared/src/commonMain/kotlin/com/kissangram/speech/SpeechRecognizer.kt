package com.kissangram.speech

public interface SpeechRecognizer {
    /**
     * Start listening for speech input
     * @return The recognized text
     * @throws Exception if recognition fails
     */
    @Throws(Exception::class)
    suspend fun startListening(): String
    
    /**
     * Stop listening for speech input
     */
    suspend fun stopListening()
    
    /**
     * Check if speech recognition is available on the device
     */
    fun isAvailable(): Boolean
    
    /**
     * Request necessary permissions for speech recognition
     * @return true if permissions are granted, false otherwise
     */
    suspend fun requestPermission(): Boolean
    
    /**
     * Check if permissions are already granted
     */
    fun hasPermission(): Boolean
}
