package eu.kanade.tachiyomi.data.track.model

/**
 * Series information a tracker can provide, used to fill in or sync a manga's details. Any field the tracker
 * doesn't know, or the user chose not to sync, is null.
 */
data class TrackMangaMetadata(
    val remoteId: Long? = null,
    val title: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val authors: String? = null,
    val artists: String? = null,
    val tags: List<String>? = null,
    /** One of the SManga status constants. */
    val status: Int? = null,
) {
    /** The fields that are allowed to be synced, see [Field]. */
    fun filter(fields: Set<Field>) =
        copy(
            title = title.takeIf { Field.TITLE in fields },
            coverUrl = coverUrl.takeIf { Field.COVER in fields },
            description = description.takeIf { Field.DESCRIPTION in fields },
            authors = authors.takeIf { Field.AUTHORS in fields },
            artists = artists.takeIf { Field.ARTISTS in fields },
            tags = tags.takeIf { Field.TAGS in fields },
            status = status.takeIf { Field.STATUS in fields },
        )

    enum class Field(
        val key: String,
    ) {
        TITLE("title"),
        AUTHORS("authors"),
        ARTISTS("artists"),
        DESCRIPTION("description"),
        COVER("cover"),
        TAGS("tags"),
        STATUS("status"),
        ;

        companion object {
            /** An empty selection means every field, same as the other multi select settings. */
            fun fromKeys(keys: Set<String>): Set<Field> = if (keys.isEmpty()) entries.toSet() else entries.filter { it.key in keys }.toSet()
        }
    }
}
