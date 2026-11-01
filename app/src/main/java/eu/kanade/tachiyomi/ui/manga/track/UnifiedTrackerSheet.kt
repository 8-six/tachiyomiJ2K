package eu.kanade.tachiyomi.ui.manga.track

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.widget.ComposeBottomSheetDialog

// KMK -->

/** Edits status, progress and score of every linked tracker in one place. */
object UnifiedTrackerSheet {
    fun show(
        activity: ComponentActivity,
        items: List<TrackItem>,
        onStatus: (TrackItem, Int) -> Unit,
        onProgress: (TrackItem, Int) -> Unit,
        onScore: (TrackItem, Int) -> Unit,
    ) {
        val dialog = ComposeBottomSheetDialog(activity)
        dialog.setContent {
            UnifiedTrackerContent(items, onStatus, onProgress, onScore)
        }
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }
}

@Composable
private fun UnifiedTrackerContent(
    items: List<TrackItem>,
    onStatus: (TrackItem, Int) -> Unit,
    onProgress: (TrackItem, Int) -> Unit,
    onScore: (TrackItem, Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tracking), style = MaterialTheme.typography.titleLarge)
        items.filter { it.track != null }.forEach { item ->
            TrackerCard(item, onStatus, onProgress, onScore)
        }
    }
}

@Composable
private fun TrackerCard(
    item: TrackItem,
    onStatus: (TrackItem, Int) -> Unit,
    onProgress: (TrackItem, Int) -> Unit,
    onScore: (TrackItem, Int) -> Unit,
) {
    val track = item.track ?: return
    val service = item.service
    val statuses = remember(service) { service.getStatusList() }
    val scores = remember(service) { service.getScoreList() }
    var status by remember { mutableStateOf(track.status) }
    var progress by remember { mutableStateOf(track.last_chapter_read.toInt()) }
    var scoreLabel by remember { mutableStateOf(service.displayScore(track)) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(service.nameRes()), style = MaterialTheme.typography.titleMedium)
            Text(track.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.status), modifier = Modifier.weight(1f))
                Picker(
                    label = service.getStatus(status),
                    options = statuses.map { service.getStatus(it) },
                ) { index ->
                    status = statuses[index]
                    if (service.isCompletedStatus(index) && track.total_chapters > 0) progress = track.total_chapters
                    onStatus(item, index)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chapters), modifier = Modifier.weight(1f))
                IconButton(
                    enabled = progress > 0,
                    onClick = {
                        progress -= 1
                        onProgress(item, progress)
                    },
                ) { Icon(Icons.Filled.Remove, contentDescription = null) }
                Text(if (track.total_chapters > 0) "$progress / ${track.total_chapters}" else "$progress")
                IconButton(
                    enabled = track.total_chapters <= 0 || progress < track.total_chapters,
                    onClick = {
                        progress += 1
                        onProgress(item, progress)
                    },
                ) { Icon(Icons.Filled.Add, contentDescription = null) }
            }

            if (scores.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.score), modifier = Modifier.weight(1f))
                    Picker(label = scoreLabel, options = scores) { index ->
                        scoreLabel = scores[index]
                        onScore(item, index)
                    }
                }
            }
        }
    }
}

@Composable
private fun Picker(
    label: String,
    options: List<String>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(label) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        options.forEachIndexed { index, text ->
            DropdownMenuItem(
                text = { Text(text) },
                onClick = {
                    open = false
                    onPick(index)
                },
            )
        }
    }
}
// KMK <--
