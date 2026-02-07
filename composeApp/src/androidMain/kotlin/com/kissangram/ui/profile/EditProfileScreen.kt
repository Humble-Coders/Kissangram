package com.kissangram.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlin.math.sqrt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kissangram.model.UserRole
import com.kissangram.ui.home.PrimaryGreen
import com.kissangram.ui.home.AccentYellow
import com.kissangram.ui.home.TextPrimary
import com.kissangram.ui.home.TextSecondary
import com.kissangram.ui.home.BackgroundColor
import com.kissangram.ui.home.ErrorRed
import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import java.io.File
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kissangram.viewmodel.EditProfileViewModel
import com.kissangram.ui.components.FilterableDropdownField


private val EditProfileBackground = Color(0xFFF8F9F1)
private val CardBackground = Color.White
private val InputBackground = Color(0xFFF8F9F1)
private val DisabledInputBackground = Color(0xFFE5E6DE).copy(alpha = 0.3f)

// Dummy data for crops
private val availableCrops = listOf(
    "Wheat", "Rice", "Sugarcane", "Cotton", "Maize", "Soybean",
    "Pulses", "Mustard", "Potato", "Tomato", "Onion", "Chili",
    "Bajra", "Jowar"
)

// Helper functions defined before EditProfileScreen
internal fun roleLabel(role: UserRole): String = when (role) {
    UserRole.FARMER -> "Farmer"
    UserRole.EXPERT -> "Expert"
    UserRole.AGRIPRENEUR -> "Agripreneur"
    UserRole.INPUT_SELLER -> "Input Seller"
    UserRole.AGRI_LOVER -> "Agri Lover"
}

private fun createImageUri(context: Context): Uri {
    val photoFile = File(
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
        "profile_${System.currentTimeMillis()}.jpg"
    )
    photoFile.parentFile?.mkdirs()

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

@Composable
fun ProfileImageSection(
    profileImageUrl: String?,
    selectedImageUri: Uri?,
    userName: String,
    onImageClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(126.dp),
            contentAlignment = Alignment.Center
        ) {
            // Gradient border circle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(PrimaryGreen, AccentYellow)
                        )
                    )
                    .padding(3.dp)
            ) {
                when {
                    selectedImageUri != null -> {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    profileImageUrl != null -> {
                        AsyncImage(
                            model = profileImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(PrimaryGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userName.firstOrNull()?.uppercase() ?: "R",
                                fontSize = 45.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Camera icon button
                FloatingActionButton(
                    onClick = onImageClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(45.dp),
                    containerColor = PrimaryGreen,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 3.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change photo",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onImageClick) {
            Text(
                "Change photo",
                color = PrimaryGreen,
                fontSize = 15.75.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CropChip(
    crop: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) PrimaryGreen else InputBackground,
        border = if (isSelected) null else BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                crop,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) Color.White else TextSecondary
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun BasicDetailsSection(
    name: TextFieldValue,
    onNameChange: (TextFieldValue) -> Unit,
    username: String,
    bio: TextFieldValue,
    onBioChange: (TextFieldValue) -> Unit,
    role: UserRole,
    onRoleChange: (UserRole) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Basic Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Name Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Name",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBackground,
                        unfocusedContainerColor = InputBackground,
                        unfocusedBorderColor = Color.Black.copy(alpha = 0.1f),
                        focusedBorderColor = PrimaryGreen
                    ),
                    shape = RoundedCornerShape(22.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                )
            }
            
            // Username Field (Read-only, auto-generated)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Username",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = TextSecondary
                        )
                        Text(
                            "Auto-generated",
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            color = TextSecondary
                        )
                    }
                }
                OutlinedTextField(
                    value = "@$username",
                    onValueChange = { },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = true,
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledContainerColor = DisabledInputBackground,
                        disabledBorderColor = Color.Black.copy(alpha = 0.05f),
                        disabledTextColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(22.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = TextSecondary
                    )
                )
            }

            // Bio Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Bio",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Text(
                        "${bio.text.length}/150",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                OutlinedTextField(
                    value = bio,
                    onValueChange = {
                        if (it.text.length <= 150) {
                            onBioChange(it)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBackground,
                        unfocusedContainerColor = InputBackground,
                        unfocusedBorderColor = Color.Black.copy(alpha = 0.1f),
                        focusedBorderColor = PrimaryGreen
                    ),
                    shape = RoundedCornerShape(22.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                )
            }

            // Role Field (Editable Dropdown)
            FilterableDropdownField(
                label = "Role",
                items = UserRole.values().map { roleLabel(it) },
                selectedItem = roleLabel(role),
                onItemSelected = { selectedRoleLabel ->
                    val selectedRole = UserRole.values().find { roleLabel(it) == selectedRoleLabel }
                    selectedRole?.let { onRoleChange(it) }
                },
                enabled = true,
                isLoading = false,
                placeholder = "Select Role",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
@Composable
private fun DropdownItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onClick),
            color = if (isSelected) PrimaryGreen.copy(alpha = 0.1f)
            else Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    color = if (isSelected) PrimaryGreen else TextPrimary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = PrimaryGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            color = Color.Black.copy(alpha = 0.05f),
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun LocationSection(
    states: List<String>,
    selectedState: String?,
    onStateSelected: (String) -> Unit,
    isLoadingStates: Boolean,
    districts: List<String>,
    selectedDistrict: String?,
    onDistrictSelected: (String) -> Unit,
    isLoadingDistricts: Boolean,
    village: String = "",
    onVillageChange: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Location",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // State Dropdown
            FilterableDropdownField(
                label = "State",
                items = states,
                selectedItem = selectedState,
                onItemSelected = onStateSelected,
                enabled = !isLoadingStates,
                isLoading = isLoadingStates,
                placeholder = "Select State",
                modifier = Modifier.fillMaxWidth()
            )

            // District Dropdown
            FilterableDropdownField(
                label = "District",
                items = districts,
                selectedItem = selectedDistrict,
                onItemSelected = onDistrictSelected,
                enabled = selectedState != null && !isLoadingDistricts,
                isLoading = isLoadingDistricts,
                placeholder = if (selectedState == null) "Select state first" else "Select District",
                modifier = Modifier.fillMaxWidth()
            )

            // Village Text Field (Optional)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Village",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Text(
                        "(Optional)",
                        fontSize = 12.sp,
                        color = TextSecondary.copy(alpha = 0.6f),
                        fontStyle = FontStyle.Italic
                    )
                }
                OutlinedTextField(
                    value = village,
                    onValueChange = onVillageChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Enter village name",
                            color = TextPrimary.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputBackground,
                        unfocusedContainerColor = InputBackground,
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = Color.Black.copy(alpha = 0.1f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(22.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp
                    )
                )
            }
        }
    }
}

@Composable
fun CropsSection(
    allCrops: List<String>,
    selectedCrops: Set<String>,
    onCropToggle: (String) -> Unit,
    isLoading: Boolean = false
) {
    var searchText by remember { mutableStateOf("") }
    
    // Filter crops based on search text
    val filteredCrops = remember(allCrops, searchText, selectedCrops) {
        if (searchText.isBlank()) {
            allCrops.filter { !selectedCrops.contains(it) }
        } else {
            allCrops.filter { 
                it.contains(searchText, ignoreCase = true) && !selectedCrops.contains(it)
            }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Crops You Grow",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Search Bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Search crops...",
                        color = TextPrimary.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                enabled = !isLoading,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InputBackground,
                    unfocusedContainerColor = InputBackground,
                    disabledContainerColor = DisabledInputBackground,
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = Color.Black.copy(alpha = 0.1f),
                    disabledBorderColor = Color.Black.copy(alpha = 0.05f),
                    focusedTextColor = PrimaryGreen,
                    focusedLabelColor = PrimaryGreen,
                ),
                shape = RoundedCornerShape(22.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            )

            // Selected Crops Section
            if (selectedCrops.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Selected Crops",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedCrops.forEach { crop ->
                            CropChip(
                                crop = crop,
                                isSelected = true,
                                onClick = { onCropToggle(crop) }
                            )
                        }
                    }
                }
            }

            // Search Results Section
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = PrimaryGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (searchText.isNotEmpty() && filteredCrops.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No crops found",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            } else if (filteredCrops.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (searchText.isNotEmpty()) {
                        Text(
                            "Search Results",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredCrops.forEach { crop ->
                            CropChip(
                                crop = crop,
                                isSelected = false,
                                onClick = { onCropToggle(crop) }
                            )
                        }
                    }
                }
            } else if (searchText.isEmpty() && allCrops.isNotEmpty()) {
                // Show all crops when no search and nothing selected
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allCrops.forEach { crop ->
                        CropChip(
                            crop = crop,
                            isSelected = selectedCrops.contains(crop),
                            onClick = { onCropToggle(crop) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatePickerScreen(
    states: List<String>,
    selectedState: String?,
    onStateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Debug: Log states
    LaunchedEffect(states) {
        println("🎯 StatePickerScreen: Received ${states.size} states")
        if (states.isEmpty()) {
            println("⚠️ StatePickerScreen: States list is EMPTY!")
        }
    }
    
    var searchText by remember { mutableStateOf("") }

    val filteredStates = remember(searchText, states) {
        if (searchText.isBlank()) {
            states
        } else {
            states.filter {
                it.contains(searchText, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Select State",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CardBackground)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = {
                    Text(
                        "Search states...",
                        color = TextSecondary.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InputBackground,
                    unfocusedContainerColor = InputBackground,
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = Color.Black.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(22.dp)
            )

            // States List
            if (filteredStates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (searchText.isNotEmpty()) "No states found" else "No states available",
                            fontSize = 16.sp,
                            color = TextSecondary
                        )
                        if (states.isEmpty()) {
                            Text(
                                text = "Please upload location data first",
                                fontSize = 14.sp,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredStates) { state ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStateSelected(state)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state,
                                fontSize = 16.sp,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (state == selectedState) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = PrimaryGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        if (state != filteredStates.last()) {
                            HorizontalDivider(
                                color = Color.Black.copy(alpha = 0.05f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistrictPickerScreen(
    districts: List<String>,
    selectedDistrict: String?,
    onDistrictSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }

    val filteredDistricts = remember(searchText, districts) {
        if (searchText.isBlank()) {
            districts
        } else {
            districts.filter {
                it.contains(searchText, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Select District",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CardBackground)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = {
                    Text(
                        "Search districts...",
                        color = TextSecondary.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InputBackground,
                    unfocusedContainerColor = InputBackground,
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = Color.Black.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(22.dp)
            )

            // Districts List
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredDistricts) { district ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDistrictSelected(district)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = district,
                            fontSize = 16.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (district == selectedDistrict) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = PrimaryGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    if (district != filteredDistricts.last()) {
                        HorizontalDivider(
                            color = Color.Black.copy(alpha = 0.05f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBackClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onNavigateToExpertDocument: () -> Unit = {},
    viewModel: EditProfileViewModel = viewModel()
) {
    // ViewModel state
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Local TextFieldValue state to preserve cursor position
    var nameTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var bioTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    
    // Sync local TextFieldValue with ViewModel state (only when ViewModel changes externally)
    LaunchedEffect(uiState.name) {
        if (nameTextFieldValue.text != uiState.name) {
            nameTextFieldValue = TextFieldValue(uiState.name)
        }
    }
    
    LaunchedEffect(uiState.bio) {
        if (bioTextFieldValue.text != uiState.bio) {
            bioTextFieldValue = TextFieldValue(uiState.bio)
        }
    }
    
    // Snackbar for showing messages
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Image picker state
    val context = LocalContext.current
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // Unsaved changes confirmation dialog
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    
    // Handle role change
    fun handleRoleChange(newRole: UserRole) {
        viewModel.onRoleSelected(newRole)
        // If Expert is selected, navigate to document upload screen
        if (newRole == UserRole.EXPERT) {
            onNavigateToExpertDocument()
        }
    }
    
    // Handle save
    fun handleSave() {
        viewModel.saveProfile(
            onSuccess = {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Profile saved successfully!")
                }
                onSaveClick()
            },
            onError = { error ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(error)
                }
            }
        )
    }
    
    // Handle back button with unsaved changes check
    fun handleBackClick() {
        if (viewModel.hasUnsavedChanges()) {
            showUnsavedChangesDialog = true
        } else {
            onBackClick()
        }
    }

    // Permission launchers
    val readMediaImagesPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImageSourceDialog = true
        }
    }

    val readStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImageSourceDialog = true
        }
    }

    // Image picker launchers
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                viewModel.updateProfileImageUri(uri)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createImageUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val galleryLauncherTiramisu = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.updateProfileImageUri(it)
        }
    }

    val galleryLauncherLegacy = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.updateProfileImageUri(it)
        }
    }

    // Function to handle image click
    fun handleImageClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    showImageSourceDialog = true
                }

                else -> {
                    readMediaImagesPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    showImageSourceDialog = true
                }

                else -> {
                    readStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    // Function to handle camera click
    fun handleCameraClick() {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                val uri = createImageUri(context)
                cameraImageUri = uri
                cameraLauncher.launch(uri)
            }

            else -> {
                cameraImageUri = createImageUri(context)
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Main Edit Profile Screen

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EditProfileBackground)
        ) {
            // Loading overlay when loading user
            if (uiState.isLoadingUser) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = PrimaryGreen)
                        Text("Loading profile...", color = TextSecondary)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Bar
                    TopAppBar(
                        title = {
                            Text(
                                "Edit Profile",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 20.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { handleBackClick() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = EditProfileBackground
                        )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(Modifier.height(16.dp))

                        // Profile Image Section
                        ProfileImageSection(
                            profileImageUrl = uiState.profileImageUrl,
                            selectedImageUri = uiState.selectedImageUri,
                            userName = uiState.name.ifEmpty { "R" },
                            onImageClick = { handleImageClick() }
                        )

                        Spacer(Modifier.height(24.dp))

                        // Basic Details Section
                        BasicDetailsSection(
                            name = nameTextFieldValue,
                            onNameChange = { 
                                nameTextFieldValue = it
                                viewModel.updateName(it.text) 
                            },
                            username = uiState.username,
                            bio = bioTextFieldValue,
                            onBioChange = { 
                                bioTextFieldValue = it
                                viewModel.updateBio(it.text) 
                            },
                            role = uiState.selectedRole,
                            onRoleChange = { handleRoleChange(it) }
                        )

                        Spacer(Modifier.height(16.dp))

                        // Location Section
                        LocationSection(
                            states = uiState.states,
                            selectedState = uiState.selectedState,
                            onStateSelected = { viewModel.onStateSelected(it) },
                            isLoadingStates = uiState.isLoadingStates,
                            districts = uiState.districts,
                            selectedDistrict = uiState.selectedDistrict,
                            onDistrictSelected = { viewModel.onDistrictSelected(it) },
                            isLoadingDistricts = uiState.isLoadingDistricts,
                            village = uiState.village,
                            onVillageChange = { viewModel.updateVillage(it) }
                        )

                        Spacer(Modifier.height(16.dp))

                        // Crops Section
                        CropsSection(
                            allCrops = uiState.allCrops,
                            selectedCrops = uiState.selectedCrops,
                            onCropToggle = { crop -> viewModel.toggleCrop(crop) },
                            isLoading = uiState.isLoadingCrops
                        )

                        Spacer(Modifier.height(100.dp))
                    }
                }

                // Image Source Options Dialog
                if (showImageSourceDialog) {
                        AlertDialog(
                            onDismissRequest = { showImageSourceDialog = false },
                            shape = RoundedCornerShape(24.dp),
                            containerColor = CardBackground,
                            title = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryGreen.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = PrimaryGreen,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "Select Photo",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = TextPrimary
                                    )
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Gallery Option
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showImageSourceDialog = false
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    galleryLauncherTiramisu.launch(
                                                        androidx.activity.result.PickVisualMediaRequest(
                                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                                        )
                                                    )
                                                } else {
                                                    galleryLauncherLegacy.launch("image/*")
                                                }
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = InputBackground
                                        ),
                                        elevation = CardDefaults.cardElevation(0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryGreen.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = null,
                                                    tint = PrimaryGreen,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    "Choose from Gallery",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    "Select photo from your device",
                                                    fontSize = 13.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }

                                    // Camera Option
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showImageSourceDialog = false
                                                handleCameraClick()
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = InputBackground
                                        ),
                                        elevation = CardDefaults.cardElevation(0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryGreen.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CameraAlt,
                                                    contentDescription = null,
                                                    tint = PrimaryGreen,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    "Take Photo",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    "Capture a new photo with camera",
                                                    fontSize = 13.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(
                                    onClick = { showImageSourceDialog = false },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        "Cancel",
                                        color = TextSecondary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        )

                }
                
                // Unsaved Changes Confirmation Dialog
                if (showUnsavedChangesDialog) {
                    AlertDialog(
                        onDismissRequest = { showUnsavedChangesDialog = false },
                        title = {
                            Text(
                                "Discard Changes?",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        },
                        text = {
                            Text(
                                "You have unsaved changes. Are you sure you want to go back?",
                                color = TextSecondary
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showUnsavedChangesDialog = false
                                    onBackClick()
                                }
                            ) {
                                Text(
                                    "Discard",
                                    color = ErrorRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUnsavedChangesDialog = false }) {
                                Text("Cancel", color = TextSecondary)
                            }
                        }
                    )
                }

                // Bottom Save Button
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = EditProfileBackground,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = { handleSave() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            disabledContainerColor = PrimaryGreen.copy(alpha = 0.5f)
                        ),
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Saving...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Text(
                                "Save Changes",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

}

