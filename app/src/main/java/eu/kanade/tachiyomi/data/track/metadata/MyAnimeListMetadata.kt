package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import kotlinx.serialization.json.JsonObject

/**
 * Turns the response of `GET /v2/manga/{id}` of MyAnimeList into [TrackMangaMetadata]. The fields it needs are listed in
 * [FIELDS].
 */
object MyAnimeListMetadata {
    const val FIELDS = "id,title,alternative_titles,synopsis,main_picture,status,media_type,genres,authors{first_name,last_name}"

    fun parse(
        manga: JsonObject,
        options: MetadataOptions = MetadataOptions(),
    ): TrackMangaMetadata {
        val alternatives = manga.obj("alternative_titles")
        val romaji = manga.text("title")
        val english = alternatives.text("en")
        val native = alternatives.text("ja")
        val title = MetadataText.pickTitle(options.titleStyle, english, romaji, native)

        val credits = MetadataText.Credits()
        manga.array("authors").forEach { entry ->
            val person = entry.obj().obj("node")
            val name = MetadataText.names(listOfNotNull(person.text("first_name"), person.text("last_name")).joinToString(" "))
            credits.add(name, entry.obj().text("role"))
        }

        val picture = manga.obj("main_picture")

        return TrackMangaMetadata(
            remoteId = manga["id"].text()?.toLongOrNull(),
            title = title,
            coverUrl = picture.text("large") ?: picture.text("medium"),
            description = MetadataText.cleanDescription(manga.text("synopsis")),
            authors = credits.authors(),
            artists = credits.artists(),
            tags = MetadataText.tags(manga.array("genres").map { it.obj().text("name") }),
            status = MetadataText.status(manga.text("status")),
            alternativeTitles =
                MetadataText.alternativeTitles(
                    title,
                    listOf(english, romaji, native) + alternatives.array("synonyms").map { it.text() },
                ),
            seriesType = TrackMangaMetadata.SeriesType.from(manga.text("media_type")),
        )
    }
}
