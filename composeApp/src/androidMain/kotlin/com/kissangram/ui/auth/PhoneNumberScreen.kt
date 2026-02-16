package com.kissangram.ui.auth

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import android.view.HapticFeedbackConstants
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kissangram.ui.components.AutoSizeText
import com.kissangram.ui.components.HoldToSpeakButton
import com.kissangram.viewmodel.PhoneNumberViewModel
import kotlin.math.min
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import kissangram.composeapp.generated.resources.Res
import kissangram.composeapp.generated.resources.*

@Composable
fun PhoneNumberScreen(
    onBackClick: () -> Unit,
    onOtpSent: (String) -> Unit
) {
    val context =LocalContext.current
    val application = context.applicationContext as android.app.Application
    val activity = context as? android.app.Activity
    
    val viewModel: PhoneNumberViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return PhoneNumberViewModel(application, activity) as T
            }
        }
    )
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Responsive scaling factors based on screen width (360dp as baseline)
    val scaleFactor = min(screenWidth.value / 360f, 1.3f)
    val padding = (27 * scaleFactor).dp
    val spacing = (18 * scaleFactor).dp
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startSpeechRecognition()
        }
    }
    
    // Check and request permission when starting speech recognition
    LaunchedEffect(Unit) {
        // This will be triggered when the screen is first composed
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
            .padding(padding)
    ) {
        // Header - Fixed at top
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Back Button
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size((54 * scaleFactor).dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF1B1B1B),
                    modifier = Modifier.size((24 * scaleFactor).dp)
                )
            }
            
            Spacer(modifier = Modifier.height(padding))
            
            // Header
            AutoSizeText(
                text = stringResource(Res.string.enter_phone_number),
                fontSize = (31.5 * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B),
                lineHeight = (47.25 * scaleFactor).sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                minFontSizeScale = 0.6f
            )
        
        Spacer(modifier = Modifier.height((9 * scaleFactor).dp))
        
        AutoSizeText(
            text = stringResource(Res.string.we_send_verification_code),
            fontSize = (17.1 * scaleFactor).sp,
            color = Color(0xFF6B6B6B),
            lineHeight = (25.65 * scaleFactor).sp,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            minFontSizeScale = 0.7f
        )
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Hold to speak button
        HoldToSpeakButton(
            isListening = uiState.isListening,
            isLoading = uiState.isLoading,
            isProcessing = uiState.isProcessing,
            onStartListening = { handleSpeechRecognitionStart() },
            onStopListening = { viewModel.stopSpeechRecognition() },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Phone Input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape((18 * scaleFactor).dp),
            color = Color.White,
            shadowElevation = (2 * scaleFactor).dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding((18 * scaleFactor).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((9 * scaleFactor).dp)
            ) {
                AutoSizeText(
                    text = uiState.countryCode,
                    fontSize = (20.25 * scaleFactor).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6B6B6B),
                    minFontSizeScale = 0.7f
                )
                
                TextField(
                    value = uiState.phoneNumber,
                    onValueChange = viewModel::updatePhoneNumber,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        AutoSizeText(
                            text = stringResource(Res.string.enter_phone_number_placeholder),
                            fontSize = (22.5 * scaleFactor).sp,
                            color = Color(0x801B1B1B),
                            minFontSizeScale = 0.7f
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = (22.5 * scaleFactor).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
        
            // Error message
            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height((8 * scaleFactor).dp))
                AutoSizeText(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = (14 * scaleFactor).sp,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    minFontSizeScale = 0.7f
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Get OTP Button
            val view = LocalView.current
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    viewModel.sendOtp(
                        onSuccess = onOtpSent,
                        onError = {}
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height((75 * scaleFactor).dp),
                enabled = !uiState.isLoading && uiState.phoneNumber.length >= 10,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2D6A4F)
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
                        text = stringResource(Res.string.get_otp),
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
