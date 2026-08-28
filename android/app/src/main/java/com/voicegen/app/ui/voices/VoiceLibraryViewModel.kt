package com.voicegen.app.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicegen.app.data.local.AppPreferences
import com.voicegen.app.data.remote.VoiceProfileDto
import com.voicegen.app.data.repository.ApiResult
import com.voicegen.app.data.repository.VoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class VoiceLibraryUiState(
    val voices: List<VoiceProfileDto> = emptyList(),
    val selectedVoiceId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class VoiceLibraryViewModel(
    private val voiceRepository: VoiceRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceLibraryUiState())
    val uiState: StateFlow<VoiceLibraryUiState> = _uiState

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val selectedId = prefs.selectedVoiceIdFlow.first()
            when (val result = voiceRepository.listVoices()) {
                is ApiResult.Success -> _uiState.value = VoiceLibraryUiState(
                    voices = result.data,
                    selectedVoiceId = selectedId,
                    isLoading = false,
                )
                is ApiResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    fun selectVoice(voiceId: String) {
        viewModelScope.launch {
            voiceRepository.setSelectedVoiceId(voiceId)
            _uiState.value = _uiState.value.copy(selectedVoiceId = voiceId)
        }
    }

    fun deleteVoice(voiceId: String) {
        viewModelScope.launch {
            when (voiceRepository.deleteVoice(voiceId)) {
                is ApiResult.Success -> {
                    if (_uiState.value.selectedVoiceId == voiceId) {
                        voiceRepository.setSelectedVoiceId(null)
                    }
                    load()
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
