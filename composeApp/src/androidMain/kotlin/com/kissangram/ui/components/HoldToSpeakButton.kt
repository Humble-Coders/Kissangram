package com.kissangram.ui.components

import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Style configuration for HoldToSpeakButton.
 * Allows customization of visual appearance while maintaining consistent behavior.
 */
data class HoldToSpeakButtonStyle(
    val buttonHeight: androidx.compose.ui.unit.Dp = 83.dp,
    val cornerRadius: androidx.compose.ui.unit.Dp = 18.dp,
    val iconSize: androidx.compose.ui.unit.Dp = 45.dp,
    val iconInnerSize: androidx.compose.ui.unit.Dp = 24.dp,
    val textSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    val textFontWeight: FontWeight = FontWeight.SemiBold,
    val horizontalPadding: androidx.compose.ui.unit.Dp = 19.dp,
    val spacing: androidx.compose.ui.unit.Dp = 14.dp,
    val scaleAnimation: Float = 1.1f,
    val backgroundColorIdle: Color = Color(0xFFF8F9F1),
    val backgroundColorListening: Color = Color(0x1A2D6A4F),
    val borderColorIdle: Color = Color(0x332D6A4F),
    val borderColorListening: Color = Color(0xFF2D6A4F),
    val iconColorIdle: Color = Color(0xFF2D6A4F),
    val iconColorListening: Color = Color(0xFFFFB703),
    val textColorIdle: Color = Color(0xFF1B1B1B),
    val textColorListening: Color = Color(0xFF2D6A4F),
    val layoutDirection: LayoutDirection = LayoutDirection.HORIZONTAL
) {
    enum class LayoutDirection {
        HORIZONTAL,
        VERTICAL
    }
}

/**
 * Default style matching the phone number screen (stable version).
 */
val DefaultHoldToSpeakButtonStyle = HoldToSpeakButtonStyle()

/**
 * Reusable hold-to-speak button component for speech recognition.
 * 
 * @param isListening Whether speech recognition is currently active
 * @param isLoading Whether the component is in a loading state
 * @param onStartListening Callback when user starts holding (press down)
 * @param onStopListening Callback when user releases (lift up)
 * @param defaultText Text to show when not listening (default: "Hold to speak")
 * @param listeningText Text to show when listening (default: "Listening... Release when done")
 * @param style Style configuration for customizing the button appearance
 * @param modifier Optional modifier for the button
 */
@Composable
fun HoldToSpeakButton(
    isListening: Boolean,
    isLoading: Boolean,
    isProcessing: Boolean = false,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    defaultText: String = "Hold to speak",
    listeningText: String = "Listening... Release when done",
    processingText: String = "Processing...",
    style: HoldToSpeakButtonStyle = DefaultHoldToSpeakButtonStyle,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (isListening) style.scaleAnimation else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "mic_scale"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(style.buttonHeight)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        if (!isListening && !isLoading && !isProcessing) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onStartListening()
                        }
                    },
                    onDragEnd = {
                        Log.d("HoldToSpeakButton", "onDragEnd called - isListening: $isListening")
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        // Always call onStopListening when drag ends, even if isListening is false
                        // This ensures we stop recognition if it's still running
                        onStopListening()
                    },
                    onDrag = { _, _ -> }
                )
            },
        shape = RoundedCornerShape(style.cornerRadius),
        color = when {
            isProcessing -> style.backgroundColorListening
            isListening -> style.backgroundColorListening
            else -> style.backgroundColorIdle
        },
        border = androidx.compose.foundation.BorderStroke(
            1.18.dp,
            when {
                isProcessing -> style.borderColorListening
                isListening -> style.borderColorListening
                else -> style.borderColorIdle
            }
        )
    ) {
        when (style.layoutDirection) {
            HoldToSpeakButtonStyle.LayoutDirection.HORIZONTAL -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = style.horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(style.spacing)
                ) {
                    Box(
                        modifier = Modifier
                            .size(style.iconSize)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isProcessing -> style.iconColorListening
                                    isListening -> style.iconColorListening
                                    else -> style.iconColorIdle
                                }
                            )
                            .scale(scale),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(style.iconInnerSize),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Microphone",
                                tint = Color.White,
                                modifier = Modifier.size(style.iconInnerSize)
                            )
                        }
                    }
                    Text(
                        text = when {
                            isProcessing -> processingText
                            isListening -> listeningText
                            else -> defaultText
                        },
                        fontSize = style.textSize,
                        fontWeight = style.textFontWeight,
                        color = when {
                            isProcessing -> style.textColorListening
                            isListening -> style.textColorListening
                            else -> style.textColorIdle
                        }
                    )
                }
            }
            HoldToSpeakButtonStyle.LayoutDirection.VERTICAL -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = style.horizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(style.iconSize)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isProcessing -> style.iconColorListening
                                    isListening -> style.iconColorListening
                                    else -> style.iconColorIdle
                                }
                            )
                            .scale(scale),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(style.iconInnerSize),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Microphone",
                                tint = Color.White,
                                modifier = Modifier.size(style.iconInnerSize)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(style.spacing))
                    Text(
                        text = when {
                            isProcessing -> processingText
                            isListening -> listeningText
                            else -> defaultText
                        },
                        fontSize = style.textSize,
                        fontWeight = style.textFontWeight,
                        color = when {
                            isProcessing -> style.textColorListening
                            isListening -> style.textColorListening
                            else -> style.textColorIdle
                        }
                    )
                }
            }
        }
    }
}
