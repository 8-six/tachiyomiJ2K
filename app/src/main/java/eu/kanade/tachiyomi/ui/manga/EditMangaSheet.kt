package eu.kanade.tachiyomi.ui.manga

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.load
import coil.request.Parameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata

// KMK -->
// Compose Material 3 layout modelled on Komikku's EditMangaDialog
sealed interface CoverChange {
    data object None : CoverChange

    data object Reset : CoverChange

    data class Custom(
        val uri: Uri,
    ) : CoverChange
}

/**
 * Editable state of the edit panel. Seeded by [EditMangaDialog] from the manga's custom values.
 */
class EditMangaState(
    val manga: Manga,
    val isLocal: Boolean,
    val originalGenres: List<String>,
    val oldSeriesType: Int,
    val languages: List<String>,
    val languageName: (String) -> String,
    val seriesTypeFor: (List<String>) -> Int,
) {
    var title by mutableStateOf("")
    var author by mutableStateOf("")
    var artist by mutableStateOf("")
    var description by mutableStateOf("")
    var status by mutableIntStateOf(0)
    var seriesType by mutableIntStateOf(oldSeriesType)
    var lang by mutableStateOf("")
    var cover by mutableStateOf<CoverChange>(CoverChange.None)
    var coverWide by mutableStateOf(false)
    var autofillBusy by mutableStateOf(false)
    var trackers by mutableStateOf<List<TrackerSource>>(emptyList())
    val trackerData = mutableStateMapOf<Int, TrackerData>()
    var syncField by mutableStateOf<TrackMangaMetadata.Field?>(null)
    var coverPickerOpen by mutableStateOf(false)
    var coverBusy by mutableStateOf(false)
    var pickedCoverUrl by mutableStateOf<String?>(null)
    var pendingTag by mutableStateOf("")
    val tags = mutableStateListOf<String>()

    val resetsReadingMode get() = seriesType != oldSeriesType

    fun setTags(newTags: List<String>) {
        tags.clear()
        tags.addAll(newTags)
        seriesType = seriesTypeFor(newTags)
    }

    fun moveTag(
        from: Int,
        to: Int,
    ) {
        if (from !in tags.indices || to !in tags.indices) return
        tags.add(to, tags.removeAt(from))
    }

    /** Moves the typed comma separated text into the tag list. */
    fun commitPendingTag() {
        val added = pendingTag.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        pendingTag = ""
        if (added.isNotEmpty()) setTags(tags + added)
    }
}

/** A tracker of the manga that can provide its details. */
class TrackerSource(
    val serviceId: Int,
    val name: String,
)

/** What a tracker answered when asked for the manga's details, for previewing before anything is applied. */
sealed interface TrackerData {
    data object Loading : TrackerData

    data class Loaded(
        val metadata: TrackMangaMetadata,
        val covers: List<TrackMangaMetadata.CoverCandidate>,
    ) : TrackerData

    data class Failed(
        val message: String,
    ) : TrackerData
}

class EditMangaActions(
    val onCoverClick: () -> Unit,
    val onLoadTrackers: () -> Unit,
    val onApplyField: (TrackMangaMetadata.Field, Int) -> Unit,
    val onPickCover: (String) -> Unit,
    val canPasteCover: () -> Boolean,
    val onPasteCover: () -> Unit,
    val onAutofill: () -> Unit,
    val onResetTags: () -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMangaSheet(
    state: EditMangaState,
    actions: EditMangaActions,
) {
    val manga = state.manga
    val syncOf: (TrackMangaMetadata.Field) -> (() -> Unit)? = { field ->
        if (state.trackers.isEmpty()) {
            null
        } else {
            {
                actions.onLoadTrackers()
                state.syncField = field
            }
        }
    }
    Column(modifier = Modifier.imePadding()) {
        EditSheetHeader(actions)
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val coverAndActions: @Composable () -> Unit = {
                EditMangaCover(state, actions)
                Column(
                    modifier = if (state.coverWide) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { state.coverPickerOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.choose_cover))
                    }
                    FilledTonalButton(
                        onClick = actions.onAutofill,
                        enabled = !state.autofillBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.autofill_from_tracker))
                    }
                    if (!state.isLocal) {
                        TextButton(
                            onClick = { state.cover = CoverChange.Reset },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.reset_cover))
                        }
                    }
                }
            }
            if (state.coverWide) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { coverAndActions() }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { coverAndActions() }
            }

            val originalTitle = if (state.isLocal) manga.url else manga.originalTitle
            EditTextField(
                value = state.title,
                onValueChange = { state.title = it },
                label = stringResource(R.string.title),
                original = originalTitle,
                canRestore = !state.isLocal,
                singleLine = true,
                onSync = syncOf(TrackMangaMetadata.Field.TITLE),
            )
            if (state.isLocal) {
                EditTextField(
                    state.author,
                    { state.author = it },
                    stringResource(R.string.author),
                    singleLine = true,
                    onSync = syncOf(TrackMangaMetadata.Field.AUTHORS),
                )
                EditTextField(
                    state.artist,
                    { state.artist = it },
                    stringResource(R.string.artist),
                    singleLine = true,
                    onSync = syncOf(TrackMangaMetadata.Field.ARTISTS),
                )
                EditTextField(
                    state.description,
                    { state.description = it },
                    stringResource(R.string.description),
                    onSync = syncOf(TrackMangaMetadata.Field.DESCRIPTION),
                )
            } else {
                EditTextField(
                    state.author,
                    { state.author = it },
                    stringResource(R.string.author),
                    original = manga.originalAuthor,
                    canRestore = true,
                    singleLine = true,
                    onSync = syncOf(TrackMangaMetadata.Field.AUTHORS),
                )
                EditTextField(
                    state.artist,
                    { state.artist = it },
                    stringResource(R.string.artist),
                    original = manga.originalArtist,
                    canRestore = true,
                    singleLine = true,
                    onSync = syncOf(TrackMangaMetadata.Field.ARTISTS),
                )
                EditTextField(
                    state.description,
                    { state.description = it },
                    stringResource(R.string.description),
                    original = manga.originalDescription,
                    canRestore = true,
                    onSync = syncOf(TrackMangaMetadata.Field.DESCRIPTION),
                )
            }

            if (state.isLocal) {
                val languages = state.languages
                DropdownField(
                    label = stringResource(R.string.language),
                    options = languages.map(state.languageName),
                    selected = languages.indexOf(state.lang).coerceAtLeast(0),
                    onSelected = { state.lang = languages[it] },
                )
            }

            val statuses = stringArrayResource(R.array.manga_statuses)
            DropdownField(
                label = stringResource(R.string.status),
                options = statuses.toList(),
                selected = state.status.coerceIn(0, statuses.lastIndex),
                onSelected = { state.status = it },
                supportingText =
                    manga.originalStatus
                        .takeIf { !state.isLocal && it != state.status }
                        ?.let { statuses.getOrNull(it) },
                onSync = syncOf(TrackMangaMetadata.Field.STATUS),
            )

            val types = stringArrayResource(R.array.series_type)
            DropdownField(
                label = stringResource(R.string.series_type),
                options = types.toList(),
                selected = (state.seriesType - 1).coerceIn(0, types.lastIndex),
                onSelected = { state.seriesType = it + 1 },
                supportingText =
                    if (state.resetsReadingMode) {
                        stringResource(R.string.changing_will_reset_reading_mode)
                    } else {
                        null
                    },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                state.tags.forEachIndexed { index, tag ->
                    val isCustom = state.originalGenres.none { it.equals(tag, ignoreCase = true) }
                    var menuOpen by remember { mutableStateOf(false) }
                    InputChip(
                        selected = isCustom,
                        onClick = { menuOpen = true },
                        label = {
                            Text(tag)
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_tag_earlier)) },
                                    enabled = index > 0,
                                    onClick = {
                                        menuOpen = false
                                        state.moveTag(index, index - 1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_tag_later)) },
                                    enabled = index < state.tags.lastIndex,
                                    onClick = {
                                        menuOpen = false
                                        state.moveTag(index, index + 1)
                                    },
                                )
                            }
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(InputChipDefaults.IconSize)
                                        .clip(RoundedCornerShape(50))
                                        .combinedClickable(onClick = { state.setTags(state.tags - tag) }),
                            )
                        },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.pendingTag,
                    onValueChange = { state.pendingTag = it },
                    label = { Text(stringResource(R.string.add_tag)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { state.commitPendingTag() }),
                    modifier = Modifier.weight(1f),
                )
                syncOf(TrackMangaMetadata.Field.TAGS)?.let { SyncIconButton(it) }
            }
            TextButton(onClick = actions.onResetTags) {
                Text(
                    stringResource(
                        if (manga.originalGenre.isNullOrBlank() || state.isLocal) {
                            R.string.clear_tags
                        } else {
                            R.string.reset_tags
                        },
                    ),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    state.syncField?.takeIf { it != TrackMangaMetadata.Field.COVER }?.let { TrackerFieldDialog(state, it, actions) }
    if (state.coverPickerOpen) CoverPickerDialog(state, actions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheetHeader(actions: EditMangaActions) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.edit_details),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.onCancel) { Text(stringResource(android.R.string.cancel)) }
            Button(onClick = actions.onSave) { Text(stringResource(R.string.save)) }
        }
        HorizontalDivider()
    }
}

@Composable
private fun SyncIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.sync_from_tracker))
    }
}

private fun fieldLabelRes(field: TrackMangaMetadata.Field) =
    when (field) {
        TrackMangaMetadata.Field.TITLE -> R.string.title
        TrackMangaMetadata.Field.AUTHORS -> R.string.author
        TrackMangaMetadata.Field.ARTISTS -> R.string.artist
        TrackMangaMetadata.Field.DESCRIPTION -> R.string.description
        TrackMangaMetadata.Field.COVER -> R.string.cover
        TrackMangaMetadata.Field.TAGS -> R.string.tags
        TrackMangaMetadata.Field.STATUS -> R.string.status
    }

/** Lets the user choose which tracker a single field is taken from, showing what each one has. */
@Composable
private fun TrackerFieldDialog(
    state: EditMangaState,
    field: TrackMangaMetadata.Field,
    actions: EditMangaActions,
) {
    val statuses = stringArrayResource(R.array.manga_statuses)
    AlertDialog(
        onDismissRequest = { state.syncField = null },
        title = { Text(stringResource(R.string.sync_field_from_tracker, stringResource(fieldLabelRes(field)))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.trackers.forEach { source ->
                    val data = state.trackerData[source.serviceId]
                    val preview = (data as? TrackerData.Loaded)?.metadata?.let { previewOf(field, it, statuses) }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = preview != null) {
                                    actions.onApplyField(field, source.serviceId)
                                    state.syncField = null
                                }.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(source.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        when {
                            data == null || data is TrackerData.Loading ->
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            data is TrackerData.Failed ->
                                Text(data.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            preview == null ->
                                Text(
                                    stringResource(R.string.tracker_not_provided),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            else -> Text(preview, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { state.syncField = null }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private fun previewOf(
    field: TrackMangaMetadata.Field,
    metadata: TrackMangaMetadata,
    statuses: Array<String>,
): String? =
    when (field) {
        TrackMangaMetadata.Field.TITLE -> metadata.title
        TrackMangaMetadata.Field.AUTHORS -> metadata.authors
        TrackMangaMetadata.Field.ARTISTS -> metadata.artists
        TrackMangaMetadata.Field.DESCRIPTION -> metadata.description
        TrackMangaMetadata.Field.TAGS -> metadata.tags?.joinToString(", ")
        TrackMangaMetadata.Field.STATUS -> metadata.status?.let { statuses.getOrNull(it) }
        TrackMangaMetadata.Field.COVER -> metadata.coverUrl
    }?.takeIf { it.isNotBlank() }

/**
 * A grid of every cover that can be used, like the results of a search: the current one, the source's own and the
 * ones the trackers offer. Picking a tracker's cover downloads it.
 */
@Composable
private fun CoverPickerDialog(
    state: EditMangaState,
    actions: EditMangaActions,
) {
    LaunchedEffect(Unit) { actions.onLoadTrackers() }
    Dialog(
        onDismissRequest = { state.coverPickerOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.choose_cover), style = MaterialTheme.typography.titleLarge)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        state.coverPickerOpen = false
                        actions.onCoverClick()
                    }) { Text(stringResource(R.string.cover_from_gallery)) }
                    if (actions.canPasteCover()) {
                        FilledTonalButton(onClick = {
                            state.coverPickerOpen = false
                            actions.onPasteCover()
                        }) { Text(stringResource(android.R.string.paste)) }
                    }
                }
                if (state.coverBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        CoverCell(
                            label = stringResource(R.string.cover_current),
                            selected = state.cover is CoverChange.None && state.pickedCoverUrl == null,
                            onClick = { state.coverPickerOpen = false },
                            load = { it.loadManga(state.manga) },
                        )
                    }
                    if (!state.isLocal) {
                        item {
                            CoverCell(
                                label = stringResource(R.string.cover_source),
                                selected = state.cover is CoverChange.Reset,
                                onClick = {
                                    state.cover = CoverChange.Reset
                                    state.pickedCoverUrl = null
                                    state.coverPickerOpen = false
                                },
                                load = {
                                    it.load(state.manga) {
                                        parameters(Parameters.Builder().set(MangaCoverFetcher.useCustomCover, false).build())
                                    }
                                },
                            )
                        }
                    }
                    state.trackers.forEach { source ->
                        when (val data = state.trackerData[source.serviceId]) {
                            is TrackerData.Loaded ->
                                items(data.covers, key = { source.serviceId.toString() + it.url }) { cover ->
                                    val size = if (cover.width != null && cover.height != null) " · ${cover.width}x${cover.height}" else ""
                                    CoverCell(
                                        label = coverTitle(cover),
                                        detail = listOfNotNull(cover.note, source.name + size),
                                        badge = cover.language?.uppercase(),
                                        selected = state.pickedCoverUrl == cover.url,
                                        onClick = { actions.onPickCover(cover.url) },
                                        load = { it.load(cover.url) },
                                    )
                                }
                            is TrackerData.Failed ->
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        "${source.name}: ${data.message}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            else ->
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text(source.name, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                        }
                    }
                }
                TextButton(onClick = { state.coverPickerOpen = false }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    }
}

/** "Volume 1", "Season 2" or plain "Cover" for covers the tracker doesn't describe. */
@Composable
private fun coverTitle(cover: TrackMangaMetadata.CoverCandidate): String {
    val index = cover.index
    return when (cover.type) {
        "volume" -> if (index != null) stringResource(R.string.cover_volume, index) else stringResource(R.string.cover_generic)
        "season" -> if (index != null) stringResource(R.string.cover_season, index) else stringResource(R.string.cover_generic)
        null, "other" -> stringResource(R.string.cover_generic) + (index?.let { " $it" } ?: "")
        else -> cover.type.replaceFirstChar { it.uppercase() } + (index?.let { " $it" } ?: "")
    }
}

@Composable
private fun CoverCell(
    label: String,
    detail: List<String> = emptyList(),
    badge: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    load: (ImageView) -> Unit,
) {
    // Same card shape and outline as the covers of the library
    val shape = RoundedCornerShape(12.dp)
    val outline =
        if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    Column(modifier = Modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(shape)
                    .border(outline, shape),
        ) {
            AndroidView(
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { load(it) },
                modifier = Modifier.fillMaxSize(),
            )
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        detail.forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditMangaCover(
    state: EditMangaState,
    actions: EditMangaActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = context.getString(R.string.cover_of_image)
                }
            },
            update = { view ->
                val onLoaded: (android.graphics.drawable.Drawable) -> Unit = {
                    if (it.intrinsicHeight > 0) state.coverWide = it.intrinsicWidth.toFloat() / it.intrinsicHeight >= WIDE_COVER_RATIO
                }
                when (val change = state.cover) {
                    is CoverChange.Custom -> view.load(change.uri) { listener(onSuccess = { _, r -> onLoaded(r.drawable) }) }
                    CoverChange.Reset ->
                        view.load(
                            state.manga,
                            builder = {
                                parameters(Parameters.Builder().set(MangaCoverFetcher.useCustomCover, false).build())
                                listener(onSuccess = { _, r -> onLoaded(r.drawable) })
                            },
                        )
                    CoverChange.None -> view.loadManga(state.manga) { listener(onSuccess = { _, r -> onLoaded(r.drawable) }) }
                }
            },
            modifier =
                Modifier
                    .then(
                        if (state.coverWide) {
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        } else {
                            Modifier.size(width = 112.dp, height = 168.dp)
                        },
                    ).clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { state.coverPickerOpen = true },
                        onLongClick = { if (actions.canPasteCover()) menuOpen = true },
                    ),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(android.R.string.paste)) },
                onClick = {
                    menuOpen = false
                    actions.onPasteCover()
                },
            )
        }
    }
}

@Composable
private fun EditTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    original: String? = null,
    canRestore: Boolean = false,
    singleLine: Boolean = false,
    onSync: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = original?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1) } },
        singleLine = singleLine,
        trailingIcon =
            if ((canRestore && original != null && value != original) || onSync != null) {
                {
                    Row {
                        if (canRestore && original != null && value != original) {
                            IconButton(onClick = { onValueChange(original) }) {
                                Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.restore_original))
                            }
                        }
                        onSync?.let { SyncIconButton(it) }
                    }
                }
            } else {
                null
            },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    supportingText: String? = null,
    onSync: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = options.getOrElse(selected) { "" },
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                supportingText = supportingText?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                modifier =
                    Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        },
                    )
                }
            }
        }
        onSync?.let { SyncIconButton(it) }
    }
}
// KMK <--

// Covers at least this wide (width/height) switch the sheet to a panorama header
private const val WIDE_COVER_RATIO = 1.2f
