package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.SeriesType
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataTextTest {
    @Test
    fun `descriptions lose their markup`() {
        assertEquals("One\nTwo", MetadataText.cleanDescription("One<br>Two"))
        assertEquals("A\n\nB", MetadataText.cleanDescription("<p>A</p><p>B</p>"))
        assertEquals("Bold text", MetadataText.cleanDescription("[b]Bold[/b] [url=http://x]text[/url]"))
        assertEquals("Tom & \"Jerry\"", MetadataText.cleanDescription("Tom &amp; &quot;Jerry&quot;"))
        assertNull(MetadataText.cleanDescription("  <br>  "))
        assertNull(MetadataText.cleanDescription(null))
    }

    @Test
    fun `names and tags have no blanks or duplicates`() {
        // the first spelling of a duplicate is kept
        assertEquals("A, b", MetadataText.names("A, b", " ", "B", null, "a"))
        assertNull(MetadataText.names(null, " "))
        assertEquals(listOf("Action", "Drama"), MetadataText.tags(listOf("Action", " ", null, "action", "Drama")))
        assertNull(MetadataText.tags(listOf(null, " ")))
    }

    @Test
    fun `credits are split by what each person did`() {
        val credits = MetadataText.Credits()
        credits.add("W", "Original Story")
        credits.add("A", "Illustration")
        credits.add("B", "Story & Art")
        credits.add("U", null)
        credits.add("X", "Translator")
        credits.add(null, "Story")
        assertEquals("W, B, U", credits.authors())
        assertEquals("A, B", credits.artists())
    }

    @Test
    fun `statuses`() {
        assertEquals(SManga.ONGOING, MetadataText.status("RELEASING"))
        assertEquals(SManga.ONGOING, MetadataText.status("currently_publishing"))
        assertEquals(SManga.COMPLETED, MetadataText.status("Finished"))
        assertEquals(SManga.ON_HIATUS, MetadataText.status("on hiatus"))
        assertEquals(SManga.CANCELLED, MetadataText.status("discontinued"))
        assertNull(MetadataText.status("not_yet_released"))
        assertNull(MetadataText.status(null))
    }

    @Test
    fun `series types from names and countries`() {
        assertEquals(SeriesType.MANHWA, SeriesType.from("KR"))
        assertEquals(SeriesType.MANHWA, SeriesType.from("Manhwa"))
        assertEquals(SeriesType.MANHUA, SeriesType.from("cn"))
        assertEquals(SeriesType.MANGA, SeriesType.from("one_shot"))
        assertEquals(SeriesType.COMIC, SeriesType.from("OEL"))
        assertNull(SeriesType.from("novel"))
        assertNull(SeriesType.from(null))
    }

    @Test
    fun `title style falls back through the other names`() {
        assertEquals("En", MetadataText.pickTitle("auto", "En", "Ro", "Na"))
        assertEquals("Ro", MetadataText.pickTitle("auto", null, "Ro", "Na"))
        assertEquals("Ro", MetadataText.pickTitle("romaji", "En", "Ro", "Na"))
        assertEquals("Na", MetadataText.pickTitle("native", "En", "Ro", "Na"))
        assertEquals("En", MetadataText.pickTitle("native", "En", null, null))
        assertNull(MetadataText.pickTitle("auto", " ", null, null))
    }

    @Test
    fun `gaps are filled from the next tracker`() {
        val first =
            TrackMangaMetadata(title = "A", authors = "X", tags = listOf("t1"), alternativeTitles = listOf("a1"), warnings = listOf("w1"))
        val second =
            TrackMangaMetadata(
                title = "B",
                authors = "Y",
                artists = "Z",
                description = "D",
                tags = listOf("t2"),
                status = SManga.COMPLETED,
                alternativeTitles = listOf("A1", "a2"),
                seriesType = SeriesType.MANHWA,
                warnings = listOf("w2"),
            )
        val merged = first.fillGapsFrom(second)
        assertEquals("A", merged.title)
        assertEquals("X", merged.authors)
        assertEquals("Z", merged.artists)
        assertEquals("D", merged.description)
        // tags of different services aren't mixed
        assertEquals(listOf("t1"), merged.tags)
        assertEquals(SManga.COMPLETED, merged.status)
        assertEquals(listOf("a1", "a2"), merged.alternativeTitles)
        assertEquals(SeriesType.MANHWA, merged.seriesType)
        assertEquals(listOf("w1", "w2"), merged.warnings)
        assertTrue(merged.has(TrackMangaMetadata.Field.ARTISTS))
        assertFalse(TrackMangaMetadata().has(TrackMangaMetadata.Field.TITLE))
    }
}
