package ru.macdroid.ollama.presentation.analytics

import ru.macdroid.ollama.domain.model.Message
import ru.macdroid.ollama.domain.model.analytics.AnalyticsContext
import ru.macdroid.ollama.domain.model.analytics.DataFile
import ru.macdroid.ollama.domain.model.analytics.DataStatistics
import ru.macdroid.ollama.domain.model.analytics.ParsedData

/**
 * MVI State for Analytics screen.
 */
data class AnalyticsState(
    val loadedFile: DataFile? = null,
    val parsedData: ParsedData? = null,
    val statistics: DataStatistics? = null,
    val analyticsContext: AnalyticsContext? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val importMessage: String? = null,
    val isComputingStats: Boolean = false,
    val isAnalyzing: Boolean = false,
    val error: AnalyticsError? = null
) {
    val hasData: Boolean get() = parsedData != null
    val canAnalyze: Boolean get() = hasData && analyticsContext != null && !isAnalyzing
}

/**
 * MVI Intents for Analytics screen.
 */
sealed interface AnalyticsIntent {
    data class ImportFile(
        val uriString: String,
        val fileName: String,
        val mimeType: String?
    ) : AnalyticsIntent

    data class UpdateInput(val text: String) : AnalyticsIntent
    data object SendQuestion : AnalyticsIntent
    data object ClearData : AnalyticsIntent
    data object DismissError : AnalyticsIntent
    data object ComputeStatistics : AnalyticsIntent
}

/**
 * MVI Side Effects for Analytics screen.
 */
sealed interface AnalyticsEffect {
    data object ScrollToBottom : AnalyticsEffect
    data object ShowFilePicker : AnalyticsEffect
    data class ShowToast(val message: String) : AnalyticsEffect
}

/**
 * Error types for Analytics screen.
 */
sealed class AnalyticsError(val message: String) {
    class FileReadError(message: String) : AnalyticsError(message)
    class ParseError(message: String) : AnalyticsError(message)
    class FileTooLarge(val maxSizeMb: Int = 6) : AnalyticsError("Файл слишком большой (макс. $maxSizeMb MB)")
    class UnsupportedFormat(val extension: String) : AnalyticsError("Неподдерживаемый формат: $extension")
    class AnalysisError(message: String) : AnalyticsError(message)
    class ModelNotAvailable : AnalyticsError("Локальная модель не загружена")
}
