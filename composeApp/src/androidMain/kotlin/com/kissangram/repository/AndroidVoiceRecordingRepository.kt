package com.kissangram.repository

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Android implementation of VoiceRecordingRepository using MediaRecorder.
 * Records audio in M4A format (AAC codec) for good quality and compatibility.
 */
class AndroidVoiceRecordingRepository(
    private val context: Context
) : VoiceRecordingRepository {
    
    companion object {
        private const val TAG = "AndroidVoiceRecording"
        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 128000
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    private var recordingStartTime: Long = 0
    private var isCurrentlyRecording = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var durationUpdateRunnable: Runnable? = null
    private var onDurationUpdate: ((Int) -> Unit)? = null
    
    override fun isAvailable(): Boolean {
        // Check if device has microphone
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }
    
    override fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    override suspend fun requestPermission(): Boolean {
        // Permission request must be handled by Activity
        // This just returns current state
        return hasPermission()
    }
    
    override suspend fun startRecording(outputFilePath: String) {
        if (isCurrentlyRecording) {
            Log.w(TAG, "Already recording, ignoring start request")
            return
        }
        
        if (!hasPermission()) {
            throw Exception("RECORD_AUDIO permission is required")
        }
        
        try {
            // Ensure parent directory exists
            val file = File(outputFilePath)
            file.parentFile?.mkdirs()
            
            // Delete existing file if any
            if (file.exists()) {
                file.delete()
            }
            
            currentFilePath = outputFilePath
            
            // Create MediaRecorder
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setOutputFile(outputFilePath)
                
                // Set max duration to 60 seconds for voice captions
                setMaxDuration(60000)
                
                // Set error listener
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaRecorder error: what=$what, extra=$extra")
                    cleanupRecorder()
                }
                
                // Set info listener for max duration reached
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        Log.d(TAG, "Max duration reached")
                        // Don't auto-stop here, let the user handle it
                    }
                }
                
                prepare()
                start()
            }
            
            mediaRecorder = recorder
            recordingStartTime = System.currentTimeMillis()
            isCurrentlyRecording = true
            
            // Start duration update timer
            startDurationUpdates()
            
            Log.d(TAG, "Recording started: $outputFilePath")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            cleanupRecorder()
            throw Exception("Failed to start recording: ${e.message}")
        }
    }
    
    override suspend fun stopRecording(): Int {
        if (!isCurrentlyRecording) {
            Log.w(TAG, "Not recording, nothing to stop")
            return 0
        }
        
        val duration = getCurrentDuration()
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.d(TAG, "Recording stopped, duration: $duration seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            // Try to get duration from file instead
            currentFilePath?.let {
                val fileDuration = getAudioDuration(it)
                if (fileDuration > 0) {
                    cleanupRecorder()
                    return fileDuration
                }
            }
        }
        
        cleanupRecorder()
        return duration
    }
    
    override suspend fun cancelRecording() {
        if (!isCurrentlyRecording) {
            return
        }
        
        Log.d(TAG, "Cancelling recording")
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder during cancel: ${e.message}")
        }
        
        // Delete the file
        currentFilePath?.let { path ->
            try {
                File(path).delete()
                Log.d(TAG, "Deleted cancelled recording: $path")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting cancelled recording: ${e.message}")
            }
        }
        
        cleanupRecorder()
    }
    
    override fun isRecording(): Boolean {
        return isCurrentlyRecording
    }
    
    override fun getCurrentDuration(): Int {
        if (!isCurrentlyRecording) {
            return 0
        }
        val elapsedMillis = System.currentTimeMillis() - recordingStartTime
        return (elapsedMillis / 1000).toInt()
    }
    
    override suspend fun deleteRecording(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted recording: $filePath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting recording: ${e.message}")
        }
    }
    
    override suspend fun getAudioDuration(filePath: String): Int {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            (durationMs / 1000).toInt()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio duration: ${e.message}")
            0
        }
    }
    
    // MARK: - Helper Methods
    
    private fun cleanupRecorder() {
        stopDurationUpdates()
        
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing recorder: ${e.message}")
        }
        
        mediaRecorder = null
        currentFilePath = null
        recordingStartTime = 0
        isCurrentlyRecording = false
    }
    
    private fun startDurationUpdates() {
        durationUpdateRunnable = object : Runnable {
            override fun run() {
                if (isCurrentlyRecording) {
                    onDurationUpdate?.invoke(getCurrentDuration())
                    mainHandler.postDelayed(this, 1000) // Update every second
                }
            }
        }
        mainHandler.post(durationUpdateRunnable!!)
    }
    
    private fun stopDurationUpdates() {
        durationUpdateRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        durationUpdateRunnable = null
    }
    
    /**
     * Set a callback to receive duration updates while recording.
     * Called every second with the current duration in seconds.
     */
    fun setOnDurationUpdate(callback: ((Int) -> Unit)?) {
        onDurationUpdate = callback
    }
    
    /**
     * Get the file path for a new voice caption recording.
     * Creates a unique filename in the app's cache directory.
     */
    fun generateVoiceCaptionPath(): String {
        val cacheDir = context.cacheDir
        val voiceCaptionsDir = File(cacheDir, "voice_captions")
        voiceCaptionsDir.mkdirs()
        
        val fileName = "voice_caption_${System.currentTimeMillis()}.m4a"
        return File(voiceCaptionsDir, fileName).absolutePath
    }
}
