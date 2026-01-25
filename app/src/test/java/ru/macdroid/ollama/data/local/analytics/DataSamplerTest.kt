package ru.macdroid.ollama.data.local.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.macdroid.ollama.domain.model.analytics.ColumnInfo
import ru.macdroid.ollama.domain.model.analytics.ColumnType
import ru.macdroid.ollama.domain.model.analytics.DataRow
import ru.macdroid.ollama.domain.model.analytics.DataSchema
import ru.macdroid.ollama.domain.model.analytics.ParsedData
import ru.macdroid.ollama.domain.model.analytics.SamplingStrategy

class DataSamplerTest {

    private lateinit var sampler: DataSampler

    @Before
    fun setup() {
        sampler = DataSampler()
    }

    @Test
    fun `determine FULL_DATA strategy for small datasets`() {
        assertEquals(SamplingStrategy.FULL_DATA, sampler.determineStrategy(100))
        assertEquals(SamplingStrategy.FULL_DATA, sampler.determineStrategy(499))
    }

    @Test
    fun `determine STATISTICAL strategy for medium datasets`() {
        assertEquals(SamplingStrategy.STATISTICAL, sampler.determineStrategy(500))
        assertEquals(SamplingStrategy.STATISTICAL, sampler.determineStrategy(1000))
        assertEquals(SamplingStrategy.STATISTICAL, sampler.determineStrategy(4999))
    }

    @Test
    fun `determine AGGREGATED strategy for large datasets`() {
        assertEquals(SamplingStrategy.AGGREGATED, sampler.determineStrategy(5000))
        assertEquals(SamplingStrategy.AGGREGATED, sampler.determineStrategy(10000))
    }

    @Test
    fun `sample returns all rows for small dataset`() {
        val data = createTestData(100)

        val sampled = sampler.sample(data)

        assertEquals(100, sampled.size)
    }

    @Test
    fun `sample returns limited rows for medium dataset`() {
        val data = createTestData(1000)

        val sampled = sampler.sample(data, maxSampleSize = 100)

        assertEquals(100, sampled.size)
    }

    @Test
    fun `sample returns minimal rows for large dataset`() {
        val data = createTestData(10000)

        val sampled = sampler.sample(data, maxSampleSize = 100)

        assertTrue(sampled.size <= 20)  // AGGREGATED strategy uses minimal sample
    }

    @Test
    fun `stratified sample includes rows from beginning and end`() {
        val data = createTestData(1000)

        val sampled = sampler.sample(data, maxSampleSize = 100)

        // Should include some rows from beginning
        val firstIds = sampled.take(20).mapNotNull { it["id"]?.toIntOrNull() }
        assertTrue("Should include rows from beginning", firstIds.any { it < 100 })

        // Should include some rows from end
        val lastIds = sampled.takeLast(20).mapNotNull { it["id"]?.toIntOrNull() }
        assertTrue("Should include rows from end", lastIds.any { it > 900 })
    }

    @Test
    fun `sample respects maxSampleSize parameter`() {
        val data = createTestData(1000)

        val sampled50 = sampler.sample(data, maxSampleSize = 50)
        val sampled100 = sampler.sample(data, maxSampleSize = 100)

        assertEquals(50, sampled50.size)
        assertEquals(100, sampled100.size)
    }

    private fun createTestData(rowCount: Int): ParsedData {
        val schema = DataSchema(
            columns = listOf(
                ColumnInfo("id", ColumnType.INTEGER),
                ColumnInfo("value", ColumnType.STRING)
            )
        )

        val rows = (0 until rowCount).map { i ->
            DataRow(mapOf("id" to i.toString(), "value" to "value_$i"))
        }

        return ParsedData(
            schema = schema,
            rows = rows,
            totalRowCount = rowCount,
            isSampled = false
        )
    }
}
