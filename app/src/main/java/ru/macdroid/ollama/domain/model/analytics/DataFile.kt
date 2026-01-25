package ru.macdroid.ollama.domain.model.analytics

import android.net.Uri
import java.util.UUID

/**
 * Represents a loaded file for analytics.
 */
data class DataFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: Uri,
    val type: FileType,
    val sizeBytes: Long,
    val loadedAt: Long = System.currentTimeMillis()
)

/**
 * Supported file types for analytics parsing.
 */
enum class FileType(val extension: String, val mimeTypes: List<String>) {
    CSV("csv", listOf("text/csv", "text/comma-separated-values", "application/csv")),
    JSON("json", listOf("application/json", "text/json")),
    LOG("log", listOf("text/plain", "text/x-log", "application/octet-stream"));

    companion object {
        fun fromExtension(extension: String): FileType? {
            return entries.find { it.extension.equals(extension, ignoreCase = true) }
        }

        fun fromFileName(fileName: String): FileType? {
            val ext = fileName.substringAfterLast('.', "")
            return fromExtension(ext)
        }

        fun fromMimeType(mimeType: String): FileType? {
            return entries.find { it.mimeTypes.any { m -> m.equals(mimeType, ignoreCase = true) } }
        }
    }
}
