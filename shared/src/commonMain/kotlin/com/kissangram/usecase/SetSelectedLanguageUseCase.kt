package com.kissangram.usecase

import com.kissangram.repository.PreferencesRepository

class SetSelectedLanguageUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(languageCode: String) {
        preferencesRepository.setSelectedLanguageCode(languageCode)
    }
}
