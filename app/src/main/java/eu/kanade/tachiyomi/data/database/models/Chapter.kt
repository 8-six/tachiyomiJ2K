package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.source.model.SChapter
import java.io.Serializable

interface Chapter :
    SChapter,
    Serializable {
    var id: Long?

    var manga_id: Long?

    var read: Boolean

    var bookmark: Boolean

    // KMK -->
    /** ARGB color of the bookmark, 0 for the theme default. */
    var bookmark_color: Int

    /** Hidden from the chapter list and reader navigation without being deleted. */
    var blacklisted: Boolean
    // KMK <--

    var last_page_read: Int

    var pages_left: Int

    var date_fetch: Long

    var source_order: Int

    val isRecognizedNumber: Boolean
        get() = chapter_number >= 0f

    companion object {
        fun create(): Chapter =
            ChapterImpl().apply {
                chapter_number = -1f
            }

        fun List<Chapter>.copy(): List<Chapter> =
            map {
                ChapterImpl().apply {
                    copyFrom(it)
                }
            }
    }

    fun copyFrom(other: Chapter) {
        id = other.id
        manga_id = other.manga_id
        read = other.read
        bookmark = other.bookmark
        bookmark_color = other.bookmark_color
        blacklisted = other.blacklisted
        last_page_read = other.last_page_read
        pages_left = other.pages_left
        date_fetch = other.date_fetch
        source_order = other.source_order
        copyFrom(other as SChapter)
    }
}
