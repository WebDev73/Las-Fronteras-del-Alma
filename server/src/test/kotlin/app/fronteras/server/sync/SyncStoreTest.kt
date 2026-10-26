package app.fronteras.server.sync

import app.fronteras.core.feedback.RetrievalMethod
import app.fronteras.core.language.LanguageTag
import app.fronteras.core.srs.Rating
import app.fronteras.core.srs.ReviewCategory
import app.fronteras.core.srs.ReviewItem
import app.fronteras.core.srs.ReviewScheduler
import app.fronteras.core.srs.ReviewSource
import app.fronteras.core.srs.ReviewSourceType
import app.fronteras.core.storage.FronterasJson
import app.fronteras.core.sync.ChangeType
import app.fronteras.core.sync.SyncChange
import app.fronteras.core.util.FixedClock
import app.fronteras.core.util.SequentialIds
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncStoreTest {
    private val base = ReviewItem("r1", LanguageTag("fr-CA"), "magasiner", "to go shopping", "Québec usage", LanguageTag("en"),
        ReviewCategory.VOCABULARY, source = ReviewSource(ReviewSourceType.MANUAL), createdAt = 0)

    private fun change(key: String, item: ReviewItem) = SyncChange(key, ChangeType.REVIEW_ITEM, item.id, FronterasJson.encodeToString(ReviewItem.serializer(), item))

    @Test
    fun `reviews from two devices are merged, persisted and pulled`() {
        val dir = Files.createTempDirectory("sync").toFile()
        val phone = ReviewScheduler(clock = FixedClock(86_400_000), ids = SequentialIds("p")).record(base, RetrievalMethod.RECOGNIZE, Rating.GOOD)
        val tablet = ReviewScheduler(clock = FixedClock(2 * 86_400_000L), ids = SequentialIds("t")).record(base, RetrievalMethod.SPEAK_ALOUD, Rating.GOOD)
        SyncStore(dir).push("ana", listOf(change("k1", phone)))
        val store = SyncStore(dir) // reload from disk
        store.push("ana", listOf(change("k2", tablet)))
        val pulled = store.pull("ana", 0).records.single()
        val merged = FronterasJson.decodeFromString(ReviewItem.serializer(), pulled.payload)
        assertEquals(2, merged.history.size)
        assertTrue(merged.production != null && merged.recognition != null)
        assertEquals(2, pulled.version)
        assertTrue(store.pull("ana", 2).records.isEmpty())
        assertTrue(store.pull("bo", 0).records.isEmpty(), "users are isolated")
    }
}
