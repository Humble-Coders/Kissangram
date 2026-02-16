package com.kissangram.ui.story

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kissangram.model.Story
import com.kissangram.model.UserStories
import com.kissangram.viewmodel.StoryViewModel
import com.kissangram.ui.home.components.VideoPlayerView
import kotlinx.coroutines.delay

@Composable
fun StoryScreen(
    userId: String,
    onBackClick: () -> Unit,
    viewModel: StoryViewModel = viewModel(
        factory = StoryViewModelFactory(
            context = LocalContext.current.applicationContext,
            initialUserId = userId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val allStoriesFinished by viewModel.allStoriesFinished.collectAsState()

    // Navigate back when all stories are finished
    LaunchedEffect(allStoriesFinished) {
        if (allStoriesFinished) {
            onBackClick()
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    if (uiState.error != null || uiState.userStories.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = uiState.error ?: "No stories available",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onBackClick) {
                    Text("Go Back", color = Color.White)
                }
            }
        }
        return
    }

    val currentUserStories = uiState.getCurrentUserStories() ?: return
    val currentStory = uiState.getCurrentStory() ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Story Content
        StoryContent(
            story = currentStory,
            modifier = Modifier.fillMaxSize()
        )

        // Swipe Gestures (placed behind top bar to not interfere with close button)
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val screenWidth = size.width
                            // Don't intercept taps in the top bar area (first 100dp)
                            val topBarHeightPx = with(density) { 100.dp.toPx() }
                            if (offset.y > topBarHeightPx) {
                                if (offset.x < screenWidth / 3) {
                                    // Left side - previous
                                    viewModel.previousStory()
                                } else if (offset.x > screenWidth * 2 / 3) {
                                    // Right side - next
                                    viewModel.nextStory()
                                }
                            }
                        }
                    )
                }
        )

        // Top Bar with Progress Indicators (placed last so it's on top and clickable)
        StoryTopBar(
            userStories = currentUserStories,
            currentStoryIndex = uiState.currentStoryIndex,
            onBackClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(8.dp)
        )
    }
}

@Composable
private fun StoryContent(
    story: Story,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (story.media.type) {
            com.kissangram.model.MediaType.IMAGE -> {
                AsyncImage(
                    model = story.media.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            com.kissangram.model.MediaType.VIDEO -> {
                // Convert StoryMedia to PostMedia for VideoPlayerView
                val postMedia = com.kissangram.model.PostMedia(
                    url = story.media.url,
                    type = com.kissangram.model.MediaType.VIDEO,
                    thumbnailUrl = story.media.thumbnailUrl
                )
                VideoPlayerView(
                    media = postMedia,
                    modifier = Modifier.fillMaxSize(),
                    isVisible = true,
                    autoPlay = true
                )
            }
        }

        // Text Overlay
        story.textOverlay?.let { overlay ->
            val density = LocalDensity.current
            val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels
            val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels
            
            Text(
                text = overlay.text,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(density) { (overlay.positionX * screenWidth).toDp() },
                        y = with(density) { (overlay.positionY * screenHeight).toDp() }
                    )
                    .padding(16.dp)
            )
        }

        // Location Badge
        story.locationName?.let { location ->
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = location,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun StoryTopBar(
    userStories: UserStories,
    currentStoryIndex: Int,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Progress Bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            userStories.stories.forEachIndexed { index, _ ->
                StoryProgressBar(
                    isActive = index == currentStoryIndex,
                    isCompleted = index < currentStoryIndex,
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Author Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Image
            AsyncImage(
                model = userStories.userProfileImageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Author Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userStories.userName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${currentStoryIndex + 1} / ${userStories.stories.size}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            // Close Button - ensure it's clickable
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun StoryProgressBar(
    isActive: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            progress = 0f
            while (progress < 1f) {
                delay(50)
                progress += 0.02f // 5 seconds total (100 * 0.02 * 50ms = 5000ms)
            }
        } else if (isCompleted) {
            progress = 1f
        } else {
            progress = 0f
        }
    }

    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.3f), MaterialTheme.shapes.small)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(Color.White, MaterialTheme.shapes.small)
        )
    }
}


// Factory for creating StoryViewModel
class StoryViewModelFactory(
    private val context: android.content.Context,
    private val initialUserId: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StoryViewModel(
                application = context.applicationContext as android.app.Application,
                initialUserId = initialUserId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
