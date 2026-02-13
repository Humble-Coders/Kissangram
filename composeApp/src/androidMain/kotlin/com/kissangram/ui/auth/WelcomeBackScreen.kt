package com.kissangram.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.ui.components.AutoSizeText
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WelcomeBackScreen(
    userName: String,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Responsive scaling factors based on screen width (360dp as baseline)
    val scaleFactor = min(screenWidth.value / 360f, 1.3f)
    val padding = (27 * scaleFactor).dp
    
    // Set auth completed flag for existing user and auto-navigate to home after 2 seconds
    LaunchedEffect(Unit) {
        val prefs = AndroidPreferencesRepository(context)
        scope.launch {
            prefs.setAuthCompleted()
        }
        delay(2000)
        onContinue()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9F1))
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Welcome message
            AutoSizeText(
                text = "Welcome back,",
                fontSize = (33.75 * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                minFontSizeScale = 0.6f
            )
            
            Spacer(modifier = Modifier.height((12 * scaleFactor).dp))
            
            // User name
            AutoSizeText(
                text = userName,
                fontSize = (33.75 * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D6A4F),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                minFontSizeScale = 0.6f
            )
            
            Spacer(modifier = Modifier.height((24 * scaleFactor).dp))
            
            // Loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size((48 * scaleFactor).dp),
                color = Color(0xFF2D6A4F),
                strokeWidth = (4 * scaleFactor).dp
            )
        }
    }
}
