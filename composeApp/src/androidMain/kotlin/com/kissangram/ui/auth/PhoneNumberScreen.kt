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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kissangram.ui.components.HoldToSpeakButton
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
            .padding(27.dp)
    ) {
        // Back Button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF1B1B1B)
            )
        }
        
        Spacer(modifier = Modifier.height(27.dp))
        
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.enter_phone_number),
                fontSize = 31.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B),
                lineHeight = 47.25.sp,
                modifier = Modifier.weight(1f)
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
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (uiState.isListening) Color(0xFFFFB703) else Color(0x33FFB703))
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Tap to speak",
                    tint = if (uiState.isListening) Color.White else Color(0xFFFFB703),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(9.dp))
        
        Text(
            text = stringResource(Res.string.we_send_verification_code),
            fontSize = 17.1.sp,
            color = Color(0xFF6B6B6B),
            lineHeight = 25.65.sp
        )
        
        Spacer(modifier = Modifier.height(18.dp))
        
        // Hold to speak button
        HoldToSpeakButton(
            isListening = uiState.isListening,
            isLoading = uiState.isLoading,
            isProcessing = uiState.isProcessing,
            onStartListening = { handleSpeechRecognitionStart() },
            onStopListening = { viewModel.stopSpeechRecognition() },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(18.dp))
        
        // Phone Input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    text = uiState.countryCode,
                    fontSize = 20.25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6B6B6B)
                )
                
                TextField(
                    value = uiState.phoneNumber,
                    onValueChange = viewModel::updatePhoneNumber,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.enter_phone_number_placeholder),
                            fontSize = 22.5.sp,
                            color = Color(0x801B1B1B)
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
                        fontSize = 22.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
        
        // Error message
        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Get OTP Button
        Button(
            onClick = {
                viewModel.sendOtp(
                    onSuccess = onOtpSent,
                    onError = {}
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp),
            enabled = !uiState.isLoading && uiState.phoneNumber.length >= 10,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2D6A4F)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text(
                    text = stringResource(Res.string.get_otp),
                    fontSize = 20.25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
