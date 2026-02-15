package com.kissangram.ui.home.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
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
 * Auto-plays when visible, muted by default
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    media: PostMedia,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    autoPlay: Boolean = true,
    onTap: () -> Unit = {}
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }
    var showThumbnail by remember { mutableStateOf(true) }
    
    val exoPlayer = remember(media.url) {
        ExoPlayerCache.getPlayer(context, media.url)
    }
    
    // Handle visibility and auto-play
    LaunchedEffect(isVisible, autoPlay) {
        if (isVisible && autoPlay) {
            // Small delay to ensure player is ready
            kotlinx.coroutines.delay(100)
            exoPlayer.play()
            isPlaying = true
            showThumbnail = false
        } else {
            exoPlayer.pause()
            isPlaying = false
            showThumbnail = true
        }
    }
    
    // Listen to player state to hide thumbnail when video starts playing
    LaunchedEffect(exoPlayer) {
        snapshotFlow { exoPlayer.playbackState }
            .collect { state ->
                if (state == Player.STATE_READY && exoPlayer.isPlaying) {
                    showThumbnail = false
                }
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
    
    Box(modifier = modifier) {
        // Show thumbnail first, then video when ready
        if (showThumbnail) {
            if (media.thumbnailUrl != null) {
                val thumbnailUrl = remember(media.thumbnailUrl) {
                    try {
                        CloudinaryUrlTransformer.transformForFeed(media.thumbnailUrl!!)
                    } catch (e: Exception) {
                        media.thumbnailUrl!!
                    }
                }
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder when no thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
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
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onTap() },
                update = { view ->
                    view.player = exoPlayer
                    if (isVisible && autoPlay && !exoPlayer.isPlaying) {
                        exoPlayer.play()
                        isPlaying = true
                        showThumbnail = false
                    }
                }
            )
        }
        
        // Play/pause overlay button
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        if (isPlaying) {
                            exoPlayer.pause()
                            isPlaying = false
                        } else {
                            exoPlayer.play()
                            isPlaying = true
                            showThumbnail = false
                        }
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
