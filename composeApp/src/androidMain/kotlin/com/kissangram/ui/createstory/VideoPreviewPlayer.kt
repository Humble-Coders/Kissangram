package com.kissangram.ui.createstory

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewPlayer(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    looping: Boolean = true,
    muted: Boolean = true
) {
    val context = LocalContext.current
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            if (looping) {
                repeatMode = Player.REPEAT_MODE_ONE
            }
            if (muted) {
                volume = 0f
            }
            prepare()
            if (autoPlay) {
                play()
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
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
        modifier = modifier.fillMaxSize(),
        update = { view ->
            // Update player if URI changes
            if (view.player?.currentMediaItem?.requestMetadata?.mediaUri != videoUri) {
                val mediaItem = MediaItem.fromUri(videoUri)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (autoPlay) {
                    exoPlayer.play()
                }
            }
        }
    )
    
    // Handle lifecycle
    LaunchedEffect(videoUri) {
        exoPlayer.apply {
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            prepare()
            if (autoPlay) {
                play()
            }
        }
    }
}
