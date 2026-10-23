package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonObject

/** Turns the response of `GET /v1/series/{id}` of MangaUpdates into [TrackMangaMetadata]. */
object MangaUpdatesMetadata {
    /** Categories are submitted and voted on by users, only the ones several people agree on are kept. */
    private const val MIN_CATEGORY_VOTES = 3

    private val statusInText = Regex("""\((ongoing|complete|completed|hiatus|cancelled|discontinued)\)""", RegexOption.IGNORE_CASE)

    fun parse(series: JsonObject): TrackMangaMetadata {
        val title = series.text("title")

        val credits = MetadataText.Credits()
        series.array("authors").forEach { author ->
            val type = author.obj().text("type")
            credits.add(author.obj().text("name"), type)
        }

        val genres = series.array("genres").map { it.obj().text("genre") }
        val categories =
            series
                .array("categories")
                .mapNotNull { it.obj() }
                .filter { (it["votes"].int() ?: 0) >= MIN_CATEGORY_VOTES }
                .sortedByDescending { it["votes"].int() ?: 0 }
                .map { it.text("category") }

        return TrackMangaMetadata(
            remoteId = series["series_id"].text()?.toLongOrNull(),
            title = title,
            coverUrl = series.obj("image").obj("url").text("original"),
            description = MetadataText.cleanDescription(series.text("description")),
            authors = credits.authors(),
            artists = credits.artists(),
            tags = MetadataText.tags(genres + categories),
            status = status(series),
            alternativeTitles = MetadataText.alternativeTitles(title, series.array("associated").map { it.obj().text("title") }),
            seriesType = TrackMangaMetadata.SeriesType.from(series.text("type")),
        )
    }

    private fun status(series: JsonObject): Int? {
        if (series["completed"].bool()) return SManga.COMPLETED
        val label =
            series.text("status")?.let {
                statusInText
                    .findAll(it)
                    .lastOrNull()
                    ?.groupValues
                    ?.get(1)
            }
        return MetadataText.status(label)
    }
}
