package eu.kanade.tachiyomi.data.database.tables

object MergedMangaTable {
    const val TABLE = "merged_manga"

    const val COL_ID = "_id"

    const val COL_MERGE_ID = "merge_id"

    const val COL_MANGA_ID = "manga_id"

    const val COL_PRIORITY = "priority"

    const val COL_IS_INFO = "is_info_manga"

    const val COL_GET_CHAPTER_UPDATES = "get_chapter_updates"

    val createTableQuery: String
        get() =
            """CREATE TABLE $TABLE(
            $COL_ID INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            $COL_MERGE_ID INTEGER NOT NULL,
            $COL_MANGA_ID INTEGER NOT NULL,
            $COL_PRIORITY INTEGER NOT NULL,
            $COL_IS_INFO INTEGER NOT NULL,
            $COL_GET_CHAPTER_UPDATES INTEGER NOT NULL,
            UNIQUE ($COL_MERGE_ID, $COL_MANGA_ID),
            FOREIGN KEY($COL_MERGE_ID) REFERENCES ${MangaTable.TABLE} (${MangaTable.COL_ID})
            ON DELETE CASCADE,
            FOREIGN KEY($COL_MANGA_ID) REFERENCES ${MangaTable.TABLE} (${MangaTable.COL_ID})
            ON DELETE CASCADE
            )"""

    val createMangaIdIndexQuery: String
        get() = "CREATE INDEX ${TABLE}_${COL_MANGA_ID}_index ON $TABLE($COL_MANGA_ID)"
}
