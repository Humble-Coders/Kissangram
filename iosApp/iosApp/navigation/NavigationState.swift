import Foundation

enum Screen: Equatable {
    // Auth Flow
    case languageSelection
    case phoneNumber(languageCode: String)
    case otp(phoneNumber: String)
    case name
    case roleSelection
    case expertDocumentUpload
    
    // Main App
    case home
    case search
    case createPost
    case reels
    case profile
    
    // Detail Screens
    case postDetail(postId: String)
    case comments(postId: String)
    case userProfile(userId: String)
    case story(userId: String)
    case notifications
    case messages
}
