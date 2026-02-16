package com.kissangram.ui.auth

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import android.view.HapticFeedbackConstants
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kissangram.ui.components.AutoSizeText
import com.kissangram.ui.components.HoldToSpeakButton
import com.kissangram.ui.components.NameScreenButtonStyle
import com.kissangram.viewmodel.NameViewModel
import kotlin.math.min
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import kissangram.composeapp.generated.resources.Res
import kissangram.composeapp.generated.resources.*

@Composable
fun NameScreen(
    onNameSaved: () -> Unit,
    viewModel: NameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Responsive scaling factors based on screen width (360dp as baseline)
    val scaleFactor = min(screenWidth.value / 360f, 1.3f)
    val padding = (27 * scaleFactor).dp
    val spacing = (36 * scaleFactor).dp
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startSpeechRecognition()
        }
    }
    
    // Handle speech recognition start with permission check
    fun handleSpeechRecognitionStart() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            viewModel.startSpeechRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9F1))
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header - Fixed at top
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step indicator
            AutoSizeText(
                text = stringResource(Res.string.step_x_of_y, 1, 3),
                fontSize = (15.75 * scaleFactor).sp,
                color = Color(0xFF6B6B6B),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                minFontSizeScale = 0.7f
            )
        }
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
        
        // Title
        AutoSizeText(
            text = stringResource(Res.string.what_should_we_call_you),
            fontSize = (33.75 * scaleFactor).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B1B1B),
            lineHeight = (50.625 * scaleFactor).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            minFontSizeScale = 0.6f
        )
        
        Spacer(modifier = Modifier.height((9 * scaleFactor).dp))
        
        AutoSizeText(
            text = stringResource(Res.string.you_can_say_or_type),
            fontSize = (16.2 * scaleFactor).sp,
            color = Color(0xFF6B6B6B),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            minFontSizeScale = 0.7f
        )
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Hold to say name button
        HoldToSpeakButton(
            isListening = uiState.isListening,
            isLoading = uiState.isLoading,
            isProcessing = uiState.isProcessing,
            onStartListening = { handleSpeechRecognitionStart() },
            onStopListening = { viewModel.stopSpeechRecognition() },
            defaultText = "Tap to say your name",
            listeningText = "Listening... Release when done",
            style = NameScreenButtonStyle,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Name Input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape((18 * scaleFactor).dp),
            color = Color(0xFFF8F9F1),
            border = androidx.compose.foundation.BorderStroke(
                (1.18 * scaleFactor).dp,
                Color(0x262D6A4F)
            )
        ) {
            TextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding),
                placeholder = {
                    AutoSizeText(
                        text = stringResource(Res.string.your_name),
                        fontSize = (19.125 * scaleFactor).sp,
                        color = Color(0x801B1B1B),
                        minFontSizeScale = 0.7f
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = (19.125 * scaleFactor).sp
                ),
                singleLine = true
            )
        }
        
        // Error message
        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
            AutoSizeText(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = (14 * scaleFactor).sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                minFontSizeScale = 0.7f
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Next Button
        val view = LocalView.current
        Button(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.saveName(
                    onSuccess = onNameSaved,
                    onError = {}
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height((75 * scaleFactor).dp),
            enabled = !uiState.isLoading && uiState.name.trim().length >= 2,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2D6A4F),
                disabledContainerColor = Color(0xFF2D6A4F).copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape((18 * scaleFactor).dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size((24 * scaleFactor).dp),
                    color = Color.White
                )
            } else {
                AutoSizeText(
                    text = stringResource(Res.string.next),
                    fontSize = (20.25 * scaleFactor).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    minFontSizeScale = 0.7f
                )
            }
        }
        }
    }
}
