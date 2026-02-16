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
import androidx.compose.ui.draw.shadow
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
    ) {
        // Section Title - with full width container to match iOS HStack behavior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 13.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Today in Nearby Fields",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // Horizontal Story List
        LazyRow(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 13.dp),
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

    Box(
        modifier = Modifier
            .width(120.dp)
            .height(193.dp)
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            // STORY IMAGE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
            ) {

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

                // Avatar
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
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentYellow.copy(alpha = 0.9f))
                        .padding(horizontal = 7.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Today",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            // USER INFO — CENTERED
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(PrimaryGreen.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🫵View",
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

    Box(
        modifier = Modifier
            .width(120.dp)
            .height(193.dp)
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Top image area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(
                        gradientBrush,
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Story",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Bottom info area — CENTERED
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Your Story",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(PrimaryGreen.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "+ Add",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryGreen
                    )
                }
            }
        }
    }
}
