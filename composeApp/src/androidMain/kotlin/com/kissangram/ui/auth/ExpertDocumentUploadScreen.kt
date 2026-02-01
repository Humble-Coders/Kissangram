package com.kissangram.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kissangram.ui.components.AutoSizeText
import com.kissangram.viewmodel.ExpertDocumentUploadViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.min

@Composable
fun ExpertDocumentUploadScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: ExpertDocumentUploadViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Responsive scaling factors based on screen width (360dp as baseline)
    val scaleFactor = min(screenWidth.value / 360f, 1.3f)
    val padding = (27 * scaleFactor).dp
    val spacing = (13.5 * scaleFactor).dp
    
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadDocument(it, context, userId)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9F1))
            .padding(padding)
    ) {
        // Header - Fixed at top
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            AutoSizeText(
                text = "Verify your expertise",
                fontSize = (31.5 * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B),
                lineHeight = (47.25 * scaleFactor).sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                minFontSizeScale = 0.6f
            )
            
            Spacer(modifier = Modifier.height((9 * scaleFactor).dp))
            
            AutoSizeText(
                text = "Upload credentials to get verified and earn trust",
                fontSize = (17.1 * scaleFactor).sp,
                color = Color(0xFF6B6B6B),
                lineHeight = (25.65 * scaleFactor).sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                minFontSizeScale = 0.7f
            )
        }
        
        Spacer(modifier = Modifier.height(padding))
        
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
        
        // Why Verify Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            AutoSizeText(
                text = "WHY VERIFY?",
                fontSize = (15.75 * scaleFactor).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2D6A4F),
                modifier = Modifier.fillMaxWidth(),
                minFontSizeScale = 0.7f
            )
            
            BenefitCard(
                icon = Icons.Default.VerifiedUser,
                title = "Verified Badge",
                description = "Stand out with a blue checkmark",
                scaleFactor = scaleFactor
            )
            
            BenefitCard(
                icon = Icons.Default.TrendingUp,
                title = "Higher Visibility",
                description = "Get featured in expert listings",
                scaleFactor = scaleFactor
            )
            
            BenefitCard(
                icon = Icons.Default.Shield,
                title = "Build Trust",
                description = "Farmers connect with verified experts",
                scaleFactor = scaleFactor
            )
        }
        
        Spacer(modifier = Modifier.height(padding))
        
        // Upload Documents Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            AutoSizeText(
                text = "UPLOAD DOCUMENTS",
                fontSize = (15.75 * scaleFactor).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2D6A4F),
                modifier = Modifier.fillMaxWidth(),
                minFontSizeScale = 0.7f
            )
            
            // Upload Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !uiState.isUploading) {
                        documentPickerLauncher.launch("*/*")
                    },
                shape = RoundedCornerShape((22 * scaleFactor).dp),
                color = Color(0xFF2D6A4F).copy(alpha = 0.03f),
                border = androidx.compose.foundation.BorderStroke(
                    (1.962 * scaleFactor).dp,
                    Color(0xFF2D6A4F)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding((20 * scaleFactor).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    Box(
                        modifier = Modifier
                            .size((63 * scaleFactor).dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2D6A4F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Upload",
                            tint = Color.White,
                            modifier = Modifier.size((31.5 * scaleFactor).dp)
                        )
                    }
                    
                    AutoSizeText(
                        text = "Tap to upload credentials",
                        fontSize = (16.875 * scaleFactor).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B1B1B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        minFontSizeScale = 0.7f
                    )
                    
                    AutoSizeText(
                        text = "Certificates, degrees, or official ID",
                        fontSize = (14.625 * scaleFactor).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6B6B6B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        minFontSizeScale = 0.7f
                    )
                }
            }
            
            // Upload Progress
            if (uiState.isUploading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy((8 * scaleFactor).dp)
                ) {
                    LinearProgressIndicator(
                        progress = uiState.uploadProgress,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF2D6A4F)
                    )
                    
                    AutoSizeText(
                        text = "Uploading... ${(uiState.uploadProgress * 100).toInt()}%",
                        fontSize = (14 * scaleFactor).sp,
                        color = Color(0xFF6B6B6B),
                        modifier = Modifier.fillMaxWidth(),
                        minFontSizeScale = 0.7f
                    )
                }
            }
            
            // Uploaded File Name
            uiState.uploadedFileName?.let { fileName ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((8 * scaleFactor).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Uploaded",
                        tint = Color(0xFF2D6A4F),
                        modifier = Modifier.size((20 * scaleFactor).dp)
                    )
                    AutoSizeText(
                        text = fileName,
                        fontSize = (14 * scaleFactor).sp,
                        color = Color(0xFF1B1B1B),
                        modifier = Modifier.weight(1f),
                        minFontSizeScale = 0.7f
                    )
                }
            }
            
            // Skip Option
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape((22 * scaleFactor).dp),
                color = Color(0xFFFFB703).copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    (0.654 * scaleFactor).dp,
                    Color(0xFFFFB703).copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding((18.5 * scaleFactor).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AutoSizeText(
                        text = "You can skip this step and verify later",
                        fontSize = (15.75 * scaleFactor).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B1B1B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        minFontSizeScale = 0.7f
                    )
                    
                    AutoSizeText(
                        text = "from your profile",
                        fontSize = (15.75 * scaleFactor).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B1B1B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        minFontSizeScale = 0.7f
                    )
                }
            }
        }
        }
        
        Spacer(modifier = Modifier.height(padding))
        
        // Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            // Complete Setup Button
            Button(
                onClick = {
                    viewModel.completeSetup(onComplete)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height((75 * scaleFactor).dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2D6A4F)
                ),
                shape = RoundedCornerShape((18 * scaleFactor).dp)
            ) {
                AutoSizeText(
                    text = "Complete Setup",
                    fontSize = (20.25 * scaleFactor).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    minFontSizeScale = 0.7f
                )
            }
            
            // Skip Button
            TextButton(
                onClick = {
                    viewModel.skipVerification(onSkip)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                AutoSizeText(
                    text = "I'll do this later",
                    fontSize = (16.875 * scaleFactor).sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B6B6B),
                    minFontSizeScale = 0.7f
                )
            }
        }
        
        // Error message
        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height((9 * scaleFactor).dp))
            AutoSizeText(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = (14 * scaleFactor).sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                minFontSizeScale = 0.7f
            )
        }
    }
}

@Composable
fun BenefitCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    scaleFactor: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape((22 * scaleFactor).dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(
            (0.654 * scaleFactor).dp,
            Color(0xFF2D6A4F).copy(alpha = 0.1f)
        ),
        shadowElevation = (1 * scaleFactor).dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding((18.5 * scaleFactor).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((13.5 * scaleFactor).dp)
        ) {
            Box(
                modifier = Modifier
                    .size((45 * scaleFactor).dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFB703).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFFFFB703),
                    modifier = Modifier.size((22.5 * scaleFactor).dp)
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy((4.5 * scaleFactor).dp)
            ) {
                AutoSizeText(
                    text = title,
                    fontSize = (16.875 * scaleFactor).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B1B1B),
                    modifier = Modifier.fillMaxWidth(),
                    minFontSizeScale = 0.7f
                )
                
                AutoSizeText(
                    text = description,
                    fontSize = (14.625 * scaleFactor).sp,
                    color = Color(0xFF6B6B6B),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    minFontSizeScale = 0.7f
                )
            }
        }
    }
}
