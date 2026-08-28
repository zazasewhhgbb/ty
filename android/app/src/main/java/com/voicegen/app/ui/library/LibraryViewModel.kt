package com.voicegen.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicegen.app.data.local.GenerationHistoryStore
import com.voicegen.app.data.local.GenerationRecord
import com.voicegen.app.data.remote.JobStatusDto
import com.voicegen.app.data.repository.ApiResult
import com.voicegen.app.data.repository.GenerationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

data class LibraryItemUiState(
    val record: GenerationRecord,
    val status: JobStatusDto? = null,
    val localFile: File? = null,
)

data class LibraryUiState(
    val items: List<LibraryItemUiState> = emptyList(),
    val isLoading: Boolean = true,
)

class LibraryViewModel(
    private val historyStore: GenerationHistoryStore,
    private val generationRepository: GenerationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    fun load() {
        viewModelScope.launch {
            historyStore.recordsFlow.collectLatest { records ->
                _uiState.value = LibraryUiState(
                    items = records.map { LibraryItemUiState(record = it) },
                    isLoading = false,
                )
                // Refresh each job's live status (in case one finished/failed
                // since it was last checked).
                records.forEach { record ->
                    viewModelScope.launch {
                        when (val result = generationRepository.getJobStatus(record.jobId)) {
                            is ApiResult.Success -> updateItemStatus(record.jobId, result.data)
                            is ApiResult.Error -> Unit // leave as unknown; server may be offline
                        }
                    }
                }
            }
        }
    }

    private fun updateItemStatus(jobId: String, status: JobStatusDto) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map {
                if (it.record.jobId == jobId) it.copy(status = status) else it
            }
        )
    }

    fun downloadAndPlay(jobId: String, outputFormat: String, onReady: (File) -> Unit) {
        viewModelScope.launch {
            val existing = _uiState.value.items.firstOrNull { it.record.jobId == jobId }?.localFile
            if (existing != null) {
                onReady(existing)
                return@launch
            }
            val file = generationRepository.downloadAudioToLocalFile(jobId, outputFormat)
            if (file != null) {
                _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items.map {
                        if (it.record.jobId == jobId) it.copy(localFile = file) else it
                    }
                )
                onReady(file)
            }
        }
    }

    fun delete(jobId: String) {
        viewModelScope.launch {
            generationRepository.deleteJob(jobId)
            historyStore.removeRecord(jobId)
        }
    }
}
