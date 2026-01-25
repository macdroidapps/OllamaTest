package ru.macdroid.ollama.data.local.analytics.parser

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.macdroid.ollama.domain.model.analytics.ColumnInfo
import ru.macdroid.ollama.domain.model.analytics.ColumnType
import ru.macdroid.ollama.domain.model.analytics.DataRow
import ru.macdroid.ollama.domain.model.analytics.DataSchema
import ru.macdroid.ollama.domain.model.analytics.ParsedData

/**
 * Parser for CSV files.
 * Supports:
 * - Auto-detection of delimiter (comma, semicolon, tab)
 * - Quoted fields with escaped quotes
 * - Header row detection
 * - Type inference from sample values
 */
class CsvParser : FileParser {

    companion object {
        private val COMMON_DELIMITERS = listOf(',', ';', '\t', '|')
        private const val SAMPLE_SIZE_FOR_TYPE_INFERENCE = 10
    }

    override fun parse(content: String, maxRows: Int): Flow<ParseResult> = flow {
        try {
            if (content.isBlank()) {
                emit(ParseResult.Error("File is empty"))
                return@flow
            }

            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                emit(ParseResult.Error("No data lines found"))
                return@flow
            }

            emit(ParseResult.Progress(0, lines.size, "Detecting delimiter..."))

            // Detect delimiter
            val delimiter = detectDelimiter(lines.first())

            // Parse header
            val headerLine = lines.first()
            val headers = parseLine(headerLine, delimiter)

            if (headers.isEmpty()) {
                emit(ParseResult.Error("Could not parse header row"))
                return@flow
            }

            emit(ParseResult.Progress(0, lines.size - 1, "Parsing rows..."))

            // Parse data rows
            val dataLines = lines.drop(1)
            val totalRows = dataLines.size
            val rowsToParse = minOf(maxRows, totalRows)
            val rows = mutableListOf<DataRow>()

            dataLines.take(rowsToParse).forEachIndexed { index, line ->
                val values = parseLine(line, delimiter)
                val row = DataRow(
                    values = headers.mapIndexed { i, header ->
                        header to values.getOrNull(i)
                    }.toMap()
                )
                rows.add(row)

                if ((index + 1) % 100 == 0) {
                    emit(ParseResult.Progress(index + 1, totalRows, null))
                }
            }

            // Infer column types from sample data
            val columns = inferColumnTypes(headers, rows.take(SAMPLE_SIZE_FOR_TYPE_INFERENCE))
            val schema = DataSchema(columns)

            val parsedData = ParsedData(
                schema = schema,
                rows = rows,
                totalRowCount = totalRows,
                isSampled = rowsToParse < totalRows
            )

            emit(ParseResult.Success(parsedData))

        } catch (e: Exception) {
            emit(ParseResult.Error("Failed to parse CSV: ${e.message}", e))
        }
    }

    override fun countRows(content: String): Int {
        return content.lines().filter { it.isNotBlank() }.size - 1  // Subtract header
    }

    private fun detectDelimiter(headerLine: String): Char {
        // Count occurrences of each potential delimiter
        val counts = COMMON_DELIMITERS.associateWith { delimiter ->
            headerLine.count { it == delimiter }
        }

        // Return delimiter with most occurrences (minimum 1)
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]

            when {
                char == '"' && !inQuotes -> {
                    inQuotes = true
                }
                char == '"' && inQuotes -> {
                    // Check for escaped quote
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                char == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> {
                    current.append(char)
                }
            }
            i++
        }

        result.add(current.toString().trim())
        return result
    }

    private fun inferColumnTypes(headers: List<String>, sampleRows: List<DataRow>): List<ColumnInfo> {
        return headers.map { header ->
            val sampleValues = sampleRows.mapNotNull { it[header] }.filter { it.isNotBlank() }
            val inferredTypes = sampleValues.map { ColumnType.infer(it) }

            // Use most common type, defaulting to STRING
            val type = inferredTypes
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: ColumnType.STRING

            val hasNulls = sampleRows.any { it[header].isNullOrBlank() }

            ColumnInfo(
                name = header,
                type = type,
                nullable = hasNulls
            )
        }
    }
}
