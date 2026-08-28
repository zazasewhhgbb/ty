package com.voicegen.app.data.remote

import com.voicegen.app.data.local.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds a Retrofit client pointed at whatever backend URL is currently
 * saved in AppPreferences (Settings screen — spec section 30). Rebuilt on
 * demand rather than as a singleton so changing the URL in Settings takes
 * effect without an app restart.
 */
class ApiClientFactory(private val prefs: AppPreferences) {

    private class AuthInterceptor(private val prefs: AppPreferences) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val apiKey = runBlocking { prefs.apiKeyFlow.first() }
            val request = chain.request().newBuilder()
                .apply { if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey") }
                .build()
            return chain.proceed(request)
        }
    }

    fun create(): VoiceGenApi {
        val baseUrl = runBlocking { prefs.backendUrlFlow.first() }
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(prefs))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)   // long-form audio downloads need headroom
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VoiceGenApi::class.java)
    }
}
