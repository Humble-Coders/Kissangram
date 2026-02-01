package com.kissangram.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.UserRole
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.usecase.CreateUserProfileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RoleSelectionUiState(
    val selectedRole: UserRole? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class RoleSelectionViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = AndroidPreferencesRepository(application.applicationContext)
    private val authRepository = AndroidAuthRepository(
        context = application.applicationContext,
        activity = null,
        preferencesRepository = prefs
    )
    private val userRepository = FirestoreUserRepository(authRepository = authRepository)
    private val createUserProfileUseCase = CreateUserProfileUseCase(
        authRepository = authRepository,
        preferencesRepository = prefs,
        userRepository = userRepository
    )

    private val _uiState = MutableStateFlow(RoleSelectionUiState())
    val uiState: StateFlow<RoleSelectionUiState> = _uiState.asStateFlow()

    fun selectRole(role: UserRole) {
        _uiState.value = _uiState.value.copy(selectedRole = role, error = null)
    }

    fun saveRole(onSuccess: () -> Unit, onError: (String) -> Unit) {
        // log role
        println("RoleSelectionViewModel: saving role=${_uiState.value.selectedRole}")
        val currentRole = _uiState.value.selectedRole
        if (currentRole == null) {
            onError("Please select a role")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                if (currentRole != UserRole.EXPERT) {
                    // log
                    println("RoleSelectionViewModel: saving user profile with role=$currentRole")
                    // Run Firestore write off Main to avoid blocking; update UI on Main
                    withContext(Dispatchers.IO) {
                        createUserProfileUseCase(role = currentRole)
                        prefs.setAuthCompleted()
                    }
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to save"
                )
                onError(e.message ?: "Failed to save")
            }
        }
    }
}
