package com.kissangram.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.CreatePostInput
import com.kissangram.model.CreatePostLocation
import com.kissangram.model.MediaItem
import com.kissangram.util.MediaCompressor
import com.kissangram.util.VideoThumbnailGenerator
import com.kissangram.model.MediaType
import com.kissangram.model.PostType
import com.kissangram.model.PostVisibility
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidCropsRepository
import com.kissangram.repository.AndroidLocationRepository
import com.kissangram.repository.AndroidStorageRepository
import com.kissangram.repository.AndroidVoiceRecordingRepository
import com.kissangram.repository.FirestorePostRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.usecase.CreatePostUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * UI State for Create Post screen
 */
data class CreatePostUiState(
    val postType: PostType = PostType.NORMAL,
    val caption: String = "",
    val mediaItems: List<MediaItem> = emptyList(), // For display - will be converted to ByteArray in buildPostInput
    val mediaItemUris: List<Pair<String, MediaType>> = emptyList(), // Store URIs separately
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
    val needsLocationPermission: Boolean = false,
    
    // Location state
    val isLoadingLocation: Boolean = false,
    val locationError: String? = null,
    
    // Location selection state
    val showLocationSheet: Boolean = false,
    val selectedState: String? = null,
    val selectedDistrict: String? = null,
    val villageName: String = "",
    val states: List<String> = emptyList(),
    val districts: List<String> = emptyList(),
    val isLoadingStates: Boolean = false,
    val isLoadingDistricts: Boolean = false,
    
    // Validation
    val isPostEnabled: Boolean = false,
    
    // Post creation state
    val isCreatingPost: Boolean = false,
    val postCreationError: String? = null,
    val postCreatedSuccessfully: Boolean = false, // Flag to trigger navigation
    
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
    private val locationRepository = AndroidLocationRepository(application)
    
    // Repositories for post creation
    private val storageRepository = AndroidStorageRepository(application)
    private val authRepository = AndroidAuthRepository(
        context = application,
        activity = null,
        preferencesRepository = com.kissangram.repository.AndroidPreferencesRepository(application)
    )
    private val userRepository = FirestoreUserRepository(authRepository = authRepository)
    private val postRepository = FirestorePostRepository(
        authRepository = authRepository,
        userRepository = userRepository
    )
    
    // Use case for creating posts
    private val createPostUseCase = CreatePostUseCase(
        storageRepository = storageRepository,
        postRepository = postRepository,
        authRepository = authRepository,
        userRepository = userRepository
    )
    
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
    
    // MARK: - Location
    
    fun showLocationSheet() {
        _uiState.update { it.copy(showLocationSheet = true) }
        loadStates()
    }
    
    fun hideLocationSheet() {
        _uiState.update { 
            it.copy(
                showLocationSheet = false,
                locationError = null
            ) 
        }
    }
    
    private fun loadStates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStates = true) }
            try {
                val states = locationRepository.getStates()
                _uiState.update { 
                    it.copy(
                        states = states,
                        isLoadingStates = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingStates = false,
                        locationError = "Failed to load states: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun selectState(state: String) {
        _uiState.update { 
            it.copy(
                selectedState = state,
                selectedDistrict = null,
                districts = emptyList()
            )
        }
        loadDistricts(state)
    }
    
    private fun loadDistricts(state: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDistricts = true) }
            try {
                val districts = locationRepository.getDistricts(state)
                _uiState.update { 
                    it.copy(
                        districts = districts,
                        isLoadingDistricts = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingDistricts = false,
                        locationError = "Failed to load districts: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun selectDistrict(district: String) {
        _uiState.update { it.copy(selectedDistrict = district) }
    }
    
    fun setVillageName(village: String) {
        _uiState.update { it.copy(villageName = village) }
    }
    
    fun useCurrentLocation() {
        viewModelScope.launch {
            if (!locationRepository.hasLocationPermission()) {
                _uiState.update { it.copy(needsLocationPermission = true) }
                return@launch
            }
            
            _uiState.update { 
                it.copy(
                    isLoadingLocation = true,
                    locationError = null
                )
            }
            
            try {
                // Get GPS coordinates
                // Permission is checked above, suppress lint warning
                @SuppressLint("MissingPermission")
                val coordinates = locationRepository.getCurrentLocation()
                if (coordinates == null) {
                    _uiState.update { 
                        it.copy(
                            isLoadingLocation = false,
                            locationError = "Unable to get current location. Please try again."
                        )
                    }
                    return@launch
                }
                
                // Reverse geocode to get location name
                val locationName = locationRepository.reverseGeocode(
                    coordinates.latitude,
                    coordinates.longitude
                )
                
                if (locationName != null) {
                    // Log coordinates for debugging
                    Log.d("CreatePostViewModel", "Current Location Selected:")
                    Log.d("CreatePostViewModel", "  Name: $locationName")
                    Log.d("CreatePostViewModel", "  Latitude: ${coordinates.latitude}")
                    Log.d("CreatePostViewModel", "  Longitude: ${coordinates.longitude}")
                    
                    _uiState.update { 
                        it.copy(
                            location = CreatePostLocation(
                                name = locationName,
                                latitude = coordinates.latitude,
                                longitude = coordinates.longitude
                            ),
                            isLoadingLocation = false,
                            showLocationSheet = false,
                            locationError = null
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoadingLocation = false,
                            locationError = "Unable to get location name. Please try manual selection."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingLocation = false,
                        locationError = "Failed to get location: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun saveManualLocation() {
        viewModelScope.launch {
            val state = _uiState.value.selectedState
            val district = _uiState.value.selectedDistrict
            val village = _uiState.value.villageName.trim()
            
            if (district == null || state == null) {
                _uiState.update { 
                    it.copy(
                        locationError = "Please select state and district"
                    )
                }
                return@launch
            }
            
            // Build location name
            val locationName = when {
                village.isNotEmpty() -> "$village, $district, $state"
                else -> "$district, $state"
            }
            
            _uiState.update { 
                it.copy(
                    isLoadingLocation = true,
                    locationError = null
                )
            }
            
            try {
                // Forward geocode to get coordinates
                val coordinates = locationRepository.forwardGeocode(locationName)
                
                // Log coordinates for debugging
                if (coordinates != null) {
                    Log.d("CreatePostViewModel", "Manual Location Selected:")
                    Log.d("CreatePostViewModel", "  Name: $locationName")
                    Log.d("CreatePostViewModel", "  Latitude: ${coordinates.latitude}")
                    Log.d("CreatePostViewModel", "  Longitude: ${coordinates.longitude}")
                } else {
                    Log.w("CreatePostViewModel", "Manual Location Selected but geocoding failed:")
                    Log.w("CreatePostViewModel", "  Name: $locationName")
                    Log.w("CreatePostViewModel", "  Coordinates: null")
                }
                
                _uiState.update { 
                    it.copy(
                        location = CreatePostLocation(
                            name = locationName,
                            latitude = coordinates?.latitude,
                            longitude = coordinates?.longitude
                        ),
                        isLoadingLocation = false,
                        showLocationSheet = false,
                        locationError = null
                    )
                }
            } catch (e: Exception) {
                // Even if geocoding fails, save the location name
                _uiState.update { 
                    it.copy(
                        location = CreatePostLocation(
                            name = locationName,
                            latitude = null,
                            longitude = null
                        ),
                        isLoadingLocation = false,
                        showLocationSheet = false,
                        locationError = null
                    )
                }
            }
        }
    }
    
    fun removeLocation() {
        _uiState.update { 
            it.copy(
                location = null,
                selectedState = null,
                selectedDistrict = null,
                villageName = ""
            )
        }
    }
    
    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(needsLocationPermission = false) }
        if (granted) {
            useCurrentLocation()
        }
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
        val uriString = uri.toString()
        
        // Validate URI string
        if (uriString.isBlank()) {
            Log.e(TAG, "addMediaItem: Cannot add media item with blank URI")
            return
        }
        
        // Validate URI scheme
        val scheme = uri.scheme
        if (scheme == null || (scheme != "file" && scheme != "content")) {
            Log.e(TAG, "addMediaItem: Invalid URI scheme: $scheme for URI: $uriString")
            return
        }
        
        // Create a placeholder MediaItem for UI display (with empty ByteArray)
        val placeholderItem = MediaItem(
            mediaData = ByteArray(0), // Placeholder for UI
            type = type,
            thumbnailData = null
        )
        _uiState.update { state ->
            state.copy(
                mediaItems = state.mediaItems + placeholderItem,
                mediaItemUris = state.mediaItemUris + (uriString to type)
            ).updateValidation()
        }
        Log.d(TAG, "addMediaItem: Added media item with URI: $uriString, type: $type")
    }
    
    fun removeMediaItem(item: MediaItem) {
        // Find the index of the item by comparing type (since ByteArray comparison might not work for placeholders)
        _uiState.update { state ->
            val index = state.mediaItems.indexOfFirst { it.type == item.type }
            if (index >= 0 && index < state.mediaItemUris.size) {
                state.copy(
                    mediaItems = state.mediaItems.filterIndexed { i, _ -> i != index },
                    mediaItemUris = state.mediaItemUris.filterIndexed { i, _ -> i != index }
                ).updateValidation()
            } else {
                state
            }
        }
    }
    
    fun removeMediaItemByIndex(index: Int) {
        _uiState.update { state ->
            if (index >= 0 && index < state.mediaItems.size && index < state.mediaItemUris.size) {
                state.copy(
                    mediaItems = state.mediaItems.filterIndexed { i, _ -> i != index },
                    mediaItemUris = state.mediaItemUris.filterIndexed { i, _ -> i != index }
                ).updateValidation()
            } else {
                state
            }
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
                        voiceCaptionUri = filePath?.takeIf { it.isNotBlank() },
                        voiceCaptionDuration = duration,
                        recordingDuration = 0,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isRecordingVoice = false,
                        voiceCaptionUri = null,
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
    
    private suspend fun uriToByteArray(uriString: String): ByteArray {
        // Validate URI string is not blank
        if (uriString.isBlank()) {
            throw IllegalArgumentException("URI string cannot be blank")
        }
        
        // Handle plain file paths (not URIs) - common for voice captions
        val file = File(uriString)
        if (file.exists() && file.isFile) {
            // It's a plain file path, read it directly
            if (!file.canRead()) {
                throw IllegalArgumentException("Cannot read file: $uriString")
            }
            try {
                return FileInputStream(file).use { it.readBytes() }
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to read file: $uriString", e)
            }
        }
        
        // Otherwise, treat it as a URI
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URI format: $uriString", e)
        }
        
        // Check if scheme is null
        val scheme = uri.scheme
        if (scheme == null) {
            throw IllegalArgumentException("URI has no scheme and is not a valid file path. URI: $uriString")
        }
        
        return when (scheme) {
            "file" -> {
                val path = uri.path
                if (path.isNullOrBlank()) {
                    throw IllegalArgumentException("File URI has no path: $uriString")
                }
                val fileFromUri = File(path)
                if (!fileFromUri.exists()) {
                    throw IllegalArgumentException("File does not exist: $uriString (path: $path)")
                }
                if (!fileFromUri.canRead()) {
                    throw IllegalArgumentException("Cannot read file: $uriString (path: $path)")
                }
                try {
                    FileInputStream(fileFromUri).use { it.readBytes() }
                } catch (e: Exception) {
                    throw IllegalArgumentException("Failed to read file: $uriString", e)
                }
            }
            "content" -> {
                try {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Cannot open input stream for content URI: $uriString")
                    try {
                        inputStream.use { it.readBytes() }
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Failed to read content URI: $uriString", e)
                    }
                } catch (e: SecurityException) {
                    throw IllegalArgumentException("Permission denied for content URI: $uriString", e)
                }
            }
            else -> throw IllegalArgumentException("Unsupported URI scheme: $scheme. URI: $uriString")
        }
    }
    
    suspend fun buildPostInput(): CreatePostInput {
        val state = _uiState.value
        
        // Convert URIs to ByteArray for media items
        // Filter out any invalid URIs and log warnings
        val mediaItemsWithData = mutableListOf<MediaItem>()
        
        state.mediaItemUris.forEachIndexed { index, (uriString, type) ->
            try {
                if (uriString.isBlank()) {
                    Log.w(TAG, "buildPostInput: Skipping media item $index - blank URI")
                    return@forEachIndexed
                }
                
                val originalMediaData = uriToByteArray(uriString)
                if (originalMediaData.isEmpty()) {
                    Log.w(TAG, "buildPostInput: Skipping media item $index - empty ByteArray")
                    return@forEachIndexed
                }
                
                // Compress media before upload
                val compressedMediaData = when (type) {
                    MediaType.IMAGE -> {
                        Log.d(TAG, "buildPostInput: Compressing image (${originalMediaData.size} bytes)")
                        MediaCompressor.compressImage(originalMediaData)
                    }
                    MediaType.VIDEO -> {
                        Log.d(TAG, "buildPostInput: Compressing video (${originalMediaData.size} bytes)")
                        MediaCompressor.compressVideo(originalMediaData, getApplication())
                    }
                }
                
                Log.d(TAG, "buildPostInput: Media compressed: ${originalMediaData.size} -> ${compressedMediaData.size} bytes")
                
                // Generate thumbnail for videos
                val thumbnailData = if (type == MediaType.VIDEO) {
                    try {
                        Log.d(TAG, "buildPostInput: Generating thumbnail for video")
                        // Save video to temp file for thumbnail generation
                        val app = getApplication<Application>()
                        val tempFile = java.io.File(app.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
                        tempFile.outputStream().use { it.write(compressedMediaData) }
                        val thumbnail = VideoThumbnailGenerator.generateThumbnailFromPath(tempFile.absolutePath)
                        tempFile.delete()
                        thumbnail
                    } catch (e: Exception) {
                        Log.w(TAG, "buildPostInput: Failed to generate thumbnail, continuing without thumbnail", e)
                        null
                    }
                } else {
                    null
                }
                
                mediaItemsWithData.add(
                    MediaItem(
                        mediaData = compressedMediaData,
                        type = type,
                        thumbnailData = thumbnailData
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "buildPostInput: Failed to process media item $index: $uriString", e)
                // Continue to next item
            }
        }
        
        // Validate we have at least one valid media item if this is a normal post
        if (state.postType == PostType.NORMAL && mediaItemsWithData.isEmpty()) {
            throw IllegalArgumentException("No valid media items found. Please add media again.")
        }
        
        // Convert voice caption URI to ByteArray if present and compress
        val voiceCaptionData = state.voiceCaptionUri?.takeIf { it.isNotBlank() }?.let { uriString ->
            try {
                val originalAudioData = uriToByteArray(uriString)
                if (originalAudioData.isEmpty()) {
                    Log.w(TAG, "buildPostInput: Voice caption URI resulted in empty ByteArray: $uriString")
                    null
                } else {
                    // Compress audio before upload
                    Log.d(TAG, "buildPostInput: Compressing voice caption (${originalAudioData.size} bytes)")
                    val compressedAudioData = MediaCompressor.compressAudio(originalAudioData)
                    Log.d(TAG, "buildPostInput: Voice caption compressed: ${originalAudioData.size} -> ${compressedAudioData.size} bytes")
                    compressedAudioData
                }
            } catch (e: Exception) {
                Log.e(TAG, "buildPostInput: Failed to convert/compress voice caption URI: $uriString", e)
                null
            }
        }
        
        return CreatePostInput(
            type = state.postType,
            text = state.caption,
            mediaItems = mediaItemsWithData,
            voiceCaptionData = voiceCaptionData,
            voiceCaptionDurationSeconds = state.voiceCaptionDuration,
            crops = state.selectedCrops,
            hashtags = state.hashtags,
            location = state.location,
            visibility = state.visibility,
            targetExpertise = if (state.postType == PostType.QUESTION) state.targetExpertise else emptyList()
        )
    }
    
    // MARK: - Create Post
    
    fun createPost(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingPost = true, postCreationError = null) }
            
            try {
                // Validate state before building input
                val state = _uiState.value
                Log.d(TAG, "createPost: State - postType=${state.postType}, mediaItemUris=${state.mediaItemUris.size}, caption length=${state.caption.length}")
                
                val input = buildPostInput()
                Log.d(TAG, "createPost: Starting post creation with ${input.mediaItems.size} media items, type=${input.type}")
                
                if (input.mediaItems.isEmpty() && input.type == PostType.NORMAL) {
                    throw IllegalArgumentException("Cannot create a normal post without media items")
                }
                
                val post = createPostUseCase(input)
                
                Log.d(TAG, "createPost: SUCCESS - Post created with ID: ${post.id}")
                
                // Update state to indicate success - UI will observe this and navigate
                _uiState.update { 
                    it.copy(
                        isCreatingPost = false,
                        postCreationError = null,
                        postCreatedSuccessfully = true
                    )
                }
                
                // Note: Navigation is handled by LaunchedEffect in UI observing postCreatedSuccessfully
                // Call onSuccess for any additional cleanup if needed
                onSuccess()
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "createPost: Validation error - ${e.message}", e)
                val errorMessage = e.message ?: "Invalid post data. Please check your inputs."
                
                _uiState.update { 
                    it.copy(
                        isCreatingPost = false,
                        postCreationError = errorMessage
                    )
                }
                
                onError(errorMessage)
            } catch (e: Exception) {
                Log.e(TAG, "createPost: FAILED - ${e.javaClass.simpleName}: ${e.message}", e)
                val errorMessage = e.message ?: "Failed to create post. Please try again."
                
                _uiState.update { 
                    it.copy(
                        isCreatingPost = false,
                        postCreationError = errorMessage
                    )
                }
                
                onError(errorMessage)
            }
        }
    }
    
    // MARK: - Clear Error
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    // MARK: - Reset Post Creation State
    
    fun resetPostCreationState() {
        _uiState.update { 
            it.copy(
                postCreatedSuccessfully = false,
                postCreationError = null
            )
        }
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
    
    companion object {
        private const val TAG = "CreatePostViewModel"
    }
}
