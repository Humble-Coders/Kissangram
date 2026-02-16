package com.kissangram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.kissangram.ui.home.BackgroundColor

private const val MIN_DISPLAY_MS = 1500L

/**
 * Full-screen loader that shows the Trator verde Lottie animation.
 * If [isLoading] becomes false before 1.5 seconds have passed, the loader stays visible
 * for at least 1.5 seconds total.
 */
@Composable
fun FullScreenLottieLoader(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    backgroundColor: Color = BackgroundColor
) {
    var loadingStartTime by remember { mutableLongStateOf(0L) }
    var showLoaderExtra by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            loadingStartTime = System.currentTimeMillis()
            showLoaderExtra = false
        } else if (loadingStartTime > 0L) {
            val start = loadingStartTime
            loadingStartTime = 0L
            showLoaderExtra = true
            val elapsed = System.currentTimeMillis() - start
            val remaining = (MIN_DISPLAY_MS - elapsed).coerceAtLeast(0L)
            kotlinx.coroutines.delay(remaining)
            showLoaderExtra = false
        }
    }

    val showLoader = isLoading || showLoaderExtra
    if (!showLoader) return

    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("trator_verde.json")
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(200.dp)
        )
    }
}
