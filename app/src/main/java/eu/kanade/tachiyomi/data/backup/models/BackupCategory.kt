package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class BackupCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Int = 0,
    // @ProtoNumber(3) val updateInterval: Int = 0, 1.x value not used in 0.x
    // Bump by 100 to specify this is a 0.x value
    @ProtoNumber(100) var flags: Int = 0,
    // J2K Specific values
    @ProtoNumber(800) var mangaSort: Char? = null,
    // KMK -->
    @ProtoNumber(803) var parentName: String? = null,
    @ProtoNumber(804) var trackerStatus: Int = 0,
    @ProtoNumber(805) var updateInterval: Int = 0,
    // KMK <--
) {
    fun getCategoryImpl(): CategoryImpl =
        CategoryImpl().apply {
            name = this@BackupCategory.name
            flags = this@BackupCategory.flags
            order = this@BackupCategory.order
            mangaSort = this@BackupCategory.mangaSort
            trackerStatus = this@BackupCategory.trackerStatus
            updateInterval = this@BackupCategory.updateInterval
        }

    companion object {
        fun copyFrom(category: Category): BackupCategory =
            BackupCategory(
                name = category.name,
                order = category.order,
                flags = category.flags,
                mangaSort = category.mangaSort,
                parentName = category.parentName,
                trackerStatus = category.trackerStatus,
                updateInterval = category.updateInterval,
            )
    }
}
