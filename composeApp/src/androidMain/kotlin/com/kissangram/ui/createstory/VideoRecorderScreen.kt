package com.kissangram.ui.createstory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import com.kissangram.ui.home.PrimaryGreen
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun VideoRecorderScreen(
    onVideoRecorded: (Uri) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var hasHandledRecording by remember { mutableStateOf(false) }
    
    val maxDurationSeconds = 30
    
    // Handle recorded video URI (only once)
    LaunchedEffect(recordedVideoUri) {
        recordedVideoUri?.let { uri ->
            if (!hasHandledRecording) {
                hasHandledRecording = true
                onVideoRecorded(uri)
            }
        }
    }
    
    // Timer for recording duration
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording && recordingTime < maxDurationSeconds) {
                kotlinx.coroutines.delay(1000)
                recordingTime++
                if (recordingTime >= maxDurationSeconds) {
                    // Auto-stop at max duration
                    scope.launch {
                        recording?.stop()
                        isRecording = false
                    }
                }
            }
        } else {
            recordingTime = 0
        }
    }
    
    // Check permissions
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, initialize camera
        }
    }
    
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Audio permission granted
        }
    }
    
    // Initialize camera
    LaunchedEffect(Unit) {
        cameraProvider = getCameraProvider(context)
        setupCamera(
            context = context,
            lifecycleOwner = lifecycleOwner,
            cameraProvider = cameraProvider,
            onVideoCaptureReady = { videoCapture = it }
        )
        
        // Request permissions if needed
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Record Video",
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
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    cameraProvider?.let { provider ->
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(view.surfaceProvider)
                        }
                        
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                videoCapture
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            )
            
            // Recording Timer
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Surface(
                        color = Color.Red.copy(alpha = 0.8f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = formatTime(recordingTime),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            
            // Bottom Controls
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            ) {
                if (!isRecording) {
                    // Record Button
                    Button(
                        onClick = {
                            startRecording(
                                context = context,
                                videoCapture = videoCapture,
                                onRecordingStarted = { rec ->
                                    recording = rec
                                    isRecording = true
                                },
                                onVideoRecorded = { uri ->
                                    recordedVideoUri = uri
                                },
                                onError = { error ->
                                    error.printStackTrace()
                                    isRecording = false
                                }
                            )
                        },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                } else {
                    // Stop Button
                    Button(
                        onClick = {
                            recording?.stop()
                            isRecording = false
                        },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}

private suspend fun getCameraProvider(context: Context): ProcessCameraProvider {
    return suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener(
                {
                    continuation.resume(future.get())
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    }
}

private fun setupCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraProvider: ProcessCameraProvider?,
    onVideoCaptureReady: (VideoCapture<Recorder>) -> Unit
) {
    cameraProvider?.let { provider ->
        val preview = Preview.Builder().build()
        
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.fromOrderedList(
                listOf(Quality.HD, Quality.SD),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            ))
            .build()
        
        val videoCapture = VideoCapture.withOutput(recorder)
        
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )
            onVideoCaptureReady(videoCapture)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@RequiresPermission(Manifest.permission.RECORD_AUDIO)
private fun startRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    onRecordingStarted: (Recording) -> Unit,
    onVideoRecorded: (Uri) -> Unit,
    onError: (Exception) -> Unit
) {
    val videoFile = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
        "story_video_${System.currentTimeMillis()}.mp4"
    )
    videoFile.parentFile?.mkdirs()
    
    val outputFileOptions = FileOutputOptions.Builder(videoFile).build()
    
    videoCapture?.output?.prepareRecording(context, outputFileOptions)
        ?.withAudioEnabled()
        ?.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> {
                    // Recording started
                }
                is VideoRecordEvent.Finalize -> {
                    if (!recordEvent.hasError()) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            videoFile
                        )
                        onVideoRecorded(uri)
                    } else {
                        onError(Exception("Recording error: ${recordEvent.cause}"))
                    }
                }
                else -> {}
            }
        }?.let { recording ->
            onRecordingStarted(recording)
        } ?: run {
            onError(Exception("Failed to start recording"))
        }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}
