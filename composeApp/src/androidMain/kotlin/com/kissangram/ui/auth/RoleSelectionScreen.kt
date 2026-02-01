package com.kissangram.ui.auth

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kissangram.model.UserRole
import com.kissangram.ui.components.AutoSizeText
import com.kissangram.viewmodel.RoleSelectionViewModel
import kotlin.math.min

@Composable
fun RoleSelectionScreen(
    onRoleSelected: (UserRole?) -> Unit,
    viewModel: RoleSelectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    
    // Responsive scaling factors - more conservative to prevent too small text
    val scaleFactor = (screenWidth.value / 360f).coerceIn(0.85f, 1.2f)
    val padding = 24.dp
    val spacing = 10.dp
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9F1))
            .padding(padding)
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            AutoSizeText(
                text = "How do you use Kissangram?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B),
                lineHeight = 30.sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                minFontSizeScale = 0.75f
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            AutoSizeText(
                text = "Choose the option that best describes you",
                fontSize = 14.sp,
                color = Color(0xFF6B6B6B),
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                minFontSizeScale = 0.85f
            )
        }
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            // Farmer card (primary, large)
            RoleCard(
                role = UserRole.FARMER,
                isSelected = uiState.selectedRole == UserRole.FARMER,
                isLarge = true,
                title = "Farmer",
                description = "I grow crops and share field updates",
                icon = Icons.Default.Eco,
                scaleFactor = scaleFactor,
                onRoleSelected = { viewModel.selectRole(UserRole.FARMER) }
            )
            
            // Or separator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF6B6B6B).copy(alpha = 0.2f),
                    thickness = 1.dp
                )
                
                Text(
                    text = "or",
                    fontSize = 14.sp,
                    color = Color(0xFF6B6B6B)
                )
                
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF6B6B6B).copy(alpha = 0.2f),
                    thickness = 1.dp
                )
            }
            
            // Grid of 4 smaller cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RoleCard(
                        role = UserRole.AGRIPRENEUR,
                        isSelected = uiState.selectedRole == UserRole.AGRIPRENEUR,
                        isLarge = false,
                        title = "Agripreneur",
                        description = null,
                        icon = Icons.Default.TrendingUp,
                        scaleFactor = scaleFactor,
                        onRoleSelected = { viewModel.selectRole(UserRole.AGRIPRENEUR) }
                    )
                    
                    RoleCard(
                        role = UserRole.INPUT_SELLER,
                        isSelected = uiState.selectedRole == UserRole.INPUT_SELLER,
                        isLarge = false,
                        title = "Input Seller",
                        description = null,
                        icon = Icons.Default.ShoppingBag,
                        scaleFactor = scaleFactor,
                        onRoleSelected = { viewModel.selectRole(UserRole.INPUT_SELLER) }
                    )
                }
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RoleCard(
                        role = UserRole.EXPERT,
                        isSelected = uiState.selectedRole == UserRole.EXPERT,
                        isLarge = false,
                        title = "Expert / Advisor",
                        description = null,
                        icon = Icons.Default.School,
                        scaleFactor = scaleFactor,
                        onRoleSelected = { viewModel.selectRole(UserRole.EXPERT) }
                    )
                    
                    RoleCard(
                        role = UserRole.AGRI_LOVER,
                        isSelected = uiState.selectedRole == UserRole.AGRI_LOVER,
                        isLarge = false,
                        title = "Agri Lover",
                        description = null,
                        icon = Icons.Default.Favorite,
                        scaleFactor = scaleFactor,
                        onRoleSelected = { viewModel.selectRole(UserRole.AGRI_LOVER) }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Next Button
        Button(
            onClick = {
                // log
                println("RoleSelectionScreen: next button clicked with role selected=${uiState.selectedRole}")
                viewModel.saveRole(
                    onSuccess = {
                        onRoleSelected(uiState.selectedRole)
                    },
                    onError = { error ->
                        // Error is handled in ViewModel
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            enabled = !uiState.isLoading && uiState.selectedRole != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2D6A4F),
                disabledContainerColor = Color(0xFF2D6A4F).copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text(
                    text = "Next",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
        
        // Error message
        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
        }
    }
}

@Composable
fun RoleCard(
    role: UserRole,
    isSelected: Boolean,
    isLarge: Boolean,
    title: String,
    description: String?,
    icon: ImageVector,
    scaleFactor: Float,
    onRoleSelected: () -> Unit
) {
    // Fixed sizes to prevent too small text
    val iconSize = if (isLarge) 90.dp else 50.dp
    val iconInnerSize = if (isLarge) 45.dp else 25.dp
    val titleSize = if (isLarge) 26.sp else 14.sp
    val descriptionSize = 15.sp
    val padding = if (isLarge) 28.dp else 16.dp
    val spacing = 14.dp
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRoleSelected() },
        shape = RoundedCornerShape(if (isLarge) 22.dp else 16.dp),
        color = Color(0xFFF8F9F1),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isLarge) 2.dp else 1.5.dp,
            color = if (isSelected) {
                Color(0xFF2D6A4F)
            } else {
                Color(0xFF2D6A4F).copy(alpha = if (isLarge) 0.15f else 0.12f)
            }
        ),
        shadowElevation = if (isSelected) 3.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(
                        Color(0xFF2D6A4F).copy(alpha = if (isLarge) 0.15f else 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFF2D6A4F),
                    modifier = Modifier.size(iconInnerSize)
                )
            }
            
            // Title
            AutoSizeText(
                text = title,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                minFontSizeScale = 0.8f
            )
            
            // Description (only for large card)
            description?.let {
                AutoSizeText(
                    text = it,
                    fontSize = descriptionSize,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6B6B6B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    minFontSizeScale = 0.85f
                )
            }
        }
    }
}
