package com.kissangram.ui.auth

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun NameScreen(
    onNameSaved: () -> Unit,
    viewModel: NameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
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
            .padding(27.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step indicator
        Text(
            text = stringResource(Res.string.step_x_of_y, 1, 3),
            fontSize = 15.75.sp,
            color = Color(0xFF6B6B6B),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Title
        Text(
            text = stringResource(Res.string.what_should_we_call_you),
            fontSize = 33.75.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B1B1B),
            lineHeight = 50.625.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(9.dp))
        
        Text(
            text = stringResource(Res.string.you_can_say_or_type),
            fontSize = 16.2.sp,
            color = Color(0xFF6B6B6B),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(36.dp))
        
        // Hold to say name button
        HoldToSpeakButton(
            isListening = uiState.isListening,
            isLoading = uiState.isLoading,
            isProcessing = uiState.isProcessing,
            onStartListening = { handleSpeechRecognitionStart() },
            onStopListening = { viewModel.stopSpeechRecognition() },
            defaultText = "Hold to say your name",
            listeningText = "Listening... Release when done",
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(36.dp))
        
        // Name Input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF8F9F1),
            border = androidx.compose.foundation.BorderStroke(
                1.18.dp,
                Color(0x262D6A4F)
            )
        ) {
            TextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(27.dp),
                placeholder = {
                    Text(
                        text = stringResource(Res.string.your_name),
                        fontSize = 19.125.sp,
                        color = Color(0x801B1B1B)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 19.125.sp
                ),
                singleLine = true
            )
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
        
        // Next Button
        Button(
            onClick = {
                viewModel.saveName(
                    onSuccess = onNameSaved,
                    onError = {}
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp),
            enabled = !uiState.isLoading && uiState.name.trim().length >= 2,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2D6A4F),
                disabledContainerColor = Color(0xFF2D6A4F).copy(alpha = 0.4f)
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
                    text = stringResource(Res.string.next),
                    fontSize = 20.25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
