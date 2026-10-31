package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import eu.kanade.tachiyomi.data.library.CategoryUpdateSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCategorySyncTest {
    private fun cat(
        id: Int,
        parent: Int = 0,
        status: Int = 0,
    ): Category =
        CategoryImpl().apply {
            this.id = id
            name = "c$id"
            parentId = parent
            trackerStatus = status
        }

    // 1 Shounen, 2 Shounen/Reading, 3 Shounen/Completed, 4 Josei, 5 Josei/Reading, 6 Josei/Completed
    private val tree =
        listOf(
            cat(1),
            cat(2, 1, SmartCategorySync.STATUS_READING),
            cat(3, 1, SmartCategorySync.STATUS_COMPLETED),
            cat(4),
            cat(5, 4, SmartCategorySync.STATUS_READING),
            cat(6, 4, SmartCategorySync.STATUS_COMPLETED),
        )

    @Test
    fun `child only membership reaches its branch and leaves other branches alone`() {
        val result = SmartCategorySync.apply(setOf(2), tree, SmartCategorySync.STATUS_COMPLETED)
        assertEquals(setOf(3), result)
    }

    @Test
    fun `manga in both branches updates both`() {
        val result = SmartCategorySync.apply(setOf(1, 4), tree, SmartCategorySync.STATUS_READING)
        assertEquals(setOf(1, 4, 2, 5), result)
    }

    @Test
    fun `ineligible branch is not changed`() {
        val result = SmartCategorySync.apply(setOf(5), tree, SmartCategorySync.STATUS_COMPLETED)
        assertEquals(setOf(6), result)
    }

    @Test
    fun `top level bound category is global and deeper nesting is transitive`() {
        val deep = listOf(cat(1), cat(2, 1), cat(3, 2, SmartCategorySync.STATUS_READING), cat(9, 0, SmartCategorySync.STATUS_READING))
        assertEquals(setOf(2, 3, 9), SmartCategorySync.apply(setOf(2), deep, SmartCategorySync.STATUS_READING))
        assertEquals(setOf(1, 9), SmartCategorySync.apply(setOf(1), deep, SmartCategorySync.STATUS_READING))
    }

    @Test
    fun `update schedule is due after the interval`() {
        val c =
            cat(1).apply {
                updateInterval = 6
                updateLastRun = 0
            }
        assertTrue(CategoryUpdateSchedule.isDue(c, 6 * 3600_000L))
        c.updateLastRun = 1_000
        assertFalse(CategoryUpdateSchedule.isDue(c, 5 * 3600_000L))
        c.updateInterval = 0
        assertTrue(CategoryUpdateSchedule.isDue(c, 0))
    }
}
