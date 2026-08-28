package com.voicegen.app.data.remote

import com.google.gson.annotations.SerializedName

data class VoiceProfileDto(
    val id: String,
    val name: String,
    @SerializedName("duration_seconds") val durationSeconds: Double,
    @SerializedName("model_name") val modelName: String,
    @SerializedName("created_at") val createdAt: String,
)

data class GenerateRequestDto(
    @SerializedName("voice_id") val voiceId: String,
    val text: String,
    val language: String = "en",
    val speed: Double = 1.0,
    @SerializedName("output_format") val outputFormat: String = "mp3",
)

data class JobStatusDto(
    @SerializedName("job_id") val jobId: String,
    val status: String, // queued | processing | completed | failed | cancelled
    val progress: Int,
    @SerializedName("current_chunk") val currentChunk: Int,
    @SerializedName("total_chunks") val totalChunks: Int,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class HealthDto(
    val status: String,
    val model: String,
    @SerializedName("cuda_available") val cudaAvailable: Boolean,
)
