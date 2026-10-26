package app.fronteras.server.ai

import app.fronteras.server.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplyFieldStreamerTest {
    private fun streamAll(json: String, chunk: Int): String {
        val s = ReplyFieldStreamer()
        return json.chunked(chunk).joinToString("") { s.feed(it) }
    }

    @Test
    fun `extracts the reply across arbitrary fragment boundaries`() {
        val expected = "¡Claro! ¿Lo quiere \"grande\" o chico?\nTenemos de todo."
        for (size in listOf(1, 2, 3, 7, 50, 10_000)) assertEquals(expected, streamAll(TestData.TURN_JSON, size), "chunk $size")
    }

    @Test
    fun `handles unicode escapes and ignores nested reply keys`() {
        val json = """{"analysis":{"reply":"nested"},"reply":"café \\ ok"}"""
        assertEquals("café \\ ok", streamAll(json, 3))
    }

    @Test
    fun `stops at the end of the value`() {
        val s = ReplyFieldStreamer()
        assertEquals("Hola", s.feed("""{"reply": "Hola", "x": "y"}"""))
        assertTrue(s.done)
        assertEquals("", s.feed("more"))
    }
}
