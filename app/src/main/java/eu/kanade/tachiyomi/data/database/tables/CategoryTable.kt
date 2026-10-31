package eu.kanade.tachiyomi.data.database.tables

object CategoryTable {
    const val TABLE = "categories"

    const val COL_ID = "_id"

    const val COL_NAME = "name"

    const val COL_ORDER = "sort"

    const val COL_FLAGS = "flags"

    const val COL_MANGA_ORDER = "manga_order"

    // KMK -->
    const val COL_PARENT_ID = "parent_id"

    const val COL_TRACKER_STATUS = "tracker_status"

    const val COL_UPDATE_INTERVAL = "update_interval"

    const val COL_UPDATE_LAST_RUN = "update_last_run"
    // KMK <--

    val createTableQuery: String
        get() =
            """CREATE TABLE $TABLE(
            $COL_ID INTEGER NOT NULL PRIMARY KEY,
            $COL_NAME TEXT NOT NULL,
            $COL_ORDER INTEGER NOT NULL,
            $COL_FLAGS INTEGER NOT NULL,
            $COL_MANGA_ORDER TEXT NOT NULL,
            $COL_PARENT_ID INTEGER NOT NULL DEFAULT 0,
            $COL_TRACKER_STATUS INTEGER NOT NULL DEFAULT 0,
            $COL_UPDATE_INTERVAL INTEGER NOT NULL DEFAULT 0,
            $COL_UPDATE_LAST_RUN INTEGER NOT NULL DEFAULT 0
            )"""

    // KMK -->
    val addParentId: String
        get() = "ALTER TABLE $TABLE ADD COLUMN $COL_PARENT_ID INTEGER NOT NULL DEFAULT 0"
    // KMK <--

    // KMK -->
    val addTrackerStatus: String
        get() = "ALTER TABLE $TABLE ADD COLUMN $COL_TRACKER_STATUS INTEGER NOT NULL DEFAULT 0"

    val addUpdateInterval: String
        get() = "ALTER TABLE $TABLE ADD COLUMN $COL_UPDATE_INTERVAL INTEGER NOT NULL DEFAULT 0"

    val addUpdateLastRun: String
        get() = "ALTER TABLE $TABLE ADD COLUMN $COL_UPDATE_LAST_RUN INTEGER NOT NULL DEFAULT 0"
    // KMK <--

    val addMangaOrder: String
        get() = "ALTER TABLE $TABLE ADD COLUMN $COL_MANGA_ORDER TEXT"
}
