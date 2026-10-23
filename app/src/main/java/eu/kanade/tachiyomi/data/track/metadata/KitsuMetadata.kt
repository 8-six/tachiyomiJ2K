package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import kotlinx.serialization.json.JsonObject

/**
 * Turns the response of `GET /manga/{id}?include=categories,mangaStaff.person` of Kitsu into [TrackMangaMetadata].
 */
object KitsuMetadata {
    fun parse(
        response: JsonObject,
        options: MetadataOptions = MetadataOptions(),
    ): TrackMangaMetadata {
        val data = response.obj("data")
        val attributes = data.obj("attributes")
        val titles = attributes.obj("titles")

        val included = response.array("included").mapNotNull { it.obj() }

        fun includedOf(type: String) = included.filter { it.text("type") == type }

        val english = titles.text("en") ?: titles.text("en_us")
        val romaji = titles.text("en_jp") ?: titles.text("en_kr") ?: titles.text("en_cn") ?: attributes.text("canonicalTitle")
        val native = titles.text("ja_jp") ?: titles.text("ko_kr") ?: titles.text("zh_cn")
        val title = MetadataText.pickTitle(options.titleStyle, english, romaji, native) ?: attributes.text("canonicalTitle")

        val people = includedOf("people").associate { it.text("id") to it.obj("attributes").text("name") }
        val credits = MetadataText.Credits()
        includedOf("mangaStaff").forEach { staff ->
            val personId =
                staff
                    .obj("relationships")
                    .obj("person")
                    .obj("data")
                    .text("id")
            credits.add(people[personId], staff.obj("attributes").text("role"))
        }

        val poster = attributes.obj("posterImage")

        return TrackMangaMetadata(
            remoteId = data.text("id")?.toLongOrNull(),
            title = title,
            coverUrl = poster.text("original") ?: poster.text("large"),
            description = MetadataText.cleanDescription(attributes.text("synopsis")),
            authors = credits.authors(),
            artists = credits.artists(),
            tags = MetadataText.tags(includedOf("categories").map { it.obj("attributes").text("title") }),
            status = MetadataText.status(attributes.text("status")),
            alternativeTitles =
                MetadataText.alternativeTitles(
                    title,
                    listOf(english, romaji, native) + attributes.array("abbreviatedTitles").map { it.text() },
                ),
            seriesType = TrackMangaMetadata.SeriesType.from(attributes.text("subtype") ?: attributes.text("mangaType")),
        )
    }
}
