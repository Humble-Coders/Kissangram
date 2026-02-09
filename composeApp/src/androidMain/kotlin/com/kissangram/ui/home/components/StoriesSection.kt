package com.kissangram.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kissangram.model.UserStories
import com.kissangram.ui.home.*

@Composable
fun StoriesSection(
    stories: List<UserStories>,
    onStoryClick: (String) -> Unit,
    onCreateStoryClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundColor)
            .padding(top = 13.dp, bottom = 13.dp)
    ) {
        // Section Title
        Text(
            text = "Today in Nearby Fields",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
        )
        
        // Horizontal Story List
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // Create Story Card (first item)
            item {
                CreateStoryCard(onClick = onCreateStoryClick)
            }
            
            // Other stories
            items(stories) { userStory ->
                StoryCard(
                    userStory = userStory,
                    onClick = { onStoryClick(userStory.userId) }
                )
            }
        }
    }
    
    // Divider
    HorizontalDivider(
        color = Color.Black.copy(alpha = 0.05f),
        thickness = 0.5.dp
    )
}

@Composable
private fun StoryCard(
    userStory: UserStories,
    onClick: () -> Unit
) {
    val story = userStory.stories.firstOrNull()
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(PrimaryGreen, AccentYellow)
    )
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(120.dp)
            .height(193.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column {
            // Story Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
            ) {
                // Background Image
                AsyncImage(
                    model = story?.media?.url ?: userStory.userProfileImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                    contentScale = ContentScale.Crop
                )
                
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )
                
                // User Avatar with gradient border
                Box(
                    modifier = Modifier
                        .padding(9.dp)
                        .size(32.dp)
                        .background(gradientBrush, CircleShape)
                        .padding(2.dp)
                        .background(Color.White, CircleShape)
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (userStory.userProfileImageUrl != null) {
                        AsyncImage(
                            model = userStory.userProfileImageUrl,
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
                                .background(gradientBrush, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userStory.userName.firstOrNull()?.toString() ?: "",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                
                // Today badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = AccentYellow.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "Today",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
            
            // User Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = userStory.userName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Crop tag
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryGreen.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "🌾 Wheat",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateStoryCard(
    onClick: () -> Unit
) {
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(PrimaryGreen, AccentYellow)
    )
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(120.dp)
            .height(193.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Story Image Area with gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(gradientBrush, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Plus icon
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Story",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            // User Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your Story",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Add Story tag
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryGreen.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "+ Add",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryGreen
                    )
                }
            }
        }
    }
}
