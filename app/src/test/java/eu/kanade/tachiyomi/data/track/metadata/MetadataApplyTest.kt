package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.SeriesType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataApplyTest {
    private fun merge(
        existing: List<String> = emptyList(),
        incoming: List<String>? = null,
        type: SeriesType? = null,
        mode: String = MetadataApply.TAGS_REPLACE,
        addType: Boolean = true,
    ) = MetadataApply.mergeTags(existing, incoming, type, mode, addType)

    @Test
    fun `replacing drops the old tags`() {
        assertEquals(listOf("C", "D"), merge(existing = listOf("A", "B"), incoming = listOf("C", "D"), addType = false))
    }

    @Test
    fun `adding keeps the old tags without duplicates`() {
        assertEquals(
            listOf("A", "B", "C"),
            merge(existing = listOf("A", "B"), incoming = listOf("b", "C"), mode = MetadataApply.TAGS_ADD, addType = false),
        )
    }

    @Test
    fun `the series type becomes a tag the reader understands`() {
        assertEquals(listOf("Action", "Manhwa"), merge(incoming = listOf("Action"), type = SeriesType.MANHWA))
    }

    @Test
    fun `the series type tag isn't added twice or when turned off`() {
        assertEquals(
            listOf("Manhwa", "Action"),
            merge(incoming = listOf("manhwa", "Action"), type = SeriesType.MANHWA).let {
                it!!.map { t ->
                    t.replaceFirstChar(Char::uppercase)
                }
            },
        )
        assertEquals(listOf("Action"), merge(incoming = listOf("Action"), type = SeriesType.MANHWA, addType = false))
        // a manga that is tagged as something already isn't given a second kind
        assertEquals(listOf("Webtoon", "Action"), merge(incoming = listOf("Webtoon", "Action"), type = SeriesType.MANHWA))
    }

    @Test
    fun `without new tags nothing changes`() {
        assertNull(merge(existing = listOf("A"), incoming = null, type = SeriesType.MANGA))
        assertNull(merge(existing = listOf("A"), incoming = emptyList()))
        // unless the type is to be added to what is there
        assertEquals(
            listOf("A", "Manga"),
            merge(existing = listOf("A"), incoming = null, type = SeriesType.MANGA, mode = MetadataApply.TAGS_ADD),
        )
    }

    @Test
    fun `alternative titles go under the description`() {
        assertEquals("Story.\n\nAlternative titles: A; B", MetadataApply.description("Story.", listOf("A", "B"), true))
        assertEquals("Story.", MetadataApply.description("Story.", listOf("A", "B"), false))
        assertEquals("Story.", MetadataApply.description("Story.", emptyList(), true))
        // there's nothing to put them under, and they must not replace the description the source has
        assertNull(MetadataApply.description(null, listOf("A"), true))
        assertEquals(
            8,
            MetadataApply
                .description("S", (1..20).map { "T$it" }, true)!!
                .substringAfter(": ")
                .split("; ")
                .size,
        )
    }
}
