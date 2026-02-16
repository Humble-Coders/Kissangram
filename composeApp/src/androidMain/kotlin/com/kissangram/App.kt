package com.kissangram

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import android.view.HapticFeedbackConstants
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import com.kissangram.R
import com.google.firebase.auth.FirebaseAuth
import com.kissangram.navigation.Screen
import com.kissangram.navigation.PostCache
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.ui.auth.ExpertDocumentUploadScreen
import com.kissangram.ui.auth.NameScreen
import com.kissangram.ui.auth.OtpScreen
import com.kissangram.ui.auth.PhoneNumberScreen
import com.kissangram.ui.auth.RoleSelectionScreen
import com.kissangram.ui.auth.WelcomeBackScreen
import com.kissangram.model.UserRole
import com.kissangram.ui.home.HomeScreen
import com.kissangram.ui.home.components.BottomNavItem
import com.kissangram.ui.home.components.KissangramBottomNavigation
import com.kissangram.ui.languageselection.LanguageSelectionScreen
import com.kissangram.ui.profile.EditProfileScreen
import com.kissangram.ui.profile.OtherUserProfileScreen
import com.kissangram.ui.profile.ProfileScreen
import com.kissangram.ui.postdetail.PostDetailScreen
import com.kissangram.ui.search.SearchScreen
import com.kissangram.ui.createpost.CreatePostScreen
import com.kissangram.ui.createstory.CreateStoryScreen
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidFollowRepository
import com.kissangram.repository.AndroidStorageRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.repository.FirestoreStoryRepository
import com.kissangram.usecase.CreateStoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
@Preview
fun App() {
    MaterialTheme {
        val context = LocalContext.current
        val prefs = remember(context) { AndroidPreferencesRepository(context.applicationContext) }
        var startDestination by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(Unit) {
            startDestination = withContext(Dispatchers.IO) {
                val completed = prefs.hasCompletedAuth()
                val hasUser = FirebaseAuth.getInstance().currentUser != null
                if (completed && hasUser) Screen.HOME else Screen.LANGUAGE_SELECTION
            }
        }
        
        val destination = startDestination
        var showSplash by remember { mutableStateOf(true) }
        
        // Show splash screen while initializing or until splash animation completes
        if (destination == null || showSplash) {
            SplashScreen(
                onSplashComplete = {
                    showSplash = false
                }
            )
            // Don't show main app until splash is complete and destination is ready
            if (destination == null || showSplash) {
                return@MaterialTheme
            }
        }
        
        val navController = rememberNavController()
        val view = LocalView.current
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        
        // Determine if we should show bottom navigation
        val showBottomNav = currentRoute in listOf(
            Screen.HOME,
            Screen.SEARCH,
            Screen.CREATE_POST,
            Screen.REELS,
            Screen.PROFILE
        )
        
        val selectedNavItem = when (currentRoute) {
            Screen.HOME -> BottomNavItem.HOME
            Screen.SEARCH -> BottomNavItem.SEARCH
            Screen.CREATE_POST -> BottomNavItem.POST
            Screen.REELS -> BottomNavItem.REELS
            Screen.PROFILE -> BottomNavItem.PROFILE
            else -> BottomNavItem.HOME
        }

        if (showBottomNav) {
            Scaffold(
                bottomBar = {
                    KissangramBottomNavigation(
                        selectedItem = selectedNavItem,
                        onItemSelected = { item ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            when (item) {
                                BottomNavItem.HOME -> navController.navigate(Screen.HOME) {
                                    // Save and restore state for bottom nav tabs
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                BottomNavItem.SEARCH -> navController.navigate(Screen.SEARCH) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                BottomNavItem.POST -> navController.navigate(Screen.CREATE_POST) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                BottomNavItem.REELS -> navController.navigate(Screen.REELS) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                BottomNavItem.PROFILE -> navController.navigate(Screen.PROFILE) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                    NavigationGraph(
                        navController = navController,
                        startDestination = destination,
                        bottomNavPadding = paddingValues
                    )
                }
            }
        } else {
            // Show screens without bottom nav (auth flow, edit profile, create story, user profile)
            NavigationGraph(
                navController = navController,
                startDestination = destination,
                bottomNavPadding = PaddingValues(0.dp)
            )
        }
    }
}

@Composable
private fun NavigationGraph(
    navController: NavHostController,
    startDestination: String,
    bottomNavPadding: PaddingValues
) {
    // Track if profile should reload (after save from EditProfile)
    var profileReloadKey by remember { mutableStateOf(0) }
    
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(500)
            ) + fadeIn(animationSpec = tween(500))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(500)
            ) + fadeOut(animationSpec = tween(500))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(500)
            ) + fadeIn(animationSpec = tween(500))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(500)
            ) + fadeOut(animationSpec = tween(500))
        }
    ) {
        // Auth Flow
        composable(Screen.LANGUAGE_SELECTION) {
            LanguageSelectionScreen(
                onLanguageSelected = { languageCode ->
                    navController.navigate(Screen.buildPhoneNumberRoute(languageCode))
                }
            )
        }
        
        composable(
            route = Screen.PHONE_NUMBER,
            arguments = listOf(navArgument("languageCode") { defaultValue = "" })
        ) { backStackEntry ->
            val languageCode = backStackEntry.arguments?.getString("languageCode") ?: ""
            PhoneNumberScreen(
                onBackClick = { navController.popBackStack() },
                onOtpSent = { phoneNumber ->
                    navController.navigate(Screen.buildOtpRoute(phoneNumber))
                }
            )
        }
        
        composable(
            route = Screen.OTP,
            arguments = listOf(navArgument("phoneNumber") { defaultValue = "" })
        ) { backStackEntry ->
            val phoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: ""
            OtpScreen(
                phoneNumber = phoneNumber,
                onBackClick = { navController.popBackStack() },
                onExistingUser = { userName ->
                    navController.navigate(Screen.buildWelcomeBackRoute(userName)) {
                        popUpTo(Screen.LANGUAGE_SELECTION) { inclusive = false }
                    }
                },
                onNewUser = {
                    navController.navigate(Screen.NAME) {
                        popUpTo(Screen.LANGUAGE_SELECTION) { inclusive = false }
                    }
                },
                onResendOtp = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.WELCOME_BACK,
            arguments = listOf(navArgument("userName") { defaultValue = "" })
        ) { backStackEntry ->
            val encodedUserName = backStackEntry.arguments?.getString("userName") ?: ""
            val userName = try {
                java.net.URLDecoder.decode(encodedUserName, "UTF-8")
            } catch (e: Exception) {
                encodedUserName
            }
            WelcomeBackScreen(
                userName = userName,
                onContinue = {
                    navController.navigate(Screen.HOME) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.NAME) {
            NameScreen(
                onNameSaved = {
                    navController.navigate(Screen.ROLE_SELECTION)
                }
            )
        }
        
        composable(Screen.ROLE_SELECTION) {
            RoleSelectionScreen(
                onRoleSelected = { selectedRole ->
                    if (selectedRole == UserRole.EXPERT) {
                        navController.navigate(Screen.EXPERT_DOCUMENT_UPLOAD)
                    } else {
                        navController.navigate(Screen.HOME) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }
                }
            )
        }
        
        composable(Screen.EXPERT_DOCUMENT_UPLOAD) {
            ExpertDocumentUploadScreen(
                onComplete = {
                    navController.navigate(Screen.HOME) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.HOME) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }
        
        // Main App
        composable(Screen.HOME) {
            HomeScreen(
                onNavigateToNotifications = { navController.navigate(Screen.NOTIFICATIONS) },
                onNavigateToMessages = { navController.navigate(Screen.MESSAGES) },
                onNavigateToProfile = { userId -> 
                    navController.navigate(Screen.buildUserProfileRoute(userId))
                },
                onNavigateToStory = { userId -> 
                    navController.navigate(Screen.buildStoryRoute(userId))
                },
                onNavigateToCreateStory = { navController.navigate(Screen.CREATE_STORY) },
                onNavigateToPostDetail = { postId, post -> 
                    post?.let { PostCache.put(postId, it) }
                    navController.navigate(Screen.buildPostDetailRoute(postId))
                },
                bottomNavPadding = bottomNavPadding
            )
        }
        
        composable(Screen.SEARCH) {
            SearchScreen(
                onUserClick = { userId -> 
                    navController.navigate(Screen.buildUserProfileRoute(userId))
                },
                onPostClick = { postId, post ->
                    navController.navigate(Screen.buildPostDetailRoute(postId))
                },
                onFollowClick = { userId ->
                    // Follow handled in ViewModel
                },
                bottomNavPadding = bottomNavPadding
            )
        }
        
        composable(Screen.CREATE_POST) {
            CreatePostScreen(
                onBackClick = { navController.popBackStack() },
                onPostClick = { postInput ->
                    // TODO: Handle post creation with postInput
                    navController.navigate(Screen.HOME) {
                        popUpTo(Screen.HOME) { inclusive = false }
                    }
                },
                bottomNavPadding = bottomNavPadding
            )
        }
        
        composable(Screen.CREATE_STORY) {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val authRepository = remember {
                AndroidAuthRepository(
                    context = context.applicationContext,
                    activity = null,
                    preferencesRepository = AndroidPreferencesRepository(context.applicationContext)
                )
            }
            val storageRepository = remember { AndroidStorageRepository(context.applicationContext) }
            val userRepository = remember { FirestoreUserRepository(authRepository = authRepository) }
            val followRepository = remember {
                AndroidFollowRepository(
                    authRepository = authRepository,
                    userRepository = userRepository
                )
            }
            val storyRepository = remember {
                FirestoreStoryRepository(
                    authRepository = authRepository,
                    followRepository = followRepository
                )
            }
            val createStoryUseCase = remember {
                CreateStoryUseCase(
                    storageRepository = storageRepository,
                    storyRepository = storyRepository,
                    authRepository = authRepository,
                    userRepository = userRepository
                )
            }
            
            var isLoading by remember { mutableStateOf(false) }
            
            var errorMessage by remember { mutableStateOf<String?>(null) }
            
            CreateStoryScreen(
                onBackClick = { navController.popBackStack() },
                onStoryClick = { storyInput ->
                    scope.launch {
                        try {
                            isLoading = true
                            errorMessage = null
                            android.util.Log.d("CreateStory", "📤 Starting story creation")
                            android.util.Log.d("CreateStory", "   - Media Data Size: ${storyInput.mediaData?.size ?: 0} bytes")
                            android.util.Log.d("CreateStory", "   - Media Type: ${storyInput.mediaType}")
                            val story = createStoryUseCase(storyInput)
                            android.util.Log.d("CreateStory", "✅ Story created successfully: ${story.id}")
                            isLoading = false
                            navController.navigate(Screen.HOME) {
                                popUpTo(Screen.HOME) { inclusive = false }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("CreateStory", "❌ Story creation failed", e)
                            android.util.Log.e("CreateStory", "   - Error Type: ${e.javaClass.simpleName}")
                            android.util.Log.e("CreateStory", "   - Error Message: ${e.message}")
                            e.printStackTrace()
                            isLoading = false
                            errorMessage = e.message ?: "Failed to create story. Please try again."
                        }
                    }
                },
                isLoading = isLoading,
                errorMessage = errorMessage,
                onDismissError = { errorMessage = null }
            )
        }
        
        composable(Screen.REELS) {
            PlaceholderScreen("Reels", bottomNavPadding = bottomNavPadding)
        }
        
        composable(Screen.PROFILE) {
            val context = LocalContext.current
            val prefs = remember { AndroidPreferencesRepository(context.applicationContext) }
            val authRepository = remember {
                AndroidAuthRepository(
                    context = context.applicationContext,
                    activity = null,
                    preferencesRepository = prefs
                )
            }
            val currentUserId = remember { 
                FirebaseAuth.getInstance().currentUser?.uid 
            }
            
            ProfileScreen(
                onBackClick = { navController.navigate(Screen.HOME) },
                onEditProfile = { navController.navigate(Screen.EDIT_PROFILE) },
                onSignOut = { 
                    navController.navigate(Screen.LANGUAGE_SELECTION) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
                onPostClick = { postId, post ->
                    post?.let { PostCache.put(postId, it) }
                    navController.navigate(Screen.buildPostDetailRoute(postId))
                },
                onFollowersClick = {
                    currentUserId?.let { userId ->
                        navController.navigate(Screen.buildFollowersListRoute(userId))
                    }
                },
                onFollowingClick = {
                    currentUserId?.let { userId ->
                        navController.navigate(Screen.buildFollowingListRoute(userId))
                    }
                },
                reloadKey = profileReloadKey,
                bottomNavPadding = bottomNavPadding
            )
        }
        
        composable(Screen.EDIT_PROFILE) {
            EditProfileScreen(
                onBackClick = { navController.popBackStack() },
                onSaveClick = {
                    profileReloadKey++
                    navController.popBackStack()
                },
                onNavigateToExpertDocument = { navController.navigate(Screen.EXPERT_DOCUMENT_UPLOAD) }
            )
        }
        
        composable(
            route = Screen.USER_PROFILE,
            arguments = listOf(navArgument("userId") { defaultValue = "" })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val context = LocalContext.current
            val prefs = remember { AndroidPreferencesRepository(context.applicationContext) }
            val authRepository = remember {
                AndroidAuthRepository(
                    context = context.applicationContext,
                    activity = null,
                    preferencesRepository = prefs
                )
            }
            
            val currentUserId = remember { 
                FirebaseAuth.getInstance().currentUser?.uid 
            }
            
            if (currentUserId == userId) {
                ProfileScreen(
                    onBackClick = { navController.popBackStack() },
                    onEditProfile = { navController.navigate(Screen.EDIT_PROFILE) },
                    onSignOut = { 
                        navController.navigate(Screen.LANGUAGE_SELECTION) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onPostClick = { postId, post ->
                        post?.let { PostCache.put(postId, it) }
                        navController.navigate(Screen.buildOwnPostDetailRoute(postId))
                    },
                    reloadKey = profileReloadKey
                )
            } else {
                OtherUserProfileScreen(
                    userId = userId,
                    onBackClick = { navController.popBackStack() },
                    onPostClick = { postId, post ->
                        post?.let { PostCache.put(postId, it) }
                        navController.navigate(Screen.buildPostDetailRoute(postId))
                    },
                    onFollowersClick = {
                        navController.navigate(Screen.buildFollowersListRoute(userId))
                    },
                    onFollowingClick = {
                        navController.navigate(Screen.buildFollowingListRoute(userId))
                    }
                )
            }
        }
        
        // Post Detail Screen (post + comments - unified view)
        composable(
            route = Screen.POST_DETAIL,
            arguments = listOf(navArgument("postId") { defaultValue = "" })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            val initialPost = PostCache.get(postId)
            PostDetailScreen(
                postId = postId,
                initialPost = initialPost,
                onBackClick = { navController.popBackStack() },
                onNavigateToProfile = { userId ->
                    navController.navigate(Screen.buildUserProfileRoute(userId))
                }
            )
        }
        
        composable(
            route = Screen.STORY,
            arguments = listOf(navArgument("userId") { defaultValue = "" })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            com.kissangram.ui.story.StoryScreen(
                userId = userId,
                onBackClick = { 
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.FOLLOWERS_LIST,
            arguments = listOf(navArgument("userId") { defaultValue = "" })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            com.kissangram.ui.profile.FollowersListScreen(
                userId = userId,
                type = com.kissangram.viewmodel.FollowersListType.FOLLOWERS,
                onBackClick = { navController.popBackStack() },
                onUserClick = { targetUserId ->
                    navController.navigate(Screen.buildUserProfileRoute(targetUserId))
                },
                bottomNavPadding = bottomNavPadding
            )
        }
        
        composable(
            route = Screen.FOLLOWING_LIST,
            arguments = listOf(navArgument("userId") { defaultValue = "" })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            com.kissangram.ui.profile.FollowersListScreen(
                userId = userId,
                type = com.kissangram.viewmodel.FollowersListType.FOLLOWING,
                onBackClick = { navController.popBackStack() },
                onUserClick = { targetUserId ->
                    navController.navigate(Screen.buildUserProfileRoute(targetUserId))
                },
                bottomNavPadding = bottomNavPadding
            )
        }
        
        composable(Screen.NOTIFICATIONS) {
            PlaceholderScreen("Notifications")
        }
        
        composable(Screen.MESSAGES) {
            PlaceholderScreen("Messages")
        }
    }
}

@Composable
private fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Logo scale animation
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        label = "scale"
    )

    // Logo alpha for subtle fade in
    val logoAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "logoAlpha"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000) // Show splash for 2 seconds
        visible = false
        kotlinx.coroutines.delay(400) // Wait for exit animation
        onSplashComplete()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFAFAFA),
                            Color(0xFFFFFFFF)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo with scale and alpha animation
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_playstore),
                    contentDescription = "Kissangram Logo",
                    modifier = Modifier
                        .size(180.dp)
                        .scale(scale)
                        .alpha(logoAlpha),
                    contentScale = ContentScale.Fit
                )



            }

            // Loading indicator at bottom
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .size(32.dp)
                    .alpha(logoAlpha * 0.6f),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
@Composable
private fun PlaceholderScreen(title: String, bottomNavPadding: PaddingValues = PaddingValues(0.dp)) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomNavPadding.calculateBottomPadding()),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "$title - Coming Soon")
    }
}
