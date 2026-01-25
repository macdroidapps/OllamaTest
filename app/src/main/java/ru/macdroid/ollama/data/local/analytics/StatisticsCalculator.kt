package ru.macdroid.ollama.data.local.analytics

import ru.macdroid.ollama.domain.model.analytics.CategoricalColumnStats
import ru.macdroid.ollama.domain.model.analytics.ColumnStatistics
import ru.macdroid.ollama.domain.model.analytics.ColumnType
import ru.macdroid.ollama.domain.model.analytics.DataStatistics
import ru.macdroid.ollama.domain.model.analytics.NumericColumnStats
import ru.macdroid.ollama.domain.model.analytics.ParsedData
import ru.macdroid.ollama.domain.model.analytics.TextColumnStats

/**
 * Calculates statistics for parsed data.
 */
class StatisticsCalculator {

    companion object {
        // If unique values < this threshold, treat as categorical
        private const val CATEGORICAL_THRESHOLD = 20
    }

    /**
     * Calculate statistics for all columns in parsed data.
     */
    fun calculate(data: ParsedData): DataStatistics {
        val columnStats = data.schema.columns.map { column ->
            val values = data.rows.map { it[column.name] }
            calculateColumnStats(column.name, column.type, values)
        }

        return DataStatistics(
            totalRows = data.totalRowCount,
            columnStats = columnStats
        )
    }

    private fun calculateColumnStats(
        name: String,
        type: ColumnType,
        values: List<String?>
    ): ColumnStatistics {
        val nonNullValues = values.filterNotNull().filter { it.isNotBlank() }
        val nullCount = values.size - nonNullValues.size

        return when (type) {
            ColumnType.INTEGER, ColumnType.DECIMAL -> {
                calculateNumericStats(name, type, nonNullValues, nullCount)
            }
            ColumnType.BOOLEAN -> {
                calculateCategoricalStats(name, type, nonNullValues, nullCount)
            }
            ColumnType.STRING, ColumnType.TIMESTAMP, ColumnType.UNKNOWN -> {
                // Decide if categorical or text based on unique count
                val uniqueCount = nonNullValues.toSet().size
                if (uniqueCount <= CATEGORICAL_THRESHOLD || uniqueCount <= nonNullValues.size / 2) {
                    calculateCategoricalStats(name, type, nonNullValues, nullCount)
                } else {
                    calculateTextStats(name, type, nonNullValues, nullCount)
                }
            }
        }
    }

    private fun calculateNumericStats(
        name: String,
        type: ColumnType,
        values: List<String>,
        nullCount: Int
    ): ColumnStatistics {
        val numbers = values.mapNotNull { it.toDoubleOrNull() }

        if (numbers.isEmpty()) {
            return TextColumnStats(
                name = name,
                type = type,
                nonNullCount = values.size,
                nullCount = nullCount,
                avgLength = values.map { it.length }.average(),
                minLength = values.minOfOrNull { it.length } ?: 0,
                maxLength = values.maxOfOrNull { it.length } ?: 0
            )
        }

        return NumericColumnStats(
            name = name,
            type = type,
            nonNullCount = numbers.size,
            nullCount = nullCount,
            min = numbers.minOrNull() ?: 0.0,
            max = numbers.maxOrNull() ?: 0.0,
            avg = numbers.average(),
            sum = numbers.sum()
        )
    }

    private fun calculateCategoricalStats(
        name: String,
        type: ColumnType,
        values: List<String>,
        nullCount: Int
    ): ColumnStatistics {
        val valueCounts = values.groupingBy { it }.eachCount()
        val topValues = valueCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }

        return CategoricalColumnStats(
            name = name,
            type = type,
            nonNullCount = values.size,
            nullCount = nullCount,
            uniqueCount = valueCounts.size,
            topValues = topValues
        )
    }

    private fun calculateTextStats(
        name: String,
        type: ColumnType,
        values: List<String>,
        nullCount: Int
    ): ColumnStatistics {
        val lengths = values.map { it.length }

        return TextColumnStats(
            name = name,
            type = type,
            nonNullCount = values.size,
            nullCount = nullCount,
            avgLength = if (lengths.isNotEmpty()) lengths.average() else 0.0,
            minLength = lengths.minOrNull() ?: 0,
            maxLength = lengths.maxOrNull() ?: 0
        )
    }
}
