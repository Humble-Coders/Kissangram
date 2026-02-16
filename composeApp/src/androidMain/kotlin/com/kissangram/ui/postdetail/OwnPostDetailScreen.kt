package com.kissangram.ui.postdetail

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kissangram.model.*
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestorePostRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.ui.components.ProfileImageLoader
import com.kissangram.ui.home.*
import com.kissangram.ui.home.components.MediaCarousel
import com.kissangram.ui.home.components.PostTextContent
import com.kissangram.usecase.DeletePostUseCase
import com.kissangram.viewmodel.CommentsViewModel
import com.kissangram.viewmodel.OwnPostDetailViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnPostDetailScreen(
    postId: String,
    initialPost: Post? = null,
    onBackClick: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application

    // Create repositories and use cases
    val authRepository = remember {
        AndroidAuthRepository(
            context = application,
            activity = null,
            preferencesRepository = AndroidPreferencesRepository(application)
        )
    }
    val userRepository = remember { FirestoreUserRepository(authRepository = authRepository) }
    val postRepository = remember {
        FirestorePostRepository(
            authRepository = authRepository,
            userRepository = userRepository
        )
    }
    val deletePostUseCase = remember { DeletePostUseCase(postRepository) }

    // Create ViewModels
    val commentsViewModel: CommentsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return CommentsViewModel(application, postId, initialPost) as T
            }
        }
    )

    val ownPostDetailViewModel: OwnPostDetailViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return OwnPostDetailViewModel(
                    application = application,
                    postId = postId,
                    commentsViewModel = commentsViewModel,
                    deletePostUseCase = deletePostUseCase
                ) as T
            }
        }
    )

    val commentsUiState by commentsViewModel.uiState.collectAsState()
    val ownPostUiState by ownPostDetailViewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var currentUserId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var textFieldFocusState by remember { mutableStateOf(false) }

    // Permission launcher for Tap to speak
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            commentsViewModel.startSpeechRecognition()
        }
    }

    fun handleSpeechRecognitionStart() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            commentsViewModel.startSpeechRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        currentUserId = commentsViewModel.getCurrentUserId()
    }

    // Show error snackbar
    LaunchedEffect(commentsUiState.error, ownPostUiState.deleteError) {
        commentsUiState.error?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = error,
                    duration = SnackbarDuration.Short
                )
            }
        }
        ownPostUiState.deleteError?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = error,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // Auto-focus input when replying
    LaunchedEffect(commentsUiState.replyingToComment) {
        if (commentsUiState.replyingToComment != null) {
            textFieldFocusState = true
            keyboardController?.show()
        }
    }

    // Auto-scroll to newly added comment
    LaunchedEffect(commentsUiState.comments.size) {
        if (commentsUiState.comments.isNotEmpty() && !commentsUiState.isPostingComment) {
            kotlinx.coroutines.delay(100)
            listState.animateScrollToItem(0)
        }
    }

    // Load more when scrolling near bottom
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val totalItems = commentsUiState.comments.size
                if (lastVisibleIndex != null && lastVisibleIndex >= totalItems - 3 && commentsUiState.hasMoreComments && !commentsUiState.isLoadingMore) {
                    commentsViewModel.loadMoreComments()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { ownPostDetailViewModel.showDeleteConfirmation() },
                        enabled = !ownPostUiState.isDeletingPost
                    ) {
                        if (ownPostUiState.isDeletingPost) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = TextPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete Post", tint = ErrorRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundColor
                )
            )
        },
        containerColor = BackgroundColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Post Header Section
                item {
                    commentsUiState.post?.let { post ->
                        Box(modifier = Modifier.padding(horizontal = 18.dp)) {
                            PostHeaderSection(
                                post = post,
                                onAuthorClick = { onNavigateToProfile(post.authorId) },
                                onLikeClick = { commentsViewModel.onLikePost(post.id) }
                            )
                        }
                    }
                }

                // Reply Indicator
                item {
                    AnimatedVisibility(
                        visible = commentsUiState.replyingToComment != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        commentsUiState.replyingToComment?.let { replyingTo ->
                            ReplyIndicator(
                                comment = replyingTo,
                                onCancel = {
                                    commentsViewModel.cancelReply()
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            )
                        }
                    }
                }

                // Comments Heading
                item {
                    Text(
                        text = "Comments (${commentsUiState.comments.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 20.dp)
                            .padding(bottom = 12.dp)
                    )
                }

                if (commentsUiState.isLoading && commentsUiState.comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PrimaryGreen)
                        }
                    }
                }

                if (commentsUiState.comments.isEmpty() && !commentsUiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = TextSecondary.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = "No comments yet",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Be the first to comment!",
                                    color = TextSecondary,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                items(
                    items = commentsUiState.comments,
                    key = { it.id }
                ) { comment ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CommentItem(
                            comment = comment,
                            currentUserId = currentUserId,
                            isTopLevel = true,
                            onAuthorClick = { onNavigateToProfile(comment.authorId) },
                            onReplyClick = { commentsViewModel.startReply(comment) },
                            onDeleteClick = { commentsViewModel.showDeleteConfirmation(comment) },
                            onViewRepliesClick = {
                                if (comment.repliesCount > 0) {
                                    commentsViewModel.toggleReplies(comment.id)
                                }
                            },
                            isRepliesExpanded = commentsUiState.expandedReplies.contains(comment.id),
                            replies = commentsUiState.repliesByParentId[comment.id] ?: emptyList(),
                            isLoadingReplies = commentsUiState.loadingRepliesFor.contains(comment.id),
                            currentUserIdForReplies = currentUserId,
                            onAuthorClickForReplies = onNavigateToProfile,
                            onReplyClickForReplies = { commentsViewModel.startReply(it) },
                            onDeleteClickForReplies = { commentsViewModel.showDeleteConfirmation(it) }
                        )
                    }
                }

                if (commentsUiState.isLoadingMore) {
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
            }

            // Bottom Input Bar
            CommentInputBar(
                text = commentsUiState.newCommentText,
                replyingTo = commentsUiState.replyingToComment,
                isPosting = commentsUiState.isPostingComment,
                isListening = commentsUiState.isListening,
                isProcessing = commentsUiState.isProcessing,
                onTextChange = commentsViewModel::onCommentTextChange,
                onPostClick = {
                    commentsViewModel.postComment()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onCancelReply = {
                    commentsViewModel.cancelReply()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onStartSpeech = { handleSpeechRecognitionStart() },
                onStopSpeech = { commentsViewModel.stopSpeechRecognition() },
                focusState = textFieldFocusState,
                onFocusChange = { textFieldFocusState = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(bottom = 80.dp)
        )
    }

    // Delete Comment Dialog
    if (commentsUiState.showDeleteDialog) {
        DeleteCommentDialog(
            reason = commentsUiState.deleteReason,
            onReasonChange = commentsViewModel::onDeleteReasonChange,
            onConfirm = { commentsViewModel.deleteComment() },
            onDismiss = commentsViewModel::dismissDeleteDialog
        )
    }

    // Delete Post Dialog
    if (ownPostUiState.showDeleteDialog) {
        DeletePostDialog(
            isDeleting = ownPostUiState.isDeletingPost,
            onConfirm = {
                ownPostDetailViewModel.deletePost(onSuccess = onBackClick)
            },
            onDismiss = ownPostDetailViewModel::dismissDeleteDialog
        )
    }
}

@Composable
private fun DeletePostDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Post",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete this post? This action cannot be undone.",
                fontSize = 16.sp,
                color = TextPrimary
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = ErrorRed,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Delete",
                        color = ErrorRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancel", color = TextSecondary, fontSize = 16.sp)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(18.dp)
    )
}
