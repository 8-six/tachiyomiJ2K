package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.SeriesType

/** How details from trackers are combined with what a manga already has. */
object MetadataApply {
    const val TAGS_REPLACE = "replace"
    const val TAGS_ADD = "add"

    private const val MAX_ALTERNATIVE_TITLES = 8

    /**
     * The tags a manga should have after syncing, or null if they should stay as they are.
     *
     * @param existing the tags it has now.
     * @param incoming the tags of the tracker.
     * @param mode [TAGS_REPLACE] drops [existing], [TAGS_ADD] keeps them and adds the new ones.
     * @param addSeriesTypeTag also tag it as manga, manhwa, ..., which is what the reader goes by. Not added if it already is.
     */
    fun mergeTags(
        existing: List<String>,
        incoming: List<String>?,
        seriesType: SeriesType?,
        mode: String,
        addSeriesTypeTag: Boolean,
    ): List<String>? {
        val typeTag = seriesType?.takeIf { addSeriesTypeTag }?.tag
        if (incoming.isNullOrEmpty() && (typeTag == null || mode != TAGS_ADD)) return null

        val combined = if (mode == TAGS_ADD) existing + incoming.orEmpty() else incoming.orEmpty()
        val result =
            combined
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .toMutableList()

        if (typeTag != null && result.none { tag -> SeriesType.entries.any { it.tag.equals(tag, ignoreCase = true) } }) {
            result += typeTag
        }
        return result
    }

    /** The description with the other names of the series under it, if that's wanted and there is a description. */
    fun description(
        description: String?,
        alternativeTitles: List<String>?,
        appendAlternativeTitles: Boolean,
    ): String? {
        if (description.isNullOrBlank() || !appendAlternativeTitles || alternativeTitles.isNullOrEmpty()) return description
        return description.trimEnd() + "\n\nAlternative titles: " + alternativeTitles.take(MAX_ALTERNATIVE_TITLES).joinToString("; ")
    }
}
