package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceCategories
import eu.kanade.tachiyomi.source.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/** Dialogs to manage source categories and to pick the one Global Search is limited to. */
class SourceCategoriesDialog(
    private val activity: Activity,
) {
    private val preferences: PreferencesHelper = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()
    private val store = SourceCategories(preferences)

    /** Lists the categories; picking one edits it, "New category" creates one. */
    fun showManager() {
        val categories = store.all()
        val names = categories.keys.sorted()
        val items = (names + activity.getString(R.string.new_source_category)).toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.source_categories)
            .setItems(items) { _, which ->
                if (which < names.size) edit(names[which], categories) else askName(categories)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Single choice between "no category" and the existing categories. */
    fun showPicker(onPicked: () -> Unit) {
        val names = store.all().keys.sorted()
        if (names.isEmpty()) {
            showManager()
            return
        }
        val items = (listOf(activity.getString(R.string.none)) + names).toTypedArray()
        val current = preferences.globalSearchSourceCategory().get()
        val checked = names.indexOf(current) + 1
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.global_search_source_category)
            .setSingleChoiceItems(items, checked) { dialog, which ->
                preferences.globalSearchSourceCategory().set(if (which == 0) "" else names[which - 1])
                onPicked()
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun askName(categories: Map<String, Set<Long>>) {
        val input = EditText(activity)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.new_source_category)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && name !in categories) edit(name, categories)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun edit(
        name: String,
        categories: Map<String, Set<Long>>,
    ) {
        val enabledLanguages = preferences.enabledLanguages().get()
        val sources =
            sourceManager
                .getCatalogueSources()
                .filter { it.lang in enabledLanguages }
                .sortedBy { "(${it.lang}) ${it.name}" }
        val members = categories[name].orEmpty()
        val checked = BooleanArray(sources.size) { sources[it].id in members }
        val labels = sources.map { "${it.name} (${it.lang})" }.toTypedArray()
        val builder =
            MaterialAlertDialogBuilder(activity)
                .setTitle(name)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
                .setPositiveButton(R.string.save) { _, _ ->
                    val ids = sources.filterIndexed { index, _ -> checked[index] }.map { it.id }.toSet()
                    store.save(categories + (name to ids))
                }.setNegativeButton(android.R.string.cancel, null)
        if (name in categories) {
            builder.setNeutralButton(R.string.delete) { _, _ -> store.save(categories - name) }
        }
        builder.show()
    }
}
// KMK <--
