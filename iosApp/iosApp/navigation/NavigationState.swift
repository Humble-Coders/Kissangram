import Foundation
import Shared

// MARK: - Auth Flow Screens (managed by manual stack, unchanged)
enum Screen {
    // Auth Flow
    case languageSelection
    case phoneNumber(languageCode: String)
    case otp(phoneNumber: String)
    case welcomeBack(userName: String)
    case name
    case roleSelection
    case expertDocumentUpload
    
    // Main App (used to track auth → main app transition)
    case home
}

// MARK: - In-App Navigation Destinations (Hashable, used with NavigationStack)
enum AppDestination: Hashable {
    case postDetail(postId: String)
    case userProfile(userId: String)
    case editProfile
    case expertDocumentUpload
    case followersList(userId: String)
    case followingList(userId: String)
}
