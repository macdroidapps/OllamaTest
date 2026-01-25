package ru.macdroid.ollama.data.local.analytics

import ru.macdroid.ollama.domain.model.analytics.DataRow
import ru.macdroid.ollama.domain.model.analytics.ParsedData
import ru.macdroid.ollama.domain.model.analytics.SamplingStrategy
import kotlin.random.Random

/**
 * Samples data rows based on strategy to fit within token budget.
 */
class DataSampler {

    companion object {
        const val FULL_DATA_THRESHOLD = 500
        const val STATISTICAL_THRESHOLD = 5000
        const val STATISTICAL_SAMPLE_SIZE = 100
    }

    /**
     * Sample rows from parsed data based on total row count.
     *
     * @param data Parsed data to sample from
     * @param maxSampleSize Maximum number of rows to include in sample
     * @return Sampled rows
     */
    fun sample(data: ParsedData, maxSampleSize: Int = STATISTICAL_SAMPLE_SIZE): List<DataRow> {
        val strategy = SamplingStrategy.forRowCount(data.totalRowCount)

        return when (strategy) {
            SamplingStrategy.FULL_DATA -> {
                // Return all rows (up to maxSampleSize for safety)
                data.rows.take(maxSampleSize)
            }
            SamplingStrategy.STATISTICAL -> {
                // Stratified sampling: take from beginning, middle, end
                stratifiedSample(data.rows, maxSampleSize)
            }
            SamplingStrategy.AGGREGATED -> {
                // Only statistics, minimal data sample
                stratifiedSample(data.rows, minOf(20, maxSampleSize))
            }
        }
    }

    /**
     * Performs stratified sampling to get representative rows.
     * Takes rows from beginning, random middle section, and end.
     */
    private fun stratifiedSample(rows: List<DataRow>, sampleSize: Int): List<DataRow> {
        if (rows.size <= sampleSize) return rows

        val result = mutableListOf<DataRow>()

        // Take ~20% from beginning (first rows often have headers context)
        val headCount = sampleSize / 5
        result.addAll(rows.take(headCount))

        // Take ~20% from end (recent data)
        val tailCount = sampleSize / 5
        val tailRows = rows.takeLast(tailCount)

        // Take ~60% randomly from middle
        val middleCount = sampleSize - headCount - tailCount
        val middleStart = headCount
        val middleEnd = rows.size - tailCount
        val middleRows = if (middleEnd > middleStart) {
            val middleRange = rows.subList(middleStart, middleEnd)
            middleRange.shuffled(Random(42)).take(middleCount)  // Fixed seed for reproducibility
        } else {
            emptyList()
        }

        result.addAll(middleRows)
        result.addAll(tailRows)

        return result
    }

    /**
     * Determine appropriate sampling strategy for given row count.
     */
    fun determineStrategy(rowCount: Int): SamplingStrategy {
        return SamplingStrategy.forRowCount(rowCount)
    }
}
