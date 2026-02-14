package com.kissangram.ui.createstory

import android.annotation.SuppressLint
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.OutputStream
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.kissangram.model.CreateStoryInput
import com.kissangram.model.CreateStoryLocation
import com.kissangram.model.MediaType
import com.kissangram.repository.AndroidLocationRepository
import com.kissangram.ui.components.FilterableDropdownField
import com.kissangram.model.PostVisibility
import com.kissangram.model.StoryTextOverlay
import com.kissangram.ui.home.BackgroundColor
import com.kissangram.ui.home.PrimaryGreen
import com.kissangram.ui.home.TextPrimary
import com.kissangram.ui.home.TextSecondary
import java.io.File

// Helper function to create image URI
private fun createImageUri(context: Context): Uri {
    val photoFile = File(
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
        "story_image_${System.currentTimeMillis()}.jpg"
    )
    photoFile.parentFile?.mkdirs()
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

// Helper function to create video URI
private fun createVideoUri(context: Context): Uri {
    val videoFile = File(
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
        "story_video_${System.currentTimeMillis()}.mp4"
    )
    videoFile.parentFile?.mkdirs()
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        videoFile
    )
}

private fun loadBaseBitmap(context: Context, mediaUri: Uri, mediaType: MediaType?): Bitmap? {
    return when (mediaType) {
        MediaType.VIDEO -> {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, mediaUri)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                retriever.release()
            }
        }
        else -> {
            context.contentResolver.openInputStream(mediaUri)?.use { BitmapFactory.decodeStream(it) }
                ?: BitmapFactory.decodeFile(mediaUri.path)
        }
    }
}

private fun buildComposedStoryBitmap(
    context: Context,
    mediaUri: Uri,
    mediaType: MediaType?,
    textOverlays: List<StoryTextOverlay>,
    location: CreateStoryLocation?,
    locationPositionX: Float,
    locationPositionY: Float
): Bitmap? {
    val base = loadBaseBitmap(context, mediaUri, mediaType) ?: return null
    val w = base.width
    val h = base.height
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawBitmap(base, 0f, 0f, null)
    base.recycle()

    val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    for (overlay in textOverlays) {
        val px = overlay.positionX * w
        val py = overlay.positionY * h
        val fontSizePx = (overlay.fontSize * (w / 1080f)).coerceIn(12f, 72f)
        textPaint.textSize = fontSizePx
        textPaint.color = (overlay.textColor and 0xFFFFFFFFL).toInt()
        canvas.save()
        canvas.translate(px, py)
        canvas.rotate(overlay.rotation)
        canvas.drawText(overlay.text, 0f, fontSizePx, textPaint)
        canvas.restore()
    }

    if (location != null) {
        val px = locationPositionX * w
        val py = locationPositionY * h
        val locPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#2D6A4F")
            alpha = (0.85 * 255).toInt()
        }
        val textPaintLoc = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = (14 * (w / 1080f))
        }
        val name = location.name
        val textWidth = textPaintLoc.measureText(name)
        val pillW = textWidth + 24 * (w / 1080f)
        val pillH = (14 * (w / 1080f)) + 16 * (h / 1920f)
        val left = px
        val top = py
        canvas.drawRoundRect(left, top, left + pillW, top + pillH, pillH / 2, pillH / 2, locPaint)
        canvas.drawText(name, left + 12 * (w / 1080f), top + pillH / 2 + (textPaintLoc.textSize / 3), textPaintLoc)
    }
    return out
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "kissangram_story_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Kissangram")
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        context.contentResolver.openOutputStream(it)?.use { stream: OutputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
    bitmap.recycle()
}

// Helper function to convert URI to ByteArray
private suspend fun uriToByteArray(context: Context, uri: Uri): ByteArray {
    return when (uri.scheme) {
        "file" -> {
            val path = uri.path
            if (path.isNullOrBlank()) {
                throw IllegalArgumentException("File URI has no path: $uri")
            }
            val file = File(path)
            if (!file.exists()) {
                throw IllegalArgumentException("File does not exist: $uri")
            }
            FileInputStream(file).use { it.readBytes() }
        }
        "content" -> {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Cannot open input stream for content URI: $uri")
        }
        else -> throw IllegalArgumentException("Unsupported URI scheme: ${uri.scheme}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStoryScreen(
    onBackClick: () -> Unit,
    onStoryClick: (CreateStoryInput) -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaType by remember { mutableStateOf<MediaType?>(null) }
    var textOverlays by remember { mutableStateOf<List<StoryTextOverlay>>(emptyList()) }
    var location by remember { mutableStateOf<CreateStoryLocation?>(null) }
    var locationPositionX by remember { mutableStateOf(0.5f) }
    var locationPositionY by remember { mutableStateOf(0.5f) }
    var isLocationOverlaySelected by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf(PostVisibility.PUBLIC) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var selectedOverlayIdForEdit by remember { mutableStateOf<String?>(null) } // ID of overlay being edited
    var selectedOverlayId by remember { mutableStateOf<String?>(null) } // For Instagram-style selection
    var isLoading by remember { mutableStateOf(false) } // Loading state for story creation

    // Media capture URIs
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    var showVisibilityDialog by remember { mutableStateOf(false) }
    var isVideoFromCamera by remember { mutableStateOf(false) }
    var showRecordedVideoPlayOverlay by remember { mutableStateOf(true) }
    var recordedVideoPlayTrigger by remember { mutableStateOf(0) }

    // Camera launcher - go directly to Create Story with photo (no crop step)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                selectedMediaUri = uri
                mediaType = MediaType.IMAGE
            }
        }
    }

    // Video launcher - uses phone's default camera app
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && cameraVideoUri != null) {
            selectedMediaUri = cameraVideoUri
            mediaType = MediaType.VIDEO
            isVideoFromCamera = true
            showRecordedVideoPlayOverlay = true
            cameraVideoUri = null
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(it)
            val detectedMediaType = when {
                mimeType?.startsWith("image/") == true -> MediaType.IMAGE
                mimeType?.startsWith("video/") == true -> MediaType.VIDEO
                else -> MediaType.IMAGE
            }
            
            selectedMediaUri = it
            mediaType = detectedMediaType
            if (detectedMediaType == MediaType.VIDEO) isVideoFromCamera = false
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createImageUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }
    
    // Audio permission launcher for video recording
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val hasCamera = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasCamera) {
                // Create video URI and launch camera app
                val uri = createVideoUri(context)
                cameraVideoUri = uri
                videoLauncher.launch(uri)
            }
        }
    }

    val isStoryEnabled = selectedMediaUri != null
    
    // Error Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            onDismissError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Top bar: Back, screen name, Text + Location (when media selected)
        TopAppBar(
            title = {
                Text(
                    text = "Your Story",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                if (selectedMediaUri != null) {
                    IconButton(onClick = {
                        showTextInputDialog = true
                        selectedOverlayIdForEdit = null
                    }) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "Add Text",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { showLocationPicker = true }) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = if (location != null) PrimaryGreen else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        if (selectedMediaUri != null) {
            // 2. Media section (image/video + text overlays) — constrain height so image never pushes top/bottom off (same as video)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(selectedOverlayId, isLocationOverlaySelected) {
                        detectTapGestures(
                            onTap = {
                                selectedOverlayId = null
                                isLocationOverlaySelected = false
                            }
                        )
                    }
            ) {
                if (mediaType == MediaType.VIDEO && selectedMediaUri != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        VideoPreviewPlayer(
                            videoUri = selectedMediaUri!!,
                            modifier = Modifier.fillMaxSize(),
                            autoPlay = !isVideoFromCamera,
                            looping = !isVideoFromCamera,
                            muted = true,
                            playTrigger = if (isVideoFromCamera) recordedVideoPlayTrigger else 0,
                            onPlaybackEnded = if (isVideoFromCamera) ({ showRecordedVideoPlayOverlay = true }) else null
                        )
                        if (isVideoFromCamera && showRecordedVideoPlayOverlay) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f))
                                    .clickable(enabled = false) { },
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        showRecordedVideoPlayOverlay = false
                                        recordedVideoPlayTrigger++
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    AsyncImage(
                        model = selectedMediaUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(Modifier.size(maxWidth, maxHeight)),
                        contentScale = ContentScale.Crop
                    )
                }
                textOverlays.forEach { overlay ->
                    DraggableTextOverlay(
                        overlay = overlay,
                        isSelected = overlay.id == selectedOverlayId,
                        onPositionChange = { newX, newY ->
                            textOverlays = textOverlays.map {
                                if (it.id == overlay.id) it.copy(positionX = newX, positionY = newY) else it
                            }
                        },
                        onSizeChange = { newSize ->
                            textOverlays = textOverlays.map {
                                if (it.id == overlay.id) it.copy(fontSize = newSize) else it
                            }
                        },
                        onRotationChange = { newRotation ->
                            textOverlays = textOverlays.map {
                                if (it.id == overlay.id) it.copy(rotation = newRotation) else it
                            }
                        },
                        onScaleChange = { newScale ->
                            textOverlays = textOverlays.map {
                                if (it.id == overlay.id) it.copy(scale = newScale) else it
                            }
                        },
                        onSelect = { selectedOverlayId = overlay.id },
                        onEdit = {
                            selectedOverlayIdForEdit = overlay.id
                            showTextInputDialog = true
                        },
                        onDelete = {
                            textOverlays = textOverlays.filter { it.id != overlay.id }
                            if (selectedOverlayId == overlay.id) selectedOverlayId = null
                        }
                    )
                }
                location?.let { loc ->
                    DraggableLocationOverlay(
                        location = loc,
                        positionX = locationPositionX,
                        positionY = locationPositionY,
                        isSelected = isLocationOverlaySelected,
                        onPositionChange = { x, y ->
                            locationPositionX = x
                            locationPositionY = y
                        },
                        onSelect = { selectedOverlayId = null; isLocationOverlaySelected = true },
                        onDelete = {
                            location = null
                            isLocationOverlaySelected = false
                        }
                    )
                }
            }

            // 3. Bottom section: Share + Save
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 24.dp)
                    .padding(vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showVisibilityDialog = true },
                        enabled = isStoryEnabled,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isStoryEnabled) PrimaryGreen else Color.Gray)
                    ) {
                        Text(
                            text = "Share",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = {
                            val uri = selectedMediaUri ?: return@Button
                            val type = mediaType
                            val overlays = textOverlays
                            val loc = location
                            val locX = locationPositionX
                            val locY = locationPositionY
                            scope.launch(Dispatchers.IO) {
                                val bitmap = buildComposedStoryBitmap(
                                    context = context,
                                    mediaUri = uri,
                                    mediaType = type,
                                    textOverlays = overlays,
                                    location = loc,
                                    locationPositionX = locX,
                                    locationPositionY = locY
                                )
                                if (bitmap != null) {
                                    saveBitmapToGallery(context, bitmap)
                                    withContext(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Saved to Gallery")
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Could not create image")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text(
                            text = "Save",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            // Empty state: media selection options
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Create Your Story",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MediaSelectionButton(
                        icon = Icons.Default.Add,
                        label = "Camera",
                        onClick = {
                            when {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
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
                        icon = Icons.Default.Add,
                        label = "Gallery",
                        onClick = { galleryLauncher.launch(PickVisualMediaRequest()) }
                    )
                    MediaSelectionButton(
                        icon = Icons.Default.Add,
                        label = "Record Video",
                        onClick = {
                            val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (hasCamera && hasAudio) {
                                val uri = createVideoUri(context)
                                cameraVideoUri = uri
                                videoLauncher.launch(uri)
                            } else {
                                if (!hasCamera) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                if (!hasAudio) audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }
            }
        }
    }

        // Visibility dialog, loading overlay, snackbar (on top of Column)
        if (showVisibilityDialog) {
            AlertDialog(
                onDismissRequest = { showVisibilityDialog = false },
                confirmButton = {},
                title = { Text("Who can see your story?", color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                showVisibilityDialog = false
                                visibility = PostVisibility.PUBLIC
                                selectedMediaUri?.let { uri ->
                                    scope.launch {
                                        try {
                                            val mediaData = uriToByteArray(context, uri)
                                            val input = CreateStoryInput(
                                                mediaData = mediaData,
                                                mediaType = mediaType,
                                                thumbnailData = null,
                                                textOverlays = textOverlays,
                                                location = location,
                                                visibility = visibility
                                            )
                                            onStoryClick(input)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }
                            }
                        ) { Text("Public", color = PrimaryGreen) }
                        TextButton(
                            onClick = {
                                showVisibilityDialog = false
                                visibility = PostVisibility.FOLLOWERS
                                selectedMediaUri?.let { uri ->
                                    scope.launch {
                                        try {
                                            val mediaData = uriToByteArray(context, uri)
                                            val input = CreateStoryInput(
                                                mediaData = mediaData,
                                                mediaType = mediaType,
                                                thumbnailData = null,
                                                textOverlays = textOverlays,
                                                location = location,
                                                visibility = visibility
                                            )
                                            onStoryClick(input)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }
                            }
                        ) { Text("My Followers", color = PrimaryGreen) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVisibilityDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = BackgroundColor
            )
        }

        if (showLocationPicker) {
            StoryLocationSelectionBottomSheet(
                onDismiss = { showLocationPicker = false },
                onLocationSelected = { loc ->
                    location = loc
                    locationPositionX = 0.5f
                    locationPositionY = 0.5f
                    showLocationPicker = false
                }
            )
        }
        
        // Full Screen Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable(enabled = false) { }, // Block all interactions
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    // Large Loading Indicator
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = PrimaryGreen,
                        strokeWidth = 4.dp
                    )
                    
                    // Loading Text
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Uploading Story",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Please wait while we upload your story...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        
        // Snackbar Host for error messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }

    // Text Input Dialog - CHANGED: Don't use remember for overlayToEdit
    if (showTextInputDialog) {
        TextOverlayInputDialog(
            initialText = selectedOverlayIdForEdit?.let { id ->
                textOverlays.find { it.id == id }?.text
            } ?: "",
            initialColor = selectedOverlayIdForEdit?.let { id ->
                textOverlays.find { it.id == id }?.textColor
            } ?: 0xFFFFFFFF,
            onDismiss = {
                showTextInputDialog = false
                selectedOverlayIdForEdit = null
            },
            onConfirm = { text, color ->
                if (selectedOverlayIdForEdit != null) {
                    // Update existing overlay - find it FRESH from current list
                    textOverlays = textOverlays.map { overlay ->
                        if (overlay.id == selectedOverlayIdForEdit) {
                            // Preserve ALL properties except text and color
                            overlay.copy(
                                text = text,
                                textColor = color
                            )
                        } else {
                            overlay
                        }
                    }
                    selectedOverlayId = selectedOverlayIdForEdit // Keep it selected
                } else {
                    // Add new overlay at center
                    val newOverlay = StoryTextOverlay(
                        text = text,
                        positionX = 0.5f,
                        positionY = 0.5f,
                        fontSize = 28f,
                        textColor = color,
                        rotation = 0f,
                        scale = 1f
                    )
                    textOverlays = textOverlays + newOverlay
                    selectedOverlayId = null
                }
                showTextInputDialog = false
                selectedOverlayIdForEdit = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryLocationSelectionBottomSheet(
    onDismiss: () -> Unit,
    onLocationSelected: (CreateStoryLocation) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationRepository = remember { AndroidLocationRepository(context) }

    var selectedTab by remember { mutableStateOf(0) }
    var states by remember { mutableStateOf<List<String>>(emptyList()) }
    var districts by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedState by remember { mutableStateOf<String?>(null) }
    var selectedDistrict by remember { mutableStateOf<String?>(null) }
    var villageName by remember { mutableStateOf("") }
    var isLoadingStates by remember { mutableStateOf(false) }
    var isLoadingDistricts by remember { mutableStateOf(false) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoadingStates = true
        try {
            states = locationRepository.getStates().sorted()
        } catch (e: Exception) {
            error = "Failed to load states: ${e.message}"
        }
        isLoadingStates = false
    }

    LaunchedEffect(selectedState) {
        selectedState?.let { state ->
            selectedDistrict = null
            districts = emptyList()
            isLoadingDistricts = true
            try {
                districts = locationRepository.getDistricts(state).sorted()
            } catch (e: Exception) {
                error = "Failed to load districts: ${e.message}"
            }
            isLoadingDistricts = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColor,
        dragHandle = {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .padding(vertical = 12.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
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
            Text(
                text = "Add Location",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabButton(label = "Current Location", isSelected = selectedTab == 0, modifier = Modifier.weight(1f)) { selectedTab = 0 }
                TabButton(label = "Manual", isSelected = selectedTab == 1, modifier = Modifier.weight(1f)) { selectedTab = 1 }
            }
            when (selectedTab) {
                0 -> {
                    StoryCurrentLocationTabContent(
                        isLoading = isLoadingLocation,
                        error = error,
                        onUseCurrentLocation = {
                            scope.launch {
                                if (!locationRepository.hasLocationPermission()) {
                                    error = "Location permission is required to use current location"
                                    return@launch
                                }
                                isLoadingLocation = true
                                error = null
                                try {
                                    @SuppressLint("MissingPermission")
                                    val coordinates = locationRepository.getCurrentLocation()
                                    if (coordinates == null) {
                                        isLoadingLocation = false
                                        error = "Unable to get current location. Please try again."
                                        return@launch
                                    }
                                    val locationName = locationRepository.reverseGeocode(coordinates.latitude, coordinates.longitude)
                                    isLoadingLocation = false
                                    onLocationSelected(
                                        CreateStoryLocation(
                                            name = locationName ?: "${coordinates.latitude}, ${coordinates.longitude}",
                                            latitude = coordinates.latitude,
                                            longitude = coordinates.longitude
                                        )
                                    )
                                    onDismiss()
                                } catch (e: Exception) {
                                    isLoadingLocation = false
                                    error = "Unable to get location: ${e.message}"
                                }
                            }
                        }
                    )
                }
                1 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (error != null) {
                            Text(text = error!!, fontSize = 14.sp, color = Color(0xFFFF6B6B))
                        }
                        FilterableDropdownField(
                            label = "State",
                            items = states,
                            selectedItem = selectedState,
                            onItemSelected = { selectedState = it },
                            isLoading = isLoadingStates,
                            enabled = !isLoadingStates,
                            placeholder = "Select State",
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilterableDropdownField(
                            label = "District",
                            items = districts,
                            selectedItem = selectedDistrict,
                            onItemSelected = { selectedDistrict = it },
                            isLoading = isLoadingDistricts,
                            enabled = selectedState != null && !isLoadingDistricts,
                            placeholder = if (selectedState == null) "Select state first" else "Select District",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Village (Optional)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                            OutlinedTextField(
                                value = villageName,
                                onValueChange = { villageName = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter village name", color = TextPrimary.copy(alpha = 0.5f)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = BackgroundColor,
                                    unfocusedContainerColor = BackgroundColor,
                                    focusedBorderColor = PrimaryGreen,
                                    unfocusedBorderColor = Color.Black.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(22.dp),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = TextPrimary)
                            )
                        }
                        Button(
                            onClick = {
                                val state = selectedState
                                val district = selectedDistrict
                                if (state == null || district == null) {
                                    error = "Please select state and district"
                                    return@Button
                                }
                                val village = villageName.trim()
                                val locationName = if (village.isEmpty()) "$district, $state" else "$village, $district, $state"
                                scope.launch {
                                    isLoadingLocation = true
                                    error = null
                                    try {
                                        val coordinates = locationRepository.forwardGeocode(locationName)
                                        isLoadingLocation = false
                                        onLocationSelected(
                                            CreateStoryLocation(
                                                name = locationName,
                                                latitude = coordinates?.latitude,
                                                longitude = coordinates?.longitude
                                            )
                                        )
                                        onDismiss()
                                    } catch (e: Exception) {
                                        isLoadingLocation = false
                                        onLocationSelected(CreateStoryLocation(name = locationName, latitude = null, longitude = null))
                                        onDismiss()
                                    }
                                }
                            },
                            enabled = selectedState != null && selectedDistrict != null && !isLoadingLocation,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, disabledContainerColor = PrimaryGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            if (isLoadingLocation) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Confirming...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            } else {
                                Text("Save Location", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
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
        modifier = modifier.height(48.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) PrimaryGreen else BackgroundColor,
        border = if (isSelected) null else BorderStroke(1.18.dp, PrimaryGreen.copy(alpha = 0.13f))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = label, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = if (isSelected) Color.White else TextSecondary)
        }
    }
}

@Composable
private fun StoryCurrentLocationTabContent(
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
            Text(text = error, fontSize = 14.sp, color = Color(0xFFFF6B6B), modifier = Modifier.padding(bottom = 8.dp))
        }
        Button(
            onClick = onUseCurrentLocation,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, disabledContainerColor = PrimaryGreen.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Detecting location...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            } else {
                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text("Use Current Location", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
        Text(text = "We'll use your GPS to find your location", fontSize = 13.sp, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun MediaSelectionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = PrimaryGreen,
        modifier = Modifier.size(120.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DraggableTextOverlay(
    overlay: StoryTextOverlay,
    isSelected: Boolean,
    onPositionChange: (Float, Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onSizeChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    /* ---------- Screen px ---------- */
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    /* ---------- Stable base values ---------- */
    var basePositionPx by remember(overlay.id) {
        mutableStateOf(
            Offset(
                overlay.positionX * screenWidthPx,
                overlay.positionY * screenHeightPx
            )
        )
    }

    var baseFontSize by remember(overlay.id) {
        mutableStateOf(overlay.fontSize)
    }

    /* ---------- Live gesture state ---------- */
    var localOffset by remember(overlay.id) { mutableStateOf(Offset.Zero) }
    var scale by remember(overlay.id) { mutableStateOf(overlay.scale) }
    var rotation by remember(overlay.id) { mutableStateOf(overlay.rotation) }
    var isTransforming by remember(overlay.id) { mutableStateOf(false) }

    val absoluteX = basePositionPx.x + localOffset.x
    val absoluteY = basePositionPx.y + localOffset.y

    val liveFontSize = (baseFontSize * scale).coerceIn(12f, 72f)

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = absoluteX
                translationY = absoluteY
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
                transformOrigin = TransformOrigin.Center
            }
            .pointerInput(overlay.id, isSelected) {
                if (!isSelected) return@pointerInput

                detectTransformGestures(
                    onGesture = { _, pan, zoom, rotationDelta ->
                        isTransforming = true
                        localOffset += pan
                        val livePx = basePositionPx + localOffset
                        onPositionChange(
                            (livePx.x / screenWidthPx).coerceIn(0f, 1f),
                            (livePx.y / screenHeightPx).coerceIn(0f, 1f)
                        )
                        if (zoom != 1f) {
                            val damped = 1f + (zoom - 1f) * 0.45f
                            scale = (scale * damped).coerceIn(0.5f, 3f)
                            onScaleChange(scale)
                            onSizeChange((baseFontSize * scale).coerceIn(12f, 72f))
                        }
                        if (rotationDelta != 0f) {
                            rotation = (rotation + rotationDelta * 0.5f) % 360f
                            onRotationChange(rotation)
                        }
                    },
                    onEnd = {
                        basePositionPx += localOffset
                        localOffset = Offset.Zero
                        isTransforming = false
                    }
                )
            }
            .pointerInput(overlay.id, isSelected, isTransforming) {
                if (!isTransforming) {
                    detectTapGestures(
                        onTap = {
                            if (!isSelected) onSelect() else onEdit()
                        },
                        onDoubleTap = {
                            if (!isSelected) onSelect()
                            onEdit()
                        }
                    )
                }
            }
    ) {
        Box {
            Row(
                modifier = Modifier
                    .widthIn(max = (config.screenWidthDp * 0.85f).dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(8.dp)
                    )
                    .then(
                        if (isSelected)
                            Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = overlay.text,
                    color = Color(overlay.textColor),
                    fontSize = liveFontSize.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 5,
                    softWrap = true
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(12.dp, (-12).dp)
                        .size(32.dp)
                        .background(Color.Red, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures { onDelete() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    /* ---------- External sync - ONLY on overlay.id change ---------- */
    LaunchedEffect(overlay.id) {
        basePositionPx = Offset(
            overlay.positionX * screenWidthPx,
            overlay.positionY * screenHeightPx
        )
        localOffset = Offset.Zero
        baseFontSize = overlay.fontSize
        scale = overlay.scale
        rotation = overlay.rotation
    }
}

@Composable
private fun DraggableLocationOverlay(
    location: CreateStoryLocation,
    positionX: Float,
    positionY: Float,
    isSelected: Boolean,
    onPositionChange: (Float, Float) -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    var basePositionPx by remember(location.name) {
        mutableStateOf(
            Offset(
                positionX * screenWidthPx,
                positionY * screenHeightPx
            )
        )
    }
    var localOffset by remember { mutableStateOf(Offset.Zero) }

    val absoluteX = basePositionPx.x + localOffset.x
    val absoluteY = basePositionPx.y + localOffset.y

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = absoluteX
                translationY = absoluteY
            }
            .pointerInput(isSelected) {
                if (!isSelected) return@pointerInput
                detectTransformGestures(
                    onGesture = { _, pan, _, _ ->
                        localOffset += pan
                        val livePx = basePositionPx + localOffset
                        onPositionChange(
                            (livePx.x / screenWidthPx).coerceIn(0f, 1f),
                            (livePx.y / screenHeightPx).coerceIn(0f, 1f)
                        )
                    },
                    onEnd = {
                        basePositionPx += localOffset
                        localOffset = Offset.Zero
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSelect() })
            }
    ) {
        Row(
            modifier = Modifier
                .background(
                    PrimaryGreen.copy(alpha = 0.85f),
                    RoundedCornerShape(20.dp)
                )
                .then(
                    if (isSelected)
                        Modifier.border(2.dp, Color.White, RoundedCornerShape(20.dp))
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color(0xFF2D6A4F),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = location.name,
                color = Color.White,
                fontSize = 14.sp
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(8.dp, (-8).dp)
                    .size(28.dp)
                    .background(Color.Red, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures { onDelete() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    onAddTextClick: () -> Unit,
    onLocationClick: () -> Unit,
    location: CreateStoryLocation?,
    onRemoveLocation: () -> Unit,
    visibility: PostVisibility,
    onVisibilitySelected: (PostVisibility) -> Unit,
    onShareClick: () -> Unit,
    isShareEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Add Text Button
                IconButton(onClick = onAddTextClick) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "Add Text",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Text",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                // Location Button
                IconButton(onClick = onLocationClick) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = if (location != null) PrimaryGreen else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Location",
                            color = if (location != null) PrimaryGreen else Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location Display
            if (location != null) {
                Surface(
                    onClick = onRemoveLocation,
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryGreen.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = PrimaryGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = location.name,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Visibility Selection
            VisibilitySelectionSection(
                selectedVisibility = visibility,
                onVisibilitySelected = onVisibilitySelected
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Share Button
            Button(
                onClick = onShareClick,
                enabled = isShareEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    disabledContainerColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Share Story",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun TextOverlayInputDialog(
    initialText: String,
    initialColor: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    val colorOptions = listOf(
        0xFFFFFFFF to "White",
        0xFF000000 to "Black",
        0xFFFF0000 to "Red",
        0xFF0000FF to "Blue",
        0xFF00FF00 to "Green",
        0xFFFFFF00 to "Yellow",
        0xFFFF00FF to "Magenta",
        0xFF00FFFF to "Cyan"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Text", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Enter text") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )

                // Color Picker
                Column {
                    Text(
                        text = "Text Color",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colorOptions.forEach { (color, label) ->
                            ColorOption(
                                color = Color(color),
                                isSelected = selectedColor == color,
                                onClick = { selectedColor = color }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text, selectedColor) },
                enabled = text.isNotBlank()
            ) {
                Text("Add", color = PrimaryGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = BackgroundColor
    )
}

@Composable
private fun ColorOption(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) PrimaryGreen else Color.Gray,
                shape = CircleShape
            )
            .clickable { onClick() }
    )
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
            color = Color.White
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
            text = "Public stories are visible to all farmers",
            fontSize = 13.5.sp,
            color = Color.White.copy(alpha = 0.7f)
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
        color = if (isSelected) PrimaryGreen else Color.Transparent,
        border = if (isSelected) null else BorderStroke(
            width = 1.18.dp,
            color = PrimaryGreen.copy(alpha = 0.5f)
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
                color = if (isSelected) Color.White else Color.White
            )
        }
    }
}