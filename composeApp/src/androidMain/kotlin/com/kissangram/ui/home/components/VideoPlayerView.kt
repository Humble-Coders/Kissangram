package com.kissangram.ui.home.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.kissangram.model.PostMedia
import com.kissangram.util.CloudinaryUrlTransformer
import com.kissangram.util.ExoPlayerCache

/**
 * Video player component for feed videos
 * Paused by default, user can tap play button to start
 * Supports batch preloading of next videos for smooth playback
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    media: PostMedia,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    autoPlay: Boolean = false,
    showFullImage: Boolean = false,
    onTap: () -> Unit = {},
    upcomingVideoUrls: List<String> = emptyList() // URLs of next videos to preload
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }
    var showThumbnail by remember { mutableStateOf(true) }
    
    val exoPlayer = remember(media.url) {
        ExoPlayerCache.getPlayer(context, media.url)
    }
    
    // Preload next videos when current video starts playing
    LaunchedEffect(isPlaying, upcomingVideoUrls) {
        if (isPlaying && upcomingVideoUrls.isNotEmpty()) {
            // Preload next 2 videos in background
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ExoPlayerCache.preloadVideos(context, upcomingVideoUrls)
            }
        }
    }
    
    // Handle auto-play when visible
    LaunchedEffect(isVisible, autoPlay) {
        if (isVisible && autoPlay) {
            // Check if player is ready before playing
            if (exoPlayer.playbackState == Player.STATE_READY) {
                exoPlayer.play()
                isPlaying = true
                showThumbnail = false
            } else {
                // Wait a bit for player to be ready
                kotlinx.coroutines.delay(100)
                if (exoPlayer.playbackState == Player.STATE_READY) {
                    exoPlayer.play()
                    isPlaying = true
                    showThumbnail = false
                }
            }
        } else if (!isVisible && isPlaying) {
            // Pause video when it becomes invisible (if playing)
            exoPlayer.pause()
            isPlaying = false
            showThumbnail = true
        }
    }
    
    // Listen to player state to hide thumbnail when video starts playing
    // Also handle auto-play when player becomes ready
    DisposableEffect(exoPlayer, autoPlay, isVisible) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    showThumbnail = false
                } else {
                    showThumbnail = true
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Auto-play when player becomes ready and autoPlay is enabled
                if (playbackState == Player.STATE_READY && autoPlay && isVisible && !exoPlayer.isPlaying) {
                    exoPlayer.play()
                    isPlaying = true
                    showThumbnail = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }
    
    // Handle mute state
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }
    
    // Pause on dispose—player stays in cache for reuse when scrolling back
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.pause()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (showFullImage) {
                    // For full-size, use aspect ratio - default to 16:9, increased height by 40%
                    // Original: 16/9 ≈ 1.778, with 40% height increase: 16/(9*1.4) ≈ 1.27
                    Modifier.aspectRatio(16f / (9f * 1.4f))
                } else {
                    Modifier.height(440.dp) // Fixed height for feed
                }
            )
            .clip(RoundedCornerShape(0.dp)) // Ensure content stays within bounds
    ) {
        // Show thumbnail when paused - using same approach as SearchScreen
        if (showThumbnail) {
            val thumbnailUrl = remember(media.type, media.thumbnailUrl, media.url) {
                when {
                    media.type == com.kissangram.model.MediaType.VIDEO && media.thumbnailUrl != null -> {
                        try {
                            CloudinaryUrlTransformer.transformForThumbnail(media.thumbnailUrl!!)
                        } catch (e: Exception) {
                            media.thumbnailUrl!!
                        }
                    }
                    media.type == com.kissangram.model.MediaType.VIDEO -> {
                        // Generate thumbnail from video URL
                        CloudinaryUrlTransformer.generateVideoThumbnailUrl(media.url)
                    }
                    else -> {
                        try {
                            CloudinaryUrlTransformer.transformForThumbnail(media.url)
                        } catch (e: Exception) {
                            media.url
                        }
                    }
                }
            }
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (showFullImage) ContentScale.Fit else ContentScale.Crop
            )
        }
        
        // Video player (only when not showing thumbnail)
        if (!showThumbnail) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        resizeMode = if (showFullImage) {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT // Fit for full-size
                        } else {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM // Fill the fixed area for feed
                        }
                        clipToPadding = false
                        clipChildren = true
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(0.dp)) // Ensure video stays within bounds
                    .clickable { onTap() }
                // Removed update block to prevent unnecessary recompositions
            )
        }
        
        // Play button overlay - show when paused
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        exoPlayer.play()
                        isPlaying = true
                        showThumbnail = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
        
        // Volume control button (top-right)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(13.dp)
                .size(40.dp)
                .clickable {
                    isMuted = !isMuted
                },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
