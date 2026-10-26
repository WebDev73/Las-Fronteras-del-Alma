package app.fronteras.core.privacy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactorTest {
    private val r = Redactor()

    @Test
    fun `redacts identifiers but keeps ordinary numbers`() {
        assertEquals("Escríbeme a [email]", r.redact("Escríbeme a juan.p@mail.es"))
        assertEquals("Mi tarjeta es [number]", r.redact("Mi tarjeta es 4111 1111 1111 1111"))
        assertEquals("Tengo 25 años y vivo en el 3º", r.redact("Tengo 25 años y vivo en el 3º"))
        assertEquals("Llama al [phone].", r.redact("Llama al 06 12 34 56 78."))
    }

    @Test
    fun `retention defaults keep no raw audio`() {
        val p = RetentionPolicy()
        assertTrue(p.rawAudioExpired(0, 1))
        assertFalse(RetentionPolicy(keepRawAudio = true).rawAudioExpired(0, 3_600_000))
    }
}
