package com.kissangram.preferences

interface PreferencesRepository {
    suspend fun getSelectedLanguageCode(): String?
    suspend fun setSelectedLanguageCode(code: String)
    suspend fun getVerificationId(): String?
    suspend fun setVerificationId(id: String)
    suspend fun clearVerificationId()
}

expect class PreferencesRepositoryFactory {
    fun create(): PreferencesRepository
}
