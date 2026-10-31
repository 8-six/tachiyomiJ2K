package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.SourceFeeds.Companion.moved
import eu.kanade.tachiyomi.source.SourceFeeds.Companion.sameTarget
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFeedsTest {
    private fun feed(
        id: Long,
        source: Long = 1,
        type: SourceFeed.Type = SourceFeed.Type.LATEST,
        search: Long? = null,
    ) = SourceFeed(id, source, type, search)

    @Test
    fun `move reorders and clamps`() {
        val list = listOf(feed(1), feed(2), feed(3))
        assertEquals(listOf(2L, 1L, 3L), list.moved(1, 1).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), list.moved(3, -10).map { it.id })
        assertEquals(list, list.moved(99, 1))
    }

    @Test
    fun `same target ignores id but not source or search`() {
        assertTrue(feed(1).sameTarget(feed(2)))
        assertFalse(feed(1).sameTarget(feed(2, source = 2)))
        val a = feed(1, type = SourceFeed.Type.SAVED_SEARCH, search = 5)
        val b = feed(2, type = SourceFeed.Type.SAVED_SEARCH, search = 6)
        assertFalse(a.sameTarget(b))
    }

    @Test
    fun `json round trip keeps order and saved search reference`() {
        val list = listOf(feed(1), feed(2, 7, SourceFeed.Type.SAVED_SEARCH, 42))
        val back = Json.decodeFromString<List<SourceFeed>>(Json.encodeToString(list))
        assertEquals(list, back)
    }
}
