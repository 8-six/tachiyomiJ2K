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
    /** Other names of the series, for the user to find it by. */
    val alternativeTitles: List<String>? = null,
    /** Whether it's a manga, manhwa, ... The reader picks its mode from it. */
    val seriesType: SeriesType? = null,
    /** Things that went wrong without making the rest unusable, like covers that couldn't be loaded. */
    val warnings: List<String> = emptyList(),
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

    /** Whether [field] has something in it. */
    fun has(field: Field): Boolean =
        when (field) {
            Field.TITLE -> !title.isNullOrBlank()
            Field.AUTHORS -> !authors.isNullOrBlank()
            Field.ARTISTS -> !artists.isNullOrBlank()
            Field.DESCRIPTION -> !description.isNullOrBlank()
            Field.COVER -> !coverUrl.isNullOrBlank()
            Field.TAGS -> !tags.isNullOrEmpty()
            Field.STATUS -> status != null
        }

    /**
     * This metadata with everything it lacks filled in from [other]. Tags aren't mixed: services name things
     * differently, so the tags of the first one that has any are kept.
     */
    fun fillGapsFrom(other: TrackMangaMetadata): TrackMangaMetadata =
        copy(
            remoteId = remoteId ?: other.remoteId,
            title = title.takeUnless { it.isNullOrBlank() } ?: other.title,
            coverUrl = coverUrl.takeUnless { it.isNullOrBlank() } ?: other.coverUrl,
            description = description.takeUnless { it.isNullOrBlank() } ?: other.description,
            authors = authors.takeUnless { it.isNullOrBlank() } ?: other.authors,
            artists = artists.takeUnless { it.isNullOrBlank() } ?: other.artists,
            tags = tags.takeUnless { it.isNullOrEmpty() } ?: other.tags,
            status = status ?: other.status,
            alternativeTitles =
                (
                    (alternativeTitles.orEmpty() + other.alternativeTitles.orEmpty()).distinctBy {
                        it.lowercase()
                    }
                ).ifEmpty { null },
            seriesType = seriesType ?: other.seriesType,
            warnings = warnings + other.warnings,
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

    /** The kinds of series the reader knows about. [tag] is the tag that makes the app treat a series as this kind. */
    enum class SeriesType(
        val tag: String,
    ) {
        MANGA("Manga"),
        MANHWA("Manhwa"),
        MANHUA("Manhua"),
        COMIC("Comic"),
        WEBTOON("Webtoon"),
        ;

        companion object {
            /** Maps what trackers call it: a type like "manhwa", or a country code like "KR". Null if it doesn't say. */
            fun from(value: String?): SeriesType? =
                when (value?.trim()?.lowercase()) {
                    "manga", "jp", "ja", "japan", "doujinshi", "doujin", "one_shot", "one-shot", "oneshot" -> MANGA
                    "manhwa", "kr", "ko", "korea", "south korea" -> MANHWA
                    "manhua", "cn", "zh", "tw", "hk", "china", "taiwan", "hong kong" -> MANHUA
                    "oel", "comic", "comics", "western" -> COMIC
                    "webtoon" -> WEBTOON
                    else -> null
                }
        }
    }
}
