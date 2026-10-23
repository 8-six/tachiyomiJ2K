package eu.kanade.tachiyomi.data.track.comick.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.comick.asHidToLong
import eu.kanade.tachiyomi.data.track.comick.fromApiListStatus
import kotlinx.serialization.Serializable

@Serializable
data class ComickLibraryEntry(
    val data: ComickLibraryEntryItem,
) {
    fun toTrack(trackerId: Int): Track =
        Track.create(trackerId).apply {
            media_id = data.hid.asHidToLong()
            title = data.title
            status = data.status.fromApiListStatus()
            last_chapter_read = data.progress?.number?.toFloatOrNull() ?: 0f
            // Ratings imported from elsewhere can have decimals, they are rounded when written
            score = data.rating?.toFloat() ?: 0f
        }
}

@Serializable
data class ComickLibraryEntryItem(
    val hid: String,
    val title: String,
    /** Follow status: 1 reading, 2 completed, 3 on hold, 4 dropped, 5 plan to read. */
    val status: Int,
    val progress: ComickLibraryEntryProgress? = null,
    val rating: Double? = null,
)

@Serializable
data class ComickLibraryEntryProgress(
    val number: String? = null,
)
