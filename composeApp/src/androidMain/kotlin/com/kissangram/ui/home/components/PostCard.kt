package com.kissangram.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.kissangram.model.*
import com.kissangram.ui.home.*

@Composable
fun PostCard(
    post: Post,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onPostClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundColor)
            .padding(horizontal = 18.dp, vertical = 4.dp)
    ) {
        // Author Header
        PostAuthorHeader(
            post = post,
            onAuthorClick = onAuthorClick
        )
        
        Spacer(modifier = Modifier.height(9.dp))
        
        // Tags Row
        PostTagsRow(post = post)
        
        // Post Image (if any)
        if (post.media.isNotEmpty()) {
            Spacer(modifier = Modifier.height(9.dp))
            PostImage(
                media = post.media.first(),
                onClick = onPostClick
            )
        }
        
        // Post Text
        if (post.text.isNotEmpty()) {
            Spacer(modifier = Modifier.height(13.dp))
            PostTextContent(
                text = post.text,
                onReadMore = onPostClick
            )
        }
        
        // Action Bar
        Spacer(modifier = Modifier.height(9.dp))
        PostActionBar(
            post = post,
            onLikeClick = onLikeClick,
            onCommentClick = onCommentClick,
            onShareClick = onShareClick,
            onSaveClick = onSaveClick
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color.Black.copy(alpha = 0.05f))
    }
}

@Composable
private fun PostAuthorHeader(
    post: Post,
    onAuthorClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAuthorClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Avatar with gradient border
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .background(
                        Brush.verticalGradient(listOf(PrimaryGreen, AccentYellow)),
                        CircleShape
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (post.authorProfileImageUrl != null) {
                    AsyncImage(
                        model = post.authorProfileImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(PrimaryGreen, AccentYellow)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.authorName.firstOrNull()?.uppercase() ?: "",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
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
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(ExpertGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Verified",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
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
        
        // Follow button (if not own post and not following)
        if (post.authorRole != UserRole.EXPERT) {
            Button(
                onClick = { /* Follow */ },
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

@Composable
private fun PostTagsRow(post: Post) {
    Row(
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
private fun PostImage(
    media: PostMedia,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(304.dp)
            .clip(RoundedCornerShape(0.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = media.url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Volume/mute button for videos
        if (media.type == MediaType.VIDEO) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(13.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PostTextContent(
    text: String,
    onReadMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 13.dp)
    ) {
        // Text icon
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
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(11.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                fontSize = 17.sp,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 25.sp
            )
            
            if (text.length > 150) {
                Text(
                    text = "Read more",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryGreen,
                    modifier = Modifier.clickable { onReadMore() }
                )
            }
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
            .padding(top = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Like
        ActionButton(
            icon = if (post.isLikedByMe) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
            label = "Like",
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
            .padding(horizontal = 13.dp, vertical = 10.dp),
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
