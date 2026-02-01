package com.kissangram.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NavController(initialScreen: Screen = Screen.LanguageSelection) {
    private val backStack = mutableListOf<Screen>()
    
    var currentScreen: Screen by mutableStateOf(initialScreen)
        private set
    
    fun navigateTo(screen: Screen) {
        backStack.add(currentScreen)
        currentScreen = screen
    }
    
    /** Replace entire back stack and set screen (e.g. restore to Home when session exists). */
    fun replaceAllWith(screen: Screen) {
        backStack.clear()
        currentScreen = screen
    }
    
    fun navigateBack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeAt(backStack.size - 1)
        }
    }
    
    fun canGoBack(): Boolean = backStack.isNotEmpty()
}
