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
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
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

    private fun resetTags(state: EditMangaState) {
        if (manga.genre.isNullOrBlank() || manga.isLocal()) {
            state.setTags(emptyList())
        } else {
            state.setTags(manga.getOriginalGenres().orEmpty())
            state.seriesType = manga.seriesType(true)
        }
    }

    fun updateCover(uri: Uri) {
        state?.cover = CoverChange.Custom(uri)
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
        const val KEY_MANGA = "manga_id"
    }
}
