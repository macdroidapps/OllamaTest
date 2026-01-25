package ru.macdroid.ollama.data.local.analytics.parser

import kotlinx.coroutines.flow.Flow
import ru.macdroid.ollama.domain.model.analytics.ParsedData

/**
 * Interface for parsing different file formats into ParsedData.
 */
interface FileParser {
    /**
     * Parse file content into structured data.
     *
     * @param content Raw file content as string
     * @param maxRows Maximum number of rows to parse for preview (default 1000)
     * @return Flow emitting parsing progress and final ParsedData
     */
    fun parse(content: String, maxRows: Int = 1000): Flow<ParseResult>

    /**
     * Count total rows in the file without fully parsing.
     *
     * @param content Raw file content
     * @return Total row count
     */
    fun countRows(content: String): Int
}

/**
 * Result wrapper for parsing progress and completion.
 */
sealed class ParseResult {
    /**
     * Parsing in progress with partial data.
     */
    data class Progress(
        val parsedRows: Int,
        val totalRows: Int?,
        val message: String? = null
    ) : ParseResult()

    /**
     * Parsing completed successfully.
     */
    data class Success(
        val data: ParsedData
    ) : ParseResult()

    /**
     * Parsing failed with error.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ParseResult()
}

/**
 * Exception thrown during file parsing.
 */
class ParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
