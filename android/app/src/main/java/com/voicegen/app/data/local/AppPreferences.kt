package com.voicegen.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "voicegen_prefs")

/**
 * Everything the app needs to "remember the voice" across restarts (spec
 * section 11): the selected voice profile id, plus connection settings.
 * The actual audio sample stays on the backend — only the id lives here.
 */
class AppPreferences(private val context: Context) {

    private object Keys {
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val API_KEY = stringPreferencesKey("api_key")
        val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
        val OUTPUT_FORMAT = stringPreferencesKey("output_format")
        val SPEED = floatPreferencesKey("speed")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val backendUrlFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.BACKEND_URL] ?: DEFAULT_DEV_URL }

    val apiKeyFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.API_KEY] ?: "" }

    val selectedVoiceIdFlow: Flow<String?> =
        context.dataStore.data.map { it[Keys.SELECTED_VOICE_ID] }

    val outputFormatFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.OUTPUT_FORMAT] ?: "mp3" }

    val speedFlow: Flow<Float> =
        context.dataStore.data.map { it[Keys.SPEED] ?: 1.0f }

    val languageFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.LANGUAGE] ?: "en" }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { it[Keys.BACKEND_URL] = url }
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[Keys.API_KEY] = key }
    }

    suspend fun setSelectedVoiceId(voiceId: String?) {
        context.dataStore.edit {
            if (voiceId == null) it.remove(Keys.SELECTED_VOICE_ID) else it[Keys.SELECTED_VOICE_ID] = voiceId
        }
    }

    suspend fun setOutputFormat(format: String) {
        context.dataStore.edit { it[Keys.OUTPUT_FORMAT] = format }
    }

    suspend fun setSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.SPEED] = speed }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language }
    }

    companion object {
        // 10.0.2.2 is the Android emulator's alias for the host machine's localhost.
        const val DEFAULT_DEV_URL = "http://10.0.2.2:8000"
    }
}
