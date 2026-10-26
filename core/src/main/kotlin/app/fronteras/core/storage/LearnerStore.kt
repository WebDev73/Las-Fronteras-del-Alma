package app.fronteras.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException

val FronterasJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "type"
}

interface LearnerStore {
    val data: StateFlow<LearnerData>
    suspend fun load(): LearnerData
    suspend fun update(transform: (LearnerData) -> LearnerData): LearnerData
    /** Deletes all stored learning history from this device. */
    suspend fun eraseAll()
    fun exportJson(): String
}

/**
 * JSON-file store with atomic replace (write temp, fsync, rename) and a schema-version
 * migration hook. Suitable for the data volumes of a single learner; see
 * docs/DECISIONS.md for the planned move to SQLite when histories grow.
 */
class FileLearnerStore(private val file: File, private val json: Json = FronterasJson) : LearnerStore {
    private val mutex = Mutex()
    private val state = MutableStateFlow(LearnerData())
    override val data: StateFlow<LearnerData> = state.asStateFlow()

    override suspend fun load(): LearnerData = mutex.withLock {
        val loaded = withContext(Dispatchers.IO) { read() }
        state.value = loaded
        loaded
    }

    override suspend fun update(transform: (LearnerData) -> LearnerData): LearnerData = mutex.withLock {
        val next = transform(state.value)
        withContext(Dispatchers.IO) { write(next) }
        state.value = next
        next
    }

    override suspend fun eraseAll() = mutex.withLock {
        withContext(Dispatchers.IO) {
            file.delete()
            File(file.path + ".tmp").delete()
            File(file.path + ".bak").delete()
        }
        state.value = LearnerData()
    }

    override fun exportJson(): String = json.encodeToString(LearnerData.serializer(), state.value.copy(outbox = emptyList()))

    private fun read(): LearnerData {
        val candidates = listOf(file, File(file.path + ".bak"))
        for (f in candidates) {
            if (!f.exists()) continue
            try {
                val text = f.readText()
                val obj = json.parseToJsonElement(text).jsonObject
                return json.decodeFromJsonElement(LearnerData.serializer(), Migrations.migrate(obj))
            } catch (e: Exception) {
                // Corrupt primary: fall through to the backup copy.
            }
        }
        return LearnerData()
    }

    private fun write(d: LearnerData) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { out ->
            out.write(json.encodeToString(LearnerData.serializer(), d).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (file.exists()) file.copyTo(File(file.path + ".bak"), overwrite = true)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Could not replace ${file.name}")
        }
    }
}

object Migrations {
    fun migrate(obj: JsonObject): JsonObject {
        val version = obj["schemaVersion"]?.jsonPrimitive?.int ?: 1
        require(version <= LearnerData.CURRENT_SCHEMA) { "Data was written by a newer app version ($version)" }
        // No migrations yet; future: when (version) { 1 -> migrate1to2(obj) ... }
        return obj
    }
}

/** In-memory store for tests and previews. */
class InMemoryLearnerStore(initial: LearnerData = LearnerData()) : LearnerStore {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()
    override val data: StateFlow<LearnerData> = state.asStateFlow()
    override suspend fun load() = state.value
    override suspend fun update(transform: (LearnerData) -> LearnerData) = mutex.withLock { transform(state.value).also { state.value = it } }
    override suspend fun eraseAll() { state.value = LearnerData() }
    override fun exportJson(): String = FronterasJson.encodeToString(LearnerData.serializer(), state.value)
}
