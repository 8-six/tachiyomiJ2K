package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

// KMK -->

/**
 * Keeps categories that are bound to a tracker status (Smart Categories) in sync with a manga's tracking state.
 *
 * A bound subcategory is only touched when its parent is a category the manga already belongs to, or an
 * ancestor of one, so a manga filed under `Shounen` is never pulled into `Josei / Reading`. Bound top level
 * categories have no branch to scope them and always take part. Categories that are not eligible are left alone.
 */
object SmartCategorySync {
    const val STATUS_NONE = 0
    const val STATUS_READING = 1
    const val STATUS_COMPLETED = 2
    const val STATUS_PLANNING = 3
    const val STATUS_ON_HOLD = 4
    const val STATUS_DROPPED = 5

    private val trackManager: TrackManager by injectLazy()

    /** Normalized status for a manga's tracks: completed wins over reading, which wins over planning. */
    fun statusOf(tracks: List<eu.kanade.tachiyomi.data.database.models.Track>): Int {
        val buckets =
            tracks.mapNotNull { track ->
                val service = trackManager.getService(track.sync_id) ?: return@mapNotNull null
                when (track.status) {
                    service.completedStatus() -> STATUS_COMPLETED
                    service.readingStatus() -> STATUS_READING
                    service.onHoldStatus() -> STATUS_ON_HOLD
                    service.droppedStatus() -> STATUS_DROPPED
                    service.planningStatus() -> STATUS_PLANNING
                    else -> null
                }
            }
        return listOf(STATUS_COMPLETED, STATUS_READING, STATUS_ON_HOLD, STATUS_DROPPED, STATUS_PLANNING).firstOrNull { it in buckets }
            ?: STATUS_NONE
    }

    /** Ids of [categories] that [membership] makes reachable: the categories themselves plus every ancestor. */
    fun reachable(
        membership: Set<Int>,
        categories: List<Category>,
    ): Set<Int> {
        val byId = categories.mapNotNull { c -> c.id?.let { it to c } }.toMap()
        val result = mutableSetOf<Int>()
        membership.forEach { start ->
            var current: Int? = start
            while (current != null && current != 0 && result.add(current)) {
                current = byId[current]?.parentId
            }
        }
        return result
    }

    /** Pure rule: the membership after applying [status] to every eligible bound category. */
    fun apply(
        membership: Set<Int>,
        categories: List<Category>,
        status: Int,
    ): Set<Int> {
        val reachable = reachable(membership, categories)
        val result = membership.toMutableSet()
        categories
            .filter { it.trackerStatus != STATUS_NONE }
            .forEach { category ->
                val id = category.id ?: return@forEach
                val eligible = category.parentId == 0 || category.parentId in reachable
                if (!eligible) return@forEach
                if (category.trackerStatus == status) result.add(id) else result.remove(id)
            }
        return result
    }

    fun sync(
        db: DatabaseHelper,
        mangaId: Long?,
    ) {
        if (mangaId == null) return
        try {
            val categories = db.getCategories().executeAsBlocking().filter { (it.id ?: 0) > 0 }
            if (categories.none { it.trackerStatus != STATUS_NONE }) return
            val tracks = db.getTracks(mangaId).executeAsBlocking()
            if (tracks.isEmpty()) return
            val manga = db.getManga(mangaId).executeAsBlocking() ?: return
            val current =
                db
                    .getCategoriesForManga(manga)
                    .executeAsBlocking()
                    .mapNotNull { it.id }
                    .toSet()
            val updated = apply(current, categories, statusOf(tracks))
            if (updated == current) return
            val mangaCategories = categories.filter { it.id in updated }.map { MangaCategory.create(manga, it) }
            db.setMangaCategories(mangaCategories, listOf(manga))
        } catch (e: Exception) {
            Timber.w(e, "Smart category sync failed")
        }
    }
}
// KMK <--
