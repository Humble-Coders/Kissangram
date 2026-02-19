import SwiftUI
import FirebaseAuth
import Shared
import Foundation
import UIKit

// Import EditProfileView
extension ContentView {
    // EditProfileView is in the same module, so it's accessible
}

// MARK: - Identifiable wrapper for story fullScreenCover
struct StoryPresentation: Identifiable {
    let id: String // userId
}

struct ContentView: View {
    // Auth state
    @State private var currentScreen: Screen = .languageSelection
    @State private var authStack: [Screen] = [] // Only for auth flow
    @State private var hasCheckedSession = false
    
    // Tab state
    @State private var selectedTab: BottomNavItem = .home
    @State private var previousTab: BottomNavItem? = nil
    @State private var homeRefreshTrigger: Int = 0
    @State private var profileReloadKey: Int = 0
    
    // Per-tab NavigationStack paths
    @State private var homePath = NavigationPath()
    @State private var searchPath = NavigationPath()
    @State private var profilePath = NavigationPath()
    
    // Side-channel for passing Post objects (Post is not Hashable)
    @State private var postCache: [String: Post] = [:]
    
    // Full-screen cover state (Story & CreateStory)
    @State private var activeStory: StoryPresentation? = nil
    @State private var showCreateStory = false
    
    private let prefs = IOSPreferencesRepository()
    
    /// Whether the user has completed auth and should see the main app
    private var isAuthenticated: Bool {
        if case .home = currentScreen { return true }
        return false
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
                                }
                            }
                        }
                    }
            } else if isAuthenticated {
                // MARK: - Main App (TabView always alive)
                TabView(selection: $selectedTab) {
                    // ── Home Tab ──
                    NavigationStack(path: $homePath) {
                        HomeView(
                            onNavigateToNotifications: { /* Disabled - not implemented */ },
                            onNavigateToMessages: { /* Disabled - not implemented */ },
                            onNavigateToProfile: { userId in
                                homePath.append(AppDestination.userProfile(userId: userId))
                            },
                            onNavigateToStory: { userId in
                                activeStory = StoryPresentation(id: userId)
                            },
                            onNavigateToCreateStory: {
                                showCreateStory = true
                            },
                            onNavigateToPostDetail: { postId, post in
                                if let post = post { postCache[postId] = post }
                                homePath.append(AppDestination.postDetail(postId: postId))
                            },
                            refreshTrigger: homeRefreshTrigger
                        )
                        .navigationDestination(for: AppDestination.self) { dest in
                            destinationView(for: dest, path: $homePath)
                        }
                    }
                    .fullScreenCover(item: $activeStory) { story in
                        StoryView(
                            userId: story.id,
                            onBackClick: { activeStory = nil }
                        )
                    }
                    .fullScreenCover(isPresented: $showCreateStory) {
                        CreateStoryViewContent(
                            onBackClick: { showCreateStory = false },
                            onStoryCreated: { showCreateStory = false }
                        )
                    }
                    .tag(BottomNavItem.home)
                    .tabItem {
                        Label("Home", systemImage: "house.fill")
                    }
                    
                    // ── Search Tab ──
                    NavigationStack(path: $searchPath) {
                        SearchView(
                            onUserClick: { userId in
                                searchPath.append(AppDestination.userProfile(userId: userId))
                            },
                            onPostClick: { postId, post in
                                if let post = post { postCache[postId] = post }
                                searchPath.append(AppDestination.postDetail(postId: postId))
                            },
                            onFollowClick: { _ in }
                        )
                        .navigationDestination(for: AppDestination.self) { dest in
                            destinationView(for: dest, path: $searchPath)
                        }
                    }
                    .tag(BottomNavItem.search)
                    .tabItem {
                        Label("Search", systemImage: "magnifyingglass")
                    }
                    
                    // ── Create Post Tab ──
                    NavigationStack {
                        CreatePostView(
                            onBackClick: { selectedTab = .home },
                            onPostClick: { postInput in
                                // TODO: Handle post creation with postInput
                                selectedTab = .home
                            }
                        )
                    }
                    .tag(BottomNavItem.post)
                    .tabItem {
                        Label("Post", systemImage: "plus.circle.fill")
                    }
                    
                    // ── Reels Tab ──
                    PlaceholderView(title: "Reels")
                        .tag(BottomNavItem.reels)
                        .tabItem {
                            Label("Reels", systemImage: "play.circle.fill")
                        }
                    
                    // ── Profile Tab ──
                    NavigationStack(path: $profilePath) {
                        ProfileView(
                            onBackClick: { selectedTab = .home },
                            onEditProfile: {
                                profilePath.append(AppDestination.editProfile)
                            },
                            onSignOut: {
                                // Reset everything and go to auth
                                homePath = NavigationPath()
                                searchPath = NavigationPath()
                                profilePath = NavigationPath()
                                postCache = [:]
                                currentScreen = .languageSelection
                                selectedTab = .home
                            },
                            onPostClick: { postId, post in
                                if let post = post { postCache[postId] = post }
                                profilePath.append(AppDestination.postDetail(postId: postId))
                            },
                            onFollowersClick: {
                                if let currentUserId = Auth.auth().currentUser?.uid {
                                    profilePath.append(AppDestination.followersList(userId: currentUserId))
                                }
                            },
                            onFollowingClick: {
                                if let currentUserId = Auth.auth().currentUser?.uid {
                                    profilePath.append(AppDestination.followingList(userId: currentUserId))
                                }
                            },
                            reloadKey: profileReloadKey
                        )
                        .navigationDestination(for: AppDestination.self) { dest in
                            destinationView(for: dest, path: $profilePath)
                        }
                    }
                    .tag(BottomNavItem.profile)
                    .tabItem {
                        Label("Profile", systemImage: "person.fill")
                    }
                }
                .onChange(of: selectedTab) { newValue in
                    // If switching to home tab from another bottom bar tab, refresh the feed
                    if newValue == .home, let prevTab = previousTab, prevTab != .home {
                        homeRefreshTrigger += 1
                    }
                    previousTab = newValue
                }
            } else {
                // MARK: - Auth Flow (unchanged)
                ZStack {
                    Color.appBackground
                    authFlowContent
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: hasCheckedSession)
        .animation(.easeInOut(duration: 0.2), value: isAuthenticated)
    }
    
    // MARK: - Shared Destination Builder
    /// Builds the view for a given AppDestination, wiring navigation callbacks to the correct path.
    @ViewBuilder
    private func destinationView(for dest: AppDestination, path: Binding<NavigationPath>) -> some View {
        switch dest {
        case .postDetail(let postId):
            CommentsView(
                postId: postId,
                initialPost: postCache[postId],
                onNavigateToProfile: { userId in
                    path.wrappedValue.append(AppDestination.userProfile(userId: userId))
                }
            )
            
        case .userProfile(let userId):
            let currentUserId = Auth.auth().currentUser?.uid
            if currentUserId == userId {
                // Viewing own profile
                ProfileView(
                    onBackClick: { /* dismiss handled by NavigationStack */ },
                    onEditProfile: {
                        path.wrappedValue.append(AppDestination.editProfile)
                    },
                    onSignOut: {
                        homePath = NavigationPath()
                        searchPath = NavigationPath()
                        profilePath = NavigationPath()
                        postCache = [:]
                        currentScreen = .languageSelection
                        selectedTab = .home
                    },
                    onPostClick: { postId, post in
                        if let post = post { postCache[postId] = post }
                        path.wrappedValue.append(AppDestination.postDetail(postId: postId))
                    },
                    onFollowersClick: {
                        path.wrappedValue.append(AppDestination.followersList(userId: userId))
                    },
                    onFollowingClick: {
                        path.wrappedValue.append(AppDestination.followingList(userId: userId))
                    },
                    reloadKey: profileReloadKey
                )
            } else {
                // Viewing another user's profile
                OtherUserProfileView(
                    userId: userId,
                    onPostClick: { postId, post in
                        if let post = post { postCache[postId] = post }
                        path.wrappedValue.append(AppDestination.postDetail(postId: postId))
                    },
                    onFollowersClick: {
                        path.wrappedValue.append(AppDestination.followersList(userId: userId))
                    },
                    onFollowingClick: {
                        path.wrappedValue.append(AppDestination.followingList(userId: userId))
                    }
                )
            }
            
        case .editProfile:
            EditProfileView(
                onSaveClick: {
                    profileReloadKey += 1
                },
                onNavigateToExpertDocument: {
                    path.wrappedValue.append(AppDestination.expertDocumentUpload)
                }
            )
            
        case .expertDocumentUpload:
            ExpertDocumentUploadView(
                onComplete: {
                    // Pop back (dismiss handles it via NavigationStack)
                },
                onSkip: {
                    // Pop back
                }
            )
            
        case .followersList(let userId):
            FollowersListView(
                userId: userId,
                type: .followers,
                onUserClick: { targetUserId in
                    path.wrappedValue.append(AppDestination.userProfile(userId: targetUserId))
                }
            )
            
        case .followingList(let userId):
            FollowersListView(
                userId: userId,
                type: .following,
                onUserClick: { targetUserId in
                    path.wrappedValue.append(AppDestination.userProfile(userId: targetUserId))
                }
            )
        }
    }
    
    // MARK: - Auth Flow (unchanged)
    @ViewBuilder
    private var authFlowContent: some View {
        switch currentScreen {
        case .languageSelection:
            LanguageSelectionView { languageCode in
                authNavigateTo(.phoneNumber(languageCode: languageCode))
            }
            
        case .phoneNumber:
            PhoneNumberView(
                onBackClick: { authNavigateBack() },
                onOtpSent: { phoneNumber in
                    authNavigateTo(.otp(phoneNumber: phoneNumber))
                }
            )
            
        case .otp(let phoneNumber):
            OtpView(
                phoneNumber: phoneNumber,
                onBackClick: { authNavigateBack() },
                onExistingUser: { userName in
                    authNavigateTo(.welcomeBack(userName: userName))
                },
                onNewUser: {
                    authNavigateTo(.name)
                },
                onResendOtp: {
                    authNavigateBack()
                }
            )
            
        case .welcomeBack(let userName):
            WelcomeBackView(
                userName: userName,
                onContinue: {
                    currentScreen = .home
                    authStack = []
                    selectedTab = .home
                }
            )
            
        case .name:
            NameView(
                onNameSaved: {
                    authNavigateTo(.roleSelection)
                }
            )
            
        case .roleSelection:
            RoleSelectionView(
                onRoleSelected: { selectedRole in
                    if selectedRole == .expert {
                        authNavigateTo(.expertDocumentUpload)
                    } else {
                        currentScreen = .home
                        authStack = []
                        selectedTab = .home
                    }
                }
            )
            
        case .expertDocumentUpload:
            ExpertDocumentUploadView(
                onComplete: {
                    currentScreen = .home
                    authStack = []
                    selectedTab = .home
                },
                onSkip: {
                    currentScreen = .home
                    authStack = []
                    selectedTab = .home
                }
            )
            
        default:
            EmptyView()
        }
    }
    
    // MARK: - Auth Navigation Helpers
    private func authNavigateTo(_ screen: Screen) {
        authStack.append(currentScreen)
        currentScreen = screen
    }
    
    private func authNavigateBack() {
        if let prev = authStack.popLast() {
            currentScreen = prev
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
