package ru.macdroid.ollama.presentation.settings

import ru.macdroid.ollama.data.local.llm.ModelInfo

data class SettingsState(
    val useLocalLlm: Boolean = false,
    val isModelAvailable: Boolean = false,
    val isModelLoading: Boolean = false,
    val isCheckingModel: Boolean = true,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val modelInfo: ModelInfo? = null,
    val ollamaServerUrl: String = "http://10.0.2.2:11434",
    val error: String? = null
) {
    val canEnableLocalLlm: Boolean
        get() = isModelAvailable && !isModelLoading

    val downloadProgressPercent: Int
        get() = (downloadProgress * 100).toInt()

    val downloadProgressText: String
        get() {
            if (totalBytes <= 0) return "Downloading..."
            val downloadedMb = downloadedBytes / 1_000_000.0
            val totalMb = totalBytes / 1_000_000.0
            return String.format("%.1f / %.1f MB", downloadedMb, totalMb)
        }
}

sealed interface SettingsIntent {
    data object LoadSettings : SettingsIntent
    data object ToggleLocalLlm : SettingsIntent
    data object DownloadModel : SettingsIntent
    data object CancelDownload : SettingsIntent
    data object DeleteModel : SettingsIntent
    data object CheckModelStatus : SettingsIntent
    data class UpdateServerUrl(val url: String) : SettingsIntent
    data object DismissError : SettingsIntent
    data object NavigateBack : SettingsIntent
}

sealed interface SettingsEffect {
    data class ShowError(val message: String) : SettingsEffect
    data object ModelDownloaded : SettingsEffect
    data object ModelDeleted : SettingsEffect
    data object NavigateBack : SettingsEffect
}
