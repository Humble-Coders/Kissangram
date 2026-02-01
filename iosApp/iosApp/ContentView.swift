import SwiftUI
import FirebaseAuth
import Shared

struct ContentView: View {
    @State private var currentScreen: Screen = .languageSelection
    @State private var navigationStack: [Screen] = []
    @State private var selectedNavItem: BottomNavItem = .home
    @State private var hasCheckedSession = false
    
    private let prefs = IOSPreferencesRepository()
    
    private var showBottomNav: Bool {
        switch currentScreen {
        case .home, .search, .createPost, .reels, .profile:
            return true
        default:
            return false
        }
    }
    
    var body: some View {
        Group {
            if !hasCheckedSession {
                Color.appBackground.ignoresSafeArea()
                    .onAppear {
                        Task {
                            let completed = (try? await prefs.hasCompletedAuth())?.boolValue ?? false
                            let hasUser = Auth.auth().currentUser != nil
                            await MainActor.run {
                                hasCheckedSession = true
                                if completed && hasUser {
                                    currentScreen = .home
                                    selectedNavItem = .home
                                    navigationStack = []
                                }
                            }
                        }
                    }
            } else if showBottomNav {
                VStack(spacing: 0) {
                    mainAppContent
                    KissangramBottomNavigation(
                        selectedItem: Binding(
                            get: { selectedNavItem },
                            set: { item in
                                selectedNavItem = item
                                switch item {
                                case .home: currentScreen = .home
                                case .search: currentScreen = .search
                                case .post: currentScreen = .createPost
                                case .reels: currentScreen = .reels
                                case .profile: currentScreen = .profile
                                }
                            }
                        )
                    )
                }
            } else {
                authFlowContent
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
                onOtpVerified: {
                    navigateTo(.name)
                },
                onResendOtp: {
                    navigateBack()
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
                onNavigateToPostDetail: { postId in navigateTo(.postDetail(postId: postId)) },
                onNavigateToComments: { postId in navigateTo(.comments(postId: postId)) }
            )
            
        case .search:
            PlaceholderView(title: "Search")
            
        case .createPost:
            PlaceholderView(title: "Create Post")
            
        case .reels:
            PlaceholderView(title: "Reels")
            
        case .profile:
            ProfileView(
                onBackClick: { navigateTo(.home); selectedNavItem = .home },
                onEditProfile: { /* TODO */ },
                onSignOut: {
                    currentScreen = .languageSelection
                    navigationStack = []
                    selectedNavItem = .home
                }
            )
            
        default:
            EmptyView()
        }
    }
    
    private func navigateTo(_ screen: Screen) {
        navigationStack.append(currentScreen)
        currentScreen = screen
        
        // Update selected nav item when navigating to main screens
        switch screen {
        case .home: selectedNavItem = .home
        case .search: selectedNavItem = .search
        case .createPost: selectedNavItem = .post
        case .reels: selectedNavItem = .reels
        case .profile: selectedNavItem = .profile
        default: break
        }
    }
    
    private func navigateBack() {
        if let previousScreen = navigationStack.popLast() {
            currentScreen = previousScreen
        }
    }
}

struct PlaceholderView: View {
    let title: String
    
    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
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
