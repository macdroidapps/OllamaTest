package ru.macdroid.ollama.domain.model.analytics

/**
 * Context prepared for LLM analysis.
 * Token budget: ~3000 tokens total.
 */
data class AnalyticsContext(
    val systemPrompt: String,
    val schemaDescription: String,
    val statisticsSummary: String,
    val dataSample: String,
    val estimatedTokens: Int
) {
    /**
     * Builds the full context string for LLM prompt.
     */
    fun toPromptContext(): String {
        return buildString {
            appendLine("## Data Schema")
            appendLine(schemaDescription)
            appendLine()
            appendLine("## Statistics")
            appendLine(statisticsSummary)
            appendLine()
            appendLine("## Data Sample")
            appendLine(dataSample)
        }
    }

    companion object {
        /**
         * Token budget allocation:
         * - System prompt: 200 tokens
         * - Schema: 100 tokens
         * - Statistics: 300 tokens
         * - Data sample: 2000 tokens
         * - Question: 100 tokens
         * - Buffer: 300 tokens
         * Total: ~3000 tokens
         */
        const val TOTAL_TOKEN_BUDGET = 3000
        const val SYSTEM_PROMPT_BUDGET = 200
        const val SCHEMA_BUDGET = 100
        const val STATISTICS_BUDGET = 300
        const val DATA_SAMPLE_BUDGET = 2000
        const val QUESTION_BUDGET = 100
        const val BUFFER = 300
    }
}

/**
 * Sampling strategy based on data size.
 */
enum class SamplingStrategy {
    /**
     * Include all data (< 500 rows).
     */
    FULL_DATA,

    /**
     * Statistical sampling with ~100 representative rows (< 5000 rows).
     */
    STATISTICAL,

    /**
     * Only statistics, no raw data (> 5000 rows).
     */
    AGGREGATED;

    companion object {
        fun forRowCount(count: Int): SamplingStrategy = when {
            count < 500 -> FULL_DATA
            count < 5000 -> STATISTICAL
            else -> AGGREGATED
        }
    }
}
