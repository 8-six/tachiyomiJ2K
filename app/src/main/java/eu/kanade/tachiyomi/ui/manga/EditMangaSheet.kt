package eu.kanade.tachiyomi.ui.manga

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.load
import coil.request.Parameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.image.coil.loadManga

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

class EditMangaActions(
    val onCoverClick: () -> Unit,
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
    Column(
        modifier =
            Modifier
                .imePadding()
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
        )
        if (state.isLocal) {
            EditTextField(state.author, { state.author = it }, stringResource(R.string.author), singleLine = true)
            EditTextField(state.artist, { state.artist = it }, stringResource(R.string.artist), singleLine = true)
            EditTextField(state.description, { state.description = it }, stringResource(R.string.description))
        } else {
            EditTextField(
                state.author,
                { state.author = it },
                stringResource(R.string.author),
                original = manga.originalAuthor,
                canRestore = true,
                singleLine = true,
            )
            EditTextField(
                state.artist,
                { state.artist = it },
                stringResource(R.string.artist),
                original = manga.originalArtist,
                canRestore = true,
                singleLine = true,
            )
            EditTextField(
                state.description,
                { state.description = it },
                stringResource(R.string.description),
                original = manga.originalDescription,
                canRestore = true,
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
        OutlinedTextField(
            value = state.pendingTag,
            onValueChange = { state.pendingTag = it },
            label = { Text(stringResource(R.string.add_tag)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { state.commitPendingTag() }),
            modifier = Modifier.fillMaxWidth(),
        )
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = actions.onCancel) { Text(stringResource(android.R.string.cancel)) }
            Button(onClick = actions.onSave) { Text(stringResource(R.string.save)) }
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
                        onClick = actions.onCoverClick,
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = original?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1) } },
        singleLine = singleLine,
        trailingIcon =
            if (canRestore && original != null && value != original) {
                {
                    IconButton(onClick = { onValueChange(original) }) {
                        Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.restore_original))
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
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
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
}
// KMK <--

// Covers at least this wide (width/height) switch the sheet to a panorama header
private const val WIDE_COVER_RATIO = 1.2f
