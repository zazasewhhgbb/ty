package com.voicegen.app.ui.record

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class RecordingPhase { IDLE, RECORDING, RECORDED, PLAYING }

data class RecordVoiceUiState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val elapsedSeconds: Int = 0,
    val recordedFile: File? = null,
)

class RecordVoiceViewModel(private val appContext: Context) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordVoiceUiState())
    val uiState: StateFlow<RecordVoiceUiState> = _uiState

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var timerJob: Job? = null
    private var outputFile: File? = null

    fun startRecording() {
        val file = File(appContext.cacheDir, "voice_recording_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mr

        _uiState.value = RecordVoiceUiState(phase = RecordingPhase.RECORDING, elapsedSeconds = 0)
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.value = _uiState.value.copy(elapsedSeconds = _uiState.value.elapsedSeconds + 1)
            }
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // stop() can throw if recording was too short — the file is still
            // handled by the duration validation on the backend upload step.
        }
        recorder?.release()
        recorder = null
        _uiState.value = _uiState.value.copy(phase = RecordingPhase.RECORDED, recordedFile = outputFile)
    }

    fun playRecording() {
        val file = outputFile ?: return
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                _uiState.value = _uiState.value.copy(phase = RecordingPhase.RECORDED)
            }
            prepare()
            start()
        }
        _uiState.value = _uiState.value.copy(phase = RecordingPhase.PLAYING)
    }

    fun stopPlayback() {
        player?.stop()
        player?.release()
        player = null
        _uiState.value = _uiState.value.copy(phase = RecordingPhase.RECORDED)
    }

    fun recordAgain() {
        stopPlayback()
        outputFile?.delete()
        _uiState.value = RecordVoiceUiState(phase = RecordingPhase.IDLE)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        recorder?.release()
        player?.release()
    }
}
