package com.voicegen.app.di

import android.content.Context
import com.voicegen.app.data.local.AppPreferences
import com.voicegen.app.data.local.GenerationHistoryStore
import com.voicegen.app.data.remote.ApiClientFactory
import com.voicegen.app.data.repository.GenerationRepository
import com.voicegen.app.data.repository.VoiceRepository

/**
 * Small hand-rolled service locator. The app is not large enough yet to
 * need Hilt/Dagger; if it grows (spec section 36 future features), this is
 * the seam where a DI framework would slot in without touching ViewModels.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val preferences = AppPreferences(appContext)
    val historyStore = GenerationHistoryStore(appContext)

    private val apiClientFactory = ApiClientFactory(preferences)

    // Rebuilt lazily each call so a Settings change to backend URL/API key
    // is picked up on the next request without restarting the app.
    private val apiProvider: () -> com.voicegen.app.data.remote.VoiceGenApi = { apiClientFactory.create() }

    val voiceRepository = VoiceRepository(apiProvider, preferences, appContext)
    val generationRepository = GenerationRepository(apiProvider, appContext)
}
