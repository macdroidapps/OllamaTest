package ru.macdroid.ollama.data.local.analytics

import ru.macdroid.ollama.data.local.llm.PromptTemplate
import ru.macdroid.ollama.domain.model.analytics.AnalyticsContext
import ru.macdroid.ollama.domain.model.analytics.DataRow
import ru.macdroid.ollama.domain.model.analytics.DataSchema
import ru.macdroid.ollama.domain.model.analytics.DataStatistics
import ru.macdroid.ollama.domain.model.analytics.ParsedData
import ru.macdroid.ollama.domain.model.analytics.SamplingStrategy

/**
 * Builds LLM context from parsed data within token budget.
 */
class ContextBuilder(
    private val tokenBudgetManager: TokenBudgetManager = TokenBudgetManager(),
    private val dataSampler: DataSampler = DataSampler()
) {

    /**
     * Build analytics context from parsed data and statistics.
     *
     * @param data Parsed data
     * @param statistics Computed statistics (optional)
     * @return AnalyticsContext ready for LLM
     */
    fun buildContext(
        data: ParsedData,
        statistics: DataStatistics?
    ): AnalyticsContext {
        val strategy = SamplingStrategy.forRowCount(data.totalRowCount)

        // Build schema description
        val schemaDescription = buildSchemaDescription(data.schema)
        val truncatedSchema = tokenBudgetManager.truncateToTokens(
            schemaDescription,
            TokenBudgetManager.SCHEMA_TOKENS
        )

        // Build statistics summary
        val statisticsSummary = buildStatisticsSummary(statistics, data.totalRowCount, strategy)
        val truncatedStats = tokenBudgetManager.truncateToTokens(
            statisticsSummary,
            TokenBudgetManager.STATISTICS_TOKENS
        )

        // Build data sample
        val sampledRows = dataSampler.sample(data)
        val dataSample = buildDataSample(data.schema, sampledRows, strategy)
        val truncatedSample = tokenBudgetManager.truncateToTokens(
            dataSample,
            TokenBudgetManager.DATA_SAMPLE_TOKENS
        )

        // Estimate total tokens
        val totalTokens = tokenBudgetManager.estimateTokens(truncatedSchema) +
                tokenBudgetManager.estimateTokens(truncatedStats) +
                tokenBudgetManager.estimateTokens(truncatedSample) +
                TokenBudgetManager.SYSTEM_PROMPT_TOKENS +
                TokenBudgetManager.QUESTION_TOKENS

        return AnalyticsContext(
            systemPrompt = PromptTemplate.ANALYTICS.systemPrompt,
            schemaDescription = truncatedSchema,
            statisticsSummary = truncatedStats,
            dataSample = truncatedSample,
            estimatedTokens = totalTokens
        )
    }

    private fun buildSchemaDescription(schema: DataSchema): String {
        return buildString {
            appendLine("Columns (${schema.columnCount}):")
            schema.columns.forEach { col ->
                val nullableStr = if (col.nullable) ", nullable" else ""
                appendLine("- ${col.name}: ${col.type.displayName}$nullableStr")
            }
        }
    }

    private fun buildStatisticsSummary(
        statistics: DataStatistics?,
        totalRows: Int,
        strategy: SamplingStrategy
    ): String {
        return buildString {
            appendLine("Total rows: $totalRows")
            appendLine("Sampling: ${strategy.name}")

            if (statistics != null) {
                appendLine()
                append(statistics.toSummaryString())
            }
        }
    }

    private fun buildDataSample(
        schema: DataSchema,
        rows: List<DataRow>,
        strategy: SamplingStrategy
    ): String {
        if (rows.isEmpty()) {
            return "No data available"
        }

        return buildString {
            when (strategy) {
                SamplingStrategy.AGGREGATED -> {
                    appendLine("(Data too large for full sample, showing ${rows.size} representative rows)")
                    appendLine()
                }
                SamplingStrategy.STATISTICAL -> {
                    appendLine("(Sampled ${rows.size} rows from dataset)")
                    appendLine()
                }
                SamplingStrategy.FULL_DATA -> {
                    // No header needed for full data
                }
            }

            // Build table format
            val columnNames = schema.columnNames()

            // Header row
            appendLine(columnNames.joinToString(" | "))
            appendLine(columnNames.map { "-".repeat(minOf(it.length, 15)) }.joinToString(" | "))

            // Data rows
            rows.forEach { row ->
                val values = columnNames.map { col ->
                    val value = row[col] ?: ""
                    // Truncate long values
                    if (value.length > 50) value.take(47) + "..." else value
                }
                appendLine(values.joinToString(" | "))
            }
        }
    }

    /**
     * Build a full prompt combining context and user question.
     * Note: System prompt is set separately via LlmEngine.updateSystemPrompt(),
     * so we only include data context and the question here.
     */
    fun buildFullPrompt(context: AnalyticsContext, question: String): String {
        return buildString {
            appendLine("=== DATA CONTEXT ===")
            appendLine()
            append(context.toPromptContext())
            appendLine()
            appendLine("=== USER QUESTION ===")
            appendLine()
            appendLine(question)
        }
    }
}
