package com.voicegen.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.historyDataStore by preferencesDataStore(name = "voicegen_history")

data class GenerationRecord(
    val jobId: String,
    val voiceId: String,
    val voiceName: String,
    val textPreview: String,
    val createdAtMillis: Long,
    val outputFormat: String,
)

/**
 * The backend's REST API (spec section 22) intentionally has no "list all
 * jobs" endpoint — job history is a client concern. This store keeps a
 * local index of job ids the user has started, so the Library screen
 * (spec section 33) can show past generations and poll each one's live
 * status via GET /jobs/{id}.
 */
class GenerationHistoryStore(private val context: Context) {
    private val key = stringPreferencesKey("records_json")
    private val gson = Gson()
    private val listType = object : TypeToken<List<GenerationRecord>>() {}.type

    val recordsFlow: Flow<List<GenerationRecord>> = context.historyDataStore.data.map { prefs ->
        val json = prefs[key] ?: "[]"
        try {
            gson.fromJson<List<GenerationRecord>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addRecord(record: GenerationRecord) {
        context.historyDataStore.edit { prefs ->
            val current = try {
                gson.fromJson<List<GenerationRecord>>(prefs[key] ?: "[]", listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            val updated = (listOf(record) + current).take(200) // newest first, capped
            prefs[key] = gson.toJson(updated)
        }
    }

    suspend fun removeRecord(jobId: String) {
        context.historyDataStore.edit { prefs ->
            val current = try {
                gson.fromJson<List<GenerationRecord>>(prefs[key] ?: "[]", listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            prefs[key] = gson.toJson(current.filterNot { it.jobId == jobId })
        }
    }
}
