package ru.macdroid.ollama.data.local.analytics

import ru.macdroid.ollama.domain.model.analytics.AnalyticsContext

/**
 * Manages token budget allocation for analytics context.
 *
 * Total budget: ~3000 tokens
 * - System prompt: 200 tokens
 * - Schema: 100 tokens
 * - Statistics: 300 tokens
 * - Data sample: 2000 tokens
 * - Question: 100 tokens
 * - Buffer: 300 tokens
 */
class TokenBudgetManager {

    companion object {
        // Average characters per token (conservative estimate for mixed content)
        private const val CHARS_PER_TOKEN = 4.0

        // Budget allocations
        const val SYSTEM_PROMPT_TOKENS = AnalyticsContext.SYSTEM_PROMPT_BUDGET
        const val SCHEMA_TOKENS = AnalyticsContext.SCHEMA_BUDGET
        const val STATISTICS_TOKENS = AnalyticsContext.STATISTICS_BUDGET
        const val DATA_SAMPLE_TOKENS = AnalyticsContext.DATA_SAMPLE_BUDGET
        const val QUESTION_TOKENS = AnalyticsContext.QUESTION_BUDGET
        const val BUFFER_TOKENS = AnalyticsContext.BUFFER
        const val TOTAL_TOKENS = AnalyticsContext.TOTAL_TOKEN_BUDGET
    }

    /**
     * Estimate token count for a string.
     */
    fun estimateTokens(text: String): Int {
        return (text.length / CHARS_PER_TOKEN).toInt()
    }

    /**
     * Get maximum character count for schema section.
     */
    fun getSchemaCharLimit(): Int {
        return (SCHEMA_TOKENS * CHARS_PER_TOKEN).toInt()
    }

    /**
     * Get maximum character count for statistics section.
     */
    fun getStatisticsCharLimit(): Int {
        return (STATISTICS_TOKENS * CHARS_PER_TOKEN).toInt()
    }

    /**
     * Get maximum character count for data sample section.
     */
    fun getDataSampleCharLimit(): Int {
        return (DATA_SAMPLE_TOKENS * CHARS_PER_TOKEN).toInt()
    }

    /**
     * Truncate text to fit within token budget.
     */
    fun truncateToTokens(text: String, maxTokens: Int): String {
        val maxChars = (maxTokens * CHARS_PER_TOKEN).toInt()
        return if (text.length <= maxChars) {
            text
        } else {
            text.take(maxChars - 3) + "..."
        }
    }

    /**
     * Calculate remaining tokens after allocations.
     */
    fun calculateRemainingTokens(
        schemaTokens: Int,
        statisticsTokens: Int,
        dataSampleTokens: Int,
        questionTokens: Int
    ): Int {
        val used = SYSTEM_PROMPT_TOKENS + schemaTokens + statisticsTokens + dataSampleTokens + questionTokens
        return TOTAL_TOKENS - used - BUFFER_TOKENS
    }

    /**
     * Check if total context fits within budget.
     */
    fun fitsWithinBudget(totalTokens: Int): Boolean {
        return totalTokens <= TOTAL_TOKENS
    }

    /**
     * Calculate how many data rows can fit in remaining budget.
     * Assumes average row size.
     */
    fun calculateMaxRows(avgRowCharacters: Int, availableTokens: Int): Int {
        if (avgRowCharacters <= 0) return 0
        val availableChars = (availableTokens * CHARS_PER_TOKEN).toInt()
        return availableChars / avgRowCharacters
    }
}
