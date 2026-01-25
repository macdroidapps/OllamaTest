package ru.macdroid.ollama.domain.model.analytics

/**
 * Statistical summary of parsed data.
 */
data class DataStatistics(
    val totalRows: Int,
    val columnStats: List<ColumnStatistics>
) {
    fun toSummaryString(): String {
        val sb = StringBuilder()
        sb.appendLine("Total rows: $totalRows")
        sb.appendLine("Columns (${columnStats.size}):")

        columnStats.forEach { col ->
            sb.appendLine("  ${col.name} (${col.type.displayName}):")
            sb.appendLine("    - Non-null: ${col.nonNullCount}/${totalRows}")

            when (col) {
                is NumericColumnStats -> {
                    sb.appendLine("    - Min: ${col.min}")
                    sb.appendLine("    - Max: ${col.max}")
                    sb.appendLine("    - Avg: ${String.format("%.2f", col.avg)}")
                }
                is CategoricalColumnStats -> {
                    sb.appendLine("    - Unique values: ${col.uniqueCount}")
                    if (col.topValues.isNotEmpty()) {
                        sb.appendLine("    - Top values: ${col.topValues.take(5).joinToString { "${it.first}(${it.second})" }}")
                    }
                }
                is TextColumnStats -> {
                    sb.appendLine("    - Avg length: ${String.format("%.1f", col.avgLength)}")
                }
            }
        }

        return sb.toString()
    }
}

/**
 * Base class for column statistics.
 */
sealed class ColumnStatistics {
    abstract val name: String
    abstract val type: ColumnType
    abstract val nonNullCount: Int
    abstract val nullCount: Int
}

/**
 * Statistics for numeric columns (INTEGER, DECIMAL).
 */
data class NumericColumnStats(
    override val name: String,
    override val type: ColumnType,
    override val nonNullCount: Int,
    override val nullCount: Int,
    val min: Double,
    val max: Double,
    val avg: Double,
    val sum: Double
) : ColumnStatistics()

/**
 * Statistics for categorical columns (STRING with limited unique values, BOOLEAN).
 */
data class CategoricalColumnStats(
    override val name: String,
    override val type: ColumnType,
    override val nonNullCount: Int,
    override val nullCount: Int,
    val uniqueCount: Int,
    val topValues: List<Pair<String, Int>>  // (value, count) sorted by count descending
) : ColumnStatistics()

/**
 * Statistics for text columns (STRING with many unique values).
 */
data class TextColumnStats(
    override val name: String,
    override val type: ColumnType,
    override val nonNullCount: Int,
    override val nullCount: Int,
    val avgLength: Double,
    val minLength: Int,
    val maxLength: Int
) : ColumnStatistics()
