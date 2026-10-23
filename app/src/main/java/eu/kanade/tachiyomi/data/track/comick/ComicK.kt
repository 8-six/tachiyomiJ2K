package eu.kanade.tachiyomi.data.track.comick

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import eu.kanade.tachiyomi.data.track.metadata.ComicKMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

/**
 * ComicK (https://comick.dev), through its official Library API. It signs in with the app of Houri, see [ComicKApi].
 */
class ComicK(
    private val context: Context,
    id: Int,
) : TrackService(id) {
    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val PLAN_TO_READ = 4
        const val DROPPED = 5

        private const val SEARCH_ID_PREFIX = "id:"
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { ComicKInterceptor(this) }
    private val api by lazy { ComicKApi(id, client, interceptor) }

    override val supportsMetadata: Boolean = true

    @StringRes
    override fun nameRes() = R.string.comick

    override fun getLogo() = R.drawable.ic_tracker_comick

    override fun getTrackerColor() = Color.rgb(0x12, 0x12, 0x12)

    override fun getLogoColor() = Color.rgb(0x1F, 0x1F, 0x1F)

    override fun getStatusList(): List<Int> = listOf(READING, COMPLETED, ON_HOLD, PLAN_TO_READ, DROPPED)

    override fun isCompletedStatus(index: Int) = getStatusList()[index] == COMPLETED

    override fun completedStatus(): Int = COMPLETED

    override fun readingStatus() = READING

    override fun planningStatus() = PLAN_TO_READ

    override fun getStatus(status: Int): String =
        with(context) {
            when (status) {
                READING -> getString(R.string.reading)
                COMPLETED -> getString(R.string.completed)
                ON_HOLD -> getString(R.string.paused)
                PLAN_TO_READ -> getString(R.string.plan_to_read)
                DROPPED -> getString(R.string.dropped)
                else -> ""
            }
        }

    override fun getGlobalStatus(status: Int): String = getStatus(status)

    // 1 to 10, and "-" for not rated
    override fun getScoreList(): List<String> = (0..10).map { if (it == 0) "-" else it.toString() }

    override fun displayScore(track: Track): String = track.score.toInt().toString()

    override suspend fun add(track: Track): Track {
        track.status = READING
        track.score = 0f
        updateNewTrackInfo(track)
        return api.addLibManga(track)
    }

    override suspend fun update(
        track: Track,
        setToRead: Boolean,
    ): Track {
        updateTrackStatus(track, setToRead, setToComplete = true, mustReadToComplete = false)
        return api.updateLibManga(track)
    }

    override suspend fun bind(track: Track): Track {
        val remoteTrack = api.findLibManga(track) ?: return add(track)
        // What ComicK has is kept
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        return update(track)
    }

    override fun canRemoveFromService(): Boolean = true

    override suspend fun removeFromService(track: Track): Boolean =
        try {
            api.deleteLibManga(track)
            true
        } catch (e: Exception) {
            Timber.w(e)
            false
        }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            val hid = query.substringAfter(SEARCH_ID_PREFIX).trim()
            return api.getMangaDetails(hid)?.let { listOf(it) } ?: emptyList()
        }
        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.findLibManga(track) ?: throw Exception("Could not find manga")
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        return track
    }

    /** The public catalog has this, it needs no account and has the covers with their size. */
    override suspend fun getMangaMetadata(track: Track): TrackMangaMetadata =
        ComicKMetadata.parse(api.getCatalogComic(track.media_id.toHid()), trackPreferences.metadataOptions(), id)

    override suspend fun login(
        username: String,
        password: String,
    ) = login(password)

    /**
     * Finishes signing in with the code ComicK sent back.
     *
     * @throws ComickMissingScopesException if the user didn't allow what is needed.
     */
    suspend fun login(code: String) {
        try {
            val oauth = api.getAccessToken(code)
            interceptor.setAuth(oauth)
            val currentUser = api.getCurrentUser()
            saveCredentials(currentUser.username ?: currentUser.id, oauth.accessToken)
        } catch (e: Exception) {
            Timber.w(e, "Signing in to ComicK failed")
            logout()
            throw e
        }
    }

    fun saveToken(oauth: ComickOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): ComickOAuth? =
        try {
            json.decodeFromString<ComickOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }

    fun verifyOAuthState(state: String): Boolean = api.verifyOAuthState(state)

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }
}
