package com.kissangram.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NavController {
    private val backStack = mutableListOf<Screen>()
    
    var currentScreen: Screen by mutableStateOf(Screen.LanguageSelection)
        private set
    
    fun navigateTo(screen: Screen) {
        backStack.add(currentScreen)
        currentScreen = screen
    }
    
    fun navigateBack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeAt(backStack.size - 1)
        }
    }
    
    fun canGoBack(): Boolean = backStack.isNotEmpty()
}
