package com.kissangram.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
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
import com.kissangram.model.User
import com.kissangram.model.UserRole
import com.kissangram.model.VerificationStatus
import com.kissangram.model.Post
import com.kissangram.model.MediaType
import com.kissangram.util.CloudinaryUrlTransformer
import com.kissangram.ui.home.PrimaryGreen
import com.kissangram.ui.home.AccentYellow
import com.kissangram.ui.home.TextPrimary
import com.kissangram.ui.home.TextSecondary
import com.kissangram.ui.home.BackgroundColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kissangram.viewmodel.ProfileViewModel
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Cancel

private val ProfileBackground = Color(0xFFFBF8F0)
private val ExpertGreen = Color(0xFF74C365)
private val VerifiedBlue = Color(0xFF2196F3)
private val PendingOrange = Color(0xFFFF9800)
private val RejectedRed = Color(0xFFBC4749)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onPostClick: (String) -> Unit = {},
    reloadKey: Int = 0, // Key that changes to trigger reload after save
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    
    // Load profile when reloadKey changes (first display or after save)
    LaunchedEffect(reloadKey) {
        viewModel.loadProfile()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        "Profile",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "More",
                                tint = TextPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sign out", color = Color(0xFFBC4749)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.signOut(onSignOut)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ProfileBackground
                )
            )

            /* ---------- CONTENT ---------- */
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
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
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(uiState.error!!, color = TextSecondary)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadProfile() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryGreen
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }

                    else -> {
                        uiState.user?.let { user ->
                            ProfileContent(
                                user = user,
                                posts = uiState.posts,
                                isLoadingPosts = uiState.isLoadingPosts,
                                onEditProfile = onEditProfile,
                                onPostClick = onPostClick,
                                paddingValues = PaddingValues(0.dp) // no Scaffold now
                            )
                        } ?: run {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No profile found", color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun ProfileContent(
    user: User,
    posts: List<Post>,
    isLoadingPosts: Boolean,
    onEditProfile: () -> Unit,
    onPostClick: (String) -> Unit,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))
        // Avatar with gradient border
        Box(
            modifier = Modifier
                .size(126.dp)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            // Gradient border circle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(PrimaryGreen, AccentYellow)))
                    .padding(3.dp)
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
                            fontSize = 48.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Name
        Text(
            text = user.name,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(Modifier.height(4.dp))
        
        // Username
        Text(
            text = "@${user.username}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))
        
        // Role and Verification Status Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Role Badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ExpertGreen.copy(alpha = 0.15f)
            ) {
                Text(
                    text = roleLabel(user.role),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryGreen
                )
            }
            
            // Verification Status Badge (if not UNVERIFIED)
            if (user.verificationStatus != VerificationStatus.UNVERIFIED) {
                Spacer(Modifier.width(8.dp))
                VerificationStatusBadge(verificationStatus = user.verificationStatus)
            }
        }
        user.location?.let { loc ->
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = listOfNotNull(loc.village, loc.district, loc.state, loc.country).joinToString(", ")
                        .ifEmpty { "—" },
                    fontSize = 14.sp,
                    color = TextSecondary,

                    )
            }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onEditProfile,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
        ) {
            Text("Edit Profile", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(count = user.postsCount, label = "Posts")
            StatItem(count = user.followersCount, label = "Followers")
            StatItem(count = user.followingCount, label = "Following")
            StatItem(count = 0, label = "Groups")
        }
        Spacer(Modifier.height(24.dp))
        // About
        Text("About", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = user.bio?.ifBlank { "No bio yet." } ?: "No bio yet.",
            fontSize = 15.sp,
            color = TextSecondary,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(24.dp))
        Text("Posts", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        
        if (isLoadingPosts) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = PrimaryGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            PostThumbnailGrid(
                posts = posts,
                onPostClick = onPostClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatItem(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

@Composable
internal fun PostThumbnailGrid(
    posts: List<Post>,
    onPostClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (posts.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No posts yet",
                fontSize = 15.sp,
                color = TextSecondary
            )
        }
    } else {
        // Use regular Column with Rows instead of LazyVerticalGrid
        // to avoid nested scrolling issues
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            posts.chunked(3).forEach { rowPosts ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowPosts.forEach { post ->
                        PostThumbnailItem(
                            post = post,
                            onClick = { onPostClick(post.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill remaining space if row has less than 3 items
                    repeat(3 - rowPosts.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PostThumbnailItem(
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
        val firstMedia = post.media.firstOrNull()
        
        if (firstMedia != null) {
            when (firstMedia.type) {
                MediaType.IMAGE -> {
                    val imageUrl = remember(firstMedia.url) {
                        try {
                            CloudinaryUrlTransformer.transformForThumbnail(firstMedia.url)
                        } catch (e: Exception) {
                            firstMedia.url
                        }
                    }
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                MediaType.VIDEO -> {
                    val thumbnailUrl = remember(firstMedia.thumbnailUrl, firstMedia.url) {
                        if (firstMedia.thumbnailUrl != null) {
                            try {
                                CloudinaryUrlTransformer.transformForThumbnail(firstMedia.thumbnailUrl!!)
                            } catch (e: Exception) {
                                firstMedia.thumbnailUrl!!
                            }
                        } else {
                            null
                        }
                    }
                    
                    if (thumbnailUrl != null) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    
                    // Play icon overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        } else {
            // No media - show placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE5E6DE)),
                contentAlignment = Alignment.Center
            ) {
                if (post.text.isNotEmpty()) {
                    Text(
                        text = post.text.take(1).uppercase(),
                        fontSize = 24.sp,
                        color = TextSecondary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "Post",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationStatusBadge(verificationStatus: VerificationStatus) {
    val statusInfo = when (verificationStatus) {
        VerificationStatus.VERIFIED -> StatusInfo(
            label = "Verified",
            color = VerifiedBlue,
            icon = Icons.Filled.Verified,
            backgroundColor = VerifiedBlue.copy(alpha = 0.15f)
        )
        VerificationStatus.PENDING -> StatusInfo(
            label = "Pending",
            color = PendingOrange,
            icon = Icons.Filled.Schedule,
            backgroundColor = PendingOrange.copy(alpha = 0.15f)
        )
        VerificationStatus.REJECTED -> StatusInfo(
            label = "Rejected",
            color = RejectedRed,
            icon = Icons.Filled.Cancel,
            backgroundColor = RejectedRed.copy(alpha = 0.15f)
        )
        VerificationStatus.UNVERIFIED -> StatusInfo(
            label = "Unverified",
            color = TextSecondary,
            icon = null,
            backgroundColor = TextSecondary.copy(alpha = 0.15f)
        )
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = statusInfo.backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            statusInfo.icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = statusInfo.color
                )
            }
            Text(
                text = statusInfo.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusInfo.color
            )
        }
    }
}

private data class StatusInfo(
    val label: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val backgroundColor: Color
)
