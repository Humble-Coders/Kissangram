package com.kissangram.ui.auth

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kissangram.ui.components.AutoSizeText
import com.kissangram.ui.components.HoldToSpeakButton
import com.kissangram.ui.components.OtpScreenButtonStyle
import com.kissangram.viewmodel.OtpViewModel
import kotlin.math.min
import org.jetbrains.compose.resources.stringResource
import kissangram.composeapp.generated.resources.Res
import kissangram.composeapp.generated.resources.*

@Composable
fun OtpScreen(
    phoneNumber: String,
    onBackClick: () -> Unit,
    onExistingUser: (String) -> Unit, // userName callback for existing users
    onNewUser: () -> Unit, // callback for new users
    onResendOtp: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: OtpViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return OtpViewModel(application, phoneNumber) as T
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoSizeText(
                    text = stringResource(Res.string.enter_otp),
                    fontSize = (31.5 * scaleFactor).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1B),
                    lineHeight = (47.25 * scaleFactor).sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    minFontSizeScale = 0.6f
                )
                
                // Tap to speak button (header)
                IconButton(
                    onClick = {
                        if (uiState.isListening) {
                            viewModel.stopSpeechRecognition()
                        } else {
                            handleSpeechRecognitionStart()
                        }
                    },
                    modifier = Modifier
                        .size((36 * scaleFactor).dp)
                        .clip(CircleShape)
                        .background(if (uiState.isListening) Color(0xFFFFB703) else Color(0x33FFB703))
                ) {
                    // Microphone icon would go here if needed
                }
            }
            
            Spacer(modifier = Modifier.height((9 * scaleFactor).dp))
            
            AutoSizeText(
                text = stringResource(Res.string.code_sent_to, phoneNumber),
                fontSize = (17.1 * scaleFactor).sp,
                color = Color(0xFF6B6B6B),
                lineHeight = (25.65 * scaleFactor).sp,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                minFontSizeScale = 0.7f
            )
        }
        
        Spacer(modifier = Modifier.height(spacing))
        
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Hold to speak button
        HoldToSpeakButton(
            isListening = uiState.isListening,
            isLoading = uiState.isLoading,
            isProcessing = uiState.isProcessing,
            onStartListening = { handleSpeechRecognitionStart() },
            onStopListening = { viewModel.stopSpeechRecognition() },
            defaultText = stringResource(Res.string.tap_to_speak),
            style = OtpScreenButtonStyle,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(spacing))
        
        // OTP Input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape((18 * scaleFactor).dp),
            color = Color.White,
            shadowElevation = (2 * scaleFactor).dp
        ) {
            TextField(
                value = uiState.otp,
                onValueChange = { newValue ->
                    if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                        viewModel.updateOtp(newValue)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding((18 * scaleFactor).dp),
                placeholder = {
                    AutoSizeText(
                        text = stringResource(Res.string.enter_6_digit_code),
                        fontSize = (27 * scaleFactor).sp,
                        color = Color(0x801B1B1B),
                        textAlign = TextAlign.Center,
                        minFontSizeScale = 0.6f
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = (27 * scaleFactor).sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (9 * scaleFactor).sp
                ),
                singleLine = true
            )
        }
        
        Spacer(modifier = Modifier.height(spacing))
        
            // Resend OTP
            TextButton(
                onClick = { viewModel.resendOtp(onResendOtp) },
                modifier = Modifier.fillMaxWidth()
            ) {
                AutoSizeText(
                    text = stringResource(Res.string.didnt_receive_code),
                    fontSize = (17.1 * scaleFactor).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D6A4F),
                    minFontSizeScale = 0.7f
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
                    maxLines = 2,
                    minFontSizeScale = 0.7f
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Verify & Continue Button
            Button(
                onClick = {
                    viewModel.verifyOtp(
                        onExistingUser = onExistingUser,
                        onNewUser = onNewUser,
                        onError = {}
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height((75 * scaleFactor).dp),
                enabled = !uiState.isLoading && uiState.otp.length == 6,
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
                        text = stringResource(Res.string.verify_continue),
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
