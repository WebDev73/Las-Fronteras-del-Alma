package app.fronteras.server.ai

import app.fronteras.core.feedback.ErrorSeverity
import app.fronteras.server.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemasTest {
    @Suppress("UNCHECKED_CAST")
    private fun checkStrict(schema: Map<String, Any>, path: String = "$") {
        when (schema["type"]) {
            "object" -> {
                assertEquals(false, schema["additionalProperties"], "$path additionalProperties")
                val props = schema["properties"] as Map<String, Map<String, Any>>
                val required = schema["required"] as List<String>
                assertTrue(props.keys.containsAll(required), "$path required ⊆ properties")
                props.forEach { (k, v) -> checkStrict(v, "$path.$k") }
            }
            "array" -> checkStrict(schema["items"] as Map<String, Any>, "$path[]")
        }
    }

    @Test
    fun `schemas are valid for structured outputs`() {
        checkStrict(Schemas.partnerTurn)
        checkStrict(Schemas.sessionAnalysis)
        assertEquals("reply", (Schemas.partnerTurn["properties"] as Map<*, *>).keys.first(), "reply streams first")
    }

    @Test
    fun `parses model output into wire types`() {
        val (reply, analysis) = Schemas.parseTurn(TestData.TURN_JSON)
        assertTrue(reply.startsWith("¡Claro!"))
        val f = analysis!!.feedback.single()
        assertEquals(ErrorSeverity.UNNATURAL, f.severity)
        assertNull(f.corrected)
        assertEquals("es:polite-request", f.patternKey)

        val a = Schemas.parseAnalysis(TestData.ANALYSIS_JSON)
        assertEquals(3.0, a.rubric.single().score)
        assertNull(a.newItems.single().example, "blank strings become null")
    }
}
