import SwiftUI
import FirebaseAuth
import Shared
import Foundation
import UIKit

// Import EditProfileView
extension ContentView {
    // EditProfileView is in the same module, so it's accessible
}

struct ContentView: View {
    @State private var currentScreen: Screen = .languageSelection
    @State private var navigationStack: [Screen] = []
    @State private var selectedTab: BottomNavItem = .home
    @State private var hasCheckedSession = false
    @State private var profileReloadKey: Int = 0 // Key to trigger ProfileView reload after save
    
    private let prefs = IOSPreferencesRepository()
    
    private var showBottomNav: Bool {
        switch currentScreen {
        case .home, .search, .createPost, .reels, .profile:
            return true
        case .editProfile, .createStory, .story:
            return false // Don't show bottom nav on edit profile, create story, and story viewer
        default:
            return false
        }
    }
    
    init() {
        // Customize tab bar appearance
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(Color.appBackground)
        appearance.shadowColor = UIColor.black.withAlphaComponent(0.05)
        
        // Selected item color
        appearance.stackedLayoutAppearance.selected.iconColor = UIColor(Color.primaryGreen)
        appearance.stackedLayoutAppearance.selected.titleTextAttributes = [
            .foregroundColor: UIColor(Color.primaryGreen),
            .font: UIFont.systemFont(ofSize: 12, weight: .semibold)
        ]
        
        // Normal item color
        appearance.stackedLayoutAppearance.normal.iconColor = UIColor(Color.textSecondary)
        appearance.stackedLayoutAppearance.normal.titleTextAttributes = [
            .foregroundColor: UIColor(Color.textSecondary),
            .font: UIFont.systemFont(ofSize: 12, weight: .medium)
        ]
        
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
    
    var body: some View {
        Group {
            if !hasCheckedSession {
                Color.appBackground
                    .onAppear {
                        Task {
                            let completed = (try? await prefs.hasCompletedAuth())?.boolValue ?? false
                            let hasUser = Auth.auth().currentUser != nil
                            await MainActor.run {
                                hasCheckedSession = true
                                if completed && hasUser {
                                    currentScreen = .home
                                    selectedTab = .home
                                    navigationStack = []
                                }
                            }
                        }
                    }
            } else if showBottomNav {
                TabView(selection: $selectedTab) {
                    // Home Tab
                    HomeView(
                        onNavigateToNotifications: { navigateTo(.notifications) },
                        onNavigateToMessages: { navigateTo(.messages) },
                        onNavigateToProfile: { userId in navigateTo(.userProfile(userId: userId)) },
                        onNavigateToStory: { userId in navigateTo(.story(userId: userId)) },
                        onNavigateToCreateStory: { navigateTo(.createStory) },
                        onNavigateToPostDetail: { postId, post in navigateTo(.postDetail(postId: postId, post: post)) }
                    )
                    .tag(BottomNavItem.home)
                    .tabItem {
                        Label("Home", systemImage: "house.fill")
                    }
                    
                    // Search Tab
                    SearchView(
                        onUserClick: { userId in navigateTo(.userProfile(userId: userId)) },
                        onPostClick: { postId, post in navigateTo(.postDetail(postId: postId, post: post)) },
                        onFollowClick: { _ in }
                    )
                    .tag(BottomNavItem.search)
                    .tabItem {
                        Label("Search", systemImage: "magnifyingglass")
                    }
                    
                    // Create Post Tab
                    CreatePostView(
                        onBackClick: { navigateBack() },
                        onPostClick: { postInput in
                            // TODO: Handle post creation with postInput
                            navigateTo(.home)
                        }
                    )
                    .tag(BottomNavItem.post)
                    .tabItem {
                        Label("Post", systemImage: "plus.circle.fill")
                    }
                    
                    // Reels Tab
                    PlaceholderView(title: "Reels")
                        .tag(BottomNavItem.reels)
                        .tabItem {
                            Label("Reels", systemImage: "play.circle.fill")
                        }
                    
                    // Profile Tab
                    ProfileView(
                        onBackClick: { navigateTo(.home); selectedTab = .home },
                        onEditProfile: { navigateTo(.editProfile) },
                        onSignOut: {
                            currentScreen = .languageSelection
                            navigationStack = []
                            selectedTab = .home
                        },
                        onPostClick: { postId, post in
                            navigateTo(.postDetail(postId: postId, post: post))
                        },
                        reloadKey: profileReloadKey
                    )
                    .tag(BottomNavItem.profile)
                    .tabItem {
                        Label("Profile", systemImage: "person.fill")
                    }
                }
                .onChange(of: selectedTab) { newValue in
                    // Clear navigation stack when using bottom nav
                    navigationStack = []
                    switch newValue {
                    case .home: currentScreen = .home
                    case .search: currentScreen = .search
                    case .post: currentScreen = .createPost
                    case .reels: currentScreen = .reels
                    case .profile: currentScreen = .profile
                    }
                }
            } else {
                ZStack {
                    // Only ignore safe areas for fullscreen screens like CreateStory and Story
                    if case .createStory = currentScreen {
                        Color.appBackground.ignoresSafeArea()
                    } else if case .story = currentScreen {
                        Color.appBackground.ignoresSafeArea()
                    } else {
                        Color.appBackground
                    }
                    
                    // Show either auth flow, edit profile, create story, user profile, or detail screens (which don't have bottom nav)
                    if case .editProfile = currentScreen {
                        mainAppContent
                    } else if case .createStory = currentScreen {
                        mainAppContent
                    } else if case .story = currentScreen {
                        mainAppContent
                    } else if case .userProfile = currentScreen {
                        mainAppContent
                    } else if case .postDetail = currentScreen {
                        mainAppContent
                    } else if case .notifications = currentScreen {
                        mainAppContent
                    } else if case .messages = currentScreen {
                        mainAppContent
                    } else {
                        authFlowContent
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: hasCheckedSession)
        .animation(.easeInOut(duration: 0.2), value: showBottomNav)
    }
    
    @ViewBuilder
    private var authFlowContent: some View {
        switch currentScreen {
        case .languageSelection:
            LanguageSelectionView { languageCode in
                navigateTo(.phoneNumber(languageCode: languageCode))
            }
            
        case .phoneNumber:
            PhoneNumberView(
                onBackClick: {
                    navigateBack()
                },
                onOtpSent: { phoneNumber in
                    navigateTo(.otp(phoneNumber: phoneNumber))
                }
            )
            
        case .otp(let phoneNumber):
            OtpView(
                phoneNumber: phoneNumber,
                onBackClick: {
                    navigateBack()
                },
                onExistingUser: { userName in
                    navigateTo(.welcomeBack(userName: userName))
                },
                onNewUser: {
                    navigateTo(.name)
                },
                onResendOtp: {
                    navigateBack()
                }
            )
            
        case .welcomeBack(let userName):
            WelcomeBackView(
                userName: userName,
                onContinue: {
                    navigateTo(.home)
                    selectedTab = .home
                    navigationStack = []
                }
            )
            
        case .name:
            NameView(
                onNameSaved: {
                    navigateTo(.roleSelection)
                }
            )
            
        case .roleSelection:
            RoleSelectionView(
                onRoleSelected: { selectedRole in
                    if selectedRole == .expert {
                        navigateTo(.expertDocumentUpload)
                    } else {
                        navigateTo(.home)
                    }
                }
            )
            
        case .expertDocumentUpload:
            ExpertDocumentUploadView(
                onComplete: {
                    navigateTo(.home)
                },
                onSkip: {
                    navigateTo(.home)
                }
            )
            
        default:
            EmptyView()
        }
    }
    
    @ViewBuilder
    private var mainAppContent: some View {
        switch currentScreen {
        case .home:
            HomeView(
                onNavigateToNotifications: { navigateTo(.notifications) },
                onNavigateToMessages: { navigateTo(.messages) },
                onNavigateToProfile: { userId in navigateTo(.userProfile(userId: userId)) },
                onNavigateToStory: { userId in navigateTo(.story(userId: userId)) },
                onNavigateToCreateStory: { navigateTo(.createStory) },
                onNavigateToPostDetail: { postId, post in navigateTo(.postDetail(postId: postId, post: post)) }
            )
            
        case .search:
            SearchView(
                onUserClick: { userId in navigateTo(.userProfile(userId: userId)) },
                onPostClick: { postId, post in navigateTo(.postDetail(postId: postId, post: post)) },
                onFollowClick: { _ in }
            )
            
        case .createPost:
            CreatePostView(
                onBackClick: { navigateBack() },
                onPostClick: { postInput in
                    // TODO: Handle post creation with postInput
                    navigateTo(.home)
                }
            )
            
        case .createStory:
            CreateStoryViewContent(
                onBackClick: { navigateBack() },
                onStoryCreated: { navigateTo(.home) }
            )
            
        case .reels:
            PlaceholderView(title: "Reels")
            
        case .profile:
            ProfileView(
                onBackClick: { navigateTo(.home); selectedTab = .home },
                onEditProfile: { navigateTo(.editProfile) },
                onSignOut: {
                    currentScreen = .languageSelection
                    navigationStack = []
                    selectedTab = .home
                },
                onPostClick: { postId, post in
                    navigateTo(.postDetail(postId: postId, post: post))
                },
                reloadKey: profileReloadKey
            )
            
        case .editProfile:
            EditProfileView(
                onBackClick: { navigateBack() },
                onSaveClick: {
                    // Increment reload key to trigger ProfileView reload
                    profileReloadKey += 1
                    navigateBack()
                },
                onNavigateToExpertDocument: { navigateTo(.expertDocumentUpload) }
            )
            
        case .userProfile(let userId):
            // Check if viewing own profile or another user's profile
            let currentUserId = Auth.auth().currentUser?.uid
            
            if currentUserId == userId {
                // Viewing own profile - show ProfileView
                ProfileView(
                    onBackClick: { navigateBack() },
                    onEditProfile: { navigateTo(.editProfile) },
                    onSignOut: {
                        currentScreen = .languageSelection
                        navigationStack = []
                        selectedTab = .home
                    },
                    onPostClick: { postId, post in navigateTo(.postDetail(postId: postId, post: post)) },
                    reloadKey: profileReloadKey
                )
            } else {
                // Viewing another user's profile - show OtherUserProfileView
                OtherUserProfileView(
                    userId: userId,
                    onBackClick: { navigateBack() },
                    onPostClick: { postId, post in navigateTo(.postDetail(postId: postId, post: post)) }
                )
            }
            
        case .story(let userId):
            StoryView(
                userId: userId,
                onBackClick: { 
                    navigateBack()
                }
            )
            
        case .postDetail(let postId, let post):
            CommentsView(
                postId: postId,
                initialPost: post,
                onBackClick: { navigateBack() },
                onNavigateToProfile: { userId in navigateTo(.userProfile(userId: userId)) }
            )
            
        default:
            EmptyView()
        }
    }
    
    private func logToFile(_ location: String, _ message: String, _ data: [String: Any] = [:]) {
        let logData: [String: Any] = [
            "location": location,
            "message": message,
            "data": data,
            "timestamp": Int(Date().timeIntervalSince1970 * 1000)
        ]
        
        if let jsonData = try? JSONSerialization.data(withJSONObject: logData),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            let logLine = jsonString + "\n"
            let logPath = "/Users/rishibhardwaj/AndroidStudioProjects/Kissangram/.cursor/debug.log"
            
            let directory = (logPath as NSString).deletingLastPathComponent
            try? FileManager.default.createDirectory(atPath: directory, withIntermediateDirectories: true, attributes: nil)
            
            if let logFile = FileHandle(forWritingAtPath: logPath) {
                logFile.seekToEndOfFile()
                logFile.write(logLine.data(using: .utf8) ?? Data())
                logFile.closeFile()
            } else {
                try? logLine.write(toFile: logPath, atomically: true, encoding: .utf8)
            }
        }
    }
    
    private func navigateTo(_ screen: Screen) {
        print("🔵 [ContentView:navigateTo] Navigating to screen: \(screen)")
        logToFile("ContentView:navigateTo", "Navigating to screen", [
            "screen": "\(screen)",
            "currentScreen": "\(currentScreen)",
            "navigationStackCount": navigationStack.count
        ])
        navigationStack.append(currentScreen)
        currentScreen = screen
        print("🔵 [ContentView:navigateTo] currentScreen updated to: \(currentScreen)")
        
        // Update selected tab when navigating to main screens
        switch screen {
        case .home: selectedTab = .home
        case .search: selectedTab = .search
        case .createPost: selectedTab = .post
        case .reels: selectedTab = .reels
        case .profile: selectedTab = .profile
        default: break
        }
        print("🔵 [ContentView:navigateTo] Navigation completed")
    }
    
    private func navigateBack() {
        if let previousScreen = navigationStack.popLast() {
            currentScreen = previousScreen
            
            // Update selected tab based on the previous screen
            switch previousScreen {
            case .home: selectedTab = .home
            case .search: selectedTab = .search
            case .createPost: selectedTab = .post
            case .reels: selectedTab = .reels
            case .profile: selectedTab = .profile
            default: break
            }
        } else {
            // If stack is empty and we're on a main screen, navigate to home
            if case .createPost = currentScreen {
                currentScreen = .home
                selectedTab = .home
            }
        }
    }
}

struct PlaceholderView: View {
    let title: String
    
    var body: some View {
        ZStack {
            Color.appBackground
            Text("\(title) - Coming Soon")
                .foregroundColor(.textSecondary)
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
