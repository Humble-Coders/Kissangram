package com.kissangram.repository

/**
 * Interface for voice recording functionality (audio capture).
 * Used for recording voice captions on posts.
 * Implementations should handle platform-specific audio recording.
 */
interface VoiceRecordingRepository {
    /**
     * Start recording audio
     * @param outputFilePath The file path where audio will be saved
     * @throws Exception if recording fails to start
     */
    @Throws(Exception::class)
    suspend fun startRecording(outputFilePath: String)
    
    /**
     * Stop recording and save the audio file
     * @return The duration of the recording in seconds
     * @throws Exception if stopping fails
     */
    @Throws(Exception::class)
    suspend fun stopRecording(): Int
    
    /**
     * Cancel recording without saving
     */
    suspend fun cancelRecording()
    
    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean
    
    /**
     * Get current recording duration in seconds
     */
    fun getCurrentDuration(): Int
    
    /**
     * Check if audio recording is available on the device
     */
    fun isAvailable(): Boolean
    
    /**
     * Check if microphone permission is granted
     */
    fun hasPermission(): Boolean
    
    /**
     * Request microphone permission
     * @return true if permission granted, false otherwise
     */
    suspend fun requestPermission(): Boolean
    
    /**
     * Delete a recorded audio file
     * @param filePath The path to the file to delete
     */
    suspend fun deleteRecording(filePath: String)
    
    /**
     * Get the duration of an audio file in seconds
     * @param filePath The path to the audio file
     * @return Duration in seconds, or 0 if unable to determine
     */
    suspend fun getAudioDuration(filePath: String): Int
}
