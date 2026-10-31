package eu.kanade.tachiyomi.data.database.mappers

import android.content.ContentValues
import android.database.Cursor
import com.pushtorefresh.storio.sqlite.SQLiteTypeMapping
import com.pushtorefresh.storio.sqlite.operations.delete.DefaultDeleteResolver
import com.pushtorefresh.storio.sqlite.operations.get.DefaultGetResolver
import com.pushtorefresh.storio.sqlite.operations.put.DefaultPutResolver
import com.pushtorefresh.storio.sqlite.queries.DeleteQuery
import com.pushtorefresh.storio.sqlite.queries.InsertQuery
import com.pushtorefresh.storio.sqlite.queries.UpdateQuery
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_FLAGS
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_ID
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_MANGA_ORDER
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_NAME
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_ORDER
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_PARENT_ID
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_TRACKER_STATUS
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_UPDATE_INTERVAL
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.COL_UPDATE_LAST_RUN
import eu.kanade.tachiyomi.data.database.tables.CategoryTable.TABLE

class CategoryTypeMapping :
    SQLiteTypeMapping<Category>(
        CategoryPutResolver(),
        CategoryGetResolver(),
        CategoryDeleteResolver(),
    )

class CategoryPutResolver : DefaultPutResolver<Category>() {
    override fun mapToInsertQuery(obj: Category) =
        InsertQuery
            .builder()
            .table(TABLE)
            .build()

    override fun mapToUpdateQuery(obj: Category) =
        UpdateQuery
            .builder()
            .table(TABLE)
            .where("$COL_ID = ?")
            .whereArgs(obj.id)
            .build()

    override fun mapToContentValues(obj: Category) =
        ContentValues(8).apply {
            put(COL_PARENT_ID, obj.parentId)
            put(COL_TRACKER_STATUS, obj.trackerStatus)
            put(COL_UPDATE_INTERVAL, obj.updateInterval)
            put(COL_UPDATE_LAST_RUN, obj.updateLastRun)
            put(COL_ID, obj.id)
            put(COL_NAME, obj.name)
            put(COL_ORDER, obj.order)
            put(COL_FLAGS, obj.flags)
            if (obj.mangaSort != null) {
                put(COL_MANGA_ORDER, obj.mangaSort.toString())
            } else {
                val orderString = obj.mangaOrder.joinToString("/")
                put(COL_MANGA_ORDER, orderString)
            }
        }
}

class CategoryGetResolver : DefaultGetResolver<Category>() {
    override fun mapFromCursor(cursor: Cursor): Category =
        CategoryImpl().apply {
            id = cursor.getInt(cursor.getColumnIndex(COL_ID))
            name = cursor.getString(cursor.getColumnIndex(COL_NAME))
            order = cursor.getInt(cursor.getColumnIndex(COL_ORDER))
            flags = cursor.getInt(cursor.getColumnIndex(COL_FLAGS))
            // KMK -->
            cursor.getColumnIndex(COL_PARENT_ID).takeIf { it >= 0 }?.let { parentId = cursor.getInt(it) }
            cursor.getColumnIndex(COL_TRACKER_STATUS).takeIf { it >= 0 }?.let { trackerStatus = cursor.getInt(it) }
            cursor.getColumnIndex(COL_UPDATE_INTERVAL).takeIf { it >= 0 }?.let { updateInterval = cursor.getInt(it) }
            cursor.getColumnIndex(COL_UPDATE_LAST_RUN).takeIf { it >= 0 }?.let { updateLastRun = cursor.getLong(it) }
            cursor.getColumnIndex("parent_name").takeIf { it >= 0 }?.let { parentName = cursor.getString(it) }
            // KMK <--

            val orderString = cursor.getString(cursor.getColumnIndex(COL_MANGA_ORDER))
            when {
                orderString.isNullOrBlank() -> {
                    mangaSort = 'a'
                    mangaOrder = emptyList()
                }
                orderString.firstOrNull()?.isLetter() == true -> {
                    mangaSort = orderString.first()
                    mangaOrder = emptyList()
                }
                else -> mangaOrder = orderString.split("/").mapNotNull { it.toLongOrNull() }
            }
        }
}

class CategoryDeleteResolver : DefaultDeleteResolver<Category>() {
    override fun mapToDeleteQuery(obj: Category) =
        DeleteQuery
            .builder()
            .table(TABLE)
            .where("$COL_ID = ?")
            .whereArgs(obj.id)
            .build()
}
