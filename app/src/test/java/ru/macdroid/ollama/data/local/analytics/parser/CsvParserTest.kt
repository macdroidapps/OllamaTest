package ru.macdroid.ollama.data.local.analytics.parser

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.macdroid.ollama.domain.model.analytics.ColumnType

class CsvParserTest {

    private lateinit var parser: CsvParser

    @Before
    fun setup() {
        parser = CsvParser()
    }

    @Test
    fun `parse simple CSV with comma delimiter`() = runBlocking {
        val csv = """
            name,age,active
            Alice,30,true
            Bob,25,false
            Charlie,35,true
        """.trimIndent()

        val results = parser.parse(csv).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(3, success!!.data.totalRowCount)
        assertEquals(3, success.data.schema.columnCount)
        assertEquals(listOf("name", "age", "active"), success.data.schema.columnNames())
    }

    @Test
    fun `parse CSV with semicolon delimiter`() = runBlocking {
        val csv = """
            product;price;quantity
            Apple;1.50;100
            Banana;0.75;200
        """.trimIndent()

        val results = parser.parse(csv).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(2, success!!.data.totalRowCount)
        assertEquals(listOf("product", "price", "quantity"), success.data.schema.columnNames())
    }

    @Test
    fun `parse CSV with quoted fields`() = runBlocking {
        val csv = """
            name,description,price
            "Widget A","A simple widget",10.00
            "Widget B","A ""special"" widget",20.00
        """.trimIndent()

        val results = parser.parse(csv).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(2, success!!.data.totalRowCount)
        assertEquals("Widget A", success.data.rows[0]["name"])
        assertEquals("A simple widget", success.data.rows[0]["description"])
        assertEquals("A \"special\" widget", success.data.rows[1]["description"])
    }

    @Test
    fun `infer column types correctly`() = runBlocking {
        val csv = """
            id,name,price,active,created
            1,Product,19.99,true,2024-01-15
            2,Item,29.99,false,2024-01-16
        """.trimIndent()

        val results = parser.parse(csv).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        val schema = success!!.data.schema

        assertEquals(ColumnType.INTEGER, schema.columns.find { it.name == "id" }?.type)
        assertEquals(ColumnType.STRING, schema.columns.find { it.name == "name" }?.type)
        assertEquals(ColumnType.DECIMAL, schema.columns.find { it.name == "price" }?.type)
        assertEquals(ColumnType.BOOLEAN, schema.columns.find { it.name == "active" }?.type)
        assertEquals(ColumnType.TIMESTAMP, schema.columns.find { it.name == "created" }?.type)
    }

    @Test
    fun `limit rows when maxRows specified`() = runBlocking {
        val csv = buildString {
            appendLine("id,value")
            repeat(100) { i ->
                appendLine("$i,value$i")
            }
        }

        val results = parser.parse(csv, maxRows = 10).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(100, success!!.data.totalRowCount)
        assertEquals(10, success.data.loadedRowCount)
        assertTrue(success.data.isSampled)
    }

    @Test
    fun `count rows correctly`() {
        val csv = """
            header1,header2
            row1,value1
            row2,value2
            row3,value3
        """.trimIndent()

        assertEquals(3, parser.countRows(csv))
    }

    @Test
    fun `handle empty CSV`() = runBlocking {
        val csv = ""

        val results = parser.parse(csv).toList()
        val error = results.filterIsInstance<ParseResult.Error>().firstOrNull()

        assertTrue("Should have error result", error != null)
    }

    @Test
    fun `emit progress during parsing`() = runBlocking {
        val csv = buildString {
            appendLine("id,value")
            repeat(200) { i ->
                appendLine("$i,value$i")
            }
        }

        val results = parser.parse(csv).toList()
        val progressResults = results.filterIsInstance<ParseResult.Progress>()

        assertFalse("Should emit progress", progressResults.isEmpty())
    }
}
