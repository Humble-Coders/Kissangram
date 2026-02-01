package com.kissangram.navigation

sealed class Screen {
    // Auth Flow
    object LanguageSelection : Screen()
    data class PhoneNumber(val languageCode: String) : Screen()
    data class Otp(val phoneNumber: String) : Screen()
    object Name : Screen()
    object RoleSelection : Screen()
    object ExpertDocumentUpload : Screen()
    
    // Main App
    object Home : Screen()
    object Search : Screen()
    object CreatePost : Screen()
    object Reels : Screen()
    object Profile : Screen()
    
    // Detail Screens
    data class PostDetail(val postId: String) : Screen()
    data class Comments(val postId: String) : Screen()
    data class UserProfile(val userId: String) : Screen()
    data class Story(val userId: String) : Screen()
    object Notifications : Screen()
    object Messages : Screen()
}
