package com.kissangram.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * A Text composable that automatically shrinks to fit within its bounds.
 * Useful for handling different screen sizes and accessibility font settings.
 *
 * @param text The text to display
 * @param fontSize The desired font size
 * @param modifier Modifier for the text
 * @param color Text color
 * @param fontWeight Font weight
 * @param lineHeight Line height
 * @param textAlign Text alignment
 * @param maxLines Maximum number of lines
 * @param minFontSizeScale Minimum scale factor (0.0-1.0) for font size. Default 0.5f means text can shrink to 50% of original size.
 */
@Composable
fun AutoSizeText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    minFontSizeScale: Float = 0.5f
) {
    var textStyle by remember(text, fontSize) { mutableStateOf(TextStyle(fontSize = fontSize)) }
    var readyToDraw by remember(text, fontSize) { mutableStateOf(false) }
    
    Text(
        text = text,
        modifier = modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        color = color,
        fontSize = textStyle.fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Visible,
        softWrap = maxLines > 1,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow) {
                val nextFontSize = textStyle.fontSize * 0.95f
                val minFontSize = fontSize * minFontSizeScale
                if (nextFontSize >= minFontSize) {
                    textStyle = textStyle.copy(fontSize = nextFontSize)
                    readyToDraw = false
                } else {
                    textStyle = textStyle.copy(fontSize = minFontSize)
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        }
    )
}
