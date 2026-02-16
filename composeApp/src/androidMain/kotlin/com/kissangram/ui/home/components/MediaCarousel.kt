package com.kissangram.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kissangram.model.PostMedia

/**
 * Horizontal carousel for displaying multiple media items
 * Shows dot indicators when there are multiple items
 * Uses HorizontalPager to match iOS TabView behavior (one item at a time)
 */
@Composable
fun MediaCarousel(
    media: List<PostMedia>,
    modifier: Modifier = Modifier,
    onMediaClick: () -> Unit,
    isVisible: Boolean = true
) {
    if (media.isEmpty()) {
        return
    }
    
    // Single media item - no carousel needed
    if (media.size == 1) {
        MediaItemView(
            media = media.first(),
            modifier = modifier,
            isVisible = isVisible,
            onTap = onMediaClick
        )
        return
    }
    
    // Multiple media items - show carousel with HorizontalPager
    val pagerState = rememberPagerState(pageCount = { media.size })
    
    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp) // Match iOS height
        ) { page ->
            MediaItemView(
                media = media[page],
                modifier = Modifier.fillMaxSize(),
                isVisible = isVisible && (page == pagerState.currentPage),
                onTap = onMediaClick
            )
        }
        
        // Dot indicators overlaid on bottom of media - transparent background
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(media.size) { index ->
                val isSelected = index == pagerState.currentPage
                Surface(
                    modifier = Modifier
                        .size(if (isSelected) 6.dp else 5.dp)
                        .padding(horizontal = 3.dp),
                    shape = CircleShape,
                    color = if (isSelected) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.5f)
                    }
                ) {}
            }
        }
    }
}
