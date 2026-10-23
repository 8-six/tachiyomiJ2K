package eu.kanade.tachiyomi.data.merge

/**
 * Links a merged manga (a manga of the [eu.kanade.tachiyomi.source.online.all.MergedSource]) to one of the manga
 * it was made from.
 */
data class MergedMangaReference(
    val id: Long? = null,
    /** Id of the merged manga. */
    val mergeId: Long,
    /** Id of the manga that is part of the merge. */
    val mangaId: Long,
    /** Lower comes first: when two manga have the same chapter, the one with the lowest priority provides it. */
    val priority: Int,
    /** Whether the details (cover, description, ...) of the merged manga follow this manga. */
    val isInfoManga: Boolean = false,
    /** Whether this manga is checked for new chapters. */
    val getChapterUpdates: Boolean = true,
)
