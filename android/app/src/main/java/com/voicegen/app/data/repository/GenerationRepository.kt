package com.voicegen.app.data.repository

import android.content.Context
import com.voicegen.app.data.remote.GenerateRequestDto
import com.voicegen.app.data.remote.JobStatusDto
import com.voicegen.app.data.remote.VoiceGenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GenerationRepository(
    private val apiProvider: () -> VoiceGenApi,
    private val context: Context,
) {
    private val api get() = apiProvider()

    suspend fun startGeneration(
        voiceId: String,
        text: String,
        language: String,
        speed: Float,
        outputFormat: String,
    ): ApiResult<JobStatusDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.generate(
                GenerateRequestDto(
                    voiceId = voiceId,
                    text = text,
                    language = language,
                    speed = speed.toDouble(),
                    outputFormat = outputFormat,
                )
            )
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error("Couldn't start generation. Please try again.")
            }
        } catch (t: Throwable) {
            ApiResult.Error(humanizeError(t))
        }
    }

    suspend fun getJobStatus(jobId: String): ApiResult<JobStatusDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getJob(jobId)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error("Couldn't fetch job status.")
            }
        } catch (t: Throwable) {
            ApiResult.Error(humanizeError(t))
        }
    }

    suspend fun cancelJob(jobId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            api.cancelJob(jobId)
            ApiResult.Success(Unit)
        } catch (t: Throwable) {
            ApiResult.Error(humanizeError(t))
        }
    }

    suspend fun deleteJob(jobId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            api.deleteJob(jobId)
            ApiResult.Success(Unit)
        } catch (t: Throwable) {
            ApiResult.Error(humanizeError(t))
        }
    }

    /**
     * Downloads finished audio to app-local storage (not the database, not
     * held in memory — spec sections 19/25/32) so it can be played, shared,
     * or saved without re-hitting the network each time.
     */
    suspend fun downloadAudioToLocalFile(jobId: String, outputFormat: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getJobAudio(jobId)
                val body = response.body() ?: return@withContext null
                val dir = File(context.filesDir, "generations").apply { mkdirs() }
                val outFile = File(dir, "$jobId.$outputFormat")
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                outFile
            } catch (t: Throwable) {
                null
            }
        }
}
