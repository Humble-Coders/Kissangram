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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kissangram.model.*
import com.kissangram.ui.components.ProfileImageLoader
import com.kissangram.ui.home.*
import com.kissangram.ui.home.components.MediaCarousel
import com.kissangram.ui.home.components.PostTextContent
import com.kissangram.viewmodel.CommentsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    initialPost: Post? = null,
    onBackClick: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application

    val viewModel: CommentsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return CommentsViewModel(application, postId, initialPost) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
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
            viewModel.startSpeechRecognition()
        }
    }

    fun handleSpeechRecognitionStart() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.startSpeechRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        currentUserId = viewModel.getCurrentUserId()
    }

    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = error,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // Auto-focus input when replying
    LaunchedEffect(uiState.replyingToComment) {
        if (uiState.replyingToComment != null) {
            textFieldFocusState = true
            keyboardController?.show()
        }
    }

    // Auto-scroll to newly added comment
    LaunchedEffect(uiState.comments.size) {
        if (uiState.comments.isNotEmpty() && !uiState.isPostingComment) {
            kotlinx.coroutines.delay(100)
            listState.animateScrollToItem(0)
        }
    }

    // Load more when scrolling near bottom
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val totalItems = uiState.comments.size
                if (lastVisibleIndex != null && lastVisibleIndex >= totalItems - 3 && uiState.hasMoreComments && !uiState.isLoadingMore) {
                    viewModel.loadMoreComments()
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
                    IconButton(onClick = { /* Menu */ }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = TextPrimary)
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
                    uiState.post?.let { post ->
                        Box(modifier = Modifier.padding(horizontal = 18.dp)) {
                            PostHeaderSection(
                                post = post,
                                onAuthorClick = { onNavigateToProfile(post.authorId) },
                                onLikeClick = { viewModel.onLikePost(post.id) }
                            )
                        }
                    }
                }

                // Reply Indicator
                item {
                    AnimatedVisibility(
                        visible = uiState.replyingToComment != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        uiState.replyingToComment?.let { replyingTo ->
                            ReplyIndicator(
                                comment = replyingTo,
                                onCancel = {
                                    viewModel.cancelReply()
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
                        text = "Comments (${uiState.comments.size})",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)
                    )
                }

                if (uiState.isLoading && uiState.comments.isEmpty()) {
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

                if (uiState.comments.isEmpty() && !uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = TextSecondary.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "No comments yet",
                                    color = TextSecondary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Be the first to comment!",
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                items(
                    items = uiState.comments,
                    key = { it.id }
                ) { comment ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CommentItem(
                            comment = comment,
                            currentUserId = currentUserId,
                            isTopLevel = true,
                            onAuthorClick = { onNavigateToProfile(comment.authorId) },
                            onReplyClick = { viewModel.startReply(comment) },
                            onDeleteClick = { viewModel.showDeleteConfirmation(comment) },
                            onViewRepliesClick = {
                                if (comment.repliesCount > 0) {
                                    viewModel.toggleReplies(comment.id)
                                }
                            },
                            isRepliesExpanded = uiState.expandedReplies.contains(comment.id),
                            replies = uiState.repliesByParentId[comment.id] ?: emptyList(),
                            isLoadingReplies = uiState.loadingRepliesFor.contains(comment.id),
                            currentUserIdForReplies = currentUserId,
                            onAuthorClickForReplies = onNavigateToProfile,
                            onReplyClickForReplies = { viewModel.startReply(it) },
                            onDeleteClickForReplies = { viewModel.showDeleteConfirmation(it) }
                        )
                    }
                }

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
            }

            // Bottom Input Bar
            CommentInputBar(
                text = uiState.newCommentText,
                replyingTo = uiState.replyingToComment,
                isPosting = uiState.isPostingComment,
                isListening = uiState.isListening,
                isProcessing = uiState.isProcessing,
                onTextChange = viewModel::onCommentTextChange,
                onPostClick = {
                    viewModel.postComment()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onCancelReply = {
                    viewModel.cancelReply()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onStartSpeech = { handleSpeechRecognitionStart() },
                onStopSpeech = { viewModel.stopSpeechRecognition() },
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

    if (uiState.showDeleteDialog) {
        DeleteCommentDialog(
            reason = uiState.deleteReason,
            onReasonChange = viewModel::onDeleteReasonChange,
            onConfirm = { viewModel.deleteComment() },
            onDismiss = viewModel::dismissDeleteDialog
        )
    }
}

@Composable
private fun PostHeaderSection(
    post: Post,
    onAuthorClick: () -> Unit,
    onLikeClick: () -> Boolean
) {
    var localLikedState by remember(post.id) { mutableStateOf(post.isLikedByMe) }
    var localLikesCount by remember(post.id) { mutableIntStateOf(post.likesCount) }

    LaunchedEffect(post.isLikedByMe, post.likesCount) {
        localLikedState = post.isLikedByMe
        localLikesCount = post.likesCount
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .clickable { onAuthorClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileImageLoader(
                    authorId = post.authorId,
                    authorName = post.authorName,
                    authorProfileImageUrl = post.authorProfileImageUrl,
                    size = 50.dp
                )
                Spacer(modifier = Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = post.location?.name ?: "Location not set",
                            fontSize = 15.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (post.media.isNotEmpty()) {
                MediaCarousel(
                    media = post.media,
                    onMediaClick = { },
                    isVisible = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ActionButton(
                    icon = if (localLikedState) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    label = localLikesCount.toString(),
                    tint = if (localLikedState) ErrorRed else TextSecondary,
                    onClick = {
                        val newLikedState = !localLikedState
                        val newLikesCount = if (newLikedState) localLikesCount + 1 else localLikesCount - 1
                        val accepted = onLikeClick()
                        if (accepted) {
                            localLikedState = newLikedState
                            localLikesCount = newLikesCount
                        }
                    }
                )
                ActionButton(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = "Comment",
                    tint = TextSecondary,
                    onClick = { }
                )
                ActionButton(
                    icon = Icons.Outlined.Share,
                    label = "Share",
                    tint = TextSecondary,
                    onClick = { }
                )
            }

            if (post.text.isNotEmpty() || post.voiceCaption != null) {
                PostTextContent(
                    text = post.text,
                    voiceCaption = post.voiceCaption,
                    onReadMore = { }
                )
            }

            if (post.crops.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    post.crops.forEach { crop ->
                        Surface(
                            color = AccentYellow.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentYellow.copy(alpha = 0.19f))
                        ) {
                            Text(
                                text = crop,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = Color.Black.copy(alpha = 0.05f),
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    currentUserId: String?,
    isTopLevel: Boolean,
    onAuthorClick: () -> Unit,
    onReplyClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onViewRepliesClick: () -> Unit = {},
    isRepliesExpanded: Boolean = false,
    replies: List<Comment> = emptyList(),
    isLoadingReplies: Boolean = false,
    currentUserIdForReplies: String? = null,
    onAuthorClickForReplies: (String) -> Unit = {},
    onReplyClickForReplies: (Comment) -> Unit = {},
    onDeleteClickForReplies: (Comment) -> Unit = {}
) {
    val isOwnComment = currentUserId != null && comment.authorId == currentUserId
    val horizontalPadding = if (isTopLevel) 18.dp else 48.dp
    val fontSize = if (isTopLevel) 17.sp else 15.sp
    val nameFontSize = if (isTopLevel) 16.sp else 15.sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        ProfileImageLoader(
            authorId = comment.authorId,
            authorName = comment.authorName,
            authorProfileImageUrl = comment.authorProfileImageUrl,
            size = if (isTopLevel) 40.dp else 32.dp,
            modifier = Modifier.clickable { onAuthorClick() }
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    text = comment.authorName,
                    fontSize = nameFontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.clickable { onAuthorClick() }
                )
                Text(
                    text = formatTimestamp(comment.createdAt),
                    fontSize = 14.sp,
                    color = Color(0xFF9B9B9B)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = comment.text,
                fontSize = fontSize,
                color = TextPrimary,
                lineHeight = (fontSize.value + 8).sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onReplyClick,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Reply",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                if (isTopLevel && comment.repliesCount > 0) {
                    TextButton(
                        onClick = onViewRepliesClick,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        if (isLoadingReplies) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = TextSecondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isRepliesExpanded) "Hide replies" else "View ${comment.repliesCount} ${if (comment.repliesCount == 1) "reply" else "replies"}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                if (isOwnComment) {
                    TextButton(
                        onClick = onDeleteClick,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Delete",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = ErrorRed
                        )
                    }
                }
            }

            if (isTopLevel && isRepliesExpanded && replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                replies.forEach { reply ->
                    CommentItem(
                        comment = reply,
                        currentUserId = currentUserIdForReplies,
                        isTopLevel = false,
                        onAuthorClick = { onAuthorClickForReplies(reply.authorId) },
                        onReplyClick = { onReplyClickForReplies(reply) },
                        onDeleteClick = { onDeleteClickForReplies(reply) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyIndicator(
    comment: Comment,
    onCancel: () -> Unit
) {
    Surface(
        color = PrimaryGreen.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Reply,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Replying to @${comment.authorUsername}",
                    fontSize = 14.sp,
                    color = PrimaryGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cancel",
                    tint = PrimaryGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CommentInputBar(
    text: String,
    replyingTo: Comment?,
    isPosting: Boolean,
    isListening: Boolean,
    isProcessing: Boolean,
    onTextChange: (String) -> Unit,
    onPostClick: () -> Unit,
    onCancelReply: () -> Unit,
    onStartSpeech: () -> Unit,
    onStopSpeech: () -> Unit,
    focusState: Boolean,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusState) {
        if (focusState) {
            focusRequester.requestFocus()
        }
    }

    Surface(color = Color.White, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(
                color = Color.Black.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                // Tap to speak - compact mic button for comments
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .clip(CircleShape)
                        .background(PrimaryGreen)
                        .pointerInput(isPosting, isProcessing) {
                            detectDragGestures(
                                onDragStart = {
                                    if (!isPosting && !isProcessing) onStartSpeech()
                                },
                                onDragEnd = { onStopSpeech() },
                                onDrag = { _, _ -> }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Tap to speak",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = if (replyingTo != null) "Reply to @${replyingTo.authorUsername}..." else "Add a comment...",
                            color = TextPrimary.copy(alpha = 0.5f),
                            fontSize = 17.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 45.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChange(it.isFocused) },
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = PrimaryGreen.copy(alpha = 0.13f),
                        focusedBorderColor = PrimaryGreen.copy(alpha = 0.3f),
                        unfocusedContainerColor = BackgroundColor,
                        focusedContainerColor = BackgroundColor,
                        cursorColor = PrimaryGreen
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 17.sp),
                    singleLine = true,
                    enabled = !isPosting,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        autoCorrect = true,
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (text.trim().isNotEmpty() && !isPosting) {
                                onPostClick()
                            }
                        }
                    )
                )

                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(
                            if (text.trim().isNotEmpty()) PrimaryGreen else Color(0xFFE5E5E5),
                            CircleShape
                        )
                        .clickable(enabled = !isPosting && text.trim().isNotEmpty()) {
                            onPostClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Send,
                            contentDescription = "Send",
                            tint = if (text.trim().isNotEmpty()) Color.White else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteCommentDialog(
    reason: String,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Comment",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Please provide a reason for deleting this comment:",
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    placeholder = { Text("Reason (required)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = PrimaryGreen.copy(alpha = 0.13f),
                        focusedBorderColor = PrimaryGreen,
                        cursorColor = PrimaryGreen
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                if (reason.isEmpty()) {
                    Text("Reason is required", fontSize = 14.sp, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = reason.trim().isNotEmpty()) {
                Text("Delete", color = ErrorRed, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontSize = 16.sp)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(18.dp)
    )
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
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(text = label, fontSize = 15.sp, color = tint)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> {
            val mins = (diff / 60_000).toInt()
            "$mins ${if (mins == 1) "min" else "mins"} ago"
        }
        diff < 86400_000 -> {
            val hours = (diff / 3600_000).toInt()
            "$hours ${if (hours == 1) "hr" else "hrs"} ago"
        }
        diff < 604800_000 -> {
            val days = (diff / 86400_000).toInt()
            "$days ${if (days == 1) "day" else "days"} ago"
        }
        else -> {
            val format = SimpleDateFormat("MMM d", Locale.getDefault())
            format.format(Date(timestamp))
        }
    }
}
