package eu.kanade.tachiyomi.data.database

import eu.kanade.tachiyomi.data.database.queries.getAllRecentsType
import eu.kanade.tachiyomi.data.database.queries.getCategoriesForMangaQuery
import eu.kanade.tachiyomi.data.database.queries.getCategoriesQuery
import eu.kanade.tachiyomi.data.database.queries.getHistoryPerPeriodQuery
import eu.kanade.tachiyomi.data.database.queries.getRecentHistoryUngrouped
import eu.kanade.tachiyomi.data.database.queries.getRecentMangasLimitQuery
import eu.kanade.tachiyomi.data.database.queries.getRecentsQuery
import eu.kanade.tachiyomi.data.database.queries.libraryQuery
import eu.kanade.tachiyomi.data.database.tables.CategoryTable
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.data.database.tables.HistoryTable
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.database.tables.MergedMangaTable
import eu.kanade.tachiyomi.data.database.tables.TrackTable
import org.junit.Test
import java.io.File

/**
 * Writes the app's real schema, migration and query SQL to a directory so they can be run against
 * sqlite3 (set -Dsql.dump.dir). Does nothing without the property.
 */
class SqlDumpTest {
    @Test
    fun dump() {
        val dir = System.getenv("SQL_DUMP_DIR")?.let(::File) ?: return
        dir.mkdirs()
        File(dir, "create.sql").writeText(
            listOf(
                MangaTable.createTableQuery,
                ChapterTable.createTableQuery,
                TrackTable.createTableQuery,
                CategoryTable.createTableQuery,
                MangaCategoryTable.createTableQuery,
                HistoryTable.createTableQuery,
                MergedMangaTable.createTableQuery,
                MangaTable.createUrlIndexQuery,
                MangaTable.createLibraryIndexQuery,
                MangaTable.createSourceIndexQuery,
                ChapterTable.createMangaIdIndexQuery,
                ChapterTable.createUnreadChaptersIndexQuery,
                ChapterTable.createUrlIndexQuery,
                ChapterTable.createDateFetchIndexQuery,
                ChapterTable.createReadByScanlatorIndexQuery,
                ChapterTable.createBookmarkedIndexQuery,
                MangaCategoryTable.createMangaIdIndexQuery,
                MangaCategoryTable.createCategoryIdIndexQuery,
                HistoryTable.createLastReadIndexQuery,
                MergedMangaTable.createMangaIdIndexQuery,
            ).joinToString(";\n", postfix = ";\n"),
        )
        File(dir, "migrate_22_28.sql").writeText(
            listOf(
                ChapterTable.addBookmarkColor,
                ChapterTable.addBlacklisted,
                MangaTable.addScanlatorPriority,
                MangaTable.addSmartMergeMode,
                CategoryTable.addParentId,
                CategoryTable.addTrackerStatus,
                CategoryTable.addUpdateInterval,
                CategoryTable.addUpdateLastRun,
                MangaTable.addPreferredTracker,
            ).joinToString(";\n", postfix = ";\n"),
        )
        val queries =
            mapOf(
                "library" to libraryQuery,
                "categories" to getCategoriesQuery(),
                "categories_for_manga" to getCategoriesForMangaQuery().replace("?", "1"),
                "recents" to getRecentsQuery("", 0, false),
                "recent_history" to getRecentHistoryUngrouped("", 0, false),
                "recent_limit" to getRecentMangasLimitQuery("", 0, false),
                "history_period" to getHistoryPerPeriodQuery(0, Long.MAX_VALUE),
                "all_recents" to getAllRecentsType("", true, false, 0, false, 25),
                "all_recents_endless" to getAllRecentsType("", false, true, 0, false, 25),
            )
        queries.forEach { (name, sql) -> File(dir, "q_$name.sql").writeText(sql.trim().trimEnd(';') + ";\n") }
    }
}
