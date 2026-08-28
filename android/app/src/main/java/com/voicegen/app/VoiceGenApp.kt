package com.voicegen.app

import android.app.Application
import com.voicegen.app.di.AppContainer

class VoiceGenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
