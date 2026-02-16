package com.kissangram.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kissangram.ui.home.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    onNotificationsClick: () -> Unit,
    onMessagesClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Kissangram",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif, // Georgia-like serif font
                color = PrimaryGreen,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        },
        navigationIcon = {
            IconButtonWithBadge(
                onClick = { /* Disabled - not implemented */ },
                icon = Icons.Outlined.Notifications,
                contentDescription = "Notifications",
                badgeColor = AccentYellow,
                showBadge = true,
                enabled = false
            )
        },
        actions = {
            IconButtonWithBadge(
                onClick = { /* Disabled - not implemented */ },
                icon = Icons.Outlined.Email,
                contentDescription = "Messages",
                badgeColor = ErrorRed,
                showBadge = true,
                enabled = false
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BackgroundColor.copy(alpha = 0.95f)
        )
        // Removed windowInsets override to respect notch/status bar
    )
}

@Composable
private fun IconButtonWithBadge(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    badgeColor: Color,
    showBadge: Boolean,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp,
            enabled = enabled
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.38f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        
        if (showBadge) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-1).dp, y = 1.dp)
                    .background(badgeColor, CircleShape)
            )
        }
    }
}
