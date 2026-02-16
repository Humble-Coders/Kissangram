package com.kissangram.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.kissangram.model.Post
import com.kissangram.model.UserInfo
import com.kissangram.model.UserRole
import com.kissangram.model.VerificationStatus
import com.kissangram.ui.home.AccentYellow
import com.kissangram.ui.home.PrimaryGreen
import com.kissangram.ui.home.TextPrimary
import com.kissangram.ui.home.TextSecondary
import com.kissangram.viewmodel.SearchViewModel
import com.kissangram.viewmodel.SuggestionSection

private val SearchBackground = Color(0xFFFBF8F0)
private val ExpertGreen = Color(0xFF74C365)
private val VerifiedBlue = Color(0xFF2196F3)
private val PendingOrange = Color(0xFFFF9800)
private val RejectedRed = Color(0xFFBC4749)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onUserClick: (String) -> Unit = {},
    onPostClick: (String, Post?) -> Unit = { _, _ -> },
    onFollowClick: (String) -> Unit = {},
    viewModel: SearchViewModel = viewModel(),
    bottomNavPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchBackground)
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "Search",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = FontFamily.Serif
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = SearchBackground
            )
            // No windowInsets override - automatically respects notch/status bar
        )
        
        // Search Bar
        SearchBar(
            query = uiState.query,
            onQueryChange = { viewModel.setQuery(it) },
            onClearClick = { viewModel.clearSearch() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        )

        // Content
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryGreen)
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                        Button(
                            onClick = { viewModel.setQuery(uiState.query) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGreen
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            uiState.query.isBlank() -> {
                // Show suggestions when query is empty
                SuggestionsContent(
                    sections = uiState.suggestionSections,
                    isRefreshing = uiState.isRefreshingSuggestions,
                    onRefresh = { viewModel.refreshSuggestions() },
                    onPostClick = onPostClick,
                    onUserClick = onUserClick,
                    onFollowClick = { userId -> viewModel.followUser(userId) },
                    bottomNavPadding = bottomNavPadding
                )
            }

            uiState.hasSearched && uiState.results.isEmpty() -> {
                // No results
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No users found",
                            color = TextSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Try a different search term",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            else -> {
                // Results list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        top = 8.dp,
                        end = 18.dp,
                        bottom = 8.dp + bottomNavPadding.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.results,
                        key = { it.id }
                    ) { user ->
                        UserSearchResultItem(
                            user = user,
                            onClick = { onUserClick(user.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = "Search farmers, experts...",
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 16.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = TextSecondary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearClick) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = PrimaryGreen,
            unfocusedBorderColor = Color.Black.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(24.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 16.sp,
            color = TextPrimary
        )
    )
}

@Composable
private fun UserSearchResultItem(
    user: UserInfo,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(PrimaryGreen, AccentYellow))
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (user.profileImageUrl != null) {
                    AsyncImage(
                        model = user.profileImageUrl,
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
                            .clip(CircleShape)
                            .background(PrimaryGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.name.firstOrNull()?.uppercase() ?: "U",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // User Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = user.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (user.verificationStatus == VerificationStatus.VERIFIED) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified",
                            modifier = Modifier.size(16.dp),
                            tint = VerifiedBlue
                        )
                    }
                }

                Text(
                    text = "@${user.username}",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Role Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ExpertGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = roleLabel(user.role),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionsContent(
    sections: List<SuggestionSection>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onPostClick: (String, Post?) -> Unit,
    onUserClick: (String) -> Unit,
    onFollowClick: (String) -> Unit,
    bottomNavPadding: PaddingValues
) {
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)
    
    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = onRefresh
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 8.dp,
                end = 18.dp,
                bottom = 8.dp + bottomNavPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (sections.isNotEmpty()) {
                items(
                    items = sections,
                    key = { section ->
                        when (section) {
                            is SuggestionSection.UserRow -> "user_row_${section.users.firstOrNull()?.id}"
                            is SuggestionSection.PostGrid -> "post_grid_${section.posts.firstOrNull()?.id}"
                        }
                    }
                ) { section ->
                    when (section) {
                        is SuggestionSection.UserRow -> {
                            SuggestedUsersRow(
                                users = section.users,
                                onUserClick = onUserClick,
                                onFollowClick = onFollowClick
                            )
                        }
                        is SuggestionSection.PostGrid -> {
                            SuggestedPostsGrid(
                                posts = section.posts,
                                onPostClick = onPostClick
                            )
                        }
                    }
                }
            } else {
                // Empty state if no suggestions
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = TextSecondary.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "No suggestions available",
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedUsersRow(
    users: List<UserInfo>,
    onUserClick: (String) -> Unit,
    onFollowClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "People to Follow",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontFamily = FontFamily.Serif
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = users,
                key = { it.id }
            ) { user ->
                SuggestedUserCard(
                    user = user,
                    onClick = { onUserClick(user.id) },
                    onFollowClick = { onFollowClick(user.id) }
                )
            }
        }
    }
}

@Composable
private fun SuggestedPostsGrid(
    posts: List<Post>,
    onPostClick: (String, Post?) -> Unit
) {
    // Use regular Column with Rows instead of LazyVerticalGrid
    // to avoid nested scrolling issues with LazyColumn
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        posts.chunked(2).forEach { rowPosts ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPosts.forEach { post ->
                    SuggestedPostItem(
                        post = post,
                        onClick = { onPostClick(post.id, post) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                // Fill remaining space if row has less than 2 items
                repeat(2 - rowPosts.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SuggestedPostItem(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        // Fixed-size container with proper clipping
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
        ) {
            // Post media thumbnail
            if (post.media.isNotEmpty()) {
                val media = post.media.first()
                val imageUrl = remember(media.type, media.thumbnailUrl, media.url) {
                    when {
                        media.type == com.kissangram.model.MediaType.VIDEO && media.thumbnailUrl != null -> {
                            try {
                                com.kissangram.util.CloudinaryUrlTransformer.transformForThumbnail(media.thumbnailUrl!!)
                            } catch (e: Exception) {
                                media.thumbnailUrl!!
                            }
                        }
                        media.type == com.kissangram.model.MediaType.VIDEO -> {
                            // Generate thumbnail from video URL
                            com.kissangram.util.CloudinaryUrlTransformer.generateVideoThumbnailUrl(media.url)
                        }
                        else -> {
                            try {
                                com.kissangram.util.CloudinaryUrlTransformer.transformForThumbnail(media.url)
                            } catch (e: Exception) {
                                media.url
                            }
                        }
                    }
                }
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PrimaryGreen.copy(alpha = 0.1f))
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Likes overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${post.likesCount}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedUserCard(
    user: UserInfo,
    onClick: () -> Unit,
    onFollowClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(PrimaryGreen, AccentYellow))
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (user.profileImageUrl != null) {
                    AsyncImage(
                        model = user.profileImageUrl,
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
                            .clip(CircleShape)
                            .background(PrimaryGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.name.firstOrNull()?.uppercase() ?: "U",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            // User Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = user.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (user.verificationStatus == VerificationStatus.VERIFIED) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified",
                            modifier = Modifier.size(14.dp),
                            tint = VerifiedBlue
                        )
                    }
                }
                
                Text(
                    text = "@${user.username}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Follow Button
                Button(
                    onClick = {
                        onFollowClick()
                        onClick() // Also navigate to profile
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGreen
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Follow",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedUserGridItem(
    user: UserInfo,
    onClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient or image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                PrimaryGreen.copy(alpha = 0.1f),
                                AccentYellow.copy(alpha = 0.1f)
                            )
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.weight(1f))
                
                // Avatar
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(listOf(PrimaryGreen, AccentYellow))
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (user.profileImageUrl != null) {
                        AsyncImage(
                            model = user.profileImageUrl,
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
                                .clip(CircleShape)
                                .background(PrimaryGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.name.firstOrNull()?.uppercase() ?: "U",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // User Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = user.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (user.verificationStatus == VerificationStatus.VERIFIED) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "Verified",
                                modifier = Modifier.size(12.dp),
                                tint = VerifiedBlue
                            )
                        }
                    }
                    
                    Text(
                        text = "@${user.username}",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Follow Button
                    Button(
                        onClick = {
                            onFollowClick()
                            onClick() // Also navigate to profile
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Follow",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.FARMER -> "Farmer"
    UserRole.EXPERT -> "Expert"
    UserRole.AGRIPRENEUR -> "Agripreneur"
    UserRole.INPUT_SELLER -> "Input Seller"
    UserRole.AGRI_LOVER -> "Agri Lover"
}
