package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// KMK -->

/**
 * User-defined groups of sources (name to source ids) used to scope Global Search. Stored as one
 * JSON string preference; an unreadable value is treated as "no categories" instead of crashing.
 */
class SourceCategories(
    private val preferences: PreferencesHelper,
    private val json: Json = Json,
) {
    fun all(): Map<String, Set<Long>> =
        try {
            json.decodeFromString<Map<String, Set<Long>>>(preferences.sourceCategories().get())
        } catch (_: Exception) {
            emptyMap()
        }

    fun save(categories: Map<String, Set<Long>>) {
        preferences.sourceCategories().set(json.encodeToString(categories))
        // A removed category must not leave Global Search scoped to nothing.
        if (preferences.globalSearchSourceCategory().get() !in categories) {
            preferences.globalSearchSourceCategory().set("")
        }
    }

    /** Source ids of the category Global Search is scoped to, or null when it is not scoped. */
    fun activeIds(): Set<Long>? {
        val name = preferences.globalSearchSourceCategory().get()
        return if (name.isBlank()) null else all()[name]
    }
}
// KMK <--
