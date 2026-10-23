package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.DelayedTrackingUpdateJob
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.launchIO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Helper method for syncing a remote track with the local chapters, and back.
 *
 * Local chapters follow the tracker (only a continuous run of chapters from the oldest one is marked as read,
 * so odd numbering like "Volume 2 Chapter 1" can't mark everything read) and the tracker follows the local
 * chapters if those are further along. Never lowers the progress stored on the tracker.
 *
 * @param db the database.
 * @param chapters a list of chapters from the source.
 * @param remoteTrack the remote Track object.
 * @param service the tracker service.
 * @return the chapter number the local chapters were advanced to, or null if nothing changed locally.
 */
fun syncChaptersWithTrackServiceTwoWay(
    db: DatabaseHelper,
    chapters: List<Chapter>,
    remoteTrack: Track,
    service: TrackService,
): Int? {
    val result = computeTrackSync(chapters, remoteTrack.last_chapter_read)

    // update remote only if the local progress is ahead of it
    if (result.localLastRead > remoteTrack.last_chapter_read) {
        remoteTrack.last_chapter_read = result.localLastRead
        launchIO {
            try {
                service.update(remoteTrack)
                db.insertTrack(remoteTrack).executeAsBlocking()
            } catch (e: Throwable) {
                Timber.w(e)
            }
        }
    }

    if (result.chaptersToMarkRead.isEmpty()) return null
    result.chaptersToMarkRead.forEach { it.read = true }
    db.updateChaptersProgress(result.chaptersToMarkRead).executeAsBlocking()
    return maxOf(result.localLastRead, result.remoteLastRead).toInt()
}

class TrackSyncResult(
    /** Unread chapters that the tracker says are read. */
    val chaptersToMarkRead: List<Chapter>,
    /** Last chapter of the continuous run of read chapters, 0 if the first one isn't read. */
    val localLastRead: Float,
    val remoteLastRead: Float,
)

/**
 * Works out how local chapters and a tracker's progress should be brought in line, without touching anything.
 */
fun computeTrackSync(
    chapters: List<Chapter>,
    remoteLastRead: Float,
): TrackSyncResult {
    // Oldest first by source order, the database order is not reliable
    val dbChapters = chapters.filter { it.isRecognizedNumber }.sortedByDescending { it.source_order }

    var lastCheckedNumber: Float
    var checkingNumber = 0f
    val chaptersToMarkRead =
        dbChapters
            .takeWhile { chapter ->
                lastCheckedNumber = checkingNumber
                checkingNumber = chapter.chapter_number
                chapter.chapter_number >= lastCheckedNumber && chapter.chapter_number <= remoteLastRead
            }.filter { !it.read }

    // only take into account continuous reading
    val localLastRead =
        dbChapters
            .sortedBy { it.chapter_number }
            .takeWhile { it.read }
            .lastOrNull()
            ?.chapter_number ?: 0F
    return TrackSyncResult(chaptersToMarkRead, localLastRead, remoteLastRead)
}

private var trackingJobs = HashMap<Long, Pair<Job?, Float?>>()

/**
 * Starts the service that updates the last chapter read in sync services. This operation
 * will run in a background thread and errors are ignored.
 */
fun updateTrackChapterMarkedAsRead(
    db: DatabaseHelper,
    preferences: PreferencesHelper,
    newLastChapter: Chapter?,
    mangaId: Long?,
    delay: Long = 3000,
    fetchTracks: (suspend () -> Unit)? = null,
) {
    if (!preferences.trackMarkedAsRead()) return
    mangaId ?: return

    val newChapterRead = newLastChapter?.chapter_number ?: 0f

    // To avoid unnecessary calls if multiple marked as read for same manga
    if (trackingJobs[mangaId]?.second ?: 0f < newChapterRead) {
        trackingJobs[mangaId]?.first?.cancel()

        // We want these to execute even if the presenter is destroyed
        trackingJobs[mangaId] = launchIO {
            delay(delay)
            updateTrackChapterRead(db, preferences, mangaId, newChapterRead)
            fetchTracks?.invoke()
            trackingJobs.remove(mangaId)
        } to newChapterRead
    }
}

suspend fun updateTrackChapterRead(
    db: DatabaseHelper,
    preferences: PreferencesHelper,
    mangaId: Long?,
    newChapterRead: Float,
    retryWhenOnline: Boolean = false,
): List<Pair<TrackService, String?>> {
    val trackManager = Injekt.get<TrackManager>()
    val trackList = db.getTracks(mangaId).executeAsBlocking()
    val failures = mutableListOf<Pair<TrackService, String?>>()
    trackList.map { track ->
        val service = trackManager.getService(track.sync_id)
        if (service != null && service.isLogged && newChapterRead > track.last_chapter_read) {
            if (retryWhenOnline && !preferences.context.isOnline()) {
                delayTrackingUpdate(preferences, mangaId, newChapterRead, track)
            } else if (preferences.context.isOnline()) {
                try {
                    track.last_chapter_read = newChapterRead
                    service.update(track, true)
                    db.insertTrack(track).executeAsBlocking()
                } catch (e: Exception) {
                    Timber.e(e)
                    failures.add(service to e.localizedMessage)
                    if (retryWhenOnline) {
                        delayTrackingUpdate(preferences, mangaId, newChapterRead, track)
                    }
                }
            }
        }
    }
    return failures
}

private fun delayTrackingUpdate(
    preferences: PreferencesHelper,
    mangaId: Long?,
    newChapterRead: Float,
    track: Track,
) {
    val trackings = preferences.trackingsToAddOnline().get().toMutableSet()
    val currentTracking = trackings.find { it.startsWith("$mangaId:${track.sync_id}:") }
    trackings.remove(currentTracking)
    trackings.add("$mangaId:${track.sync_id}:$newChapterRead")
    preferences.trackingsToAddOnline().set(trackings)
    DelayedTrackingUpdateJob.setupTask(preferences.context)
}
