package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.SeriesType
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parsers run on real responses of the trackers for "The Extra's Academy Survival Guide" (AniList, MangaUpdates,
 * Kitsu) and for a Bangumi subject, so a change in what they send shows up here.
 */
class TrackerMetadataTest {
    private fun fixture(name: String) = Json.parseToJsonElement(javaClass.getResource("/trackers/$name")!!.readText())

    // region AniList
    private val aniListMedia by lazy { fixture("anilist_media.json").jsonObject["data"]!!.jsonObject["Media"]!!.jsonObject }

    @Test
    fun `anilist gives everything for the real series`() {
        val m = AniListMetadata.parse(aniListMedia)
        assertEquals(172619L, m.remoteId)
        assertEquals("The Extra’s Academy Survival Guide", m.title)
        assertEquals("Korita, Green Kyrin", m.authors)
        assertEquals("Green Kyrin", m.artists)
        assertEquals(SManga.ONGOING, m.status)
        assertEquals(SeriesType.MANHWA, m.seriesType)
        assertTrue(m.coverUrl!!.contains("bx172619"))
        assertTrue(m.description!!.startsWith("Ed Rothstaylor"))
        assertEquals(listOf("Action", "Fantasy"), m.tags!!.take(2))
        assertTrue("Isekai" in m.tags!!)
    }

    @Test
    fun `anilist title style`() {
        assertEquals(
            "Academy-eseo Saranamgi",
            AniListMetadata.parse(aniListMedia, MetadataOptions(titleStyle = MetadataOptions.TITLE_ROMAJI)).title,
        )
        assertEquals("아카데미에서 살아남기", AniListMetadata.parse(aniListMedia, MetadataOptions(titleStyle = MetadataOptions.TITLE_NATIVE)).title)
    }

    @Test
    fun `anilist alternative titles hold the other names but not the title`() {
        val alternatives = AniListMetadata.parse(aniListMedia).alternativeTitles!!
        assertTrue("Academy-eseo Saranamgi" in alternatives)
        assertTrue("아카데미에서 살아남기" in alternatives)
        assertTrue("三流反派的學院生存記" in alternatives)
        assertFalse("The Extra’s Academy Survival Guide" in alternatives)
    }

    @Test
    fun `anilist tag relevance and spoilers`() {
        val all = AniListMetadata.parse(aniListMedia, MetadataOptions(minTagRank = 0)).tags!!
        val relevant = AniListMetadata.parse(aniListMedia, MetadataOptions(minTagRank = 85)).tags!!
        assertTrue(all.size > relevant.size)
        assertTrue("Male Protagonist" in relevant)
        assertFalse("Unrequited Love" in relevant)

        val spoiler =
            Json
                .parseToJsonElement(
                    """{"genres":[],"tags":[{"name":"Hidden","rank":90,"isMediaSpoiler":true,"isGeneralSpoiler":false},{"name":"Shown","rank":90}]}""",
                ).jsonObject
        assertEquals(listOf("Shown"), AniListMetadata.parse(spoiler, MetadataOptions(hideSpoilerTags = true)).tags)
        assertEquals(listOf("Hidden", "Shown"), AniListMetadata.parse(spoiler, MetadataOptions(hideSpoilerTags = false)).tags)
    }
    // endregion

    // region MangaUpdates
    private val mangaUpdates by lazy { fixture("mangaupdates_series.json").jsonObject }

    @Test
    fun `mangaupdates gives everything for the real series`() {
        val m = MangaUpdatesMetadata.parse(mangaUpdates)
        assertEquals(41254648118L, m.remoteId)
        assertEquals("The Extra's Academy Survival Guide", m.title)
        assertEquals("Corita", m.authors)
        assertEquals("Greenkyrin", m.artists)
        assertEquals(SeriesType.MANHWA, m.seriesType)
        assertEquals("https://cdn.mangaupdates.com/image/i538828.jpg", m.coverUrl)
        // "(Ongoing)" is only in the free text of the status
        assertEquals(SManga.ONGOING, m.status)
        assertTrue("Action" in m.tags!!)
    }

    @Test
    fun `mangaupdates only keeps categories several people voted for`() {
        val tags = MangaUpdatesMetadata.parse(mangaUpdates).tags!!
        assertTrue("Academy" in tags)
        // 2 votes
        assertFalse("Alcohol" in tags)
        // most voted categories come first, after the genres
        assertTrue(tags.indexOf("Academy") > tags.indexOf("Slice of Life"))
    }

    @Test
    fun `mangaupdates completed flag wins over the text`() {
        val done = JsonObject(mangaUpdates + ("completed" to Json.parseToJsonElement("true")))
        assertEquals(SManga.COMPLETED, MangaUpdatesMetadata.parse(done).status)
    }

    @Test
    fun `mangaupdates alternative titles`() {
        assertTrue("Academy-eseo Saranamgi" in MangaUpdatesMetadata.parse(mangaUpdates).alternativeTitles!!)
    }
    // endregion

    // region Kitsu
    private val kitsu by lazy { fixture("kitsu_manga.json").jsonObject }

    @Test
    fun `kitsu gives what the real series has`() {
        val m = KitsuMetadata.parse(kitsu)
        assertEquals(70681L, m.remoteId)
        assertEquals("The Extra's Academy Survival Guide", m.title)
        assertEquals(SManga.ONGOING, m.status)
        assertEquals(SeriesType.MANHWA, m.seriesType)
        assertNotNull(m.coverUrl)
        assertTrue(m.description!!.startsWith("I possessed"))
        assertEquals(listOf("Action", "Drama", "Adventure", "Fantasy", "Romance", "Seinen", "Supernatural"), m.tags)
        // this series has no staff on Kitsu
        assertNull(m.authors)
        assertNull(m.artists)
    }

    @Test
    fun `kitsu staff is read from the included people`() {
        val response =
            Json
                .parseToJsonElement(
                    """
                    {"data":{"id":"1","attributes":{"canonicalTitle":"T"}},
                     "included":[
                       {"id":"10","type":"people","attributes":{"name":"Writer One"}},
                       {"id":"11","type":"people","attributes":{"name":"Artist Two"}},
                       {"id":"12","type":"people","attributes":{"name":"Both Three"}},
                       {"id":"20","type":"mangaStaff","attributes":{"role":"Story"},"relationships":{"person":{"data":{"id":"10"}}}},
                       {"id":"21","type":"mangaStaff","attributes":{"role":"Art"},"relationships":{"person":{"data":{"id":"11"}}}},
                       {"id":"22","type":"mangaStaff","attributes":{"role":"Story & Art"},"relationships":{"person":{"data":{"id":"12"}}}}
                     ]}
                    """.trimIndent(),
                ).jsonObject
        val m = KitsuMetadata.parse(response)
        assertEquals("Writer One, Both Three", m.authors)
        assertEquals("Artist Two, Both Three", m.artists)
    }
    // endregion

    // region Bangumi
    private val bangumi by lazy { fixture("bangumi_subject.json").jsonObject }

    @Test
    fun `bangumi gives what the real subject has`() {
        val m = BangumiMetadata.parse(bangumi)
        assertEquals(280788L, m.remoteId)
        assertEquals("Schoolday 学院岛", m.title)
        assertEquals("kai", m.authors)
        assertNull(m.artists)
        assertEquals(listOf("香港", "中国"), m.tags)
        assertTrue(m.coverUrl!!.endsWith("280788_a58NH.jpg"))
    }

    @Test
    fun `bangumi infobox values can be a list`() {
        val subject =
            Json
                .parseToJsonElement(
                    """{"name":"N","infobox":[{"key":"作者","value":[{"v":"A"},{"v":"B"}]},{"key":"作画","value":"C"}]}""",
                ).jsonObject
        val m = BangumiMetadata.parse(subject)
        assertEquals("A, B", m.authors)
        assertEquals("C", m.artists)
    }
    // endregion

    // region MyAnimeList and Shikimori, from their documented responses
    @Test
    fun `myanimelist response`() {
        val manga =
            Json
                .parseToJsonElement(
                    """
                    {"id":13,"title":"One Piece","main_picture":{"medium":"https://m/m.jpg","large":"https://m/l.jpg"},
                     "alternative_titles":{"synonyms":["OP"],"en":"One Piece (EN)","ja":"ONE PIECE"},
                     "synopsis":"Pirates &amp; treasure.","media_type":"manga","status":"currently_publishing",
                     "genres":[{"id":1,"name":"Action"},{"id":2,"name":"Adventure"}],
                     "authors":[{"node":{"id":1881,"first_name":"Eiichiro","last_name":"Oda"},"role":"Story & Art"}]}
                    """.trimIndent(),
                ).jsonObject
        val m = MyAnimeListMetadata.parse(manga)
        assertEquals(13L, m.remoteId)
        assertEquals("One Piece (EN)", m.title)
        assertEquals("One Piece", MyAnimeListMetadata.parse(manga, MetadataOptions(titleStyle = MetadataOptions.TITLE_ROMAJI)).title)
        assertEquals("Eiichiro Oda", m.authors)
        assertEquals("Eiichiro Oda", m.artists)
        assertEquals("https://m/l.jpg", m.coverUrl)
        assertEquals("Pirates & treasure.", m.description)
        assertEquals(SManga.ONGOING, m.status)
        assertEquals(SeriesType.MANGA, m.seriesType)
        assertEquals(listOf("Action", "Adventure"), m.tags)
        assertTrue("OP" in m.alternativeTitles!!)
    }

    @Test
    fun `shikimori response`() {
        val manga =
            Json
                .parseToJsonElement(
                    """
                    {"id":2,"name":"Berserk","russian":"Берсерк","image":{"original":"/system/mangas/original/2.jpg"},
                     "kind":"manga","status":"ongoing","english":["Berserk"],"japanese":["ベルセルク"],"synonyms":[],
                     "description_html":"<div class=\"b-text_with_paragraphs\">Guts.<br>Griffith.</div>",
                     "genres":[{"id":1,"name":"Action"},{"id":2,"name":"Seinen"}]}
                    """.trimIndent(),
                ).jsonObject
        val roles =
            Json.parseToJsonElement(
                """[{"roles":["Story & Art"],"roles_russian":[],"person":{"id":1,"name":"Kentaro Miura"}},
                    {"roles":[],"person":null,"character":{"id":5,"name":"Guts"}}]""",
            ) as JsonArray
        val m = ShikimoriMetadata.parse(manga, roles)
        assertEquals("Berserk", m.title)
        assertEquals("https://shikimori.one/system/mangas/original/2.jpg", m.coverUrl)
        assertEquals("Kentaro Miura", m.authors)
        assertEquals("Kentaro Miura", m.artists)
        assertEquals(SManga.ONGOING, m.status)
        assertEquals("Guts.\nGriffith.", m.description)
        assertTrue("Берсерк" in m.alternativeTitles!!)
    }
    // endregion

    @Test
    fun `missing fields never crash a parser`() {
        val empty = JsonObject(emptyMap())
        listOf(
            AniListMetadata.parse(empty),
            MangaUpdatesMetadata.parse(empty),
            KitsuMetadata.parse(empty),
            MyAnimeListMetadata.parse(empty),
            ShikimoriMetadata.parse(empty),
            BangumiMetadata.parse(empty),
        ).forEach {
            assertNull(it.title)
            assertNull(it.tags)
            assertNull(it.authors)
        }
    }
}
