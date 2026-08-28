package com.voicegen.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicegen.app.data.local.AppPreferences
import com.voicegen.app.data.local.GenerationHistoryStore
import com.voicegen.app.data.local.GenerationRecord
import com.voicegen.app.data.remote.VoiceProfileDto
import com.voicegen.app.data.repository.ApiResult
import com.voicegen.app.data.repository.GenerationRepository
import com.voicegen.app.data.repository.VoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedVoice: VoiceProfileDto? = null,
    val text: String = "",
    val language: String = "en",
    val speed: Float = 1.0f,
    val outputFormat: String = "mp3",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val startedJobId: String? = null,
    val noVoicesRemain: Boolean = false,
)

class HomeViewModel(
    private val voiceRepository: VoiceRepository,
    private val generationRepository: GenerationRepository,
    private val prefs: AppPreferences,
    private val historyStore: GenerationHistoryStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    fun loadSelectedVoice() {
        viewModelScope.launch {
            val speed = prefs.speedFlow.first()
            val format = prefs.outputFormatFlow.first()
            val language = prefs.languageFlow.first()
            _uiState.value = _uiState.value.copy(speed = speed, outputFormat = format, language = language)

            val selectedId = prefs.selectedVoiceIdFlow.first()
            if (selectedId == null) {
                _uiState.value = _uiState.value.copy(noVoicesRemain = true)
                return@launch
            }
            when (val result = voiceRepository.getVoice(selectedId)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(selectedVoice = result.data, noVoicesRemain = false)
                is ApiResult.Error -> _uiState.value = _uiState.value.copy(noVoicesRemain = true)
            }
        }
    }

    fun onTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(text = text)
    }

    fun onImportedText(text: String) {
        _uiState.value = _uiState.value.copy(text = text)
    }

    fun onSpeedChanged(speed: Float) {
        _uiState.value = _uiState.value.copy(speed = speed)
        viewModelScope.launch { prefs.setSpeed(speed) }
    }

    fun onOutputFormatChanged(format: String) {
        _uiState.value = _uiState.value.copy(outputFormat = format)
        viewModelScope.launch { prefs.setOutputFormat(format) }
    }

    fun onLanguageChanged(language: String) {
        _uiState.value = _uiState.value.copy(language = language)
        viewModelScope.launch { prefs.setLanguage(language) }
    }

    fun generate() {
        val voice = _uiState.value.selectedVoice ?: return
        val text = _uiState.value.text
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter or import some text first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
            val result = generationRepository.startGeneration(
                voiceId = voice.id,
                text = text,
                language = _uiState.value.language,
                speed = _uiState.value.speed,
                outputFormat = _uiState.value.outputFormat,
            )
            when (result) {
                is ApiResult.Success -> {
                    historyStore.addRecord(
                        GenerationRecord(
                            jobId = result.data.jobId,
                            voiceId = voice.id,
                            voiceName = voice.name,
                            textPreview = text.take(120),
                            createdAtMillis = System.currentTimeMillis(),
                            outputFormat = _uiState.value.outputFormat,
                        )
                    )
                    _uiState.value = _uiState.value.copy(isSubmitting = false, startedJobId = result.data.jobId)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = result.message)
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun consumeStartedJob() {
        _uiState.value = _uiState.value.copy(startedJobId = null)
    }
}
