package com.voicegen.app.ui.generation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicegen.app.data.repository.ApiResult
import com.voicegen.app.data.repository.GenerationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class GenerationUiState(
    val status: String = "queued",
    val progress: Int = 0,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val elapsedSeconds: Int = 0,
    val errorMessage: String? = null,
    val downloadedFile: File? = null,
    val outputFormat: String = "mp3",
)

/**
 * Polls GET /jobs/{id} on an interval (spec section 16/17). Because job
 * state lives entirely on the backend, re-opening the app on an in-flight
 * job id and calling loadJob() again picks the poll right back up —
 * satisfying "if the application is closed and reopened, it should be
 * able to retrieve the job status" (spec section 17).
 */
class GenerationViewModel(
    private val generationRepository: GenerationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerationUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState

    private var pollJob: Job? = null
    private var timerJob: Job? = null

    fun start(jobId: String, outputFormat: String) {
        _uiState.value = _uiState.value.copy(outputFormat = outputFormat)
        pollJob?.cancel()
        timerJob?.cancel()

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_uiState.value.status !in listOf("completed", "failed", "cancelled")) {
                    _uiState.value = _uiState.value.copy(elapsedSeconds = _uiState.value.elapsedSeconds + 1)
                }
            }
        }

        pollJob = viewModelScope.launch {
            while (true) {
                when (val result = generationRepository.getJobStatus(jobId)) {
                    is ApiResult.Success -> {
                        val job = result.data
                        _uiState.value = _uiState.value.copy(
                            status = job.status,
                            progress = job.progress,
                            currentChunk = job.currentChunk,
                            totalChunks = job.totalChunks,
                            errorMessage = job.errorMessage,
                        )
                        if (job.status == "completed") {
                            val file = generationRepository.downloadAudioToLocalFile(jobId, outputFormat)
                            _uiState.value = _uiState.value.copy(downloadedFile = file)
                            timerJob?.cancel()
                            return@launch
                        }
                        if (job.status in listOf("failed", "cancelled")) {
                            timerJob?.cancel()
                            return@launch
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(errorMessage = result.message)
                    }
                }
                delay(2000)
            }
        }
    }

    fun cancel(jobId: String) {
        viewModelScope.launch { generationRepository.cancelJob(jobId) }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        timerJob?.cancel()
    }
}
