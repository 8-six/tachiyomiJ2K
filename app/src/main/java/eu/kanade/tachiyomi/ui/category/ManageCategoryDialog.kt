package eu.kanade.tachiyomi.ui.category

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.fredporciuncula.flow.preferences.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MangaCategoryDialogBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.library.LibrarySort
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import uy.kohesive.injekt.injectLazy

class ManageCategoryDialog(
    bundle: Bundle? = null,
) : DialogController(bundle) {
    constructor(category: Category?, updateLibrary: ((Int?) -> Unit)) : this() {
        this.updateLibrary = updateLibrary
        this.category = category
    }

    private var updateLibrary: ((Int?) -> Unit)? = null
    private var category: Category? = null

    // KMK -->
    private var selectedParentId = 0
    private var selectedTrackerStatus = 0
    private var selectedInterval = 0
    // KMK <--

    private val preferences by injectLazy<PreferencesHelper>()
    private val db by injectLazy<DatabaseHelper>()
    lateinit var binding: MangaCategoryDialogBinding

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val dialog = dialog(activity!!).create()
        onViewCreated()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                if (onPositiveButtonClick()) {
                    dialog.dismiss()
                }
            }
        }
        return dialog
    }

    fun dialog(activity: Activity): MaterialAlertDialogBuilder =
        activity.materialAlertDialog().apply {
            setTitle(if (category == null) R.string.new_category else R.string.manage_category)
            binding = MangaCategoryDialogBinding.inflate(activity.layoutInflater)
            setView(binding.root)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(R.string.save) { dialog, _ ->
                if (onPositiveButtonClick()) {
                    dialog.dismiss()
                }
            }
        }

    fun show(activity: Activity) {
        val dialog = dialog(activity).create()
        onViewCreated()
        dialog.setOnShowListener {
            binding.title.requestFocus()
        }
        dialog.show()
    }

    private fun onPositiveButtonClick(): Boolean {
        val text = binding.title.text.toString()
        val categoryExists = categoryExists(text)
        val category = this.category ?: Category.create(text)
        if (category.id != 0) {
            if (text.isNotBlank() &&
                !categoryExists &&
                !text.equals(this.category?.name ?: "", true)
            ) {
                category.name = text
                if (this.category == null) {
                    val categories = db.getCategories().executeAsBlocking()
                    category.order = (categories.maxOfOrNull { it.order } ?: 0) + 1
                    category.mangaSort = LibrarySort.Title.categoryValue
                    val dbCategory = db.insertCategory(category).executeAsBlocking()
                    category.id = dbCategory.insertedId()?.toInt()
                    this.category = category
                } else {
                    db.insertCategory(category).executeAsBlocking()
                }
            } else if (categoryExists) {
                binding.categoryTextLayout.error =
                    binding.categoryTextLayout.context.getString(R.string.category_with_name_exists)
                return false
            } else if (text.isBlank()) {
                binding.categoryTextLayout.error =
                    binding.categoryTextLayout.context.getString(R.string.category_cannot_be_blank)
                return false
            }
        }
        when (
            updatePref(
                preferences.downloadNewChaptersInCategories(),
                preferences.excludeCategoriesInDownloadNew(),
                binding.downloadNew,
            )
        ) {
            true -> preferences.downloadNewChapters().set(true)
            false -> preferences.downloadNewChapters().set(false)
            else -> {}
        }
        if (preferences.libraryUpdateInterval().get() > 0 &&
            updatePref(
                preferences.libraryUpdateCategories(),
                preferences.libraryUpdateCategoriesExclude(),
                binding.includeGlobal,
            ) == false
        ) {
            preferences.libraryUpdateInterval().set(0)
            LibraryUpdateJob.setupTask(preferences.context, 0)
        }
        // KMK -->
        if (category.id != null &&
            category.id != 0 &&
            category.id != -1 &&
            (
                category.parentId != selectedParentId ||
                    category.trackerStatus != selectedTrackerStatus ||
                    category.updateInterval != selectedInterval
            )
        ) {
            category.parentId = selectedParentId
            category.trackerStatus = selectedTrackerStatus
            category.updateInterval = selectedInterval
            db.insertCategory(category).executeAsBlocking()
        }
        // KMK <--
        updateLibrary?.invoke(category.id)
        return true
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun categoryExists(name: String): Boolean =
        db.getCategories().executeAsBlocking().any {
            it.name.equals(name, true) && category?.id != it.id
        }

    fun onViewCreated() {
        if (category?.id ?: 0 <= 0 && category != null) {
            binding.categoryTextLayout.isVisible = false
        }
        binding.editCategories.isVisible = category != null
        // KMK -->
        setupParentPicker()
        setupSmartPickers()
        // KMK <--
        binding.editCategories.setOnClickListener {
            router.popCurrentController()
            router.pushController(CategoryController().withFadeTransaction())
        }
        binding.title.addTextChangedListener {
            binding.categoryTextLayout.error = null
        }
        binding.title.hint =
            category?.name ?: binding.editCategories.context.getString(R.string.category)
        binding.title.append(category?.name ?: "")
        val downloadNew = preferences.downloadNewChapters().get()
        setCheckbox(
            binding.downloadNew,
            preferences.downloadNewChaptersInCategories(),
            preferences.excludeCategoriesInDownloadNew(),
            true,
        )
        if (downloadNew && preferences.downloadNewChaptersInCategories().get().isEmpty()) {
            binding.downloadNew.isVisible = false
        } else if (!downloadNew) {
            binding.downloadNew.isVisible = true
        }
        if (!downloadNew) {
            binding.downloadNew.isChecked = false
        }
        setCheckbox(
            binding.includeGlobal,
            preferences.libraryUpdateCategories(),
            preferences.libraryUpdateCategoriesExclude(),
            preferences.libraryUpdateInterval().get() > 0,
        )
    }

    // KMK -->
    private fun setupSmartPickers() {
        val current = category
        val statusButton = binding.smartStatus
        val intervalButton = binding.updateInterval
        if (current != null && (current.id ?: 0) <= 0) {
            statusButton.isVisible = false
            intervalButton.isVisible = false
            return
        }
        selectedTrackerStatus = current?.trackerStatus ?: 0
        selectedInterval = current?.updateInterval ?: 0
        val statuses =
            listOf(
                0 to R.string.none,
                eu.kanade.tachiyomi.data.track.SmartCategorySync.STATUS_READING to R.string.reading,
                eu.kanade.tachiyomi.data.track.SmartCategorySync.STATUS_COMPLETED to R.string.completed,
                eu.kanade.tachiyomi.data.track.SmartCategorySync.STATUS_PLANNING to R.string.plan_to_read,
            )
        val intervals =
            listOf(
                0 to R.string.follow_global_schedule,
                6 to R.string.every_6_hours,
                12 to R.string.every_12_hours,
                24 to R.string.daily,
                48 to R.string.every_2_days,
                72 to R.string.every_3_days,
                168 to R.string.weekly,
            )

        fun refresh() {
            val ctx = statusButton.context
            statusButton.text =
                ctx.getString(
                    R.string.smart_category_status_value,
                    ctx.getString(statuses.first { it.first == selectedTrackerStatus }.second),
                )
            intervalButton.text =
                ctx.getString(
                    R.string.category_update_schedule_value,
                    ctx.getString(
                        intervals.firstOrNull { it.first == selectedInterval }?.second ?: R.string.follow_global_schedule,
                    ),
                )
        }
        refresh()
        statusButton.setOnClickListener {
            val ctx = it.context
            ctx
                .materialAlertDialog()
                .setTitle(R.string.smart_category_status)
                .setSingleChoiceItems(
                    statuses.map { s -> ctx.getString(s.second) }.toTypedArray(),
                    statuses.indexOfFirst { s ->
                        s.first ==
                            selectedTrackerStatus
                    },
                ) { dialog, which ->
                    selectedTrackerStatus = statuses[which].first
                    refresh()
                    dialog.dismiss()
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        intervalButton.setOnClickListener {
            val ctx = it.context
            ctx
                .materialAlertDialog()
                .setTitle(R.string.category_update_schedule)
                .setSingleChoiceItems(
                    intervals.map { s -> ctx.getString(s.second) }.toTypedArray(),
                    intervals
                        .indexOfFirst { s ->
                            s.first ==
                                selectedInterval
                        }.coerceAtLeast(0),
                ) { dialog, which ->
                    selectedInterval = intervals[which].first
                    refresh()
                    dialog.dismiss()
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupParentPicker() {
        val current = category
        val button = binding.parentCategory
        if (current != null && (current.id ?: 0) <= 0) {
            button.isVisible = false
            return
        }
        selectedParentId = current?.parentId ?: 0
        val all = db.getCategories().executeAsBlocking().filter { (it.id ?: 0) > 0 }

        // A category can't become a child of itself or of one of its own descendants.
        fun descendantIds(id: Int): Set<Int> {
            val children = all.filter { it.parentId == id }.mapNotNull { it.id }
            return children.toSet() + children.flatMap { descendantIds(it) }
        }
        val blocked = current?.id?.let { descendantIds(it) + it }.orEmpty()
        val options = all.filter { it.id !in blocked }

        fun refreshLabel() {
            val name =
                all.firstOrNull { it.id == selectedParentId }?.name
                    ?: button.context.getString(R.string.parent_category_none)
            button.text = button.context.getString(R.string.parent_category_value, name)
        }
        refreshLabel()
        button.setOnClickListener {
            val labels =
                (listOf(button.context.getString(R.string.parent_category_none)) + options.map { it.name }).toTypedArray()
            val checked = options.indexOfFirst { it.id == selectedParentId } + 1
            button.context
                .materialAlertDialog()
                .setTitle(R.string.parent_category)
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    selectedParentId = if (which == 0) 0 else options[which - 1].id ?: 0
                    refreshLabel()
                    dialog.dismiss()
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
    // KMK <--

    /** Update a pref based on checkbox, and return if the pref is not empty */
    private fun updatePref(
        categories: Preference<Set<String>>,
        excludeCategories: Preference<Set<String>>,
        box: TriStateCheckBox,
    ): Boolean? {
        val categoryId = category?.id ?: return null
        if (!box.isVisible) return null
        val updateCategories = categories.get().toMutableSet()
        val excludeUpdateCategories = excludeCategories.get().toMutableSet()
        when (box.state) {
            TriStateCheckBox.State.CHECKED -> {
                updateCategories.add(categoryId.toString())
                excludeUpdateCategories.remove(categoryId.toString())
            }
            TriStateCheckBox.State.IGNORE -> {
                updateCategories.remove(categoryId.toString())
                excludeUpdateCategories.add(categoryId.toString())
            }
            TriStateCheckBox.State.UNCHECKED -> {
                updateCategories.remove(categoryId.toString())
                excludeUpdateCategories.remove(categoryId.toString())
            }
        }
        categories.set(updateCategories)
        excludeCategories.set(excludeUpdateCategories)
        return updateCategories.isNotEmpty()
    }

    private fun setCheckbox(
        box: TriStateCheckBox,
        categories: Preference<Set<String>>,
        excludeCategories: Preference<Set<String>>,
        shouldShow: Boolean,
    ) {
        val updateCategories = categories.get()
        val excludeUpdateCategories = excludeCategories.get()
        box.isVisible = (updateCategories.isNotEmpty() || excludeUpdateCategories.isNotEmpty()) && shouldShow
        if (shouldShow) {
            box.state =
                when {
                    updateCategories.any { category?.id == it.toIntOrNull() } -> TriStateCheckBox.State.CHECKED
                    excludeUpdateCategories.any { category?.id == it.toIntOrNull() } -> TriStateCheckBox.State.IGNORE
                    else -> TriStateCheckBox.State.UNCHECKED
                }
        }
    }
}
