package eu.kanade.tachiyomi.data.track.model

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMangaMetadataTest {
    private val full =
        TrackMangaMetadata(
            remoteId = 1,
            title = "t",
            coverUrl = "c",
            description = "d",
            authors = "a",
            artists = "r",
            tags = listOf("x"),
            status = 2,
        )

    @Test
    fun `empty selection means every field`() {
        assertEquals(Field.entries.toSet(), Field.fromKeys(emptySet()))
        assertEquals(full, full.filter(Field.fromKeys(emptySet())))
    }

    @Test
    fun `only selected fields are kept`() {
        val filtered = full.filter(Field.fromKeys(setOf("title", "cover")))
        assertEquals("t", filtered.title)
        assertEquals("c", filtered.coverUrl)
        assertNull(filtered.description)
        assertNull(filtered.authors)
        assertNull(filtered.artists)
        assertNull(filtered.tags)
        assertNull(filtered.status)
    }

    @Test
    fun `unknown keys are ignored`() {
        assertEquals(setOf(Field.TAGS), Field.fromKeys(setOf("tags", "bogus")))
    }
}
