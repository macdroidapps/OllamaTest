package ru.macdroid.ollama.data.local.analytics.parser

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JsonParserTest {

    private lateinit var parser: JsonParser

    @Before
    fun setup() {
        parser = JsonParser(Json { ignoreUnknownKeys = true; isLenient = true })
    }

    @Test
    fun `parse JSON array of objects`() = runBlocking {
        val json = """
            [
                {"name": "Alice", "age": 30, "active": true},
                {"name": "Bob", "age": 25, "active": false}
            ]
        """.trimIndent()

        val results = parser.parse(json).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(2, success!!.data.totalRowCount)
        assertEquals("Alice", success.data.rows[0]["name"])
        assertEquals("30", success.data.rows[0]["age"])
    }

    @Test
    fun `parse JSON Lines format`() = runBlocking {
        val jsonLines = """
            {"id": 1, "event": "click"}
            {"id": 2, "event": "view"}
            {"id": 3, "event": "click"}
        """.trimIndent()

        val results = parser.parse(jsonLines).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals(3, success!!.data.totalRowCount)
        assertEquals("click", success.data.rows[0]["event"])
    }

    @Test
    fun `flatten nested objects`() = runBlocking {
        val json = """
            [
                {"user": {"name": "Alice", "email": "alice@example.com"}, "action": "login"}
            ]
        """.trimIndent()

        val results = parser.parse(json).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertEquals("Alice", success!!.data.rows[0]["user.name"])
        assertEquals("alice@example.com", success.data.rows[0]["user.email"])
        assertEquals("login", success.data.rows[0]["action"])
    }

    @Test
    fun `handle arrays in values`() = runBlocking {
        val json = """
            [
                {"id": 1, "tags": ["a", "b", "c"]}
            ]
        """.trimIndent()

        val results = parser.parse(json).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        assertTrue(success!!.data.rows[0]["tags"]?.contains("a") == true)
    }

    @Test
    fun `handle null values`() = runBlocking {
        val json = """
            [
                {"name": "Alice", "email": null},
                {"name": "Bob", "email": "bob@example.com"}
            ]
        """.trimIndent()

        val results = parser.parse(json).toList()
        val success = results.filterIsInstance<ParseResult.Success>().firstOrNull()

        assertTrue("Should have success result", success != null)
        // Verify we have 2 rows
        assertEquals(2, success!!.data.totalRowCount)
        // Verify Bob's email is correctly parsed
        assertEquals("bob@example.com", success.data.rows[1]["email"])
    }

    @Test
    fun `count rows correctly`() {
        val json = """
            [
                {"id": 1},
                {"id": 2},
                {"id": 3}
            ]
        """.trimIndent()

        assertEquals(3, parser.countRows(json))
    }

    @Test
    fun `handle empty JSON`() = runBlocking {
        val json = ""

        val results = parser.parse(json).toList()
        val error = results.filterIsInstance<ParseResult.Error>().firstOrNull()

        assertTrue("Should have error result", error != null)
    }

    @Test
    fun `handle empty array`() = runBlocking {
        val json = "[]"

        val results = parser.parse(json).toList()
        val error = results.filterIsInstance<ParseResult.Error>().firstOrNull()

        assertTrue("Should have error result for empty array", error != null)
    }
}
