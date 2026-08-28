package com.voicegen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicegen.app.data.local.AppPreferences
import com.voicegen.app.data.remote.ApiClientFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val backendUrl: String = "",
    val apiKey: String = "",
    val outputFormat: String = "mp3",
    val isTestingConnection: Boolean = false,
    val connectionResult: String? = null,
)

class SettingsViewModel(
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun load() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(
                backendUrl = prefs.backendUrlFlow.first(),
                apiKey = prefs.apiKeyFlow.first(),
                outputFormat = prefs.outputFormatFlow.first(),
            )
        }
    }

    fun onBackendUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(backendUrl = url, connectionResult = null)
    }

    fun onApiKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, connectionResult = null)
    }

    fun save() {
        viewModelScope.launch {
            prefs.setBackendUrl(_uiState.value.backendUrl.trim())
            prefs.setApiKey(_uiState.value.apiKey.trim())
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            save()
            _uiState.value = _uiState.value.copy(isTestingConnection = true, connectionResult = null)
            try {
                val api = ApiClientFactory(prefs).create()
                val response = api.health()
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionResult = if (response.isSuccessful) {
                        "Connected. Model: ${response.body()?.model ?: "unknown"} (CUDA: ${response.body()?.cudaAvailable})"
                    } else {
                        "Server responded with an error (HTTP ${response.code()})."
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionResult = "Couldn't reach the server. Check the URL and that the backend is running.",
                )
            }
        }
    }
}
