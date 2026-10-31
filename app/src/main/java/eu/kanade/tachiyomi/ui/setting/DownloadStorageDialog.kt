package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.text.format.Formatter
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.CoroutineScope
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Lists the downloads folder per source folder with the space each one uses, and deletes the ones
 * the user ticks. Only the ticked source folders are touched; nothing outside the downloads
 * directory is ever listed, so unrelated files are safe.
 */
class DownloadStorageDialog(
    private val activity: Activity,
    private val scope: CoroutineScope,
) {
    private val preferences: PreferencesHelper = Injekt.get()
    private val downloadManager: DownloadManager = Injekt.get()

    private class Entry(
        val dir: UniFile,
        val size: Long,
    )

    fun show() {
        activity.toast(R.string.calculating)
        scope.launchIO {
            val root = UniFile.fromUri(activity, preferences.downloadsDirectory().get().toUri())
            val entries =
                root
                    ?.listFiles()
                    .orEmpty()
                    .filter { it.isDirectory }
                    .map { Entry(it, sizeOf(it)) }
                    .sortedByDescending { it.size }
            withUIContext { showList(entries) }
        }
    }

    private fun showList(entries: List<Entry>) {
        if (entries.isEmpty()) {
            activity.toast(R.string.no_downloads_found)
            return
        }
        val total = entries.sumOf { it.size }
        val checked = BooleanArray(entries.size)
        val labels =
            entries
                .map { "${it.dir.name.orEmpty()} - ${Formatter.formatFileSize(activity, it.size)}" }
                .toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.storage_usage_total, Formatter.formatFileSize(activity, total)))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.delete) { _, _ ->
                val selected = entries.filterIndexed { index, _ -> checked[index] }
                if (selected.isNotEmpty()) confirmDelete(selected)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(selected: List<Entry>) {
        val size = Formatter.formatFileSize(activity, selected.sumOf { it.size })
        MaterialAlertDialogBuilder(activity)
            .setMessage(activity.getString(R.string.delete_selected_downloads_message, selected.size, size))
            .setPositiveButton(R.string.delete) { _, _ ->
                scope.launchIO {
                    selected.forEach { it.dir.delete() }
                    downloadManager.refreshCache()
                    withUIContext { activity.toast(R.string.downloads_deleted) }
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sizeOf(file: UniFile): Long =
        if (file.isDirectory) {
            file.listFiles().orEmpty().sumOf { sizeOf(it) }
        } else {
            file.length()
        }
}
// KMK <--
