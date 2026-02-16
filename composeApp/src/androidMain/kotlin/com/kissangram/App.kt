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
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import androidx.compose.ui.unit.dp

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
        if (destination == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@MaterialTheme
        }
        
        val navController = rememberNavController()
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
        startDestination = startDestination
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
                    navController.navigate(Screen.buildOwnPostDetailRoute(postId))
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
        
        // Own Post Detail Screen (with delete functionality)
        composable(
            route = Screen.OWN_POST_DETAIL,
            arguments = listOf(navArgument("postId") { defaultValue = "" })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            val initialPost = PostCache.get(postId)
            com.kissangram.ui.postdetail.OwnPostDetailScreen(
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
        
        composable(Screen.NOTIFICATIONS) {
            PlaceholderScreen("Notifications")
        }
        
        composable(Screen.MESSAGES) {
            PlaceholderScreen("Messages")
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
