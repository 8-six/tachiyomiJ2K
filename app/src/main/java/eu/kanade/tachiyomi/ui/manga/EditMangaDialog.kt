package eu.kanade.tachiyomi.ui.manga

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.track.TrackMetadataSync
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.clipboardHasImage
import eu.kanade.tachiyomi.util.system.getClipboardImageUri
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.widget.ComposeBottomSheetDialog
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

// KMK -->
// Compose Material 3 edit panel; behaviour of the previous XML dialog is kept
// KMK <--
class EditMangaDialog : DialogController {
    private val manga: Manga

    private var state: EditMangaState? = null

    private val infoController
        get() = targetController as MangaDetailsController

    constructor(target: MangaDetailsController, manga: Manga) : super(
        Bundle()
            .apply {
                putLong(KEY_MANGA, manga.id!!)
            },
    ) {
        targetController = target
        this.manga = manga
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        manga =
            Injekt
                .get<DatabaseHelper>()
                .getManga(bundle.getLong(KEY_MANGA))
                .executeAsBlocking()!!
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val activity = activity as ComponentActivity
        val state = createState(activity).also { state = it }
        val dialog = ComposeBottomSheetDialog(activity)
        val actions =
            EditMangaActions(
                onCoverClick = { infoController.changeCover() },
                onLoadTrackers = { loadTrackers(state) },
                onApplyField = { field, serviceId -> applyField(state, field, serviceId) },
                onPickCover = { url -> pickTrackerCover(state, url) },
                canPasteCover = { activity.clipboardHasImage() },
                onPasteCover = { activity.getClipboardImageUri()?.let(::updateCover) },
                onAutofill = { autofillFromTracker(state) },
                onResetTags = { resetTags(state) },
                onSave = {
                    save(state)
                    dialog.dismiss()
                },
                onCancel = { dialog.dismiss() },
            )
        dialog.setContent { EditMangaSheet(state, actions) }
        // Opens like a drawer, a bit over half the screen, instead of covering all of it
        dialog.behavior.apply {
            isFitToContents = false
            halfExpandedRatio = HALF_EXPANDED_RATIO
            expandedOffset = (activity.resources.displayMetrics.density * EXPANDED_TOP_MARGIN_DP).toInt() + statusBarHeight(activity)
            skipCollapsed = true
            setState(BottomSheetBehavior.STATE_HALF_EXPANDED)
        }
        launchUI {
            val sources = TrackMetadataSync().getSources(manga)
            state.trackers = sources.map { (_, service) -> TrackerSource(service.id, activity.getString(service.nameRes())) }
        }
        return dialog
    }

    private fun createState(activity: ComponentActivity): EditMangaState {
        val isLocal = manga.isLocal()
        val languages = mutableListOf("")
        if (isLocal) {
            val extensionManager: ExtensionManager by injectLazy()
            val activeLangs =
                infoController.presenter.preferences
                    .enabledLanguages()
                    .get()
            languages.addAll(
                extensionManager.availableExtensionsFlow.value
                    .groupBy { it.lang }
                    .keys
                    .sortedWith(
                        compareBy(
                            { it !in activeLangs },
                            { LocaleHelper.getSourceDisplayName(it, activity) },
                        ),
                    ).filter { it != "all" && it != "other" },
            )
        }
        return EditMangaState(
            manga = manga,
            isLocal = isLocal,
            originalGenres = manga.getOriginalGenres().orEmpty(),
            oldSeriesType = manga.seriesType(),
            languages = languages,
            languageName = { LocaleHelper.getSourceDisplayName(it, activity) },
            seriesTypeFor = { manga.seriesType(customTags = it.joinToString(", ")) },
        ).apply {
            // Fields stay empty unless they differ from the source's values
            if (isLocal) {
                if (manga.title != manga.url) title = manga.title
                author = manga.author.orEmpty()
                artist = manga.artist.orEmpty()
                description = manga.description.orEmpty()
                lang = LocalSource.getMangaLang(manga, activity)
                if (lang !in languages) lang = ""
            } else {
                if (manga.title != manga.originalTitle) title = manga.title
                if (manga.author != manga.originalAuthor) author = manga.author.orEmpty()
                if (manga.artist != manga.originalArtist) artist = manga.artist.orEmpty()
                if (manga.description != manga.originalDescription) description = manga.description.orEmpty()
            }
            status = manga.status.coerceIn(SManga.UNKNOWN, SManga.ON_HIATUS)
            tags.addAll(manga.getGenres().orEmpty())
        }
    }

    private fun statusBarHeight(activity: ComponentActivity): Int =
        androidx.core.view.ViewCompat
            .getRootWindowInsets(activity.window.decorView)
            ?.getInsets(
                androidx.core.view.WindowInsetsCompat.Type
                    .statusBars(),
            )?.top ?: 0

    /** Asks every tracker of the manga for its details once, so fields and covers can be previewed. */
    private fun loadTrackers(state: EditMangaState) {
        launchUI {
            val sync = TrackMetadataSync()
            val trackManager: eu.kanade.tachiyomi.data.track.TrackManager =
                uy.kohesive.injekt.Injekt
                    .get()
            sync.getSources(manga).forEach { (track, service) ->
                if (state.trackerData[service.id] != null) return@forEach
                state.trackerData[service.id] = TrackerData.Loading
                launchUI {
                    state.trackerData[service.id] =
                        try {
                            val metadata =
                                withIOContext { service.getMangaMetadata(track).withCoverSource(service.id) }
                            val extra =
                                if (service.id == eu.kanade.tachiyomi.data.track.TrackManager.MANGABAKA && track.media_id > 0) {
                                    runCatching { withIOContext { trackManager.mangaBaka.getCoverCandidates(track.media_id) } }
                                        .getOrDefault(emptyList())
                                } else {
                                    emptyList()
                                }
                            val covers = (metadata.coverCandidates() + extra).distinctBy { it.url }
                            TrackerData.Loaded(metadata, covers)
                        } catch (e: Exception) {
                            Timber.e(e)
                            TrackerData.Failed(e.message ?: e.javaClass.simpleName)
                        }
                }
            }
        }
    }

    private fun applyField(
        state: EditMangaState,
        field: TrackMangaMetadata.Field,
        serviceId: Int,
    ) {
        val metadata = (state.trackerData[serviceId] as? TrackerData.Loaded)?.metadata ?: return
        val sync = TrackMetadataSync()
        when (field) {
            TrackMangaMetadata.Field.TITLE -> metadata.title?.let { state.title = it }
            TrackMangaMetadata.Field.AUTHORS -> metadata.authors?.let { state.author = it }
            TrackMangaMetadata.Field.ARTISTS -> metadata.artists?.let { state.artist = it }
            TrackMangaMetadata.Field.DESCRIPTION -> sync.descriptionFor(metadata)?.let { state.description = it }
            TrackMangaMetadata.Field.STATUS -> metadata.status?.let { state.status = it.coerceIn(SManga.UNKNOWN, SManga.ON_HIATUS) }
            TrackMangaMetadata.Field.TAGS -> sync.tagsFor(state.tags.toList(), metadata)?.let { state.setTags(it) }
            TrackMangaMetadata.Field.COVER -> metadata.coverUrl?.let { pickTrackerCover(state, it) }
        }
    }

    private fun pickTrackerCover(
        state: EditMangaState,
        url: String,
    ) {
        val context = activity ?: return
        launchUI {
            state.coverBusy = true
            try {
                TrackMetadataSync().downloadCover(context, url)?.let {
                    updateCover(it)
                    state.pickedCoverUrl = url
                    state.coverPickerOpen = false
                }
            } catch (e: Exception) {
                Timber.e(e)
                context.toast(context.getString(R.string.tracker_metadata_error, "", e.message.orEmpty()))
            } finally {
                state.coverBusy = false
            }
        }
    }

    private fun resetTags(state: EditMangaState) {
        if (manga.genre.isNullOrBlank() || manga.isLocal()) {
            state.setTags(emptyList())
        } else {
            state.setTags(manga.getOriginalGenres().orEmpty())
            state.seriesType = manga.seriesType(true)
        }
    }

    fun updateCover(uri: Uri) {
        state?.apply {
            cover = CoverChange.Custom(uri)
            // A cover from the gallery isn't one of the tracker covers
            if (pickedCoverUrl != null && uri.lastPathSegment?.startsWith("tracker_cover") != true) pickedCoverUrl = null
        }
    }

    private fun autofillFromTracker(state: EditMangaState) {
        val context = activity ?: return
        launchUI {
            val sync = TrackMetadataSync()
            val sources = sync.getSources(manga)
            when {
                sources.isEmpty() -> context.toast(R.string.no_tracker_with_metadata)
                sources.size == 1 ->
                    applyTrackerMetadata(
                        state,
                        sync,
                        context.getString(sources.first().second.nameRes()),
                    ) { sync.fetchAll(manga) }
                else -> {
                    // The first choice combines them all, which is what a sync does
                    val names =
                        (
                            listOf(context.getString(R.string.all_trackers_combined)) +
                                sources.map { context.getString(it.second.nameRes()) }
                        ).toTypedArray()
                    context
                        .materialAlertDialog()
                        .setTitle(R.string.select_tracker)
                        .setItems(names) { _, index ->
                            launchUI {
                                if (index == 0) {
                                    applyTrackerMetadata(state, sync, names[0]) { sync.fetchAll(manga) }
                                } else {
                                    val (track, service) = sources[index - 1]
                                    applyTrackerMetadata(state, sync, names[index]) { sync.fetch(track, service) }
                                }
                            }
                        }.show()
                }
            }
        }
    }

    private suspend fun applyTrackerMetadata(
        state: EditMangaState,
        sync: TrackMetadataSync,
        name: String,
        load: suspend () -> TrackMangaMetadata,
    ) {
        val context = activity ?: return
        state.autofillBusy = true
        try {
            val metadata = withIOContext { load() }
            metadata.title?.let { state.title = it }
            metadata.authors?.let { state.author = it }
            metadata.artists?.let { state.artist = it }
            sync.descriptionFor(metadata)?.let { state.description = it }
            metadata.status?.let { state.status = it.coerceIn(SManga.UNKNOWN, SManga.ON_HIATUS) }
            if (TrackMangaMetadata.Field.TAGS in sync.enabledFields()) {
                sync.tagsFor(state.tags.toList(), metadata)?.let { state.setTags(it) }
            }
            metadata.coverUrl?.let { url ->
                sync.downloadCover(context, url)?.let { updateCover(it) }
            }
            if (metadata.warnings.isNotEmpty()) {
                context.toast(
                    context.getString(R.string.tracker_metadata_warning, metadata.warnings.joinToString()),
                    Toast.LENGTH_LONG,
                )
            }
        } catch (e: Exception) {
            Timber.e(e)
            context.toast(context.getString(R.string.tracker_metadata_error, name, e.message.orEmpty()))
        } finally {
            state.autofillBusy = false
        }
    }

    private fun save(state: EditMangaState) {
        state.commitPendingTag()
        val cover = state.cover
        infoController.presenter.updateManga(
            state.title,
            state.author,
            state.artist,
            (cover as? CoverChange.Custom)?.uri,
            state.description,
            state.tags.toTypedArray(),
            state.status,
            if (state.resetsReadingMode) state.seriesType else null,
            if (state.isLocal) state.lang else null,
            cover is CoverChange.Reset,
        )
    }

    private companion object {
        const val HALF_EXPANDED_RATIO = 0.6f
        const val EXPANDED_TOP_MARGIN_DP = 8
        const val KEY_MANGA = "manga_id"
    }
}
