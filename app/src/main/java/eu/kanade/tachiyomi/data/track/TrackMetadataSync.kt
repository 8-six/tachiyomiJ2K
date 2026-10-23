package eu.kanade.tachiyomi.data.track

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.system.withIOContext
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Fetches series information from the trackers a manga is tracked with, so it can fill in or sync the manga's details.
 * Which details are used is controlled by [TrackPreferences.metadataSyncFields].
 */
class TrackMetadataSync {
    private val db: DatabaseHelper by injectLazy()
    private val trackManager: TrackManager by injectLazy()
    private val trackPreferences: TrackPreferences by injectLazy()
    private val network: NetworkHelper by injectLazy()

    /** The tracks of [manga] whose tracker is able to provide metadata. */
    suspend fun getSources(manga: Manga): List<Pair<Track, TrackService>> =
        db
            .getTracks(manga)
            .executeOnIO()
            .mapNotNull { track ->
                val service = trackManager.getService(track.sync_id) ?: return@mapNotNull null
                if (service.supportsMetadata) track to service else null
            }

    /** The fields that are allowed to be synced. */
    fun enabledFields() = TrackMangaMetadata.Field.fromKeys(trackPreferences.metadataSyncFields().get())

    suspend fun fetch(
        track: Track,
        service: TrackService,
    ): TrackMangaMetadata = service.getMangaMetadata(track).filter(enabledFields())

    /** Downloads [url] to the cache so it can be used like a picked cover image. */
    suspend fun downloadCover(
        context: Context,
        url: String,
    ): Uri? =
        withIOContext {
            val file = File(context.cacheDir, "tracker_cover")
            network.client
                .newCall(GET(url))
                .awaitSuccess()
                .use { response ->
                    response.body.byteStream().use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                }
            file.toUri()
        }
}
