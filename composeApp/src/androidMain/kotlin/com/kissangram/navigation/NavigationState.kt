package com.kissangram.navigation

sealed class Screen {
    // Auth Flow
    object LanguageSelection : Screen()
    data class PhoneNumber(val languageCode: String) : Screen()
    data class Otp(val phoneNumber: String) : Screen()
    data class WelcomeBack(val userName: String) : Screen()
    object Name : Screen()
    object RoleSelection : Screen()
    object ExpertDocumentUpload : Screen()
    
    // Main App
    object Home : Screen()
    object Search : Screen()
    object CreatePost : Screen()
    object CreateStory : Screen()
    object Reels : Screen()
    object Profile : Screen()
    
    // Detail Screens
    data class PostDetail(val postId: String) : Screen()
    data class UserProfile(val userId: String) : Screen()
    data class Story(val userId: String) : Screen()
    object Notifications : Screen()
    object Messages : Screen()
    object EditProfile : Screen()
    
    companion object {
        // Route constants
        const val LANGUAGE_SELECTION = "language_selection"
        const val PHONE_NUMBER = "phone_number/{languageCode}"
        const val OTP = "otp/{phoneNumber}"
        const val WELCOME_BACK = "welcome_back/{userName}"
        const val NAME = "name"
        const val ROLE_SELECTION = "role_selection"
        const val EXPERT_DOCUMENT_UPLOAD = "expert_document_upload"
        const val HOME = "home"
        const val SEARCH = "search"
        const val CREATE_POST = "create_post"
        const val CREATE_STORY = "create_story"
        const val REELS = "reels"
        const val PROFILE = "profile"
        const val POST_DETAIL = "post_detail/{postId}"
        const val USER_PROFILE = "user_profile/{userId}"
        const val STORY = "story/{userId}"
        const val NOTIFICATIONS = "notifications"
        const val MESSAGES = "messages"
        const val EDIT_PROFILE = "edit_profile"
        
        // Helper function to convert Screen to route
        fun Screen.toRoute(): String {
            return when (this) {
                is LanguageSelection -> LANGUAGE_SELECTION
                is PhoneNumber -> buildPhoneNumberRoute(languageCode)
                is Otp -> buildOtpRoute(phoneNumber)
                is WelcomeBack -> buildWelcomeBackRoute(userName)
                is Name -> NAME
                is RoleSelection -> ROLE_SELECTION
                is ExpertDocumentUpload -> EXPERT_DOCUMENT_UPLOAD
                is Home -> HOME
                is Search -> SEARCH
                is CreatePost -> CREATE_POST
                is CreateStory -> CREATE_STORY
                is Reels -> REELS
                is Profile -> PROFILE
                is PostDetail -> buildPostDetailRoute(postId)
                is UserProfile -> buildUserProfileRoute(userId)
                is Story -> buildStoryRoute(userId)
                is Notifications -> NOTIFICATIONS
                is Messages -> MESSAGES
                is EditProfile -> EDIT_PROFILE
            }
        }
        
        // Route builder functions
        fun buildPhoneNumberRoute(languageCode: String): String {
            return "phone_number/$languageCode"
        }
        
        fun buildOtpRoute(phoneNumber: String): String {
            return "otp/${java.net.URLEncoder.encode(phoneNumber, "UTF-8")}"
        }
        
        fun buildWelcomeBackRoute(userName: String): String {
            return "welcome_back/${java.net.URLEncoder.encode(userName, "UTF-8")}"
        }
        
        fun buildPostDetailRoute(postId: String): String {
            return "post_detail/$postId"
        }
        
        fun buildUserProfileRoute(userId: String): String {
            return "user_profile/$userId"
        }
        
        fun buildStoryRoute(userId: String): String {
            return "story/$userId"
        }
    }
}
