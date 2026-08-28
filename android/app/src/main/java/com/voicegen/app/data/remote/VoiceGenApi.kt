package com.voicegen.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

/** Mirrors backend/app/api/*.py exactly — see backend README for the source of truth. */
interface VoiceGenApi {

    @GET("health")
    suspend fun health(): Response<HealthDto>

    @Multipart
    @POST("voices")
    suspend fun createVoice(
        @Part("name") name: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<VoiceProfileDto>

    @GET("voices")
    suspend fun listVoices(): Response<List<VoiceProfileDto>>

    @GET("voices/{id}")
    suspend fun getVoice(@Path("id") id: String): Response<VoiceProfileDto>

    @Streaming
    @GET("voices/{id}/sample")
    suspend fun getVoiceSample(@Path("id") id: String): Response<ResponseBody>

    @DELETE("voices/{id}")
    suspend fun deleteVoice(@Path("id") id: String): Response<Unit>

    @POST("generate")
    suspend fun generate(@Body request: GenerateRequestDto): Response<JobStatusDto>

    @GET("jobs/{id}")
    suspend fun getJob(@Path("id") id: String): Response<JobStatusDto>

    @POST("jobs/{id}/cancel")
    suspend fun cancelJob(@Path("id") id: String): Response<Unit>

    @Streaming
    @GET("jobs/{id}/audio")
    suspend fun getJobAudio(@Path("id") id: String): Response<ResponseBody>

    @DELETE("jobs/{id}")
    suspend fun deleteJob(@Path("id") id: String): Response<Unit>
}
