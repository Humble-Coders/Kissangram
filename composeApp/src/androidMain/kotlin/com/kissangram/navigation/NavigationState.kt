package com.kissangram.navigation

sealed class Screen {
    object LanguageSelection : Screen()
    data class PhoneNumber(val languageCode: String) : Screen()
    data class Otp(val phoneNumber: String) : Screen()
    object Name : Screen()
    // Add more screens as needed
}
