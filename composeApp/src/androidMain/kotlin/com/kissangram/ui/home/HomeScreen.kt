package com.kissangram.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.kissangram.ui.home.components.*
import com.kissangram.viewmodel.HomeViewModel
import com.kissangram.model.Post
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

val BackgroundColor = Color(0xFFF8F9F1)
val PrimaryGreen = Color(0xFF2D6A4F)
val AccentYellow = Color(0xFFFFB703)
val TextPrimary = Color(0xFF1B1B1B)
val TextSecondary = Color(0xFF6B6B6B)
val ExpertGreen = Color(0xFF74C365)
val ErrorRed = Color(0xFFBC4749)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToStory: (String) -> Unit = {},
    onNavigateToCreateStory: () -> Unit = {},
    onNavigateToPostDetail: (String, Post?) -> Unit = { _, _ -> },
    bottomNavPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    
    // Load content when screen is first created (same as ViewModel init)
    LaunchedEffect(Unit) {
        viewModel.loadContent()
    }
    
    // Track visible post indices for video auto-play
    val visiblePostIndices = remember { mutableStateSetOf<Int>() }
    
    // Update visible posts when layout changes
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { visibleIndices ->
                // Filter out story section (index 0) and get post indices
                // Posts start from index 1 (after stories)
                val postIndices = visibleIndices.filter { it > 0 }.map { it - 1 }
                visiblePostIndices.clear()
                visiblePostIndices.addAll(postIndices)
            }
    }

    LaunchedEffect(uiState.posts.size, uiState.isLoading, uiState.error) {
        Log.d("HomeScreen", "uiState: posts=${uiState.posts.size} isLoading=${uiState.isLoading} error=${uiState.error}")
    }
    
    // Dev: Upload locations state
    var showUploadDialog by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    
    // Dev: Upload crops state
    var showCropsUploadDialog by remember { mutableStateOf(false) }
    var cropsUploadStatus by remember { mutableStateOf<String?>(null) }
    var isCropsUploading by remember { mutableStateOf(false) }
    
    // Load more when reaching end
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && 
                    lastVisibleIndex >= uiState.posts.size - 2 && 
                    uiState.hasMorePosts && 
                    !uiState.isLoadingMore) {
                    viewModel.loadMorePosts()
                }
            }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                HomeTopBar(
                    onNotificationsClick = onNavigateToNotifications,
                    onMessagesClick = onNavigateToMessages
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
        // Content
        if (uiState.isLoading && uiState.posts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator(color = PrimaryGreen)
                Spacer(modifier = Modifier.weight(1f))
            }
        } else if (uiState.error != null && uiState.posts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.error ?: "Something went wrong",
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadContent() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("Retry")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        } else {
            val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isRefreshing)
            
            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = { viewModel.refreshFeed() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomNavPadding.calculateBottomPadding())
                ) {
                    // Stories Section
                    item {
                        StoriesSection(
                            stories = uiState.stories,
                            onStoryClick = onNavigateToStory,
                            onCreateStoryClick = onNavigateToCreateStory
                        )
                    }

                    // Spacing between stories and first post (matching iOS)
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Posts
                    itemsIndexed(
                        items = uiState.posts,
                        key = { _, post -> post.id }
                    ) { index, post ->
                        val isVisible = visiblePostIndices.contains(index)
                        val isOwnPost = post.authorId == uiState.currentUserId
                        val isFollowingAuthor = uiState.authorIdToIsFollowing[post.authorId] == true
                        PostCard(
                            post = post,
                            isVisible = isVisible,
                            isOwnPost = isOwnPost,
                            isFollowingAuthor = isFollowingAuthor,
                            onLikeClick = { viewModel.onLikePost(post.id) },
                            onCommentClick = { onNavigateToPostDetail(post.id, post) },
                            onShareClick = { /* Share */ },
                            onSaveClick = { viewModel.onSavePost(post.id) },
                            onAuthorClick = { onNavigateToProfile(post.authorId) },
                            onPostClick = { onNavigateToPostDetail(post.id, post) },
                            onFollowClick = { viewModel.onFollow(post.authorId) },
                            onUnfollowClick = { viewModel.unfollowAndRemovePosts(post.authorId) }
                        )
                    }

                    // Loading more indicator
                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                            CircularProgressIndicator(
                                color = PrimaryGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                    // End of feed
                    if (!uiState.hasMorePosts && uiState.posts.isNotEmpty()) {
                        item {
                            EndOfFeedSection()
                        }
                    }
                    
                    // Bottom padding (matching iOS)
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                



            }
        }
        }


        

    }
}
