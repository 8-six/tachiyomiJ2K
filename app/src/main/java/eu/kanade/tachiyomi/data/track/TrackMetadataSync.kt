package eu.kanade.tachiyomi.data.track

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.track.metadata.MetadataApply
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.Field
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Gets series information from the trackers a manga is tracked with and applies it to the manga. Which details are
 * used, which tracker is asked first and how they are combined with what the manga has is set in the tracking settings,
 * see [TrackPreferences].
 */
class TrackMetadataSync {
    private val db: DatabaseHelper by injectLazy()
    private val trackManager: TrackManager by injectLazy()
    private val trackPreferences: TrackPreferences by injectLazy()
    private val network: NetworkHelper by injectLazy()
    private val coverCache: CoverCache by injectLazy()
    private val customMangaManager: CustomMangaManager by injectLazy()

    /**
     * The tracks of [manga] whose tracker is able to provide details, in the order they are asked in: the preferred
     * tracker first, then the others by [TrackManager.metadataPriority].
     */
    suspend fun getSources(manga: Manga): List<Pair<Track, TrackService>> {
        val preferred = trackPreferences.preferredMetadataTracker().get().toIntOrNull()
        val priority = trackManager.metadataPriority
        return db
            .getTracks(manga)
            .executeOnIO()
            .mapNotNull { track ->
                val service = trackManager.getService(track.sync_id) ?: return@mapNotNull null
                if (service.supportsMetadata) track to service else null
            }.sortedBy { (_, service) ->
                when {
                    service.id == preferred -> -1
                    else -> priority.indexOf(service).let { if (it < 0) priority.size else it }
                }
            }
    }

    /** The fields that are allowed to be synced. */
    fun enabledFields() = Field.fromKeys(trackPreferences.metadataSyncFields().get())

    /** The details a single tracker has. */
    suspend fun fetch(
        track: Track,
        service: TrackService,
    ): TrackMangaMetadata = service.getMangaMetadata(track).filter(enabledFields())

    /**
     * The details of every tracker of [manga] combined: the first tracker that has a detail provides it. Trackers are
     * only asked while something that is to be synced is still missing, and only the first one if the user turned off
     * filling gaps from the others.
     *
     * @throws Exception if no tracker could be asked, with what went wrong.
     */
    suspend fun fetchAll(manga: Manga): TrackMangaMetadata {
        val sources = getSources(manga)
        if (sources.isEmpty()) throw Exception("No tracker with details")

        val fields = enabledFields()
        val fillGaps = trackPreferences.metadataFillGaps().get()
        var merged: TrackMangaMetadata? = null
        val failures = mutableListOf<String>()

        for ((track, service) in sources) {
            try {
                val metadata = fetch(track, service)
                merged = merged?.fillGapsFrom(metadata) ?: metadata
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "Could not get details from tracker ${service.id}")
                failures += "${networkName(service)}: ${e.message ?: e.javaClass.simpleName}"
            }
            if (merged != null && (!fillGaps || fields.all { merged!!.has(it) })) break
        }

        val result = merged ?: throw Exception(failures.joinToString("; "))
        return result.copy(warnings = result.warnings + failures)
    }

    // TrackService only has a string resource for its name, which needs a context
    private fun networkName(service: TrackService) = Injekt.get<Application>().getString(service.nameRes())

    /** The tags a manga should have after syncing [metadata], or null to leave them. */
    fun tagsFor(
        existing: List<String>,
        metadata: TrackMangaMetadata,
    ): List<String>? =
        MetadataApply.mergeTags(
            existing = existing,
            incoming = metadata.tags,
            seriesType = metadata.seriesType,
            mode = trackPreferences.metadataTagsMode().get(),
            addSeriesTypeTag = trackPreferences.metadataSeriesTypeTag().get(),
        )

    /** The description of [metadata], with the other titles under it if the user wants them. */
    fun descriptionFor(metadata: TrackMangaMetadata): String? =
        MetadataApply.description(metadata.description, metadata.alternativeTitles, trackPreferences.metadataAlternativeTitles().get())

    /**
     * Applies [metadata] to the custom info and cover of [manga].
     *
     * @param overwrite replace details the user changed. If false only details that are still the source's are replaced.
     * @return the details that were changed.
     */
    suspend fun apply(
        manga: Manga,
        metadata: TrackMangaMetadata,
        overwrite: Boolean,
    ): Set<Field> {
        if (manga.isLocal()) return emptySet()
        val id = manga.id ?: return emptySet()
        val custom = customMangaManager.getCustomInfo(manga)
        val changed = mutableSetOf<Field>()

        fun text(
            field: Field,
            incoming: String?,
            current: String?,
        ): String? {
            val value = incoming?.trim()?.takeIf { it.isNotEmpty() }
            return if (value != null && (overwrite || current == null) && value != current) {
                changed += field
                value
            } else {
                current
            }
        }

        val title = text(Field.TITLE, metadata.title, custom?.title)
        val author = text(Field.AUTHORS, metadata.authors, custom?.author)
        val artist = text(Field.ARTISTS, metadata.artists, custom?.artist)
        val description = text(Field.DESCRIPTION, descriptionFor(metadata), custom?.description)

        var genre: Array<String>? = custom?.genre
        if (Field.TAGS in enabledFields() && (overwrite || genre == null)) {
            tagsFor(manga.getGenres().orEmpty(), metadata)?.let { tags ->
                // The same as the source has isn't an edit
                if (tags.joinToString(", ") != manga.originalGenre && tags != genre?.toList()) {
                    genre = tags.toTypedArray()
                    changed += Field.TAGS
                }
            }
        }

        var status = custom?.status
        if (metadata.status != null &&
            (overwrite || status == null) &&
            metadata.status != manga.originalStatus &&
            metadata.status != status
        ) {
            status = metadata.status
            changed += Field.STATUS
        }

        customMangaManager.saveMangaInfo(CustomMangaManager.MangaJson(id, title, author, artist, description, genre, status))

        // A cover is only kept for library entries
        val coverUrl = metadata.coverUrl
        if (coverUrl != null && manga.favorite && (overwrite || !coverCache.getCustomCoverFile(manga).exists())) {
            val context = Injekt.get<Application>()
            downloadCover(context, coverUrl)?.let { uri ->
                context.contentResolver.openInputStream(uri)?.let {
                    coverCache.setCustomCoverToCache(manga, it)
                    changed += Field.COVER
                }
            }
        }
        return changed
    }

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
