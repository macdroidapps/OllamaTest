package ru.macdroid.ollama.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.macdroid.ollama.data.local.llm.LlmEngine
import ru.macdroid.ollama.data.local.llm.ModelDownloadState
import ru.macdroid.ollama.data.local.llm.ModelManager
import ru.macdroid.ollama.data.local.llm.ModelManagerContract
import ru.macdroid.ollama.data.local.preferences.SettingsPreferences

class SettingsViewModel(
    private val settingsPreferences: SettingsPreferences,
    private val modelManager: ModelManagerContract,
    private val llmEngine: LlmEngine
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SettingsEffect>()
    val effect: SharedFlow<SettingsEffect> = _effect.asSharedFlow()

    private var downloadJob: Job? = null

    init {
        onIntent(SettingsIntent.LoadSettings)
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.LoadSettings -> loadSettings()
            is SettingsIntent.ToggleLocalLlm -> toggleLocalLlm()
            is SettingsIntent.DownloadModel -> downloadModel()
            is SettingsIntent.CancelDownload -> cancelDownload()
            is SettingsIntent.DeleteModel -> deleteModel()
            is SettingsIntent.CheckModelStatus -> checkModelStatus()
            is SettingsIntent.UpdateServerUrl -> updateServerUrl(intent.url)
            is SettingsIntent.DismissError -> dismissError()
            is SettingsIntent.NavigateBack -> navigateBack()
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingModel = true) }

            val useLocalLlm = settingsPreferences.useLocalLlm.first()
            val serverUrl = settingsPreferences.ollamaServerUrl.first()
            val isModelAvailable = modelManager.isModelAvailable()
            val modelInfo = modelManager.getModelInfo()

            _state.update {
                it.copy(
                    useLocalLlm = useLocalLlm,
                    ollamaServerUrl = serverUrl,
                    isModelAvailable = isModelAvailable,
                    modelInfo = modelInfo,
                    isCheckingModel = false
                )
            }
        }
    }

    private fun toggleLocalLlm() {
        viewModelScope.launch {
            val currentState = _state.value

            if (!currentState.isModelAvailable && !currentState.useLocalLlm) {
                _effect.emit(SettingsEffect.ShowError("Please download the model first"))
                return@launch
            }

            val newValue = !currentState.useLocalLlm
            settingsPreferences.setUseLocalLlm(newValue)

            if (!newValue && llmEngine.isModelLoaded()) {
                llmEngine.unloadModel()
            }

            _state.update { it.copy(useLocalLlm = newValue) }
        }
    }

    private fun downloadModel() {
        if (_state.value.isModelLoading) return

        downloadJob = viewModelScope.launch {
            modelManager.downloadModel(ModelManager.DEFAULT_MODEL_URL)
                .collect { downloadState ->
                    when (downloadState) {
                        is ModelDownloadState.Idle -> {
                            _state.update {
                                it.copy(isModelLoading = false, downloadProgress = 0f)
                            }
                        }
                        is ModelDownloadState.Downloading -> {
                            _state.update {
                                it.copy(
                                    isModelLoading = true,
                                    downloadProgress = downloadState.progress,
                                    downloadedBytes = downloadState.downloadedBytes,
                                    totalBytes = downloadState.totalBytes
                                )
                            }
                        }
                        is ModelDownloadState.Completed -> {
                            val modelInfo = modelManager.getModelInfo()
                            _state.update {
                                it.copy(
                                    isModelLoading = false,
                                    isModelAvailable = true,
                                    downloadProgress = 1f,
                                    modelInfo = modelInfo
                                )
                            }
                            _effect.emit(SettingsEffect.ModelDownloaded)
                        }
                        is ModelDownloadState.Error -> {
                            _state.update {
                                it.copy(
                                    isModelLoading = false,
                                    downloadProgress = 0f,
                                    error = downloadState.message
                                )
                            }
                            _effect.emit(SettingsEffect.ShowError(downloadState.message))
                        }
                    }
                }
        }
    }

    private fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update {
            it.copy(
                isModelLoading = false,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L
            )
        }
    }

    private fun deleteModel() {
        viewModelScope.launch {
            if (llmEngine.isModelLoaded()) {
                llmEngine.unloadModel()
            }

            modelManager.deleteModel()
                .onSuccess {
                    settingsPreferences.setUseLocalLlm(false)
                    _state.update {
                        it.copy(
                            isModelAvailable = false,
                            useLocalLlm = false,
                            modelInfo = null
                        )
                    }
                    _effect.emit(SettingsEffect.ModelDeleted)
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                    _effect.emit(SettingsEffect.ShowError(e.message ?: "Failed to delete model"))
                }
        }
    }

    private fun checkModelStatus() {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingModel = true) }
            val isAvailable = modelManager.isModelAvailable()
            val modelInfo = if (isAvailable) modelManager.getModelInfo() else null
            _state.update {
                it.copy(
                    isModelAvailable = isAvailable,
                    modelInfo = modelInfo,
                    isCheckingModel = false
                )
            }
        }
    }

    private fun updateServerUrl(url: String) {
        viewModelScope.launch {
            settingsPreferences.setOllamaServerUrl(url)
            _state.update { it.copy(ollamaServerUrl = url) }
        }
    }

    private fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _effect.emit(SettingsEffect.NavigateBack)
        }
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
    }
}
