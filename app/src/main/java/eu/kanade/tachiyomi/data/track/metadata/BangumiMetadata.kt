package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Turns the response of `GET /v0/subjects/{id}` of Bangumi into [TrackMangaMetadata]. */
object BangumiMetadata {
    private val authorKeys = setOf("作者", "原作", "脚本", "原著")
    private val artistKeys = setOf("作画", "插图", "插画", "作画/原作")
    private const val MAX_TAGS = 20

    fun parse(
        subject: JsonObject,
        options: MetadataOptions = MetadataOptions(),
    ): TrackMangaMetadata {
        val native = subject.text("name")
        val chinese = subject.text("name_cn")
        val title =
            if (options.titleStyle == MetadataOptions.TITLE_NATIVE) {
                native ?: chinese
            } else {
                chinese ?: native
            }

        val authors = mutableListOf<String>()
        val artists = mutableListOf<String>()
        subject.array("infobox").forEach { entry ->
            val key = entry.obj().text("key") ?: return@forEach
            val values = infoboxValues(entry.obj()?.get("value"))
            when (key) {
                in authorKeys -> authors += values
                in artistKeys -> artists += values
            }
        }

        val images = subject.obj("images")

        return TrackMangaMetadata(
            remoteId = subject["id"].text()?.toLongOrNull(),
            title = title,
            coverUrl = images.text("large") ?: images.text("common"),
            description = MetadataText.cleanDescription(subject.text("summary")),
            authors = MetadataText.names(*authors.toTypedArray()),
            artists = MetadataText.names(*artists.toTypedArray()),
            // Tags are what users call it; the first one is usually just the media type ("漫画")
            tags =
                MetadataText
                    .tags(
                        subject
                            .array("tags")
                            .mapNotNull { it.obj() }
                            .sortedByDescending { it["count"].int() ?: 0 }
                            .mapNotNull { it.text("name") }
                            .filterNot { it == "漫画" },
                    )?.take(MAX_TAGS),
            alternativeTitles = MetadataText.alternativeTitles(title, listOf(native, chinese)),
        )
    }

    /** A value of the infobox is a string or a list of `{ "v": ... }`. */
    private fun infoboxValues(value: JsonElement?): List<String> =
        value.text()?.let { listOf(it) }
            ?: value.array().mapNotNull { it.obj().text("v") }
}
