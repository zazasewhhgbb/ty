package com.voicegen.app.ui.voicesetup

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicegen.app.data.remote.VoiceProfileDto
import com.voicegen.app.data.repository.ApiResult
import com.voicegen.app.data.repository.VoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class VoiceSetupUiState(
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
    val createdVoice: VoiceProfileDto? = null,
)

class VoiceSetupViewModel(
    private val voiceRepository: VoiceRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceSetupUiState())
    val uiState: StateFlow<VoiceSetupUiState> = _uiState

    fun uploadFromUri(uri: Uri, displayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)

            val file = copyUriToCache(uri)
            if (file == null) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    errorMessage = "Couldn't read that file. Please choose a different audio file.",
                )
                return@launch
            }

            val mimeType = resolveMimeType(uri) ?: "audio/*"
            when (val result = voiceRepository.uploadVoiceFromFile(displayName, file, mimeType)) {
                is ApiResult.Success -> {
                    voiceRepository.setSelectedVoiceId(result.data.id)
                    _uiState.value = _uiState.value.copy(isUploading = false, createdVoice = result.data)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isUploading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun uploadRecordedFile(file: File, displayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
            when (val result = voiceRepository.uploadVoiceFromFile(displayName, file, "audio/mp4")) {
                is ApiResult.Success -> {
                    voiceRepository.setSelectedVoiceId(result.data.id)
                    _uiState.value = _uiState.value.copy(isUploading = false, createdVoice = result.data)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isUploading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val resolver = appContext.contentResolver
            val extension = resolveMimeType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "audio"
            val outFile = File(appContext.cacheDir, "upload_${System.currentTimeMillis()}.$extension")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            } ?: return null
            outFile
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveMimeType(uri: Uri): String? = appContext.contentResolver.getType(uri)
}
