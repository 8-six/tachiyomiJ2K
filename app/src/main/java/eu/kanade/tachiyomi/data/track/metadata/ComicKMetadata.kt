package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.comick.ComicKApi
import eu.kanade.tachiyomi.data.track.comick.asHidToLong
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.CoverCandidate
import kotlinx.serialization.json.JsonObject

/**
 * Turns the response of `GET /v1.0/comic/{hid}/` of ComicK's public catalog into [TrackMangaMetadata].
 *
 * The covers are the reason to use ComicK: they are listed with their size, and are often bigger than the ones of
 * other trackers.
 */
object ComicKMetadata {
    /** Categories are voted on by users, only the ones several people agree on are kept. */
    private const val MIN_CATEGORY_SCORE = 3

    fun parse(
        response: JsonObject,
        options: MetadataOptions = MetadataOptions(),
        trackerId: Int = 0,
    ): TrackMangaMetadata {
        val comic = response.obj("comic")
        val titles = comic.array("md_titles").mapNotNull { it.obj() }

        // Of the titles in a language the one ComicK marks as the default is the real one, the others are aliases
        fun defaultTitle(languageMatches: (String) -> Boolean) =
            titles
                .filter { languageMatches(it.text("lang").orEmpty()) }
                .let { inLanguage -> inLanguage.firstOrNull { it["is_default"].bool() } ?: inLanguage.firstOrNull() }
                .text("title")

        val english = defaultTitle { it == "en" }
        val romaji = defaultTitle { it.endsWith("-ro") } ?: comic.text("title")
        val original = comic.text("iso639_1")
        val native = original?.let { language -> defaultTitle { it == language } }
        val title = MetadataText.pickTitle(options.titleStyle, english, romaji, native)

        val genres = comic.array("md_comic_md_genres").map { it.obj().obj("md_genres").text("name") }
        val categories =
            comic
                .array("mu_comic_categories")
                .mapNotNull { it.obj() }
                .filter { (it["score"].int() ?: 0) >= MIN_CATEGORY_SCORE }
                .sortedByDescending { it["score"].int() ?: 0 }
                .map { it.obj("mu_categories").text("title") }

        val covers = covers(comic.array("md_covers").mapNotNull { it.obj() }, options.comickLatestCover, trackerId)

        return TrackMangaMetadata(
            remoteId = comic.text("hid")?.takeIf { it.length == 8 }?.asHidToLong(),
            title = title,
            coverUrl = covers.firstOrNull()?.url,
            covers = covers,
            description = MetadataText.cleanDescription(comic.text("desc")),
            authors = MetadataText.names(*response.array("authors").map { it.obj().text("name") }.toTypedArray()),
            artists = MetadataText.names(*response.array("artists").map { it.obj().text("name") }.toTypedArray()),
            tags = MetadataText.tags(genres + response.text("demographic") + categories),
            status = status(comic?.get("status").int()),
            alternativeTitles = MetadataText.alternativeTitles(title, titles.map { it.text("title") } + comic.text("title")),
            seriesType = TrackMangaMetadata.SeriesType.from(comic.text("country")),
        )
    }

    /**
     * The cover to use first: the cover of the series, or the one of the latest volume. A volume cover is offered next
     * to it when it's wanted, so the better of the two can win.
     */
    private fun covers(
        covers: List<JsonObject>,
        latest: Boolean,
        trackerId: Int,
    ): List<CoverCandidate> {
        fun JsonObject.toCandidate() =
            text("b2key")?.let { key ->
                CoverCandidate("${ComicKApi.COVER_URL}/$key", this["w"].int(), this["h"].int(), trackerId)
            }

        val primary = covers.firstOrNull { it["is_primary"].bool() } ?: covers.firstOrNull()
        val newest = covers.filter { it.text("vol")?.toFloatOrNull() != null }.maxByOrNull { it.text("vol")!!.toFloat() }
        return (if (latest) listOf(newest, primary) else listOf(primary))
            .mapNotNull { it?.toCandidate() }
            .distinctBy { it.url }
    }

    /** ComicK: 1 ongoing, 2 completed, 3 cancelled, 4 hiatus. */
    private fun status(status: Int?): Int? =
        when (status) {
            1 -> MetadataText.status("ongoing")
            2 -> MetadataText.status("completed")
            3 -> MetadataText.status("cancelled")
            4 -> MetadataText.status("hiatus")
            else -> null
        }
}
