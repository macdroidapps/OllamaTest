package ru.macdroid.ollama.data.local.analytics.parser

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogParserTest {

    private lateinit var parser: LogParser

    @Before
    fun setup() {
        parser = LogParser()
    }

    @Test
    fun `parse standard log format`() = runBlocking {
        val log = """
            2024-01-15 10:30:00 INFO [Main] Application started
            2024-01-15 10:30:01 DEBUG [DB] Connection established
            2024-01-15 10:30:02 ERROR [API] Request failed
        """.trimIndent()

        val results = parser.parse(log).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(3, success!!.data.totalRowCount)
        assertEquals("INFO", success.data.rows[0]["level"])
        assertEquals("Main", success.data.rows[0]["source"])
        assertEquals("Application started", success.data.rows[0]["message"])
    }

    @Test
    fun `parse simple log format with brackets`() = runBlocking {
        val log = """
            [INFO] Server starting
            [DEBUG] Loading configuration
            [ERROR] Failed to connect
        """.trimIndent()

        val results = parser.parse(log).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(3, success!!.data.totalRowCount)
        assertEquals("INFO", success.data.rows[0]["level"])
        assertEquals("Server starting", success.data.rows[0]["message"])
    }

    @Test
    fun `parse simple log format with colon`() = runBlocking {
        val log = """
            INFO: Application initialized
            WARN: Low memory warning
            ERROR: Critical failure
        """.trimIndent()

        val results = parser.parse(log).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(3, success!!.data.totalRowCount)
        assertEquals("INFO", success.data.rows[0]["level"])
    }

    @Test
    fun `parse key-value log format`() = runBlocking {
        val log = """
            timestamp=2024-01-15T10:30:00Z level=INFO message="User logged in" user_id=123
            timestamp=2024-01-15T10:30:01Z level=DEBUG message="Cache hit" key=session_456
        """.trimIndent()

        val results = parser.parse(log).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(2, success!!.data.totalRowCount)
        assertEquals("INFO", success.data.rows[0]["level"])
        assertEquals("User logged in", success.data.rows[0]["message"])
        assertEquals("123", success.data.rows[0]["user_id"])
    }

    @Test
    fun `fallback to raw lines for unknown format`() = runBlocking {
        val log = """
            Some random text line 1
            Another random line 2
            Yet another line 3
        """.trimIndent()

        val results = parser.parse(log).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(3, success!!.data.totalRowCount)
        assertTrue(success.data.schema.columnNames().contains("line"))
    }

    @Test
    fun `count rows correctly`() {
        val log = """
            [INFO] Line 1
            [DEBUG] Line 2
            [ERROR] Line 3
            [WARN] Line 4
        """.trimIndent()

        assertEquals(4, parser.countRows(log))
    }

    @Test
    fun `handle empty log`() = runBlocking {
        val log = ""

        val results = parser.parse(log).toList()
        val error = results.filterIsInstance<ParseResult.Error>().firstOrNull()

        assertTrue("Should have error result", error != null)
    }

    @Test
    fun `skip blank lines`() = runBlocking {
        val log = """
            [INFO] Line 1

            [DEBUG] Line 2

            [ERROR] Line 3
        """.trimIndent()

        val results = parser.parse(log).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(3, success!!.data.totalRowCount)
    }
}
