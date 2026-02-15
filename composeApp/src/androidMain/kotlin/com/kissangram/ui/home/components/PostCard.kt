package com.kissangram.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.DisposableEffect
import coil.compose.AsyncImage
import com.kissangram.model.*
import com.kissangram.ui.components.ProfileImageLoader
import com.kissangram.ui.home.*

@Composable
fun PostCard(
    post: Post,
    isVisible: Boolean = true,
    isOwnPost: Boolean = false,
    isFollowingAuthor: Boolean = false,
    onLikeClick: () -> Boolean, // Returns true if request was accepted
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onPostClick: () -> Unit,
    onFollowClick: () -> Unit = {},
    onUnfollowClick: () -> Unit = {}
) {
    // ⚡ INSTAGRAM APPROACH: Local state for instant visual feedback
    // Updates immediately on click, but only if ViewModel accepts the request
    var localLikedState by remember(post.id) { mutableStateOf(post.isLikedByMe) }
    var localLikesCount by remember(post.id) { mutableIntStateOf(post.likesCount) }
    
    // Sync local state with actual post state when it changes (from ViewModel or refresh)
    // This ensures local state matches server state after requests complete
    LaunchedEffect(post.isLikedByMe, post.likesCount) {
        localLikedState = post.isLikedByMe
        localLikesCount = post.likesCount
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Author Header
            PostAuthorHeader(
                post = post,
                isOwnPost = isOwnPost,
                isFollowingAuthor = isFollowingAuthor,
                onAuthorClick = onAuthorClick,
                onFollowClick = onFollowClick,
                onUnfollowClick = onUnfollowClick
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Tags Row
            PostTagsRow(post = post)
            
            // Post Media (if any)
            if (post.media.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                MediaCarousel(
                    media = post.media,
                    onMediaClick = onPostClick,
                    isVisible = isVisible,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            // Post Text
            if (post.text.isNotEmpty() || post.voiceCaption != null) {
                Spacer(modifier = Modifier.height(if (post.media.isEmpty()) 10.dp else 14.dp))
                PostTextContent(
                    text = post.text,
                    voiceCaption = post.voiceCaption,
                    onReadMore = onPostClick
                )
            }
            
            // Action Bar - use local state for instant feedback
            Spacer(modifier = Modifier.height(4.dp))
            PostActionBar(
                post = post.copy(
                    isLikedByMe = localLikedState,
                    likesCount = localLikesCount
                ),
                onLikeClick = {
                    // ⚡ Update local state IMMEDIATELY (before ViewModel call)
                    // This gives instant visual feedback with zero perceived lag
                    val newLikedState = !localLikedState
                    val newLikesCount = if (newLikedState) localLikesCount + 1 else localLikesCount - 1
                    
                    // Call ViewModel first to check if it accepts the request
                    val accepted = onLikeClick()
                    
                    // Only update local state if ViewModel accepted the request
                    // This prevents sync issues when rapid clicks are ignored
                    if (accepted) {
                        localLikedState = newLikedState
                        localLikesCount = newLikesCount
                    }
                    // If not accepted (already processing), local state stays as-is
                    // LaunchedEffect will sync it with actual post state when request completes
                },
                onCommentClick = onCommentClick,
                onShareClick = onShareClick,
                onSaveClick = onSaveClick
            )
        }
    }
}

@Composable
private fun PostAuthorHeader(
    post: Post,
    isOwnPost: Boolean,
    isFollowingAuthor: Boolean,
    onAuthorClick: () -> Unit,
    onFollowClick: () -> Unit,
    onUnfollowClick: () -> Unit
) {
    var showUnfollowMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp)
            .padding(top = 16.dp)
            .clickable { onAuthorClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Avatar - fetches profile image asynchronously when not in post
            ProfileImageLoader(
                authorId = post.authorId,
                authorName = post.authorName,
                authorProfileImageUrl = post.authorProfileImageUrl,
                size = 45.dp
            )
            
            Spacer(modifier = Modifier.width(11.dp))
            
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.authorName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (post.authorVerificationStatus == VerificationStatus.VERIFIED) {
                        Spacer(modifier = Modifier.width(7.dp))
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Verified",
                            tint = ExpertGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = when (post.authorRole) {
                            UserRole.EXPERT -> "Agricultural Expert"
                            UserRole.FARMER -> post.location?.name ?: "Farmer"
                            UserRole.AGRIPRENEUR -> "Agripreneur"
                            UserRole.INPUT_SELLER -> "Input Seller"
                            UserRole.AGRI_LOVER -> "Agri Lover"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }
        }
        
        // Follow / 3-dot menu (if not own post and not expert)
        if (!isOwnPost && post.authorRole != UserRole.EXPERT) {
            if (isFollowingAuthor) {
                Box {
                    IconButton(onClick = { showUnfollowMenu = true }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "More options",
                            tint = TextPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = showUnfollowMenu,
                        onDismissRequest = { showUnfollowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Unfollow", color = Color(0xFFBC4749))
                            },
                            onClick = {
                                showUnfollowMenu = false
                                onUnfollowClick()
                            }
                        )
                    }
                }
            } else {
                Button(
                    onClick = onFollowClick,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 19.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "+ Follow",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PostTagsRow(post: Post) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Role tag
        when (post.authorRole) {
            UserRole.EXPERT -> {
                TagChip(
                    text = "Expert advice",
                    backgroundColor = ExpertGreen.copy(alpha = 0.08f),
                    textColor = ExpertGreen,
                    dotColor = ExpertGreen
                )
            }
            UserRole.FARMER -> {
                if (post.location != null) {
                    TagChip(
                        text = "Nearby farmer",
                        backgroundColor = AccentYellow.copy(alpha = 0.08f),
                        textColor = AccentYellow,
                        dotColor = AccentYellow
                    )
                }
            }
            else -> {}
        }
        
        // Crop tags
        post.crops.take(2).forEach { crop ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AccentYellow.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    AccentYellow.copy(alpha = 0.19f)
                )
            ) {
                Text(
                    text = crop.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun TagChip(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    dotColor: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

@Composable
fun PostTextContent(
    text: String,
    voiceCaption: com.kissangram.model.VoiceContent?,
    onReadMore: () -> Unit
) {
    // Playback state
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    
    // Handler for progress updates (matching CreatePostViewModel pattern)
    val playbackHandler = remember { Handler(Looper.getMainLooper()) }
    var playbackUpdateRunnable by remember { mutableStateOf<Runnable?>(null) }
    
    // Stop playback function (matching CreatePostViewModel pattern)
    val stopPlayback: () -> Unit = {
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
        isPlaying = false
        playbackProgress = 0
    }
    
    // Start playback progress updates (matching CreatePostViewModel pattern)
    val startPlaybackProgressUpdates: () -> Unit = {
        playbackUpdateRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val progress = player.currentPosition / 1000
                        playbackProgress = progress
                        
                        // Check if reached duration limit
                        if (voiceCaption != null && progress >= voiceCaption.durationSeconds) {
                            stopPlayback()
                        } else {
                            playbackHandler.postDelayed(this, 500)
                        }
                    }
                }
            }
        }
        playbackHandler.post(playbackUpdateRunnable!!)
    }
    
    // Cleanup MediaPlayer and Handler callbacks on dispose
    DisposableEffect(voiceCaption?.url) {
        onDispose {
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
            isPlaying = false
            playbackProgress = 0
        }
    }
    
    // Handle playback
    val onPlayClick: () -> Unit = onPlayClick@ {
        val caption = voiceCaption
        if (caption == null) {
            return@onPlayClick
        }
        
        if (isPlaying) {
            // Stop playback
            stopPlayback()
        } else {
            // Start playback
            try {
                val url = caption.url
                if (url.isBlank()) {
                    return@onPlayClick
                }
                
                // Stop any existing playback
                stopPlayback()
                
                val player = MediaPlayer().apply {
                    // Handle remote URLs (http/https) and local file paths
                    setDataSource(url)
                    
                    setOnPreparedListener {
                        start()
                        isPlaying = true
                        playbackProgress = 0
                        // Start progress updates
                        startPlaybackProgressUpdates()
                    }
                    
                    setOnCompletionListener {
                        stopPlayback()
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        android.util.Log.e("PostCard", "MediaPlayer error: what=$what, extra=$extra")
                        stopPlayback()
                        true
                    }
                    
                    prepareAsync()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                android.util.Log.e("PostCard", "Failed to start playback: ${e.message}", e)
                stopPlayback()
            }
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Left icon/button - show play button if voiceCaption exists, otherwise text icon
        if (voiceCaption != null) {
            // Voice caption play button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isPlaying) Color(0xFFFF6B6B) else PrimaryGreen,
                            shape = CircleShape
                        )
                        .clickable { onPlayClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

            }
        } else {
            // Text icon (when no voice caption)
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = PrimaryGreen.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Notes,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(11.dp))
        
        // Text caption (right side)
        if (text.isNotEmpty()) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = text,
                    fontSize = 17.sp,
                    color = TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 23.sp // Approximate lineSpacing of 6dp (17sp + 6dp ≈ 23sp)
                )
                
                if (text.length > 150) {
                    TextButton(
                        onClick = onReadMore,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Read more",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryGreen
                        )
                    }
                }
            }
        } else {
            // If no text but has voice caption, take up space
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PostActionBar(
    post: Post,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Like - show count only
        ActionButton(
            icon = if (post.isLikedByMe) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
            label = post.likesCount.toString(),
            tint = if (post.isLikedByMe) ErrorRed else TextSecondary,
            onClick = onLikeClick
        )
        
        // Comment
        ActionButton(
            icon = Icons.Outlined.ChatBubbleOutline,
            label = "Comment",
            tint = TextSecondary,
            onClick = onCommentClick
        )
        
        // Share
        ActionButton(
            icon = Icons.Outlined.Share,
            label = "Share",
            tint = TextSecondary,
            onClick = onShareClick
        )
        
        // Save
        IconButton(onClick = onSaveClick) {
            Icon(
                imageVector = if (post.isSavedByMe) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Save",
                tint = if (post.isSavedByMe) PrimaryGreen else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}
