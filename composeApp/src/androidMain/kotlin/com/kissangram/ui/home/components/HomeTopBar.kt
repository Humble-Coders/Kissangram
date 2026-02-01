package com.kissangram.ui.home.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kissangram.ui.home.*
import com.kissangram.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    onNotificationsClick: () -> Unit,
    onMessagesClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Notifications Button
                IconButtonWithBadge(
                    onClick = onNotificationsClick,
                    icon = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    badgeColor = AccentYellow,
                    showBadge = true
                )
                
                // Logo Image
                Image(
                    painter = painterResource(id = R.drawable.kissangram_logo),
                    contentDescription = "Kissangram",
                    modifier = Modifier.size(100.dp)
                )
                
                // Messages Button
                IconButtonWithBadge(
                    onClick = onMessagesClick,
                    icon = Icons.Outlined.Email,
                    contentDescription = "Messages",
                    badgeColor = ErrorRed,
                    showBadge = true
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BackgroundColor.copy(alpha = 0.95f)
        ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

@Composable
private fun IconButtonWithBadge(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    badgeColor: Color,
    showBadge: Boolean
) {
    Box {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        if (showBadge) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .background(badgeColor, CircleShape)
            )
        }
    }
}
