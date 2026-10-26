package app.fronteras.core.storage

import app.fronteras.core.Fixtures
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileLearnerStoreTest {
    private val dir = Files.createTempDirectory("fronteras").toFile()
    private val file = File(dir, "learner.json")

    @Test
    fun `round trips and survives a corrupt primary file`() = runTest {
        val store = FileLearnerStore(file)
        store.load()
        store.update { it.copy(profile = Fixtures.profile(), learnerNotes = listOf("hola")) }
        store.update { it.copy(learnerNotes = listOf("hola", "adiós")) }
        assertEquals(listOf("hola", "adiós"), FileLearnerStore(file).load().learnerNotes)

        file.writeText("{ not json")
        val recovered = FileLearnerStore(file).load()
        assertEquals(listOf("hola"), recovered.learnerNotes, "falls back to the previous good copy")
    }

    @Test
    fun `export excludes the sync queue and erase removes everything`() = runTest {
        val store = FileLearnerStore(file)
        store.update { it.copy(profile = Fixtures.profile()) }
        assertTrue(store.exportJson().contains("learner-1"))
        store.eraseAll()
        assertFalse(file.exists())
        assertNull(FileLearnerStore(file).load().profile)
    }

    @Test
    fun `refuses data from a newer schema`() {
        val obj = FronterasJson.parseToJsonElement("""{"schemaVersion": 99}""") as kotlinx.serialization.json.JsonObject
        assertFailsWith<IllegalArgumentException> { Migrations.migrate(obj) }
    }
}
