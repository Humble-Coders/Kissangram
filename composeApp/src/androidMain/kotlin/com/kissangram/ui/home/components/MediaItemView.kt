package com.kissangram.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kissangram.model.MediaType
import com.kissangram.model.PostMedia
import com.kissangram.util.CloudinaryUrlTransformer
import coil.compose.AsyncImage

/**
 * Component that displays either an image or video based on media type
 */
@Composable
fun MediaItemView(
    media: PostMedia,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onTap: () -> Unit = {},
    showFullImage: Boolean = false, // If true, show full image without fixed height constraint
    autoPlay: Boolean = false // If true, videos will auto-play when visible
) {
    var imageLoadError by remember { mutableStateOf<String?>(null) }
    var useOriginalUrl by remember { mutableStateOf(false) }
    
    when (media.type) {
        MediaType.IMAGE -> {
            val imageUrl = remember(media.url, useOriginalUrl) {
                if (useOriginalUrl) {
                    media.url
                } else {
                    try {
                        CloudinaryUrlTransformer.transformForFeed(media.url)
                    } catch (e: Exception) {
                        media.url // Fallback to original URL
                    }
                }
            }
            
            if (showFullImage) {
                // Full-size image - let it determine its own height based on aspect ratio
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .clickable { onTap() },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit, // Fit to show full image
                        onError = { error ->
                            val errorMsg = error.result.throwable?.message ?: "Unknown error"
                            imageLoadError = errorMsg
                            
                            // Try original URL if transformed URL failed
                            if (!useOriginalUrl && imageUrl != media.url) {
                                useOriginalUrl = true
                            }
                        },
                        onSuccess = {
                            imageLoadError = null
                        }
                    )
                    
                    // Show error message if loading failed
                    if (imageLoadError != null && useOriginalUrl) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Failed to load image",
                                    color = Color.White,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = media.url.take(50) + "...",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else {
                // Feed image - fixed height with crop
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .clickable { onTap() },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop, // Crop for feed
                        onError = { error ->
                            val errorMsg = error.result.throwable?.message ?: "Unknown error"
                            imageLoadError = errorMsg
                            
                            // Try original URL if transformed URL failed
                            if (!useOriginalUrl && imageUrl != media.url) {
                                useOriginalUrl = true
                            }
                        },
                        onSuccess = {
                            imageLoadError = null
                        }
                    )
                    
                    // Show error message if loading failed
                    if (imageLoadError != null && useOriginalUrl) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Failed to load image",
                                    color = Color.White,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = media.url.take(50) + "...",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
        MediaType.VIDEO -> {
            VideoPlayerView(
                media = media,
                modifier = modifier,
                isVisible = isVisible,
                autoPlay = autoPlay,
                showFullImage = showFullImage,
                onTap = onTap
            )
        }
    }
}
