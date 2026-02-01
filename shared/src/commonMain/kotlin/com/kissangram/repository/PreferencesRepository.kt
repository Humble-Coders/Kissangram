package com.kissangram.repository

/**
 * Local preferences and session state.
 * Use DataStore on Android and UserDefaults on iOS (best practice).
 */
interface PreferencesRepository {
    // Language preference (persisted across sessions)
    suspend fun getSelectedLanguageCode(): String?
    suspend fun setSelectedLanguageCode(code: String)
    
    // OTP verification flow (temporary)
    suspend fun getVerificationId(): String?
    suspend fun setVerificationId(id: String)
    suspend fun clearVerificationId()
    
    // Auth completion – user has finished onboarding (role selection / doc upload)
    suspend fun hasCompletedAuth(): Boolean
    suspend fun setAuthCompleted()
    
    /** Clears session so next launch shows auth flow. Does NOT clear language preference. */
    suspend fun clearSession()
}
