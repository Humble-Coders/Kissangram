package com.kissangram.ui.languageselection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.Language
import com.kissangram.preferences.PreferencesRepositoryFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LanguageSelectionViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    private val preferencesRepository = PreferencesRepositoryFactory(application).create()
    
    private val _uiState = MutableStateFlow(LanguageSelectionUiState())
    val uiState: StateFlow<LanguageSelectionUiState> = _uiState.asStateFlow()
    
    init {
        loadInitialState()
    }
    
    private fun loadInitialState() {
        viewModelScope.launch {
            val savedLanguage = preferencesRepository.getSelectedLanguageCode()
            val selectedLanguage = savedLanguage?.let { code ->
                Language.SUPPORTED_LANGUAGES.find { it.code == code }
            } ?: Language.SUPPORTED_LANGUAGES.first() // Default to Hindi
            
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
            preferencesRepository.setSelectedLanguageCode(selectedCode)
            onLanguageSelected(selectedCode)
        }
    }
}

data class LanguageSelectionUiState(
    val selectedLanguage: Language = Language.SUPPORTED_LANGUAGES.first(),
    val searchQuery: String = "",
    val filteredLanguages: List<Language> = Language.SUPPORTED_LANGUAGES
)
