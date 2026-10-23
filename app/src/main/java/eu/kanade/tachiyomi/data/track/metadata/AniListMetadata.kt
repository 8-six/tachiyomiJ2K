package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import kotlinx.serialization.json.JsonObject

/** Turns the `Media` object of AniList's GraphQL API (see `AnilistApi.metadataQuery`) into [TrackMangaMetadata]. */
object AniListMetadata {
    fun parse(
        media: JsonObject,
        options: MetadataOptions = MetadataOptions(),
    ): TrackMangaMetadata {
        val titles = media.obj("title")
        val english = titles.text("english")
        val romaji = titles.text("romaji")
        val native = titles.text("native")
        val title = MetadataText.pickTitle(options.titleStyle, english, romaji, native)

        val credits = MetadataText.Credits()
        media.obj("staff").array("edges").forEach { edge ->
            credits.add(
                edge
                    .obj()
                    .obj("node")
                    .obj("name")
                    .text("full"),
                edge.obj().text("role"),
            )
        }

        val genres = media.array("genres").map { it.text() }
        val tags =
            media
                .array("tags")
                .mapNotNull { it.obj() }
                .filter { !options.hideSpoilerTags || !(it["isMediaSpoiler"].bool() || it["isGeneralSpoiler"].bool()) }
                .filter { (it["rank"].int() ?: 100) >= options.minTagRank }
                .sortedByDescending { it["rank"].int() ?: 0 }
                .map { it.text("name") }

        val cover = media.obj("coverImage")

        return TrackMangaMetadata(
            remoteId = media["id"].text()?.toLongOrNull(),
            title = title,
            coverUrl = cover.text("extraLarge") ?: cover.text("large"),
            description = MetadataText.cleanDescription(media.text("description")),
            authors = credits.authors(),
            artists = credits.artists(),
            tags = MetadataText.tags(genres + tags),
            status = MetadataText.status(media.text("status")),
            alternativeTitles =
                MetadataText.alternativeTitles(
                    title,
                    listOf(english, romaji, native) + media.array("synonyms").map { it.text() },
                ),
            seriesType = TrackMangaMetadata.SeriesType.from(media.text("countryOfOrigin")),
        )
    }
}
