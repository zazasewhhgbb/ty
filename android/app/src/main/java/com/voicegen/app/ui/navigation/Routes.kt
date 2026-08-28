package com.voicegen.app.ui.navigation

sealed class Routes(val route: String) {
    data object VoiceSetup : Routes("voice_setup")
    data object RecordVoice : Routes("record_voice")
    data object VoiceReady : Routes("voice_ready/{voiceId}") {
        fun build(voiceId: String) = "voice_ready/$voiceId"
    }
    data object Home : Routes("home")
    data object Generation : Routes("generation/{jobId}/{outputFormat}") {
        fun build(jobId: String, outputFormat: String) = "generation/$jobId/$outputFormat"
    }
    data object Library : Routes("library")
    data object VoiceLibrary : Routes("voice_library")
    data object Settings : Routes("settings")
}
