package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// KMK -->

/**
 * One configured feed: the latest or popular listing of [sourceId], or the saved search
 * [savedSearchId] run on that source. Saved searches are referenced by id so renaming one keeps
 * its feed.
 */
@Serializable
data class SourceFeed(
    val id: Long,
    val sourceId: Long,
    val type: Type,
    val savedSearchId: Long? = null,
) {
    enum class Type { LATEST, POPULAR, SAVED_SEARCH }
}

/**
 * The ordered list of feeds, kept as one JSON string preference so it travels with app preference
 * backups. An unreadable value is treated as "no feeds".
 */
class SourceFeeds(
    private val preferences: PreferencesHelper,
    private val json: Json = Json,
) {
    fun all(): List<SourceFeed> =
        try {
            json.decodeFromString<List<SourceFeed>>(preferences.sourceFeeds().get())
        } catch (_: Exception) {
            emptyList()
        }

    fun save(feeds: List<SourceFeed>) = preferences.sourceFeeds().set(json.encodeToString(feeds))

    /** Adds [feed] unless an identical source/type/saved-search feed exists; returns whether it was added. */
    fun add(feed: SourceFeed): Boolean {
        val current = all()
        if (current.any { it.sameTarget(feed) }) return false
        save(current + feed)
        return true
    }

    fun remove(id: Long) = save(all().filterNot { it.id == id })

    /** Moves the feed [id] by [offset] places, clamped to the list. */
    fun move(
        id: Long,
        offset: Int,
    ) = save(all().moved(id, offset))

    /** Drops feeds whose saved search no longer exists. */
    fun pruneSavedSearches(existingIds: Set<Long>) {
        val current = all()
        val kept = current.filter { it.type != SourceFeed.Type.SAVED_SEARCH || it.savedSearchId in existingIds }
        if (kept.size != current.size) save(kept)
    }

    companion object {
        fun SourceFeed.sameTarget(other: SourceFeed) =
            sourceId == other.sourceId && type == other.type && savedSearchId == other.savedSearchId

        fun List<SourceFeed>.moved(
            id: Long,
            offset: Int,
        ): List<SourceFeed> {
            val from = indexOfFirst { it.id == id }
            if (from == -1) return this
            val to = (from + offset).coerceIn(0, lastIndex)
            return toMutableList().apply { add(to, removeAt(from)) }
        }
    }
}
// KMK <--
