package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaImage
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaImageFile
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaImagesResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaRawCover
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The real covers of https://mangabaka.org/manhwa/5307: 25 covers in 6 languages, volumes, seasons and others, up to
 * number 8, and several of the same number.
 */
class MangaBakaCoversTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val images by lazy {
        json
            .decodeFromString<MangaBakaImagesResult>(javaClass.getResource("/mangabaka/images_5307.json")!!.readText())
            .data
    }

    private fun urlOf(id: Long) =
        images
            .first { it.id == id }
            .image!!
            .raw!!
            .url

    private fun choose(
        mode: String,
        language: String = MangaBaka.LANGUAGE_ANY,
        type: String = MangaBaka.COVER_TYPE_ANY,
        safeOnly: Boolean = true,
        original: String? = "ko",
        list: List<MangaBakaImage> = images,
    ) = MangaBakaCovers.choose(list, mode, language, type, safeOnly, original)

    @Test
    fun `the real series has many covers`() {
        assertEquals(25, images.size)
    }

    @Test
    fun `latest is the highest numbered cover`() {
        // number 8, the only one of its number
        assertEquals(urlOf(125164), choose(MangaBaka.COVER_LATEST))
    }

    @Test
    fun `first is the lowest numbered cover`() {
        // volume 0 from LINE Manga
        assertEquals(urlOf(125181), choose(MangaBaka.COVER_FIRST))
    }

    @Test
    fun `latest respects the language`() {
        assertEquals(urlOf(125165), choose(MangaBaka.COVER_LATEST, language = "en"))
        assertEquals(urlOf(125183), choose(MangaBaka.COVER_LATEST, language = "ja"))
        // zh also covers zh-Hant
        assertEquals(urlOf(125168), choose(MangaBaka.COVER_LATEST, language = "zh"))
    }

    @Test
    fun `latest respects the type`() {
        assertEquals(urlOf(125181), choose(MangaBaka.COVER_LATEST, type = "volume"))
        assertEquals(urlOf(125172), choose(MangaBaka.COVER_LATEST, type = "other"))
        assertEquals(urlOf(125164), choose(MangaBaka.COVER_LATEST, type = "season"))
    }

    @Test
    fun `no cover left after filtering gives nothing`() {
        assertNull(choose(MangaBaka.COVER_LATEST, language = "de"))
        assertNull(choose(MangaBaka.COVER_LATEST, list = emptyList()))
    }

    @Test
    fun `the default mode never picks an extra cover`() {
        assertNull(choose(MangaBaka.COVER_DEFAULT))
    }

    private fun image(
        id: Long,
        index: Double?,
        language: String,
        createdAt: String,
        rating: String = "safe",
    ) = MangaBakaImage(
        id = id,
        type = "season",
        language = language,
        indexNumeric = index,
        contentRating = rating,
        createdAt = createdAt,
        image = MangaBakaImageFile(raw = MangaBakaRawCover("https://img/$id")),
    )

    @Test
    fun `of the same number the original language wins, then the newest upload`() {
        val list =
            listOf(
                image(1, 3.0, "en", "2026-06-01"),
                image(2, 3.0, "ko", "2026-01-01"),
                image(3, 3.0, "ko", "2026-05-01"),
            )
        assertEquals("https://img/3", choose(MangaBaka.COVER_LATEST, original = "ko", list = list))
        // without an original language only the upload date is left
        assertEquals("https://img/1", choose(MangaBaka.COVER_LATEST, original = null, list = list))
    }

    @Test
    fun `unsafe covers are skipped unless allowed`() {
        val list = listOf(image(1, 1.0, "ko", "2026-01-01"), image(2, 2.0, "ko", "2026-01-01", rating = "erotica"))
        assertEquals("https://img/1", choose(MangaBaka.COVER_LATEST, list = list))
        assertEquals("https://img/2", choose(MangaBaka.COVER_LATEST, safeOnly = false, list = list))
    }

    @Test
    fun `covers without a number lose against numbered ones`() {
        val list = listOf(image(1, null, "ko", "2026-09-01"), image(2, 1.0, "ko", "2026-01-01"))
        assertEquals("https://img/2", choose(MangaBaka.COVER_LATEST, list = list))
    }
}
