import Foundation
import Shared

enum Screen {
    // Auth Flow
    case languageSelection
    case phoneNumber(languageCode: String)
    case otp(phoneNumber: String)
    case welcomeBack(userName: String)
    case name
    case roleSelection
    case expertDocumentUpload
    
    // Main App
    case home
    case search
    case createPost
    case createStory
    case reels
    case profile
    
    // Detail Screens
    case postDetail(postId: String)
    case comments(postId: String, post: Post?)
    case userProfile(userId: String)
    case story(userId: String)
    case notifications
    case messages
    case editProfile
}
