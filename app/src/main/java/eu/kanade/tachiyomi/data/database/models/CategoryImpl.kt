package eu.kanade.tachiyomi.data.database.models

class CategoryImpl : Category {
    override var id: Int? = null

    override lateinit var name: String

    override var order: Int = 0

    override var flags: Int = 0

    override var mangaOrder: List<Long> = emptyList()

    override var mangaSort: Char? = null

    override var isAlone: Boolean = false

    override var isHidden: Boolean = false

    override var isDynamic: Boolean = false

    override var sourceId: Long? = null

    override var langId: String? = null

    // KMK -->
    override var parentId: Int = 0

    override var parentName: String? = null

    override var trackerStatus: Int = 0

    override var updateInterval: Int = 0

    override var updateLastRun: Long = 0
    // KMK <--

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val category = other as Category

        return name == category.name
    }

    override fun hashCode(): Int = name.hashCode()
}
