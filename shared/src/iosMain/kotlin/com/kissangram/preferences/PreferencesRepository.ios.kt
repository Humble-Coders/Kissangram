package com.kissangram.preferences

import platform.Foundation.NSUserDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PreferencesRepositoryFactory {
    actual fun create(): PreferencesRepository {
        return IOSPreferencesRepository()
    }
}

class IOSPreferencesRepository : PreferencesRepository {
    private val userDefaults = NSUserDefaults.standardUserDefaults
    
    override suspend fun getSelectedLanguageCode(): String? = withContext(Dispatchers.Main) {
        userDefaults.objectForKey(KEY_SELECTED_LANGUAGE) as? String
    }
    
    override suspend fun setSelectedLanguageCode(code: String) = withContext(Dispatchers.Main) {
        userDefaults.setObject(code, forKey = KEY_SELECTED_LANGUAGE)
    }
    
    override suspend fun getVerificationId(): String? = withContext(Dispatchers.Main) {
        userDefaults.objectForKey(KEY_VERIFICATION_ID) as? String
    }
    
    override suspend fun setVerificationId(id: String) = withContext(Dispatchers.Main) {
        userDefaults.setObject(id, forKey = KEY_VERIFICATION_ID)
    }
    
    override suspend fun clearVerificationId() = withContext(Dispatchers.Main) {
        userDefaults.removeObjectForKey(KEY_VERIFICATION_ID)
    }
    
    companion object {
        private const val KEY_SELECTED_LANGUAGE = "selected_language_code"
        private const val KEY_VERIFICATION_ID = "verification_id"
    }
}
