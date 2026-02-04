package com.kissangram.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.CreatePostInput
import com.kissangram.model.CreatePostLocation
import com.kissangram.model.MediaItem
import com.kissangram.model.MediaType
import com.kissangram.model.PostType
import com.kissangram.model.PostVisibility
import com.kissangram.repository.AndroidCropsRepository
import com.kissangram.repository.AndroidVoiceRecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for Create Post screen
 */
data class CreatePostUiState(
    val postType: PostType = PostType.NORMAL,
    val caption: String = "",
    val mediaItems: List<MediaItem> = emptyList(),
    val selectedCrops: List<String> = emptyList(),
    val visibility: PostVisibility = PostVisibility.PUBLIC,
    val location: CreatePostLocation? = null,
    val hashtags: List<String> = emptyList(),
    val hashtagInput: String = "",
    val targetExpertise: List<String> = emptyList(),
    
    // Crops state
    val allCrops: List<String> = emptyList(),
    val cropSearchQuery: String = "",
    val isLoadingCrops: Boolean = false,
    val showAllCrops: Boolean = false, // Whether to show all crops or just first 10
    
    // Voice caption state
    val voiceCaptionUri: String? = null,
    val voiceCaptionDuration: Int = 0,
    val isRecordingVoice: Boolean = false,
    val recordingDuration: Int = 0,
    
    // Voice playback state
    val isPlayingVoice: Boolean = false,
    val playbackProgress: Int = 0, // Current playback position in seconds
    
    // Permission state
    val needsAudioPermission: Boolean = false,
    
    // Validation
    val isPostEnabled: Boolean = false,
    
    // Error state
    val errorMessage: String? = null
) {
    // Computed property for filtered and limited crops
    val visibleCrops: List<String>
        get() {
            val unselectedCrops = allCrops.filter { !selectedCrops.contains(it) }
            val filteredCrops = if (cropSearchQuery.isBlank()) {
                unselectedCrops
            } else {
                unselectedCrops.filter { it.contains(cropSearchQuery, ignoreCase = true) }
            }
            return if (showAllCrops || cropSearchQuery.isNotBlank()) {
                filteredCrops
            } else {
                filteredCrops.take(10)
            }
        }
    
    val hasMoreCrops: Boolean
        get() {
            if (cropSearchQuery.isNotBlank()) return false
            val unselectedCrops = allCrops.filter { !selectedCrops.contains(it) }
            return !showAllCrops && unselectedCrops.size > 10
        }
    
    val remainingCropsCount: Int
        get() {
            val unselectedCrops = allCrops.filter { !selectedCrops.contains(it) }
            return (unselectedCrops.size - 10).coerceAtLeast(0)
        }
}

class CreatePostViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    private val voiceRecordingRepository = AndroidVoiceRecordingRepository(application)
    private val cropsRepository = AndroidCropsRepository()
    
    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()
    
    private var currentVoiceCaptionPath: String? = null
    
    // Audio playback
    private var mediaPlayer: MediaPlayer? = null
    private val playbackHandler = Handler(Looper.getMainLooper())
    private var playbackUpdateRunnable: Runnable? = null
    
    init {
        // Set up duration update callback
        voiceRecordingRepository.setOnDurationUpdate { duration ->
            _uiState.update { it.copy(recordingDuration = duration) }
        }
        
        // Load crops
        loadCrops()
    }
    
    // MARK: - Crops
    
    private fun loadCrops() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCrops = true) }
            try {
                val crops = cropsRepository.getAllCrops()
                _uiState.update { 
                    it.copy(
                        allCrops = crops,
                        isLoadingCrops = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingCrops = false,
                        errorMessage = "Failed to load crops: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun setCropSearchQuery(query: String) {
        _uiState.update { it.copy(cropSearchQuery = query) }
    }
    
    fun toggleShowAllCrops() {
        _uiState.update { it.copy(showAllCrops = !it.showAllCrops) }
    }
    

    // MARK: - Post Type
    
    fun setPostType(type: PostType) {
        _uiState.update { 
            it.copy(postType = type).updateValidation()
        }
    }
    
    // MARK: - Caption
    
    fun setCaption(caption: String) {
        _uiState.update { 
            it.copy(caption = caption).updateValidation()
        }
    }
    
    // MARK: - Media Items
    
    fun addMediaItem(uri: Uri, type: MediaType) {
        val newItem = MediaItem(
            localUri = uri.toString(),
            type = type
        )
        _uiState.update { state ->
            state.copy(mediaItems = state.mediaItems + newItem).updateValidation()
        }
    }
    
    fun removeMediaItem(item: MediaItem) {
        _uiState.update { state ->
            state.copy(mediaItems = state.mediaItems - item).updateValidation()
        }
    }
    
    // MARK: - Crops
    
    fun toggleCrop(crop: String) {
        _uiState.update { state ->
            val newCrops = if (state.selectedCrops.contains(crop)) {
                state.selectedCrops - crop
            } else {
                state.selectedCrops + crop
            }
            state.copy(selectedCrops = newCrops)
        }
    }
    
    // MARK: - Visibility
    
    fun setVisibility(visibility: PostVisibility) {
        _uiState.update { it.copy(visibility = visibility) }
    }
    
    // MARK: - Location
    
    fun setLocation(location: CreatePostLocation?) {
        _uiState.update { it.copy(location = location) }
    }
    
    // MARK: - Hashtags
    
    fun setHashtagInput(input: String) {
        _uiState.update { it.copy(hashtagInput = input) }
    }
    
    fun addHashtag(tag: String) {
        val cleanTag = tag.replace("#", "").trim()
        if (cleanTag.isNotBlank() && !_uiState.value.hashtags.contains(cleanTag)) {
            _uiState.update { state ->
                state.copy(
                    hashtags = state.hashtags + cleanTag,
                    hashtagInput = ""
                )
            }
        }
    }
    
    fun removeHashtag(tag: String) {
        _uiState.update { state ->
            state.copy(hashtags = state.hashtags - tag)
        }
    }
    
    // MARK: - Target Expertise (for questions)
    
    fun toggleExpertise(expertise: String) {
        _uiState.update { state ->
            val newExpertise = if (state.targetExpertise.contains(expertise)) {
                state.targetExpertise - expertise
            } else {
                state.targetExpertise + expertise
            }
            state.copy(targetExpertise = newExpertise)
        }
    }
    
    // MARK: - Voice Recording
    
    fun hasAudioPermission(): Boolean {
        return voiceRecordingRepository.hasPermission()
    }
    
    fun startVoiceRecording() {
        if (!voiceRecordingRepository.hasPermission()) {
            _uiState.update { it.copy(needsAudioPermission = true) }
            return
        }
        
        viewModelScope.launch {
            try {
                // Generate a new file path
                val filePath = voiceRecordingRepository.generateVoiceCaptionPath()
                currentVoiceCaptionPath = filePath
                
                voiceRecordingRepository.startRecording(filePath)
                
                _uiState.update { 
                    it.copy(
                        isRecordingVoice = true,
                        recordingDuration = 0,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isRecordingVoice = false,
                        errorMessage = "Failed to start recording: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun stopVoiceRecording() {
        viewModelScope.launch {
            try {
                val duration = voiceRecordingRepository.stopRecording()
                val filePath = currentVoiceCaptionPath
                
                _uiState.update { 
                    it.copy(
                        isRecordingVoice = false,
                        voiceCaptionUri = filePath,
                        voiceCaptionDuration = duration,
                        recordingDuration = 0,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isRecordingVoice = false,
                        errorMessage = "Failed to stop recording: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun cancelVoiceRecording() {
        viewModelScope.launch {
            voiceRecordingRepository.cancelRecording()
            currentVoiceCaptionPath = null
            
            _uiState.update { 
                it.copy(
                    isRecordingVoice = false,
                    recordingDuration = 0
                )
            }
        }
    }
    
    fun removeVoiceCaption() {
        // Stop playback first if playing
        stopVoicePlayback()
        
        viewModelScope.launch {
            _uiState.value.voiceCaptionUri?.let { uri ->
                voiceRecordingRepository.deleteRecording(uri)
            }
            
            _uiState.update { 
                it.copy(
                    voiceCaptionUri = null,
                    voiceCaptionDuration = 0,
                    isPlayingVoice = false,
                    playbackProgress = 0
                )
            }
        }
    }
    
    // MARK: - Voice Playback
    
    fun playVoiceCaption() {
        val uri = _uiState.value.voiceCaptionUri ?: return
        
        try {
            // Stop any existing playback
            stopVoicePlayback()
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(uri)
                prepare()
                
                setOnCompletionListener {
                    stopVoicePlayback()
                }
                
                setOnErrorListener { _, _, _ ->
                    stopVoicePlayback()
                    _uiState.update { it.copy(errorMessage = "Failed to play audio") }
                    true
                }
                
                start()
            }
            
            _uiState.update { it.copy(isPlayingVoice = true, playbackProgress = 0) }
            
            // Start progress updates
            startPlaybackProgressUpdates()
            
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to play: ${e.message}") }
            stopVoicePlayback()
        }
    }
    
    fun stopVoicePlayback() {
        // Stop progress updates
        playbackUpdateRunnable?.let { playbackHandler.removeCallbacks(it) }
        playbackUpdateRunnable = null
        
        // Stop and release media player
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            // Ignore - may already be stopped
        }
        
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        
        mediaPlayer = null
        
        _uiState.update { it.copy(isPlayingVoice = false, playbackProgress = 0) }
    }
    
    fun toggleVoicePlayback() {
        if (_uiState.value.isPlayingVoice) {
            stopVoicePlayback()
        } else {
            playVoiceCaption()
        }
    }
    
    private fun startPlaybackProgressUpdates() {
        playbackUpdateRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val progress = player.currentPosition / 1000
                        _uiState.update { it.copy(playbackProgress = progress) }
                        playbackHandler.postDelayed(this, 500)
                    }
                }
            }
        }
        playbackHandler.post(playbackUpdateRunnable!!)
    }
    
    fun toggleVoiceRecording() {
        if (_uiState.value.isRecordingVoice) {
            stopVoiceRecording()
        } else {
            startVoiceRecording()
        }
    }
    
    fun onAudioPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(needsAudioPermission = false) }
        if (granted) {
            startVoiceRecording()
        }
    }
    
    // MARK: - Build Post Input
    
    fun buildPostInput(): CreatePostInput {
        val state = _uiState.value
        return CreatePostInput(
            type = state.postType,
            text = state.caption,
            mediaItems = state.mediaItems,
            voiceCaptionUri = state.voiceCaptionUri,
            voiceCaptionDurationSeconds = state.voiceCaptionDuration,
            crops = state.selectedCrops,
            hashtags = state.hashtags,
            location = state.location,
            visibility = state.visibility,
            targetExpertise = if (state.postType == PostType.QUESTION) state.targetExpertise else emptyList()
        )
    }
    
    // MARK: - Clear Error
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    // MARK: - Cleanup
    
    override fun onCleared() {
        super.onCleared()
        // Stop playback
        stopVoicePlayback()
        
        // Cancel any ongoing recording
        if (voiceRecordingRepository.isRecording()) {
            viewModelScope.launch {
                voiceRecordingRepository.cancelRecording()
            }
        }
    }
    
    // MARK: - Private Helpers
    
    private fun CreatePostUiState.updateValidation(): CreatePostUiState {
        val isEnabled = when (postType) {
            PostType.QUESTION -> caption.isNotBlank()
            PostType.NORMAL -> mediaItems.isNotEmpty() && caption.isNotBlank()
        }
        return copy(isPostEnabled = isEnabled)
    }
}
