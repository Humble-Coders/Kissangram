package com.kissangram.ui.languageselection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import kissangram.composeapp.generated.resources.Res
import kissangram.composeapp.generated.resources.choose_language
import kissangram.composeapp.generated.resources.choose_language_punjabi
import kissangram.composeapp.generated.resources.continue_button
import kissangram.composeapp.generated.resources.search_language
import com.kissangram.model.Language
import com.kissangram.ui.components.AutoSizeText
import com.kissangram.viewmodel.LanguageSelectionViewModel
import kotlin.math.min

@Composable
fun LanguageSelectionScreen(
    onLanguageSelected: (String) -> Unit,
    viewModel: LanguageSelectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Responsive scaling factors based on screen width (360dp as baseline)
    val scaleFactor = min(screenWidth.value / 360f, 1.3f)
    val padding = (27 * scaleFactor).dp
    val spacing = (13 * scaleFactor).dp
    val headerSpacing = (9 * scaleFactor).dp
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9F1))
            .padding(padding)
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(headerSpacing)
        ) {
            AutoSizeText(
                text = stringResource(Res.string.choose_language),
                fontSize = (31.5 * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B),
                lineHeight = (47.25 * scaleFactor).sp,
                maxLines = 1,
                minFontSizeScale = 0.6f,
                modifier = Modifier.fillMaxWidth()
            )
            AutoSizeText(
                text = stringResource(Res.string.choose_language_punjabi),
                fontSize = (18 * scaleFactor).sp,
                color = Color(0xFF6B6B6B),
                lineHeight = (27 * scaleFactor).sp,
                maxLines = 1,
                minFontSizeScale = 0.7f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Search Bar
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = viewModel::onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            scaleFactor = scaleFactor
        )
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Language List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy((13.5 * scaleFactor).dp)
        ) {
            items(uiState.filteredLanguages) { language ->
                LanguageItem(
                    language = language,
                    isSelected = language.code == uiState.selectedLanguage.code,
                    onClick = { viewModel.onLanguageSelected(language) },
                    scaleFactor = scaleFactor
                )
            }
        }
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Continue Button
        Button(
            onClick = { viewModel.onContinueClicked(onLanguageSelected) },
            modifier = Modifier
                .fillMaxWidth()
                .height((75 * scaleFactor).dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2D6A4F)
            ),
            shape = RoundedCornerShape((18 * scaleFactor).dp)
        ) {
            AutoSizeText(
                text = stringResource(Res.string.continue_button),
                fontSize = (20.25 * scaleFactor).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                minFontSizeScale = 0.7f
            )
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    scaleFactor: Float = 1f
) {
    Surface(
        modifier = modifier.height((63 * scaleFactor).dp),
        shape = RoundedCornerShape((18 * scaleFactor).dp),
        color = Color.White,
        shadowElevation = (2 * scaleFactor).dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (18 * scaleFactor).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((14 * scaleFactor).dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size((22.5 * scaleFactor).dp),
                tint = Color(0xFF6B6B6B)
            )
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    AutoSizeText(
                        text = stringResource(Res.string.search_language),
                        color = Color(0x801B1B1B),
                        fontSize = (18 * scaleFactor).sp,
                        maxLines = 1,
                        minFontSizeScale = 0.7f
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = (18 * scaleFactor).sp)
            )
        }
    }
}

@Composable
fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit,
    scaleFactor: Float = 1f
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height((109 * scaleFactor).dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape((18 * scaleFactor).dp),
        color = Color.White,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke((1.18 * scaleFactor).dp, Color(0xFF2D6A4F))
        } else {
            null
        },
        shadowElevation = if (isSelected) (4 * scaleFactor).dp else (2 * scaleFactor).dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (23.7 * scaleFactor).dp, vertical = (1.18 * scaleFactor).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((18 * scaleFactor).dp)
        ) {
            // Language Icon Circle
            Box(
                modifier = Modifier
                    .size((36 * scaleFactor).dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color(0xFF2D6A4F)
                        else Color(0x4DE5E6DE)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size((22.5 * scaleFactor).dp),
                    tint = if (isSelected) Color.White else Color(0xFF6B6B6B)
                )
            }
            
            // Language Names
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy((4.5 * scaleFactor).dp)
            ) {
                AutoSizeText(
                    text = language.nativeName,
                    fontSize = (22.5 * scaleFactor).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B1B1B),
                    lineHeight = (33.75 * scaleFactor).sp,
                    maxLines = 1,
                    minFontSizeScale = 0.6f,
                    modifier = Modifier.fillMaxWidth()
                )
                AutoSizeText(
                    text = language.englishName,
                    fontSize = (15.75 * scaleFactor).sp,
                    color = Color(0xFF6B6B6B),
                    lineHeight = (23.625 * scaleFactor).sp,
                    maxLines = 1,
                    minFontSizeScale = 0.7f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Speaker Icon
            Box(
                modifier = Modifier
                    .size((54 * scaleFactor).dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFB703)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Play pronunciation",
                    modifier = Modifier.size((27 * scaleFactor).dp),
                    tint = Color(0xFFFFB703)
                )
            }
        }
    }
}
