package com.voicegen.app.data.repository

import android.content.Context
import com.voicegen.app.data.local.AppPreferences
import com.voicegen.app.data.remote.VoiceGenApi
import com.voicegen.app.data.remote.VoiceProfileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class VoiceRepository(
    private val apiProvider: () -> VoiceGenApi,
    private val prefs: AppPreferences,
    private val context: Context,
) {
    private val api get() = apiProvider()

    suspend fun listVoices(): ApiResult<List<VoiceProfileDto>> = safeCall { api.listVoices() }

    suspend fun getVoice(id: String): ApiResult<VoiceProfileDto> = safeCall { api.getVoice(id) }

    /** contentUri is copied to a temp file first since Retrofit's MultipartBody needs a real File/stream. */
    suspend fun uploadVoiceFromFile(name: String, file: File, mimeType: String): ApiResult<VoiceProfileDto> =
        withContext(Dispatchers.IO) {
            try {
                val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
                val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", file.name, fileBody)
                val response = api.createVoice(namePart, filePart)
                if (response.isSuccessful && response.body() != null) {
                    ApiResult.Success(response.body()!!)
                } else {
                    ApiResult.Error(parseError(response.errorBody()?.string()))
                }
            } catch (t: Throwable) {
                ApiResult.Error(humanizeError(t))
            }
        }

    suspend fun deleteVoice(id: String): ApiResult<Unit> = safeCall { api.deleteVoice(id) }

    /** Downloads the reference sample to a local cache file so ExoPlayer can play it. */
    suspend fun downloadSampleToCache(voiceId: String): File? = withContext(Dispatchers.IO) {
        try {
            val response = api.getVoiceSample(voiceId)
            val body = response.body() ?: return@withContext null
            val outFile = File(context.cacheDir, "voice_sample_$voiceId.audio")
            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            outFile
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun setSelectedVoiceId(id: String?) = prefs.setSelectedVoiceId(id)

    private suspend fun <T> safeCall(block: suspend () -> retrofit2.Response<T>): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                val response = block()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) ApiResult.Success(body) else ApiResult.Success(Unit as T)
                } else {
                    ApiResult.Error(parseError(response.errorBody()?.string()))
                }
            } catch (t: Throwable) {
                ApiResult.Error(humanizeError(t))
            }
        }

    private fun parseError(body: String?): String {
        if (body.isNullOrBlank()) return "The server returned an error."
        return try {
            val obj = org.json.JSONObject(body)
            obj.optString("detail", "The server returned an error.")
        } catch (e: Exception) {
            "The server returned an error."
        }
    }
}
