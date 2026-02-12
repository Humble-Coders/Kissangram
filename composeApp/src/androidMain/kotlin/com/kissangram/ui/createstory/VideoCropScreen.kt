package com.kissangram.ui.createstory

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import androidx.media3.transformer.Composition
import com.kissangram.ui.home.PrimaryGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.EditedMediaItem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCropScreen(
    videoUri: Uri,
    onCropComplete: (Uri) -> Unit,
    onSkip: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0f) }
    var videoDuration by remember { mutableStateOf(0L) } // in milliseconds
    var startTime by remember { mutableStateOf(0L) } // in milliseconds
    var endTime by remember { mutableStateOf(0L) } // in milliseconds
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) } // in milliseconds
    
    // Initialize video duration
    LaunchedEffect(videoUri) {
        // ExoPlayer must be accessed on the main thread
        val player = ExoPlayer.Builder(context).build()
        val mediaItem = MediaItem.fromUri(videoUri)
        player.setMediaItem(mediaItem)
        player.prepare()
        
        // Wait for player to be ready (on main thread)
        while (player.duration <= 0) {
            kotlinx.coroutines.delay(100)
        }
        
        videoDuration = player.duration
        endTime = videoDuration.coerceAtMost(30000) // Max 30 seconds for stories
        player.release()
    }
    
    val trimmedDuration = (endTime - startTime) / 1000f // in seconds

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Trim Video",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f))
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Video Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    VideoPreviewPlayer(
                        videoUri = videoUri,
                        modifier = Modifier.fillMaxSize(),
                        autoPlay = isPlaying,
                        looping = false
                    )
                }
                
                // Timeline Scrubber
                VideoTimelineScrubber(
                    videoDuration = videoDuration,
                    startTime = startTime,
                    endTime = endTime,
                    currentPosition = currentPosition,
                    onStartTimeChange = { startTime = it.coerceIn(0, endTime - 1000) },
                    onEndTimeChange = { endTime = it.coerceIn(startTime + 1000, videoDuration) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                )
                
                // Duration Display
                Text(
                    text = "Duration: ${formatDuration(trimmedDuration)}",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )
                
                // Bottom Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Play/Pause Button
                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Skip Button
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 2.dp
                        )
                    ) {
                        Text("Skip", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    
                    // Trim Button
                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                try {
                                    val trimmedUri = trimVideo(
                                        context = context,
                                        inputUri = videoUri,
                                        startTimeMs = startTime,
                                        endTimeMs = endTime,
                                        onProgress = { progress ->
                                            processingProgress = progress
                                        }
                                    )
                                    isProcessing = false
                                    onCropComplete(trimmedUri)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    isProcessing = false
                                    // Handle error - could show snackbar
                                }
                            }
                        },
                        enabled = !isProcessing && trimmedDuration >= 1f,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Trim",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trim", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            
            // Processing Overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = PrimaryGreen
                        )
                        Text(
                            text = "Trimming video...",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        LinearProgressIndicator(
                            progress = processingProgress,
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(4.dp),
                            color = PrimaryGreen
                        )
                        Text(
                            text = "${(processingProgress * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoTimelineScrubber(
    videoDuration: Long,
    startTime: Long,
    endTime: Long,
    currentPosition: Long,
    onStartTimeChange: (Long) -> Unit,
    onEndTimeChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var timelineWidth by remember { mutableStateOf(0f) }
    
    val startPosition = if (videoDuration > 0) (startTime.toFloat() / videoDuration) else 0f
    val endPosition = if (videoDuration > 0) (endTime.toFloat() / videoDuration) else 1f
    val currentPositionPercent = if (videoDuration > 0) (currentPosition.toFloat() / videoDuration) else 0f
    
    Box(modifier = modifier) {
        Column {
            // Timeline track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    .onSizeChanged { timelineWidth = it.width.toFloat() }
            ) {
                // Selected range highlight
                Box(
                    modifier = Modifier
                        .offset(x = (startPosition * timelineWidth).dp)
                        .width(((endPosition - startPosition) * timelineWidth).dp)
                        .fillMaxHeight()
                        .background(PrimaryGreen, RoundedCornerShape(2.dp))
                )
                
                // Current position indicator
                if (currentPositionPercent in startPosition..endPosition) {
                    Box(
                        modifier = Modifier
                            .offset(x = (currentPositionPercent * timelineWidth - 4).dp)
                            .width(8.dp)
                            .height(12.dp)
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .offset(y = (-4).dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Handles row
            Box(modifier = Modifier.fillMaxWidth()) {
                // Start handle
                Box(
                    modifier = Modifier
                        .offset(x = (startPosition * timelineWidth - 12).dp)
                        .width(24.dp)
                        .height(24.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .pointerInput(startPosition, endPosition, timelineWidth, videoDuration) {
                            var startDragOffset = 0f
                            detectDragGestures(
                                onDragStart = { offset ->
                                    startDragOffset = startPosition * timelineWidth
                                },
                                onDrag = { change, dragAmount ->
                                    val newPixelPosition = (startDragOffset + dragAmount.x).coerceIn(0f, (endPosition * timelineWidth) - 24f)
                                    val newPosition = (newPixelPosition / timelineWidth).coerceIn(0f, endPosition - 0.01f)
                                    onStartTimeChange((newPosition * videoDuration).toLong())
                                    startDragOffset = newPixelPosition
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(4.dp)
                            .height(16.dp)
                            .background(Color.Black, RoundedCornerShape(2.dp))
                    )
                }
                
                // End handle
                Box(
                    modifier = Modifier
                        .offset(x = (endPosition * timelineWidth - 12).dp)
                        .width(24.dp)
                        .height(24.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .pointerInput(startPosition, endPosition, timelineWidth, videoDuration) {
                            var endDragOffset = 0f
                            detectDragGestures(
                                onDragStart = { offset ->
                                    endDragOffset = endPosition * timelineWidth
                                },
                                onDrag = { change, dragAmount ->
                                    val newPixelPosition = (endDragOffset + dragAmount.x).coerceIn((startPosition * timelineWidth) + 24f, timelineWidth)
                                    val newPosition = (newPixelPosition / timelineWidth).coerceIn(startPosition + 0.01f, 1f)
                                    onEndTimeChange((newPosition * videoDuration).toLong())
                                    endDragOffset = newPixelPosition
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(4.dp)
                            .height(16.dp)
                            .background(Color.Black, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Float): String {
    val secs = seconds.toInt()
    return String.format("%d:%02d", secs / 60, secs % 60)
}

@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun trimVideo(
    context: Context,
    inputUri: Uri,
    startTimeMs: Long,
    endTimeMs: Long,
    onProgress: (Float) -> Unit
): Uri = withContext(Dispatchers.IO) {
    val outputFile = File(
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
        "story_video_trimmed_${System.currentTimeMillis()}.mp4"
    )
    outputFile.parentFile?.mkdirs()

    // Create a suspendCancellableCoroutine to wait for transformation
    suspendCancellableCoroutine<Uri> { continuation ->
        val transformer = Transformer.Builder(context)
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        onProgress(1.0f)
                        val outputUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            outputFile
                        )
                        continuation.resume(outputUri, null)
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        continuation.resumeWithException(exportException)
                    }
                }
            )
            .build()

        // Create media item with clipping configuration for trimming
        val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startTimeMs)
            .setEndPositionMs(endTimeMs)
            .build()
        
        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(clippingConfiguration)
            .build()
        
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(false)
            .build()

        // Start transformation with output path
        transformer.start(editedMediaItem, outputFile.absolutePath)

        onProgress(0.5f)

        continuation.invokeOnCancellation {
            transformer.cancel()
        }
    }
}
