package com.kissangram.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.kissangram.model.Language
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.usecase.GetSelectedLanguageUseCase
import com.kissangram.usecase.SetSelectedLanguageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class LanguageSelectionViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    private val preferencesRepository = AndroidPreferencesRepository(application)
    private val getSelectedLanguageUseCase = GetSelectedLanguageUseCase(preferencesRepository)
    private val setSelectedLanguageUseCase = SetSelectedLanguageUseCase(preferencesRepository)
    
    private val _uiState = MutableStateFlow(LanguageSelectionUiState())
    val uiState: StateFlow<LanguageSelectionUiState> = _uiState.asStateFlow()
    
    init {
        loadInitialState()
        uploadTestDataToFirestore()
    }
    
    /** One-time test write to verify Firestore uploads work. Check Firebase Console → test_uploads. */
    private fun uploadTestDataToFirestore() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Firestore test upload: STARTING...")
                    // Use named database "kissangram" instead of "(default)"
                    val db = FirebaseFirestore.getInstance(
                        com.google.firebase.FirebaseApp.getInstance(),
                        "kissangram"
                    )
                    val data = hashMapOf<String, Any>(
                        "timestamp" to System.currentTimeMillis(),
                        "random" to UUID.randomUUID().toString(),
                        "source" to "language_selection"
                    )
                    val startTime = System.currentTimeMillis()
                    kotlinx.coroutines.withTimeout(10_000L) {
                        db.collection("test_uploads").document().set(data).await()
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Firestore test upload SUCCEEDED in ${elapsed}ms: test_uploads/${data["random"]}")
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.e(TAG, "Firestore test upload TIMED OUT after 10s - Firestore may be unreachable", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore test upload FAILED", e)
                }
            }
        }
    }
    
    private fun loadInitialState() {
        viewModelScope.launch {
            val selectedLanguage = getSelectedLanguageUseCase()
            
            _uiState.value = _uiState.value.copy(
                selectedLanguage = selectedLanguage,
                filteredLanguages = Language.SUPPORTED_LANGUAGES
            )
        }
    }
    
    fun onLanguageSelected(language: Language) {
        _uiState.value = _uiState.value.copy(selectedLanguage = language)
    }
    
    fun onSearchQueryChanged(query: String) {
        val filtered = if (query.isBlank()) {
            Language.SUPPORTED_LANGUAGES
        } else {
            Language.SUPPORTED_LANGUAGES.filter {
                it.englishName.contains(query, ignoreCase = true) ||
                it.nativeName.contains(query, ignoreCase = true)
            }
        }
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredLanguages = filtered
        )
    }
    
    fun onContinueClicked(onLanguageSelected: (String) -> Unit) {
        viewModelScope.launch {
            val selectedCode = _uiState.value.selectedLanguage.code
            setSelectedLanguageUseCase(selectedCode)
            onLanguageSelected(selectedCode)
        }
    }
}

data class LanguageSelectionUiState(
    val selectedLanguage: Language = Language.SUPPORTED_LANGUAGES.first(),
    val searchQuery: String = "",
    val filteredLanguages: List<Language> = Language.SUPPORTED_LANGUAGES
)

private const val TAG = "LanguageSelectionVM"
