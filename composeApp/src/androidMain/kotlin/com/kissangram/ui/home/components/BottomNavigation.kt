package com.kissangram.ui.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kissangram.ui.home.*

enum class BottomNavItem(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String,
    val labelHindi: String
) {
    HOME(Icons.Filled.Home, Icons.Outlined.Home, "Home", "होम"),
    SEARCH(Icons.Filled.Search, Icons.Outlined.Search, "Search", "खोजें"),
    POST(Icons.Filled.AddCircle, Icons.Outlined.AddCircle, "Post", "पोस्ट"),
    REELS(Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "Reels", "रील्स"),
    PROFILE(Icons.Filled.Person, Icons.Outlined.Person, "Profile", "प्रोफ़ाइल")
}

@Composable
fun KissangramBottomNavigation(
    selectedItem: BottomNavItem,
    onItemSelected: (BottomNavItem) -> Unit,
    useHindi: Boolean = false,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = BackgroundColor
    ) {
        BottomNavItem.values().forEach { item ->
            val isSelected = item == selectedItem
            val isDisabled = item == BottomNavItem.REELS // Disable Reels as it's not implemented
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = if (useHindi) item.labelHindi else item.label
                    )
                },
                label = {
                    Text(text = if (useHindi) item.labelHindi else item.label)
                },
                selected = isSelected,
                onClick = { if (!isDisabled) onItemSelected(item) },
                enabled = !isDisabled,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryGreen,
                    selectedTextColor = PrimaryGreen,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    disabledIconColor = TextSecondary.copy(alpha = 0.38f),
                    disabledTextColor = TextSecondary.copy(alpha = 0.38f)
                )
            )
        }
    }
}
