package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Turns the responses of `GET /api/mangas/{id}` and `GET /api/mangas/{id}/roles` of Shikimori into [TrackMangaMetadata].
 */
object ShikimoriMetadata {
    fun parse(
        manga: JsonObject,
        roles: JsonArray = JsonArray(emptyList()),
        baseUrl: String = "https://shikimori.one",
    ): TrackMangaMetadata {
        val romaji = manga.text("name")
        val english = manga.array("english").firstNotNullOfOrNull { it.text() }
        val native = manga.array("japanese").firstNotNullOfOrNull { it.text() }
        val title = MetadataText.pickTitle(MetadataOptions.TITLE_AUTO, english, romaji, native)

        val credits = MetadataText.Credits()
        roles.forEach { entry ->
            val person = entry.obj().obj("person") ?: return@forEach
            val name = person.text("name") ?: return@forEach
            entry.obj().array("roles").forEach { role -> credits.add(name, role.text()) }
        }

        val image = manga.obj("image").text("original")

        return TrackMangaMetadata(
            remoteId = manga["id"].text()?.toLongOrNull(),
            title = title,
            coverUrl = image?.let { if (it.startsWith("http")) it else baseUrl + it },
            description = MetadataText.cleanDescription(manga.text("description_html") ?: manga.text("description")),
            authors = credits.authors(),
            artists = credits.artists(),
            tags = MetadataText.tags(manga.array("genres").map { it.obj().text("name") }),
            status = MetadataText.status(manga.text("status")),
            alternativeTitles =
                MetadataText.alternativeTitles(
                    title,
                    listOf(english, romaji, native, manga.text("russian")) + manga.array("synonyms").map { it.text() },
                ),
            seriesType = TrackMangaMetadata.SeriesType.from(manga.text("kind")),
        )
    }
}
