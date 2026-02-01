package com.kissangram

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.auth.FirebaseAuth
import com.kissangram.navigation.NavController
import com.kissangram.navigation.Screen
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.ui.auth.ExpertDocumentUploadScreen
import com.kissangram.ui.auth.NameScreen
import com.kissangram.ui.auth.OtpScreen
import com.kissangram.ui.auth.PhoneNumberScreen
import com.kissangram.ui.auth.RoleSelectionScreen
import com.kissangram.model.UserRole
import com.kissangram.ui.home.HomeScreen
import com.kissangram.ui.home.components.BottomNavItem
import com.kissangram.ui.home.components.KissangramBottomNavigation
import com.kissangram.ui.languageselection.LanguageSelectionScreen
import com.kissangram.ui.profile.ProfileScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@Preview
fun App() {
    MaterialTheme {
        val context = LocalContext.current
        val prefs = remember(context) { AndroidPreferencesRepository(context.applicationContext) }
        var initialScreen by remember { mutableStateOf<Screen?>(null) }
        
        LaunchedEffect(Unit) {
            initialScreen = withContext(Dispatchers.IO) {
                val completed = prefs.hasCompletedAuth()
                val hasUser = FirebaseAuth.getInstance().currentUser != null
                if (completed && hasUser) Screen.Home else Screen.LanguageSelection
            }
        }
        
        val screen = initialScreen
        if (screen == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@MaterialTheme
        }
        
        val navController = remember(screen) { NavController(screen) }
        val currentScreen = navController.currentScreen
        
        // Determine if we should show bottom navigation
        val showBottomNav = currentScreen is Screen.Home ||
                currentScreen is Screen.Search ||
                currentScreen is Screen.CreatePost ||
                currentScreen is Screen.Reels ||
                currentScreen is Screen.Profile
        
        val selectedNavItem = when (currentScreen) {
            is Screen.Home -> BottomNavItem.HOME
            is Screen.Search -> BottomNavItem.SEARCH
            is Screen.CreatePost -> BottomNavItem.POST
            is Screen.Reels -> BottomNavItem.REELS
            is Screen.Profile -> BottomNavItem.PROFILE
            else -> BottomNavItem.HOME
        }
        
        if (showBottomNav) {
            Scaffold(
                bottomBar = {
                    KissangramBottomNavigation(
                        selectedItem = selectedNavItem,
                        onItemSelected = { item ->
                            when (item) {
                                BottomNavItem.HOME -> navController.navigateTo(Screen.Home)
                                BottomNavItem.SEARCH -> navController.navigateTo(Screen.Search)
                                BottomNavItem.POST -> navController.navigateTo(Screen.CreatePost)
                                BottomNavItem.REELS -> navController.navigateTo(Screen.Reels)
                                BottomNavItem.PROFILE -> navController.navigateTo(Screen.Profile)
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    MainAppContent(navController, currentScreen)
                }
            }
        } else {
            AuthFlowContent(navController, currentScreen)
        }
    }
}

@Composable
private fun AuthFlowContent(navController: NavController, currentScreen: Screen) {
    when (currentScreen) {
        is Screen.LanguageSelection -> {
            LanguageSelectionScreen(
                onLanguageSelected = { languageCode ->
                    navController.navigateTo(Screen.PhoneNumber(languageCode))
                }
            )
        }
        
        is Screen.PhoneNumber -> {
            PhoneNumberScreen(
                onBackClick = { navController.navigateBack() },
                onOtpSent = { phoneNumber ->
                    navController.navigateTo(Screen.Otp(phoneNumber))
                }
            )
        }
        
        is Screen.Otp -> {
            OtpScreen(
                phoneNumber = currentScreen.phoneNumber,
                onBackClick = { navController.navigateBack() },
                onOtpVerified = {
                    navController.navigateTo(Screen.Name)
                },
                onResendOtp = {
                    navController.navigateBack()
                }
            )
        }
        
        is Screen.Name -> {
            NameScreen(
                onNameSaved = {
                    navController.navigateTo(Screen.RoleSelection)
                }
            )
        }
        
        is Screen.RoleSelection -> {
            RoleSelectionScreen(
                onRoleSelected = { selectedRole ->
                    if (selectedRole == UserRole.EXPERT) {
                        navController.navigateTo(Screen.ExpertDocumentUpload)
                    } else {
                        navController.navigateTo(Screen.Home)
                    }
                }
            )
        }
        
        is Screen.ExpertDocumentUpload -> {
            ExpertDocumentUploadScreen(
                onComplete = {
                    navController.navigateTo(Screen.Home)
                },
                onSkip = {
                    navController.navigateTo(Screen.Home)
                }
            )
        }
        
        else -> {}
    }
}

@Composable
private fun MainAppContent(navController: NavController, currentScreen: Screen) {
    when (currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                onNavigateToNotifications = { navController.navigateTo(Screen.Notifications) },
                onNavigateToMessages = { navController.navigateTo(Screen.Messages) },
                onNavigateToProfile = { userId -> navController.navigateTo(Screen.UserProfile(userId)) },
                onNavigateToStory = { userId -> navController.navigateTo(Screen.Story(userId)) },
                onNavigateToPostDetail = { postId -> navController.navigateTo(Screen.PostDetail(postId)) },
                onNavigateToComments = { postId -> navController.navigateTo(Screen.Comments(postId)) }
            )
        }
        
        is Screen.Search -> {
            PlaceholderScreen("Search")
        }
        
        is Screen.CreatePost -> {
            PlaceholderScreen("Create Post")
        }
        
        is Screen.Reels -> {
            PlaceholderScreen("Reels")
        }
        
        is Screen.Profile -> {
            ProfileScreen(
                onBackClick = { navController.navigateTo(Screen.Home) },
                onEditProfile = { /* TODO: Edit profile */ },
                onSignOut = { navController.replaceAllWith(Screen.LanguageSelection) }
            )
        }
        
        else -> {}
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(text = "$title - Coming Soon")
    }
}