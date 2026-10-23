package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaGenresResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaImagesResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItemResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaSearchResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing uses real responses of https://api.mangabaka.org captured as test resources. */
class MangaBakaItemTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private fun resource(name: String) = javaClass.getResource("/mangabaka/$name")!!.readText()

    private val series by lazy { json.decodeFromString<MangaBakaItemResult>(resource("series_1.json")).data }

    @Test
    fun `series payload keeps parsing with the new fields`() {
        assertEquals(1L, series.id)
        assertEquals(listOf("Hyun-Seok Yun"), series.authors)
        assertEquals(listOf("Hyun-Seok Yun"), series.artists)
        assertEquals("completed", series.status)
        assertEquals("safe", series.contentRating)
        assertEquals("ko", series.originalLanguage)
        assertNotNull(series.cover.bestUrl())
        assertTrue(series.genres.orEmpty().contains("slice_of_life"))
    }

    @Test
    fun `search payload parses`() {
        val results = json.decodeFromString<MangaBakaSearchResult>(resource("search.json")).data
        assertEquals(1, results.size)
        assertTrue(results.first().chooseBestTitle().isNotBlank())
    }

    @Test
    fun `automatic title prefers english`() {
        assertEquals("DICE", series.chooseBestTitle())
        assertEquals("DICE", series.chooseBestTitle(MangaBaka.LANGUAGE_AUTO))
        assertEquals("DICE", series.chooseBestTitle(null))
    }

    @Test
    fun `preferred language wins over the default order`() {
        assertEquals("다이스", series.chooseBestTitle("ko"))
        assertEquals("DADO : El cubo que cambia todo", series.chooseBestTitle("es"))
    }

    @Test
    fun `native and romanized titles`() {
        assertEquals("다이스", series.chooseBestTitle(MangaBakaItem.TITLE_NATIVE))
        assertEquals("DICE", series.chooseBestTitle(MangaBakaItem.TITLE_ROMANIZED))
    }

    @Test
    fun `missing language falls back to the default order`() {
        // the series has no french title
        assertEquals("DICE", series.chooseBestTitle("fr"))
    }

    @Test
    fun `tags carry the group they belong to`() {
        val tags = series.tagsV2.orEmpty()
        assertTrue(tags.isNotEmpty())
        assertTrue(tags.any { it.isSpoiler })
        // every group of the real data must be offered in the settings
        val unknown = tags.map { it.category }.toSet() - MangaBaka.TAG_CATEGORIES.toSet()
        assertEquals(emptySet<String>(), unknown)
        assertEquals("Themes", tags.first { it.namePath?.startsWith("Themes") == true }.category)
    }

    @Test
    fun `images and genres parse`() {
        val images = json.decodeFromString<MangaBakaImagesResult>(resource("images_13.json")).data
        assertEquals("season", images.single().type)
        assertEquals("ko", images.single().language)
        assertNotNull(
            images
                .single()
                .image
                ?.raw
                ?.url,
        )

        val genres = json.decodeFromString<MangaBakaGenresResult>(resource("genres.json")).data
        assertEquals("Action", genres.first { it.value == "action" }.label)
    }
}
