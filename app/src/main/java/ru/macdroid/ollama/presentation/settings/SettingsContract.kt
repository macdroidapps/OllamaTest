package ru.macdroid.ollama.presentation.settings

import ru.macdroid.ollama.data.local.llm.ModelInfo
import ru.macdroid.ollama.data.local.llm.PromptTemplate

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
    val error: String? = null,
    // LLM Generation Parameters
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 512,
    val promptTemplate: PromptTemplate = PromptTemplate.ASSISTANT
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

    // LLM Generation Parameter Intents
    data class UpdateTemperature(val value: Float) : SettingsIntent
    data class UpdateTopP(val value: Float) : SettingsIntent
    data class UpdateTopK(val value: Int) : SettingsIntent
    data class UpdateRepeatPenalty(val value: Float) : SettingsIntent
    data class UpdateMaxTokens(val value: Int) : SettingsIntent
    data class UpdatePromptTemplate(val template: PromptTemplate) : SettingsIntent
}

sealed interface SettingsEffect {
    data class ShowError(val message: String) : SettingsEffect
    data object ModelDownloaded : SettingsEffect
    data object ModelDeleted : SettingsEffect
    data object NavigateBack : SettingsEffect
}
