package com.kissangram.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.User
import com.kissangram.model.UserRole
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidCropsRepository
import com.kissangram.repository.AndroidLocationRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.usecase.GetAllCropsUseCase
import com.kissangram.usecase.GetCurrentUserUseCase
import com.kissangram.usecase.GetDistrictsUseCase
import com.kissangram.usecase.GetStatesUseCase
import com.kissangram.usecase.UpdateProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditProfileViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    // Repositories
    private val prefs = AndroidPreferencesRepository(application.applicationContext)
    private val authRepository = AndroidAuthRepository(
        context = application.applicationContext,
        activity = null,
        preferencesRepository = prefs
    )
    private val userRepository = FirestoreUserRepository(authRepository = authRepository)
    private val locationRepository = AndroidLocationRepository()
    private val cropsRepository = AndroidCropsRepository()
    
    // Use cases
    private val getCurrentUserUseCase = GetCurrentUserUseCase(userRepository)
    private val updateProfileUseCase = UpdateProfileUseCase(userRepository)
    private val getStatesUseCase = GetStatesUseCase(locationRepository)
    private val getDistrictsUseCase = GetDistrictsUseCase(locationRepository)
    private val getAllCropsUseCase = GetAllCropsUseCase(cropsRepository)
    
    // UI State
    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()
    
    init {
        loadCurrentUser()
        loadStates()
        loadCrops()
    }
    
    /**
     * Load current user profile from Firestore
     */
    fun loadCurrentUser() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingUser = true, userError = null)
            
            try {
                val user = getCurrentUserUseCase()
                println("✅ EditProfileViewModel: Loaded user = ${user?.name}")
                
                if (user != null) {
                    _uiState.value = _uiState.value.copy(
                        currentUser = user,
                        name = user.name,
                        bio = user.bio ?: "",
                        profileImageUrl = user.profileImageUrl,
                        selectedRole = user.role,
                        selectedState = user.location?.state,
                        selectedDistrict = user.location?.district,
                        village = user.location?.village ?: "",
                        selectedCrops = user.expertise.toSet(),
                        isLoadingUser = false
                    )
                    
                    // Load districts if state is already selected
                    user.location?.state?.let { state ->
                        loadDistricts(state)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingUser = false,
                        userError = "User not found"
                    )
                }
            } catch (e: Exception) {
                println("❌ EditProfileViewModel: Error loading user - ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoadingUser = false,
                    userError = e.message ?: "Failed to load user"
                )
            }
        }
    }
    
    /**
     * Load all available states from Firestore
     */
    fun loadStates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingStates = true, statesError = null)
            
            try {
                val states = getStatesUseCase()
                println("✅ EditProfileViewModel: Loaded ${states.size} states")
                _uiState.value = _uiState.value.copy(
                    states = states,
                    isLoadingStates = false
                )
            } catch (e: Exception) {
                println("❌ EditProfileViewModel: Error loading states - ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoadingStates = false,
                    statesError = e.message ?: "Failed to load states"
                )
            }
        }
    }
    
    /**
     * Load districts for the selected state
     */
    fun loadDistricts(state: String) {
        if (state.isBlank()) {
            _uiState.value = _uiState.value.copy(districts = emptyList())
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDistricts = true, districtsError = null)
            
            try {
                val districts = getDistrictsUseCase(state)
                _uiState.value = _uiState.value.copy(
                    districts = districts,
                    isLoadingDistricts = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingDistricts = false,
                    districtsError = e.message ?: "Failed to load districts"
                )
            }
        }
    }
    
    /**
     * Called when user selects a state
     */
    fun onStateSelected(state: String) {
        _uiState.value = _uiState.value.copy(
            selectedState = state,
            selectedDistrict = null, // Reset district when state changes
            districts = emptyList()
        )
        loadDistricts(state)
    }
    
    /**
     * Called when user selects a district
     */
    fun onDistrictSelected(district: String) {
        _uiState.value = _uiState.value.copy(selectedDistrict = district)
    }
    
    /**
     * Clear location selection
     */
    fun clearLocationSelection() {
        _uiState.value = _uiState.value.copy(
            selectedState = null,
            selectedDistrict = null,
            districts = emptyList()
        )
    }
    
    /**
     * Load all available crops from Firestore
     */
    fun loadCrops() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCrops = true, cropsError = null)
            
            try {
                val crops = getAllCropsUseCase()
                println("✅ EditProfileViewModel: Loaded ${crops.size} crops")
                _uiState.value = _uiState.value.copy(
                    allCrops = crops,
                    isLoadingCrops = false
                )
            } catch (e: Exception) {
                println("❌ EditProfileViewModel: Error loading crops - ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoadingCrops = false,
                    cropsError = e.message ?: "Failed to load crops"
                )
            }
        }
    }
    
    // ==================== Profile Field Updates ====================
    
    /**
     * Update name field
     */
    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }
    
    /**
     * Update bio field
     */
    fun updateBio(bio: String) {
        _uiState.value = _uiState.value.copy(bio = bio)
    }
    
    /**
     * Update profile image URL (after image selection)
     */
    fun updateProfileImageUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri)
    }
    
    /**
     * Update village field
     */
    fun updateVillage(village: String) {
        _uiState.value = _uiState.value.copy(village = village)
    }
    
    /**
     * Called when user selects a role
     */
    fun onRoleSelected(role: UserRole) {
        _uiState.value = _uiState.value.copy(selectedRole = role)
    }
    
    /**
     * Toggle crop selection
     */
    fun toggleCrop(crop: String) {
        val currentCrops = _uiState.value.selectedCrops
        val newCrops = if (currentCrops.contains(crop)) {
            currentCrops - crop
        } else {
            currentCrops + crop
        }
        _uiState.value = _uiState.value.copy(selectedCrops = newCrops)
    }
    
    // ==================== Save Profile ====================
    
    /**
     * Save all profile changes to Firestore
     */
    fun saveProfile(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            
            // Validate
            if (state.name.isBlank()) {
                onError("Name cannot be empty")
                return@launch
            }
            
            _uiState.value = state.copy(isSaving = true, saveError = null)
            
            try {
                println("📝 EditProfileViewModel: Saving profile...")
                
                // TODO: Upload image to Firebase Storage if selectedImageUri is set
                // For now, we'll skip image upload and use existing URL
                val imageUrl = state.profileImageUrl
                
                updateProfileUseCase(
                    name = state.name,
                    bio = state.bio.ifBlank { null },
                    profileImageUrl = imageUrl,
                    role = state.selectedRole,
                    state = state.selectedState,
                    district = state.selectedDistrict,
                    village = state.village.ifBlank { null },
                    crops = state.selectedCrops.toList().ifEmpty { null }
                )
                
                println("✅ EditProfileViewModel: Profile saved successfully!")
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
                onSuccess()
                
            } catch (e: IllegalArgumentException) {
                println("❌ EditProfileViewModel: Validation error - ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveError = e.message ?: "Validation failed"
                )
                onError(e.message ?: "Validation failed")
                
            } catch (e: Exception) {
                println("❌ EditProfileViewModel: Save error - ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveError = e.message ?: "Failed to save profile"
                )
                onError(e.message ?: "Failed to save profile")
            }
        }
    }
    
    /**
     * Clear save success flag
     */
    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }
    
    /**
     * Check if there are unsaved changes
     */
    fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        val user = state.currentUser ?: return false
        
        // Check if any field has changed
        val nameChanged = state.name != user.name
        val bioChanged = state.bio != (user.bio ?: "")
        val roleChanged = state.selectedRole != user.role
        val stateChanged = state.selectedState != user.location?.state
        val districtChanged = state.selectedDistrict != user.location?.district
        val villageChanged = state.village != (user.location?.village ?: "")
        val cropsChanged = state.selectedCrops != user.expertise.toSet()
        val imageChanged = state.selectedImageUri != null
        
        return nameChanged || bioChanged || roleChanged || stateChanged || 
               districtChanged || villageChanged || cropsChanged || imageChanged
    }
}

data class EditProfileUiState(
    // Current user
    val currentUser: User? = null,
    
    // Editable fields
    val name: String = "",
    val bio: String = "",
    val profileImageUrl: String? = null,
    val selectedImageUri: Uri? = null,
    val village: String = "",
    val selectedRole: UserRole = UserRole.FARMER,
    val selectedCrops: Set<String> = emptySet(),
    
    // Location data
    val states: List<String> = emptyList(),
    val districts: List<String> = emptyList(),
    val selectedState: String? = null,
    val selectedDistrict: String? = null,
    
    // Crops data
    val allCrops: List<String> = emptyList(),
    
    // Loading states
    val isLoadingUser: Boolean = false,
    val isLoadingStates: Boolean = false,
    val isLoadingDistricts: Boolean = false,
    val isLoadingCrops: Boolean = false,
    val isSaving: Boolean = false,
    
    // Errors
    val userError: String? = null,
    val statesError: String? = null,
    val districtsError: String? = null,
    val cropsError: String? = null,
    val saveError: String? = null,
    
    // Success state
    val saveSuccess: Boolean = false
)
