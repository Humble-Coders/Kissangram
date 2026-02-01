package com.kissangram.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kissangram_prefs")

class AndroidPreferencesRepository(
    private val context: Context
) : com.kissangram.repository.PreferencesRepository {

    override suspend fun getSelectedLanguageCode(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_SELECTED_LANGUAGE]
        }.first()
    }

    override suspend fun setSelectedLanguageCode(code: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_LANGUAGE] = code
        }
    }

    override suspend fun getVerificationId(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_VERIFICATION_ID]
        }.first()
    }

    override suspend fun setVerificationId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VERIFICATION_ID] = id
        }
    }

    override suspend fun clearVerificationId() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_VERIFICATION_ID)
        }
    }

    override suspend fun hasCompletedAuth(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_AUTH_COMPLETED] ?: false
        }.first()
    }

    override suspend fun setAuthCompleted() {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_COMPLETED] = true
        }
    }

    override suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_AUTH_COMPLETED)
            prefs.remove(KEY_VERIFICATION_ID)
            // Keep KEY_SELECTED_LANGUAGE so language preference persists
        }
    }

    companion object {
        private val KEY_SELECTED_LANGUAGE = stringPreferencesKey("selected_language_code")
        private val KEY_VERIFICATION_ID = stringPreferencesKey("verification_id")
        private val KEY_AUTH_COMPLETED = booleanPreferencesKey("auth_completed")
    }
}
