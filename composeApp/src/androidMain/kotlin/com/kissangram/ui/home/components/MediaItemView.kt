package com.kissangram.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kissangram.model.MediaType
import com.kissangram.model.PostMedia
import com.kissangram.util.CloudinaryUrlTransformer

/**
 * Component that displays either an image or video based on media type
 */
@Composable
fun MediaItemView(
    media: PostMedia,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onTap: () -> Unit = {}
) {
    var imageLoadError by remember { mutableStateOf<String?>(null) }
    var useOriginalUrl by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp) // Match iOS height
            .clip(RoundedCornerShape(14.dp)) // Match iOS rounded corners
            .clickable { onTap() }
    ) {
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
                
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
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
            MediaType.VIDEO -> {
                VideoPlayerView(
                    media = media,
                    modifier = Modifier.fillMaxSize(),
                    isVisible = isVisible,
                    autoPlay = true,
                    onTap = onTap
                )
            }
        }
    }
}
