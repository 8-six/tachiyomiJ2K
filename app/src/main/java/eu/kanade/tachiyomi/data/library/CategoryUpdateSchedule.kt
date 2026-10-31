package eu.kanade.tachiyomi.data.library

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category

// KMK -->

/** Per-category update intervals for scheduled library updates. */
object CategoryUpdateSchedule {
    private const val HOUR_MS = 60L * 60 * 1000

    fun isDue(
        category: Category,
        now: Long,
    ): Boolean = category.updateInterval <= 0 || now - category.updateLastRun >= category.updateInterval * HOUR_MS

    /**
     * Ids of the categories (including the default one, 0) that take part in an automatic update at [now].
     * Categories with an interval that came due are stamped so they are not selected again too early.
     */
    fun dueCategoryIds(
        db: DatabaseHelper,
        now: Long,
    ): Set<Int> {
        val categories = db.getCategories().executeAsBlocking().filter { (it.id ?: 0) > 0 }
        val due = categories.filter { isDue(it, now) }
        due.filter { it.updateInterval > 0 }.forEach {
            it.updateLastRun = now
            db.insertCategory(it).executeAsBlocking()
        }
        return due.mapNotNull { it.id }.toSet() + 0
    }
}
// KMK <--
