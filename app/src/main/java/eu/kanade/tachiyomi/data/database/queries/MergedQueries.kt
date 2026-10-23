package eu.kanade.tachiyomi.data.database.queries

import android.content.ContentValues
import android.database.Cursor
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.InsertQuery
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.tables.MergedMangaTable
import eu.kanade.tachiyomi.data.merge.MergedMangaReference

/**
 * Which manga make up a merged manga. These are small and only touched by [eu.kanade.tachiyomi.data.merge.MergeManager]
 * and the merged source, so they use plain queries and are blocking.
 */
interface MergedQueries : DbProvider {
    /** The manga a merge is made of, best priority first. */
    fun getMergedReferences(mergeId: Long): List<MergedMangaReference> =
        db
            .lowLevel()
            .rawQuery(
                RawQuery
                    .builder()
                    .query(
                        "SELECT * FROM ${MergedMangaTable.TABLE} WHERE ${MergedMangaTable.COL_MERGE_ID} = ? " +
                            "ORDER BY ${MergedMangaTable.COL_PRIORITY}, ${MergedMangaTable.COL_ID}",
                    ).args(mergeId)
                    .build(),
            ).useRows { it.toReference() }

    /** The merges a manga is part of. */
    fun getMergedReferencesForManga(mangaId: Long): List<MergedMangaReference> =
        db
            .lowLevel()
            .rawQuery(
                RawQuery
                    .builder()
                    .query("SELECT * FROM ${MergedMangaTable.TABLE} WHERE ${MergedMangaTable.COL_MANGA_ID} = ?")
                    .args(mangaId)
                    .build(),
            ).useRows { it.toReference() }

    fun insertMergedReference(reference: MergedMangaReference): Long =
        db.lowLevel().insert(
            InsertQuery
                .builder()
                .table(MergedMangaTable.TABLE)
                .build(),
            reference.toContentValues(),
        )

    fun updateMergedReference(reference: MergedMangaReference) {
        db.lowLevel().update(
            UpdateQuery
                .builder()
                .table(MergedMangaTable.TABLE)
                .where("${MergedMangaTable.COL_MERGE_ID} = ? AND ${MergedMangaTable.COL_MANGA_ID} = ?")
                .whereArgs(reference.mergeId, reference.mangaId)
                .build(),
            reference.toContentValues(),
        )
    }

    fun deleteMergedReference(
        mergeId: Long,
        mangaId: Long,
    ) {
        db.lowLevel().delete(
            DeleteQuery
                .builder()
                .table(MergedMangaTable.TABLE)
                .where("${MergedMangaTable.COL_MERGE_ID} = ? AND ${MergedMangaTable.COL_MANGA_ID} = ?")
                .whereArgs(mergeId, mangaId)
                .build(),
        )
    }

    private fun MergedMangaReference.toContentValues() =
        ContentValues(5).apply {
            put(MergedMangaTable.COL_MERGE_ID, mergeId)
            put(MergedMangaTable.COL_MANGA_ID, mangaId)
            put(MergedMangaTable.COL_PRIORITY, priority)
            put(MergedMangaTable.COL_IS_INFO, if (isInfoManga) 1 else 0)
            put(MergedMangaTable.COL_GET_CHAPTER_UPDATES, if (getChapterUpdates) 1 else 0)
        }

    private fun Cursor.toReference() =
        MergedMangaReference(
            id = getLong(getColumnIndexOrThrow(MergedMangaTable.COL_ID)),
            mergeId = getLong(getColumnIndexOrThrow(MergedMangaTable.COL_MERGE_ID)),
            mangaId = getLong(getColumnIndexOrThrow(MergedMangaTable.COL_MANGA_ID)),
            priority = getInt(getColumnIndexOrThrow(MergedMangaTable.COL_PRIORITY)),
            isInfoManga = getInt(getColumnIndexOrThrow(MergedMangaTable.COL_IS_INFO)) == 1,
            getChapterUpdates = getInt(getColumnIndexOrThrow(MergedMangaTable.COL_GET_CHAPTER_UPDATES)) == 1,
        )

    private fun <T> Cursor.useRows(map: (Cursor) -> T): List<T> =
        use {
            buildList {
                while (moveToNext()) add(map(it))
            }
        }
}
