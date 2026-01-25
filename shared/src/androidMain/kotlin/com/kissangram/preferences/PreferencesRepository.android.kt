package com.kissangram.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PreferencesRepositoryFactory(private val context: Context) {
    actual fun create(): PreferencesRepository {
        return AndroidPreferencesRepository(context)
    }
}

class AndroidPreferencesRepository(
    private val context: Context
) : PreferencesRepository {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("kissangram_prefs", Context.MODE_PRIVATE)
    }
    
    override suspend fun getSelectedLanguageCode(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_SELECTED_LANGUAGE, null)
    }
    
    override suspend fun setSelectedLanguageCode(code: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_SELECTED_LANGUAGE, code).apply()
    }
    
    override suspend fun getVerificationId(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_VERIFICATION_ID, null)
    }
    
    override suspend fun setVerificationId(id: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_VERIFICATION_ID, id).apply()
    }
    
    override suspend fun clearVerificationId() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_VERIFICATION_ID).apply()
    }
    
    companion object {
        private const val KEY_SELECTED_LANGUAGE = "selected_language_code"
        private const val KEY_VERIFICATION_ID = "verification_id"
    }
}
