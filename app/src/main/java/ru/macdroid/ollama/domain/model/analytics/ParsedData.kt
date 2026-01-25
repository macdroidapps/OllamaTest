package ru.macdroid.ollama.domain.model.analytics

/**
 * Represents parsed data from a file.
 */
data class ParsedData(
    val schema: DataSchema,
    val rows: List<DataRow>,
    val totalRowCount: Int,
    val isSampled: Boolean
) {
    /**
     * Returns the actual number of rows in this parsed data (may be less than totalRowCount if sampled).
     */
    val loadedRowCount: Int get() = rows.size
}

/**
 * Schema describing the structure of parsed data.
 */
data class DataSchema(
    val columns: List<ColumnInfo>
) {
    val columnCount: Int get() = columns.size

    fun columnNames(): List<String> = columns.map { it.name }

    fun toSchemaString(): String {
        return columns.joinToString(", ") { "${it.name}: ${it.type.displayName}" }
    }
}

/**
 * Information about a single column in the data schema.
 */
data class ColumnInfo(
    val name: String,
    val type: ColumnType,
    val nullable: Boolean = true
)

/**
 * Data types for columns.
 */
enum class ColumnType(val displayName: String) {
    STRING("String"),
    INTEGER("Integer"),
    DECIMAL("Decimal"),
    BOOLEAN("Boolean"),
    TIMESTAMP("Timestamp"),
    UNKNOWN("Unknown");

    companion object {
        /**
         * Infers column type from a sample value.
         */
        fun infer(value: String?): ColumnType {
            if (value.isNullOrBlank()) return UNKNOWN

            val trimmed = value.trim()

            // Try boolean
            if (trimmed.equals("true", ignoreCase = true) ||
                trimmed.equals("false", ignoreCase = true)) {
                return BOOLEAN
            }

            // Try integer
            if (trimmed.toLongOrNull() != null) {
                return INTEGER
            }

            // Try decimal
            if (trimmed.toDoubleOrNull() != null) {
                return DECIMAL
            }

            // Try timestamp patterns
            if (isTimestamp(trimmed)) {
                return TIMESTAMP
            }

            return STRING
        }

        private fun isTimestamp(value: String): Boolean {
            // Common timestamp patterns
            val patterns = listOf(
                Regex("""\d{4}-\d{2}-\d{2}"""),  // 2024-01-15
                Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}"""),  // ISO 8601
                Regex("""\d{2}/\d{2}/\d{4}"""),  // 01/15/2024
                Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")  // 2024-01-15 10:30:00
            )
            return patterns.any { it.matches(value) }
        }
    }
}

/**
 * A single row of data, represented as a map of column name to value.
 */
data class DataRow(
    val values: Map<String, String?>
) {
    operator fun get(columnName: String): String? = values[columnName]

    fun toValueList(columns: List<String>): List<String?> {
        return columns.map { values[it] }
    }
}
