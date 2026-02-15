package com.kissangram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.ui.home.AccentYellow
import com.kissangram.ui.home.PrimaryGreen

private val profileImageCache = mutableMapOf<String, String?>()

@Composable
fun ProfileImageLoader(
    authorId: String,
    authorName: String,
    authorProfileImageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp
) {
    var displayUrl by remember(authorId) {
        mutableStateOf(authorProfileImageUrl?.takeIf { it.isNotBlank() } ?: profileImageCache[authorId]?.takeIf { it.isNotBlank() })
    }

    val context = LocalContext.current

    LaunchedEffect(authorId, authorProfileImageUrl) {
        if (!authorProfileImageUrl.isNullOrBlank()) {
            displayUrl = authorProfileImageUrl
            profileImageCache[authorId] = authorProfileImageUrl
            return@LaunchedEffect
        }
        if (profileImageCache.containsKey(authorId)) {
            displayUrl = profileImageCache[authorId]
            return@LaunchedEffect
        }
        try {
            val prefs = AndroidPreferencesRepository(context.applicationContext)
            val authRepo = AndroidAuthRepository(
                context = context.applicationContext,
                activity = null,
                preferencesRepository = prefs
            )
            val userRepo = FirestoreUserRepository(authRepository = authRepo)
            val user = userRepo.getUser(authorId)
            val url = user?.profileImageUrl?.takeIf { it.isNotBlank() }
            profileImageCache[authorId] = url
            displayUrl = url
        } catch (e: Exception) {
            displayUrl = null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.verticalGradient(listOf(PrimaryGreen, AccentYellow)),
                CircleShape
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!displayUrl.isNullOrBlank()) {
            AsyncImage(
                model = displayUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(size - 4.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size - 4.dp)
                    .background(
                        Brush.verticalGradient(listOf(PrimaryGreen, AccentYellow)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = authorName.firstOrNull()?.uppercase() ?: "",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
