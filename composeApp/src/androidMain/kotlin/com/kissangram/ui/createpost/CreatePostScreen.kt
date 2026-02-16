package com.kissangram.ui.createpost

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kissangram.model.CreatePostInput
import com.kissangram.model.CreatePostLocation
import com.kissangram.model.MediaItem
import com.kissangram.model.MediaType
import com.kissangram.model.PostType
import com.kissangram.model.PostVisibility
import com.kissangram.ui.home.BackgroundColor
import com.kissangram.ui.home.PrimaryGreen
import com.kissangram.ui.home.TextPrimary
import com.kissangram.ui.home.TextSecondary
import com.kissangram.viewmodel.CreatePostViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kissangram.viewmodel.CreatePostUiState
import com.kissangram.ui.components.FilterableDropdownField

// MARK: - Helper Functions
private fun createImageUri(context: Context): Uri {
    val photoFile = File(
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
        "post_image_${System.currentTimeMillis()}.jpg"
    )
    photoFile.parentFile?.mkdirs()
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

private fun createVideoUri(context: Context): Uri {
    val videoFile = File(
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
        "post_video_${System.currentTimeMillis()}.mp4"
    )
    videoFile.parentFile?.mkdirs()
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        videoFile
    )
}

// buildPostInput is now handled by ViewModel.buildPostInput() which converts URIs to ByteArray


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    onBackClick: () -> Unit,
    onPostClick: (CreatePostInput) -> Unit = {},
    viewModel: CreatePostViewModel = viewModel(),
    bottomNavPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    // State (keeping local state for non-voice related items for now)
    // postType, caption, and mediaItems are now managed by ViewModel (uiState)
    // selectedCrops is now managed by ViewModel (uiState.selectedCrops)
    // location is now managed by ViewModel (uiState.location)
    var visibility by remember { mutableStateOf(PostVisibility.PUBLIC) }
    var hashtags by remember { mutableStateOf<List<String>>(emptyList()) }
    var hashtagInput by remember { mutableStateOf("") }
    
    // Question-specific state
    var targetExpertise by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Media capture URIs
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    
    val focusManager = LocalFocusManager.current
    
    // Post is enabled when there's media OR text (for questions, text alone is enough)
    val isPostEnabled = uiState.isPostEnabled
    
    // Audio recording permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onAudioPermissionResult(isGranted)
    }
    
    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onLocationPermissionResult(isGranted)
    }
    
    // Handle audio permission request from ViewModel
    LaunchedEffect(uiState.needsAudioPermission) {
        if (uiState.needsAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    // Handle location permission request from ViewModel
    LaunchedEffect(uiState.needsLocationPermission) {
        if (uiState.needsLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    
    // Show error snackbar if there's an error
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            // Could show a Snackbar here
            viewModel.clearError()
        }
    }
    
    // Handle successful post creation - navigate back to home
    LaunchedEffect(uiState.postCreatedSuccessfully) {
        if (uiState.postCreatedSuccessfully) {
            // Reset the flag first
            viewModel.resetPostCreationState()
            // Navigate back to home screen
            onBackClick()
        }
    }
    
    // Camera launcher for photos (must be defined before permission launchers)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                viewModel.addMediaItem(uri, MediaType.IMAGE)
            }
        }
    }
    
    // Video launcher (must be defined before permission launchers)
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            cameraVideoUri?.let { uri ->
                viewModel.addMediaItem(uri, MediaType.VIDEO)
            }
        }
    }
    
    // Gallery launchers (must be defined before permission launchers)
    val galleryLauncherTiramisu = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            // Determine if it's an image or video
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(it)
            val mediaType = when {
                mimeType?.startsWith("image/") == true -> MediaType.IMAGE
                mimeType?.startsWith("video/") == true -> MediaType.VIDEO
                else -> MediaType.IMAGE // Default to image
            }
            
            viewModel.addMediaItem(it, mediaType)
        }
    }
    
    val galleryLauncherLegacy = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(it)
            val mediaType = when {
                mimeType?.startsWith("image/") == true -> MediaType.IMAGE
                mimeType?.startsWith("video/") == true -> MediaType.VIDEO
                else -> MediaType.IMAGE
            }
            
            viewModel.addMediaItem(it, mediaType)
        }
    }
    
    // Permission launchers (defined after the launchers they reference)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createImageUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }
    
    val readMediaImagesPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncherTiramisu.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
    }
    
    val readMediaVideoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncherTiramisu.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
    }
    
    val readStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncherLegacy.launch("image/*")
        }
    }
    
    val videoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createVideoUri(context)
            cameraVideoUri = uri
            videoLauncher.launch(uri)
        }
    }
    
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            TopAppBar(
                title = {
                    Text(
                        text = "Create Post",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundColor
                ),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = Color.Black.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(0.dp)
                )
            )
            
            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 18.dp)
                    .padding(bottom = 120.dp + bottomNavPadding.calculateBottomPadding()) // Space for bottom button + bottom nav
            ) {
            // Post Type Toggle
            PostTypeToggle(
                selectedType = uiState.postType,
                onTypeSelected = { viewModel.setPostType(it) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Media Selection Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(13.5.dp)
            ) {
                MediaSelectionButton(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED -> {
                                val uri = createImageUri(context)
                                cameraImageUri = uri
                                cameraLauncher.launch(uri)
                            }

                            else -> {
                                cameraImageUri = createImageUri(context)
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    }
                )
                MediaSelectionButton(
                    icon = Icons.Default.Image,
                    label = "Gallery",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            when {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_MEDIA_IMAGES
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    galleryLauncherTiramisu.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }

                                else -> {
                                    readMediaImagesPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                }
                            }
                        } else {
                            when {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    galleryLauncherLegacy.launch("image/*")
                                }

                                else -> {
                                    readStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            }
                        }
                    }
                )
                MediaSelectionButton(
                    icon = Icons.Default.Videocam,
                    label = "Video",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED -> {
                                val uri = createVideoUri(context)
                                cameraVideoUri = uri
                                videoLauncher.launch(uri)
                            }

                            else -> {
                                cameraVideoUri = createVideoUri(context)
                                videoPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    }
                )
            }

            // Media Preview (if any media selected)
            if (uiState.mediaItemUris.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                MediaPreviewSection(
                    mediaItemUris = uiState.mediaItemUris,
                    onRemoveMedia = { index ->
                        viewModel.removeMediaItemByIndex(index)
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Caption Text Area
            OutlinedTextField(
                value = uiState.caption,
                onValueChange = { viewModel.setCaption(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder = {
                    Text(
                        text = if (uiState.postType == PostType.QUESTION) "Ask your question..." else "Enter caption",
                        color = TextPrimary.copy(alpha = 0.5f),
                        fontSize = 16.875.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = BackgroundColor,
                    focusedContainerColor = BackgroundColor,
                    unfocusedBorderColor = PrimaryGreen.copy(alpha = 0.13f),
                    focusedBorderColor = PrimaryGreen.copy(alpha = 0.13f)
                ),
                shape = RoundedCornerShape(18.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.875.sp,
                    lineHeight = 25.313.sp
                ),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Voice Caption Button
            VoiceCaptionSection(
                voiceCaptionUri = uiState.voiceCaptionUri,
                voiceCaptionDuration = if (uiState.isRecordingVoice) uiState.recordingDuration else uiState.voiceCaptionDuration,
                isRecording = uiState.isRecordingVoice,
                isPlaying = uiState.isPlayingVoice,
                playbackProgress = uiState.playbackProgress,
                onRecordClick = {
                    viewModel.toggleVoiceRecording()
                },
                onPlayClick = {
                    viewModel.toggleVoicePlayback()
                },
                onRemoveVoiceCaption = {
                    viewModel.removeVoiceCaption()
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Hashtags Section
            HashtagsSection(
                hashtags = hashtags,
                hashtagInput = hashtagInput,
                onHashtagInputChange = { hashtagInput = it },
                onAddHashtag = { tag ->
                    if (tag.isNotBlank() && !hashtags.contains(tag)) {
                        hashtags = hashtags + tag.replace("#", "").trim()
                        hashtagInput = ""
                    }
                    focusManager.clearFocus()
                },
                onRemoveHashtag = { tag ->
                    hashtags = hashtags - tag
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Crop Selection
            CropSelectionSection(
                allCrops = uiState.allCrops,
                selectedCrops = uiState.selectedCrops.toSet(),
                visibleCrops = uiState.visibleCrops,
                searchQuery = uiState.cropSearchQuery,
                isLoading = uiState.isLoadingCrops,
                hasMoreCrops = uiState.hasMoreCrops,
                remainingCropsCount = uiState.remainingCropsCount,
                showAllCrops = uiState.showAllCrops,
                onSearchQueryChange = { viewModel.setCropSearchQuery(it) },
                onCropSelected = { viewModel.toggleCrop(it) },
                onToggleShowAll = { viewModel.toggleShowAllCrops() }
            )

            // Question-specific: Target Expertise (only for questions)
            if (uiState.postType == PostType.QUESTION) {
                Spacer(modifier = Modifier.height(18.dp))
                TargetExpertiseSection(
                    selectedExpertise = targetExpertise,
                    onExpertiseSelected = { expertise ->
                        targetExpertise = if (targetExpertise.contains(expertise)) {
                            targetExpertise - expertise
                        } else {
                            targetExpertise + expertise
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Visibility Selection
            VisibilitySelectionSection(
                selectedVisibility = visibility,
                onVisibilitySelected = { visibility = it }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Location Button
            LocationButton(
                location = uiState.location?.name,
                onLocationClick = { 
                    viewModel.showLocationSheet()
                },
                onRemoveLocation = {
                    viewModel.removeLocation()
                }
            )

            Spacer(modifier = Modifier.height(68
                .dp))

            }
        }
        
        // Bottom Post Button - Fixed at bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = BackgroundColor,
            shadowElevation = 8.dp
        ) {
            val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 12.dp)
                    .padding(bottom = navigationBarPadding.calculateBottomPadding()+ 72.dp ) // System nav + custom bottom nav bar
            ) {
                Button(
                    onClick = {
                        viewModel.createPost(
                            onSuccess = {
                                // Navigation is handled by LaunchedEffect observing postCreatedSuccessfully
                                // This callback is kept for any additional cleanup if needed
                            },
                            onError = { error ->
                                // Error is already shown in UI state, but we can show a snackbar here if needed
                            }
                        )
                    },
                    enabled = isPostEnabled && !uiState.isCreatingPost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(65.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPostEnabled) PrimaryGreen else Color(0xFFE5E5E5),
                        contentColor = if (isPostEnabled) Color.White else Color(0xFF9B9B9B),
                        disabledContainerColor = Color(0xFFE5E5E5),
                        disabledContentColor = Color(0xFF9B9B9B)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (uiState.isCreatingPost) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Posting...",
                            fontSize = 19.125.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = if (uiState.postType == PostType.QUESTION) "Ask Question" else "Post",
                            fontSize = 19.125.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        
        // Location Selection Bottom Sheet
        if (uiState.showLocationSheet) {
            LocationSelectionBottomSheet(
                uiState = uiState,
                onDismiss = { viewModel.hideLocationSheet() },
                onUseCurrentLocation = { viewModel.useCurrentLocation() },
                onStateSelected = { viewModel.selectState(it) },
                onDistrictSelected = { viewModel.selectDistrict(it) },
                onVillageChange = { viewModel.setVillageName(it) },
                onSave = { viewModel.saveManualLocation() }
            )
        }
    }
}

// MARK: - Post Type Toggle
@Composable
private fun PostTypeToggle(
    selectedType: PostType,
    onTypeSelected: (PostType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "Post Type",
            fontSize = 16.875.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(13.5.dp)
        ) {
            PostTypeButton(
                label = "Normal Post",
                isSelected = selectedType == PostType.NORMAL,
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelected(PostType.NORMAL) }
            )
            PostTypeButton(
                label = "Ask Question",
                isSelected = selectedType == PostType.QUESTION,
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelected(PostType.QUESTION) }
            )
        }
    }
}

@Composable
private fun PostTypeButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(50.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) PrimaryGreen else BackgroundColor,
        border = if (isSelected) null else BorderStroke(
            width = 1.18.dp,
            color = PrimaryGreen.copy(alpha = 0.13f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 14.625.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else TextPrimary
            )
        }
    }
}

// MARK: - Media Selection Button
@Composable
private fun MediaSelectionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(101.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = BackgroundColor,
        border = BorderStroke(
            width = 1.18.dp,
            color = PrimaryGreen.copy(alpha = 0.13f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(31.5.dp),
                tint = PrimaryGreen
            )
            Text(
                text = label,
                fontSize = 14.625.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryGreen
            )
        }
    }
}

// MARK: - Media Preview Section
@Composable
private fun MediaPreviewSection(
    mediaItemUris: List<Pair<String, MediaType>>,
    onRemoveMedia: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "Selected Media (${mediaItemUris.size})",
            fontSize = 14.625.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            itemsIndexed(mediaItemUris) { index, (uriString, type) ->
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray.copy(alpha = 0.2f))
                ) {
                    AsyncImage(
                        model = uriString,
                        contentDescription = "Media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Video indicator
                    if (type == MediaType.VIDEO) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(32.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Remove button
                    IconButton(
                        onClick = { onRemoveMedia(index) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Voice Caption Section
@Composable
private fun VoiceCaptionSection(
    voiceCaptionUri: String?,
    voiceCaptionDuration: Int,
    isRecording: Boolean,
    isPlaying: Boolean = false,
    playbackProgress: Int = 0,
    onRecordClick: () -> Unit,
    onPlayClick: () -> Unit = {},
    onRemoveVoiceCaption: () -> Unit
) {
    if (voiceCaptionUri != null) {
        // Show recorded voice caption with play button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp),
            shape = RoundedCornerShape(18.dp),
            color = PrimaryGreen.copy(alpha = 0.1f),
            border = BorderStroke(
                width = 1.18.dp,
                color = PrimaryGreen.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Stop button
                    Box(
                        modifier = Modifier
                            .size(40.5.dp)
                            .background(
                                color = if (isPlaying) Color(0xFFFF6B6B) else PrimaryGreen,
                                shape = CircleShape
                            )
                            .clickable(onClick = onPlayClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play",
                            modifier = Modifier.size(22.dp),
                            tint = Color.White
                        )
                    }
                    
                    Column {
                        Text(
                            text = if (isPlaying) "Playing..." else "Voice Caption",
                            fontSize = 14.625.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isPlaying) "${playbackProgress}s / ${voiceCaptionDuration}s" else "${voiceCaptionDuration}s",
                            fontSize = 12.sp,
                            color = if (isPlaying) PrimaryGreen else TextSecondary
                        )
                    }
                }
                
                // Remove button
                IconButton(onClick = onRemoveVoiceCaption) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = TextSecondary
                    )
                }
            }
        }
    } else {
        // Show record button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clickable(onClick = onRecordClick),
            shape = RoundedCornerShape(18.dp),
            color = if (isRecording) PrimaryGreen.copy(alpha = 0.15f) else BackgroundColor,
            border = BorderStroke(
                width = 1.18.dp,
                color = if (isRecording) PrimaryGreen else PrimaryGreen.copy(alpha = 0.08f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 19.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(13.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.5.dp)
                            .background(
                                color = if (isRecording) PrimaryGreen else PrimaryGreen.copy(alpha = 0.08f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Voice",
                            modifier = Modifier.size(20.25.dp),
                            tint = if (isRecording) Color.White else PrimaryGreen
                        )
                    }
                    Text(
                        text = if (isRecording) "Recording..." else "Add voice caption",
                        fontSize = 16.875.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
                Text(
                    text = if (isRecording) "Tap to stop" else "Tap to record",
                    fontSize = 14.625.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRecording) PrimaryGreen else TextSecondary
                )
            }
        }
    }
}

// MARK: - Hashtags Section
@Composable
private fun HashtagsSection(
    hashtags: List<String>,
    hashtagInput: String,
    onHashtagInputChange: (String) -> Unit,
    onAddHashtag: (String) -> Unit,
    onRemoveHashtag: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "Hashtags",
            fontSize = 16.875.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        // Hashtag input
        OutlinedTextField(
            value = hashtagInput,
            onValueChange = onHashtagInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Type and press enter to add #hashtag",
                    color = TextPrimary.copy(alpha = 0.5f),
                    fontSize = 14.625.sp
                )
            },
            leadingIcon = {
                Text(
                    text = "#",
                    color = PrimaryGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = BackgroundColor,
                focusedContainerColor = BackgroundColor,
                unfocusedBorderColor = PrimaryGreen.copy(alpha = 0.13f),
                focusedBorderColor = PrimaryGreen.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onAddHashtag(hashtagInput) }
            )
        )
        
        // Display added hashtags
        if (hashtags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                hashtags.forEach { tag ->
                    HashtagChip(
                        hashtag = tag,
                        onRemove = { onRemoveHashtag(tag) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HashtagChip(
    hashtag: String,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PrimaryGreen.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$hashtag",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryGreen
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onRemove),
                tint = PrimaryGreen
            )
        }
    }
}

// MARK: - Crop Selection Section
@Composable
private fun CropSelectionSection(
    allCrops: List<String>,
    selectedCrops: Set<String>,
    visibleCrops: List<String>,
    searchQuery: String,
    isLoading: Boolean,
    hasMoreCrops: Boolean,
    remainingCropsCount: Int,
    showAllCrops: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onCropSelected: (String) -> Unit,
    onToggleShowAll: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "What crop is this?",
            fontSize = 16.875.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Search crops...",
                    color = TextPrimary.copy(alpha = 0.5f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextSecondary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            enabled = !isLoading,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BackgroundColor,
                unfocusedContainerColor = BackgroundColor,
                focusedBorderColor = PrimaryGreen,
                unfocusedBorderColor = Color.Black.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(22.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                color = TextPrimary
            )
        )
        
        // Selected Crops Section
        if (selectedCrops.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Selected Crops",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedCrops.forEach { crop ->
                        CropChipWithRemove(
                            crop = crop,
                            onClick = { onCropSelected(crop) }
                        )
                    }
                }
            }
        }
        
        // Available Crops Section
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = PrimaryGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else if (searchQuery.isNotEmpty() && visibleCrops.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No crops found",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
        } else if (visibleCrops.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "Search Results",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleCrops.forEach { crop ->
                        CropChip(
                            crop = crop,
                            isSelected = false,
                            onClick = { onCropSelected(crop) }
                        )
                    }
                }
                
                // Show More / Show Less Button
                if (hasMoreCrops || showAllCrops) {
                    TextButton(
                        onClick = onToggleShowAll,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = if (showAllCrops) "Show Less" else "+$remainingCropsCount more crops",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryGreen
                        )
                        Icon(
                            imageVector = if (showAllCrops) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showAllCrops) "Show Less" else "Show More",
                            tint = PrimaryGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CropChip(
    crop: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) PrimaryGreen else BackgroundColor,
        border = BorderStroke(
            width = 1.18.dp,
            color = if (isSelected) PrimaryGreen else Color.Black.copy(alpha = 0.1f)
        )
    ) {
        Text(
            text = crop,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun CropChipWithRemove(
    crop: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = PrimaryGreen
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = crop,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// MARK: - Target Expertise Section (for Questions)
@Composable
private fun TargetExpertiseSection(
    selectedExpertise: Set<String>,
    onExpertiseSelected: (String) -> Unit
) {
    val expertiseOptions = listOf(
        "Crop Doctor",
        "Soil Expert",
        "Irrigation Specialist",
        "Pest Control",
        "Organic Farming",
        "Seeds & Varieties",
        "Farm Equipment",
        "Market & Pricing"
    )
    
    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = "Who should answer? (Optional)",
            fontSize = 16.875.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        Text(
            text = "Select expertise areas to get relevant answers",
            fontSize = 13.5.sp,
            color = TextSecondary
        )
        
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            expertiseOptions.forEach { expertise ->
                ExpertiseChip(
                    expertise = expertise,
                    isSelected = selectedExpertise.contains(expertise),
                    onClick = { onExpertiseSelected(expertise) }
                )
            }
        }
    }
}

@Composable
private fun ExpertiseChip(
    expertise: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) Color(0xFFFFA726).copy(alpha = 0.15f) else BackgroundColor,
        border = BorderStroke(
            width = 1.18.dp,
            color = if (isSelected) Color(0xFFFFA726) else PrimaryGreen.copy(alpha = 0.13f)
        )
    ) {
        Text(
            text = expertise,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color(0xFFFFA726) else TextPrimary
        )
    }
}

// MARK: - Visibility Selection Section
@Composable
private fun VisibilitySelectionSection(
    selectedVisibility: PostVisibility,
    onVisibilitySelected: (PostVisibility) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = "Who can see this?",
            fontSize = 16.875.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(13.5.dp)
        ) {
            VisibilityButton(
                label = "Public",
                isSelected = selectedVisibility == PostVisibility.PUBLIC,
                modifier = Modifier.weight(1f),
                onClick = { onVisibilitySelected(PostVisibility.PUBLIC) }
            )
            VisibilityButton(
                label = "My Followers",
                isSelected = selectedVisibility == PostVisibility.FOLLOWERS,
                modifier = Modifier.weight(1f),
                onClick = { onVisibilitySelected(PostVisibility.FOLLOWERS) }
            )
        }
        
        Text(
            text = "Public posts are visible to all farmers",
            fontSize = 13.5.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun VisibilityButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) PrimaryGreen else BackgroundColor,
        border = if (isSelected) null else BorderStroke(
            width = 1.18.dp,
            color = PrimaryGreen.copy(alpha = 0.13f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 15.75.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else TextPrimary
            )
        }
    }
}

// MARK: - Location Button
@Composable
private fun LocationButton(
    location: String?,
    onLocationClick: () -> Unit,
    onRemoveLocation: () -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(13.5.dp)
    ) {
        Text(
            text = "Add location",
            fontSize = 16.875.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (location != null) PrimaryGreen.copy(alpha = 0.1f) else BackgroundColor,
            border = BorderStroke(
                width = 1.18.dp,
                color = if (location != null) PrimaryGreen.copy(alpha = 0.3f) else PrimaryGreen.copy(alpha = 0.08f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onLocationClick),
                    horizontalArrangement = Arrangement.spacedBy(13.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.5.dp)
                            .background(
                                color = if (location != null) PrimaryGreen else PrimaryGreen.copy(alpha = 0.08f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            modifier = Modifier.size(20.25.dp),
                            tint = if (location != null) Color.White else PrimaryGreen
                        )
                    }
                    Text(
                        text = location ?: "Select location",
                        fontSize = 16.875.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (location != null) TextPrimary else TextPrimary.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (location != null) {
                    IconButton(onClick = onRemoveLocation) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Navigate",
                        modifier = Modifier.size(22.5.dp),
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}

// MARK: - Location Selection Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSelectionBottomSheet(
    uiState: CreatePostUiState,
    onDismiss: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onStateSelected: (String) -> Unit,
    onDistrictSelected: (String) -> Unit,
    onVillageChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Current Location, 1 = Manual
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColor,
        dragHandle = {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .padding(vertical = 12.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(18.dp))
            // Title
            Text(
                text = "Add Location",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            
            // Tab Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabButton(
                    label = "Current Location",
                    isSelected = selectedTab == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = 0 }
                )
                TabButton(
                    label = "Manual",
                    isSelected = selectedTab == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = 1 }
                )
            }
            
            // Content based on selected tab
            when (selectedTab) {
                0 -> {
                    // Current Location Tab
                    CurrentLocationTab(
                        isLoading = uiState.isLoadingLocation,
                        error = uiState.locationError,
                        onUseCurrentLocation = onUseCurrentLocation
                    )
                }
                1 -> {
                    // Manual Selection Tab
                    ManualLocationTab(
                        states = uiState.states,
                        selectedState = uiState.selectedState,
                        districts = uiState.districts,
                        selectedDistrict = uiState.selectedDistrict,
                        village = uiState.villageName,
                        isLoadingStates = uiState.isLoadingStates,
                        isLoadingDistricts = uiState.isLoadingDistricts,
                        error = uiState.locationError,
                        onStateSelected = onStateSelected,
                        onDistrictSelected = onDistrictSelected,
                        onVillageChange = onVillageChange,
                        onSave = onSave
                    )
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) PrimaryGreen else BackgroundColor,
        border = if (isSelected) null else BorderStroke(
            width = 1.18.dp,
            color = PrimaryGreen.copy(alpha = 0.13f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 14.625.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else TextPrimary
            )
        }
    }
}

@Composable
private fun CurrentLocationTab(
    isLoading: Boolean,
    error: String?,
    onUseCurrentLocation: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (error != null) {
            Text(
                text = error,
                fontSize = 14.sp,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        Button(
            onClick = onUseCurrentLocation,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryGreen,
                disabledContainerColor = PrimaryGreen.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Detecting location...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Use Current Location",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
        
        Text(
            text = "We'll use your GPS to find your location",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ManualLocationTab(
    states: List<String>,
    selectedState: String?,
    districts: List<String>,
    selectedDistrict: String?,
    village: String,
    isLoadingStates: Boolean,
    isLoadingDistricts: Boolean,
    error: String?,
    onStateSelected: (String) -> Unit,
    onDistrictSelected: (String) -> Unit,
    onVillageChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (error != null) {
            Text(
                text = error,
                fontSize = 14.sp,
                color = Color(0xFFFF6B6B)
            )
        }
        
        // State Dropdown
        FilterableDropdownField(
            label = "State",
            items = states,
            selectedItem = selectedState,
            onItemSelected = onStateSelected,
            isLoading = isLoadingStates,
            enabled = !isLoadingStates,
            placeholder = "Select State",
            modifier = Modifier.fillMaxWidth()
        )
        
        // District Dropdown
        FilterableDropdownField(
            label = "District",
            items = districts,
            selectedItem = selectedDistrict,
            onItemSelected = onDistrictSelected,
            isLoading = isLoadingDistricts,
            enabled = selectedState != null && !isLoadingDistricts,
            placeholder = if (selectedState == null) "Select state first" else "Select District",
            modifier = Modifier.fillMaxWidth()
        )
        
        // Village (Optional)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Village",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Text(
                    text = "(Optional)",
                    fontSize = 12.sp,
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontStyle = FontStyle.Italic
                )
            }
            OutlinedTextField(
                value = village,
                onValueChange = onVillageChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Enter village name",
                        color = TextPrimary.copy(alpha = 0.5f)
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BackgroundColor,
                    unfocusedContainerColor = BackgroundColor,
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = Color.Black.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(22.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            )
        }
        
        // Save Button
        Button(
            onClick = onSave,
            enabled = selectedState != null && selectedDistrict != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryGreen,
                disabledContainerColor = PrimaryGreen.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                text = "Save Location",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

