package com.kissangram

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.kissangram.navigation.NavController
import com.kissangram.navigation.Screen
import com.kissangram.ui.auth.NameScreen
import com.kissangram.ui.auth.OtpScreen
import com.kissangram.ui.auth.PhoneNumberScreen
import com.kissangram.ui.languageselection.LanguageSelectionScreen

@Composable
@Preview
fun App() {
    MaterialTheme {
        val navController = remember { NavController() }
        val currentScreen = navController.currentScreen
        
        when (currentScreen) {
            is Screen.LanguageSelection -> {
                LanguageSelectionScreen(
                    onLanguageSelected = { languageCode ->
                        navController.navigateTo(Screen.PhoneNumber(languageCode))
                    }
                )
            }
            
            is Screen.PhoneNumber -> {
                PhoneNumberScreen(
                    onBackClick = { navController.navigateBack() },
                    onOtpSent = { phoneNumber ->
                        navController.navigateTo(Screen.Otp(phoneNumber))
                    }
                )
            }
            
            is Screen.Otp -> {
                OtpScreen(
                    phoneNumber = currentScreen.phoneNumber,
                    onBackClick = { navController.navigateBack() },
                    onOtpVerified = {
                        navController.navigateTo(Screen.Name)
                    },
                    onResendOtp = {
                        // Navigate back to phone number screen to resend
                        navController.navigateBack()
                    }
                )
            }
            
            is Screen.Name -> {
                NameScreen(
                    onNameSaved = {
                        // TODO: Navigate to main app/home screen
                        // For now, just stay on this screen
                    }
                )
            }
        }
    }
}