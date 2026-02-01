package com.kissangram.usecase

import com.kissangram.model.Language
import com.kissangram.repository.PreferencesRepository

class GetSelectedLanguageUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(): Language {
        val savedCode = preferencesRepository.getSelectedLanguageCode()
        return savedCode?.let { code ->
            Language.SUPPORTED_LANGUAGES.find { it.code == code }
        } ?: Language.SUPPORTED_LANGUAGES.first() // Default to Hindi
    }
}
