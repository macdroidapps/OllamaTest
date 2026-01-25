package ru.macdroid.ollama.data.local.analytics.parser

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import ru.macdroid.ollama.domain.model.analytics.ColumnInfo
import ru.macdroid.ollama.domain.model.analytics.ColumnType
import ru.macdroid.ollama.domain.model.analytics.DataRow
import ru.macdroid.ollama.domain.model.analytics.DataSchema
import ru.macdroid.ollama.domain.model.analytics.ParsedData

/**
 * Parser for JSON files.
 * Supports:
 * - JSON array of objects (primary format)
 * - JSON Lines (JSONL) format
 * - Nested object flattening
 * - Type inference from JSON types
 */
class JsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : FileParser {

    companion object {
        private const val SAMPLE_SIZE_FOR_SCHEMA = 10
        private const val NESTED_SEPARATOR = "."
    }

    override fun parse(content: String, maxRows: Int): Flow<ParseResult> = flow {
        try {
            if (content.isBlank()) {
                emit(ParseResult.Error("File is empty"))
                return@flow
            }

            emit(ParseResult.Progress(0, null, "Detecting JSON format..."))

            // Try to parse as JSON array first, then as JSON Lines
            val jsonObjects = parseJsonContent(content)

            if (jsonObjects.isEmpty()) {
                emit(ParseResult.Error("No JSON objects found"))
                return@flow
            }

            val totalRows = jsonObjects.size
            val rowsToParse = minOf(maxRows, totalRows)

            emit(ParseResult.Progress(0, totalRows, "Building schema..."))

            // Build schema from sample objects
            val sampleObjects = jsonObjects.take(SAMPLE_SIZE_FOR_SCHEMA)
            val columns = buildSchemaFromSample(sampleObjects)
            val schema = DataSchema(columns)

            emit(ParseResult.Progress(0, totalRows, "Parsing rows..."))

            // Parse rows
            val rows = mutableListOf<DataRow>()
            jsonObjects.take(rowsToParse).forEachIndexed { index, obj ->
                val flatValues = flattenObject(obj)
                val row = DataRow(values = flatValues)
                rows.add(row)

                if ((index + 1) % 100 == 0) {
                    emit(ParseResult.Progress(index + 1, totalRows, null))
                }
            }

            val parsedData = ParsedData(
                schema = schema,
                rows = rows,
                totalRowCount = totalRows,
                isSampled = rowsToParse < totalRows
            )

            emit(ParseResult.Success(parsedData))

        } catch (e: Exception) {
            emit(ParseResult.Error("Failed to parse JSON: ${e.message}", e))
        }
    }

    override fun countRows(content: String): Int {
        return try {
            parseJsonContent(content).size
        } catch (e: Exception) {
            0
        }
    }

    private fun parseJsonContent(content: String): List<JsonObject> {
        val trimmed = content.trim()

        // Try JSON array format
        if (trimmed.startsWith('[')) {
            return try {
                val array = json.parseToJsonElement(trimmed) as? JsonArray
                array?.filterIsInstance<JsonObject>() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        // Try JSON Lines format (newline-delimited JSON)
        return trimmed.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    json.parseToJsonElement(line) as? JsonObject
                } catch (e: Exception) {
                    null
                }
            }
    }

    private fun buildSchemaFromSample(sampleObjects: List<JsonObject>): List<ColumnInfo> {
        // Collect all unique keys across sample objects
        val allKeys = mutableSetOf<String>()
        val keyTypes = mutableMapOf<String, MutableList<ColumnType>>()
        val keyNullability = mutableMapOf<String, Boolean>()

        sampleObjects.forEach { obj ->
            val flatValues = flattenObject(obj)
            flatValues.forEach { (key, value) ->
                allKeys.add(key)
                val type = inferTypeFromValue(value)
                keyTypes.getOrPut(key) { mutableListOf() }.add(type)
                if (value == null) {
                    keyNullability[key] = true
                }
            }
        }

        return allKeys.sorted().map { key ->
            val types = keyTypes[key] ?: listOf(ColumnType.UNKNOWN)
            val mostCommonType = types
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: ColumnType.STRING

            ColumnInfo(
                name = key,
                type = mostCommonType,
                nullable = keyNullability[key] == true
            )
        }
    }

    private fun flattenObject(obj: JsonObject, prefix: String = ""): Map<String, String?> {
        val result = mutableMapOf<String, String?>()

        obj.forEach { (key, value) ->
            val fullKey = if (prefix.isEmpty()) key else "$prefix$NESTED_SEPARATOR$key"

            when (value) {
                is JsonObject -> {
                    result.putAll(flattenObject(value, fullKey))
                }
                is JsonArray -> {
                    // For arrays, store as string representation
                    result[fullKey] = value.toString()
                }
                is JsonPrimitive -> {
                    result[fullKey] = if (value.isString) value.content else value.toString()
                }
                is JsonNull -> {
                    result[fullKey] = null
                }
            }
        }

        return result
    }

    private fun inferTypeFromValue(value: String?): ColumnType {
        if (value == null) return ColumnType.UNKNOWN

        // Try to parse as JSON primitive to get original type
        return try {
            val element = json.parseToJsonElement(value)
            if (element is JsonPrimitive) {
                when {
                    element.booleanOrNull != null -> ColumnType.BOOLEAN
                    element.longOrNull != null -> ColumnType.INTEGER
                    element.doubleOrNull != null -> ColumnType.DECIMAL
                    else -> ColumnType.infer(value)
                }
            } else {
                ColumnType.STRING
            }
        } catch (e: Exception) {
            ColumnType.infer(value)
        }
    }
}
