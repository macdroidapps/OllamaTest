package ru.macdroid.ollama.data.local.analytics.parser

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.macdroid.ollama.domain.model.analytics.ColumnInfo
import ru.macdroid.ollama.domain.model.analytics.ColumnType
import ru.macdroid.ollama.domain.model.analytics.DataRow
import ru.macdroid.ollama.domain.model.analytics.DataSchema
import ru.macdroid.ollama.domain.model.analytics.ParsedData

/**
 * Parser for log files.
 * Supports common log formats:
 * - Standard: "2024-01-15 10:30:00 INFO [Main] Message text"
 * - Apache/Nginx: "127.0.0.1 - - [15/Jan/2024:10:30:00 +0000] \"GET /path HTTP/1.1\" 200 1234"
 * - Simple: "INFO: Message text" or "[INFO] Message text"
 * - Key-value: "timestamp=2024-01-15 level=INFO message=Text"
 */
class LogParser : FileParser {

    companion object {
        // Common log patterns
        private val STANDARD_LOG_PATTERN = Regex(
            """^(\d{4}-\d{2}-\d{2}[T ]?\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:[+-]\d{2}:?\d{2})?)\s+(\w+)\s+\[?([^\]]+)]?\s+(.*)$"""
        )

        private val APACHE_LOG_PATTERN = Regex(
            """^(\S+)\s+(\S+)\s+(\S+)\s+\[([^\]]+)]\s+"([^"]+)"\s+(\d+)\s+(\d+)(?:\s+"([^"]*)")?(?:\s+"([^"]*)")?"""
        )

        private val SIMPLE_LOG_PATTERN = Regex(
            """^(?:\[(\w+)]|(\w+):)\s+(.*)$"""
        )

        private val KEY_VALUE_PATTERN = Regex(
            """(\w+)=(?:"([^"]*)"|([^\s]+))"""
        )

        private val LOG_LEVELS = setOf("TRACE", "DEBUG", "INFO", "WARN", "WARNING", "ERROR", "FATAL", "CRITICAL")
    }

    override fun parse(content: String, maxRows: Int): Flow<ParseResult> = flow {
        try {
            if (content.isBlank()) {
                emit(ParseResult.Error("File is empty"))
                return@flow
            }

            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                emit(ParseResult.Error("No log lines found"))
                return@flow
            }

            emit(ParseResult.Progress(0, lines.size, "Detecting log format..."))

            // Detect format from sample lines
            val sampleLines = lines.take(10)
            val format = detectFormat(sampleLines)

            emit(ParseResult.Progress(0, lines.size, "Parsing with ${format.name} format..."))

            val totalRows = lines.size
            val rowsToParse = minOf(maxRows, totalRows)
            val rows = mutableListOf<DataRow>()
            val allKeys = mutableSetOf<String>()

            lines.take(rowsToParse).forEachIndexed { index, line ->
                val parsed = parseLine(line, format)
                if (parsed.isNotEmpty()) {
                    rows.add(DataRow(values = parsed))
                    allKeys.addAll(parsed.keys)
                }

                if ((index + 1) % 100 == 0) {
                    emit(ParseResult.Progress(index + 1, totalRows, null))
                }
            }

            if (rows.isEmpty()) {
                emit(ParseResult.Error("Could not parse any log lines"))
                return@flow
            }

            // Build schema from collected keys
            val columns = allKeys.sorted().map { key ->
                val sampleValues = rows.take(10).mapNotNull { it[key] }
                val type = inferColumnType(key, sampleValues)
                ColumnInfo(name = key, type = type, nullable = true)
            }

            val parsedData = ParsedData(
                schema = DataSchema(columns),
                rows = rows,
                totalRowCount = totalRows,
                isSampled = rowsToParse < totalRows
            )

            emit(ParseResult.Success(parsedData))

        } catch (e: Exception) {
            emit(ParseResult.Error("Failed to parse log file: ${e.message}", e))
        }
    }

    override fun countRows(content: String): Int {
        return content.lines().count { it.isNotBlank() }
    }

    private enum class LogFormat {
        STANDARD,
        APACHE,
        SIMPLE,
        KEY_VALUE,
        RAW
    }

    private fun detectFormat(sampleLines: List<String>): LogFormat {
        val formatMatches = mapOf(
            LogFormat.STANDARD to sampleLines.count { STANDARD_LOG_PATTERN.matches(it) },
            LogFormat.APACHE to sampleLines.count { APACHE_LOG_PATTERN.matches(it) },
            LogFormat.SIMPLE to sampleLines.count { SIMPLE_LOG_PATTERN.matches(it) },
            LogFormat.KEY_VALUE to sampleLines.count { KEY_VALUE_PATTERN.findAll(it).count() >= 2 }
        )

        return formatMatches.maxByOrNull { it.value }?.takeIf { it.value > sampleLines.size / 3 }?.key
            ?: LogFormat.RAW
    }

    private fun parseLine(line: String, format: LogFormat): Map<String, String?> {
        return when (format) {
            LogFormat.STANDARD -> parseStandardLog(line)
            LogFormat.APACHE -> parseApacheLog(line)
            LogFormat.SIMPLE -> parseSimpleLog(line)
            LogFormat.KEY_VALUE -> parseKeyValueLog(line)
            LogFormat.RAW -> mapOf("line" to line)
        }
    }

    private fun parseStandardLog(line: String): Map<String, String?> {
        val match = STANDARD_LOG_PATTERN.matchEntire(line) ?: return mapOf("line" to line)
        return mapOf(
            "timestamp" to match.groupValues[1],
            "level" to match.groupValues[2].uppercase(),
            "source" to match.groupValues[3],
            "message" to match.groupValues[4]
        )
    }

    private fun parseApacheLog(line: String): Map<String, String?> {
        val match = APACHE_LOG_PATTERN.matchEntire(line) ?: return mapOf("line" to line)
        return mapOf(
            "ip" to match.groupValues[1],
            "identity" to match.groupValues[2].takeIf { it != "-" },
            "user" to match.groupValues[3].takeIf { it != "-" },
            "timestamp" to match.groupValues[4],
            "request" to match.groupValues[5],
            "status" to match.groupValues[6],
            "size" to match.groupValues[7],
            "referer" to match.groupValues.getOrNull(8)?.takeIf { it.isNotBlank() && it != "-" },
            "user_agent" to match.groupValues.getOrNull(9)?.takeIf { it.isNotBlank() && it != "-" }
        ).filterValues { it != null }
    }

    private fun parseSimpleLog(line: String): Map<String, String?> {
        val match = SIMPLE_LOG_PATTERN.matchEntire(line) ?: return mapOf("line" to line)
        val level = (match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]).uppercase()
        return mapOf(
            "level" to level,
            "message" to match.groupValues[3]
        )
    }

    private fun parseKeyValueLog(line: String): Map<String, String?> {
        val matches = KEY_VALUE_PATTERN.findAll(line)
        val result = mutableMapOf<String, String?>()

        matches.forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].takeIf { it.isNotEmpty() } ?: match.groupValues[3]
            result[key] = value
        }

        if (result.isEmpty()) {
            return mapOf("line" to line)
        }

        return result
    }

    private fun inferColumnType(key: String, sampleValues: List<String>): ColumnType {
        // Use key name hints
        val lowerKey = key.lowercase()
        return when {
            lowerKey.contains("time") || lowerKey.contains("date") -> ColumnType.TIMESTAMP
            lowerKey == "level" -> ColumnType.STRING
            lowerKey == "status" || lowerKey == "code" -> ColumnType.INTEGER
            lowerKey == "size" || lowerKey == "bytes" || lowerKey == "count" -> ColumnType.INTEGER
            else -> {
                // Infer from values
                val types = sampleValues.map { ColumnType.infer(it) }
                types.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ColumnType.STRING
            }
        }
    }
}
