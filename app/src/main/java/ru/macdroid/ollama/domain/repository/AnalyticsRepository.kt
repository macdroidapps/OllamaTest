package ru.macdroid.ollama.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import ru.macdroid.ollama.domain.model.analytics.AnalyticsContext
import ru.macdroid.ollama.domain.model.analytics.DataFile
import ru.macdroid.ollama.domain.model.analytics.DataStatistics
import ru.macdroid.ollama.domain.model.analytics.FileType
import ru.macdroid.ollama.domain.model.analytics.ParsedData

/**
 * Repository for data analytics operations.
 */
interface AnalyticsRepository {

    /**
     * Import and parse a file from URI.
     *
     * @param uri File URI
     * @param fileName Original file name
     * @param type File type (CSV, JSON, LOG)
     * @return Flow emitting import progress and result
     */
    fun importFile(uri: Uri, fileName: String, type: FileType): Flow<ImportResult>

    /**
     * Compute statistics for parsed data.
     *
     * @param data Parsed data
     * @return Computed statistics
     */
    suspend fun computeStatistics(data: ParsedData): DataStatistics

    /**
     * Build analytics context for LLM from data and statistics.
     *
     * @param data Parsed data
     * @param statistics Computed statistics (optional)
     * @return Analytics context ready for LLM
     */
    fun buildContext(data: ParsedData, statistics: DataStatistics?): AnalyticsContext

    /**
     * Analyze data with a question using local LLM.
     *
     * @param question User's question
     * @param context Analytics context with data
     * @return Flow of response tokens
     */
    fun analyzeWithLlm(question: String, context: AnalyticsContext): Flow<String>
}

/**
 * Result of file import operation.
 */
sealed class ImportResult {
    data class Progress(
        val stage: ImportStage,
        val progress: Float,  // 0.0 to 1.0
        val message: String? = null
    ) : ImportResult()

    data class Success(
        val dataFile: DataFile,
        val parsedData: ParsedData
    ) : ImportResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ImportResult()
}

/**
 * Stages of file import.
 */
enum class ImportStage {
    READING,
    PARSING,
    VALIDATING,
    COMPLETE
}
