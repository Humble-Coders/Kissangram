package com.kissangram.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.kissangram.ui.home.components.*
import com.kissangram.viewmodel.HomeViewModel
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
    onNavigateToPostDetail: (String) -> Unit = {},
    onNavigateToComments: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryGreen)
            }
        } else if (uiState.error != null && uiState.posts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
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
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Stories Section
                item {
                    StoriesSection(
                        stories = uiState.stories,
                        onStoryClick = onNavigateToStory,
                        onCreateStoryClick = onNavigateToCreateStory
                    )
                }

                // Posts
                items(
                    items = uiState.posts,
                    key = { it.id }
                ) { post ->
                    PostCard(
                        post = post,
                        onLikeClick = { viewModel.onLikePost(post.id) },
                        onCommentClick = { onNavigateToComments(post.id) },
                        onShareClick = { /* Share */ },
                        onSaveClick = { viewModel.onSavePost(post.id) },
                        onAuthorClick = { onNavigateToProfile(post.authorId) },
                        onPostClick = { onNavigateToPostDetail(post.id) }
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
                
                // DEV: Upload buttons section (remove after use)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Dev Tools",
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        
                        // Upload Locations Button
                        Button(
                            onClick = { showUploadDialog = true },
                            enabled = !isUploading,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isUploading) "Uploading..." else "Upload Locations")
                        }
                        
                        // Upload Crops Button
                        Button(
                            onClick = { showCropsUploadDialog = true },
                            enabled = !isCropsUploading,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentYellow)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCropsUploading) "Uploading..." else "Upload Crops",
                                color = TextPrimary
                            )
                        }
                        
                        // Status messages
                        uploadStatus?.let {
                            Text(
                                text = it,
                                color = if (it.contains("success", ignoreCase = true)) PrimaryGreen else ErrorRed,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize
                            )
                        }
                        
                        cropsUploadStatus?.let {
                            Text(
                                text = it,
                                color = if (it.contains("success", ignoreCase = true)) PrimaryGreen else ErrorRed,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize
                            )
                        }
                    }
                }


            }
        }
        }

        // Upload Locations Confirmation Dialog
        if (showUploadDialog) {
            AlertDialog(
                onDismissRequest = { showUploadDialog = false },
                title = { Text("Upload Locations") },
                text = { Text("Upload all India states and districts data to Firestore? This is a one-time operation.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showUploadDialog = false
                            isUploading = true
                            viewModel.uploadLocationsToFirestore(
                                onSuccess = {
                                    isUploading = false
                                    uploadStatus = "Locations uploaded successfully!"
                                },
                                onError = { error ->
                                    isUploading = false
                                    uploadStatus = "Error: $error"
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("Upload")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUploadDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Upload Crops Confirmation Dialog
        if (showCropsUploadDialog) {
            AlertDialog(
                onDismissRequest = { showCropsUploadDialog = false },
                title = { Text("Upload Crops") },
                text = { Text("Upload all crops data (categorized) to Firestore? This is a one-time operation.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showCropsUploadDialog = false
                            isCropsUploading = true
                            viewModel.uploadCropsToFirestore(
                                onSuccess = {
                                    isCropsUploading = false
                                    cropsUploadStatus = "Crops uploaded successfully!"
                                },
                                onError = { error ->
                                    isCropsUploading = false
                                    cropsUploadStatus = "Error: $error"
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentYellow)
                    ) {
                        Text("Upload", color = TextPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCropsUploadDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

    }
}
