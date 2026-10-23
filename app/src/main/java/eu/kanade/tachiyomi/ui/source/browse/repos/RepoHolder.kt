package eu.kanade.tachiyomi.ui.source.browse.repos

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.RepoItemBinding
import eu.kanade.tachiyomi.extension.model.RepoMetadata
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * Holder used to display repo items.
 *
 * @param view The view used by repo items.
 * @param adapter The adapter containing this holder.
 */
class RepoHolder(
    view: View,
    val adapter: RepoAdapter,
) : FlexibleViewHolder(view, adapter) {
    private val binding = RepoItemBinding.bind(view)

    init {
        binding.editButton.setOnClickListener {
            submitChanges()
        }
        binding.discordButton.setOnClickListener {
            adapter.repoItemListener.onDiscordClick(flexibleAdapterPosition)
        }
        binding.toggleButton.setOnClickListener {
            adapter.repoItemListener.onToggleEnabled(flexibleAdapterPosition)
        }
    }

    private var createRepo = false
    private var hasDiscord = false
    private var isLoading = false
    private var isRepoEnabled = true
    private var regularDrawable: Drawable? = null

    /**
     * Binds this holder with the given repo.
     *
     * @param repo The repo URL.
     * @param metadata The repo's cached display metadata, if it's been fetched.
     */
    fun bind(
        repo: String,
        metadata: RepoMetadata?,
        enabled: Boolean = true,
    ) {
        isRepoEnabled = enabled
        // Set capitalized title.
        binding.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitChanges()
            }
            true
        }
        createRepo = repo == RepoPresenter.CREATE_REPO_ITEM
        hasDiscord = metadata?.discordUrl != null
        if (createRepo) {
            binding.title.text = itemView.context.getString(R.string.action_add_repo)
            binding.title.setTextColor(
                ContextCompat.getColor(itemView.context, R.color.material_on_background_disabled),
            )
            regularDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.ic_add_24dp)
            binding.editButton.icon = null
            binding.editText.setText(adapter.pendingUrl.orEmpty())
            adapter.pendingUrl = null
            binding.editText.hint = ""
            binding.discordButton.isVisible = false
            binding.toggleButton.isVisible = false
            itemView.alpha = 1f
            binding.title.paintFlags = binding.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        } else {
            binding.title.text = metadata?.name?.takeIf { it.isNotBlank() } ?: repo
            binding.title.maxLines = 2
            binding.title.setTextColor(itemView.context.getResourceColor(R.attr.colorOnBackground))
            regularDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.ic_open_in_webview_24dp)
            binding.openStoreButton.setOnClickListener {
                adapter.repoItemListener.onLogoClick(flexibleAdapterPosition)
            }
            binding.editText.setText(repo)
            binding.toggleButton.icon =
                ContextCompat.getDrawable(
                    itemView.context,
                    if (enabled) R.drawable.ic_eye_24dp else R.drawable.ic_eye_off_24dp,
                )
            binding.toggleButton.contentDescription =
                itemView.context.getString(if (enabled) R.string.disable_repo else R.string.enable_repo)
            binding.toggleButton.tooltipText = binding.toggleButton.contentDescription
            itemView.alpha = if (enabled) 1f else 0.5f
            binding.title.paintFlags =
                if (enabled) {
                    binding.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                } else {
                    binding.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun isEditing(editing: Boolean) {
        itemView.isActivated = editing
        binding.title.isInvisible = editing
        binding.editText.isInvisible = !editing
        binding.discordButton.isVisible = !editing && !createRepo && hasDiscord
        binding.toggleButton.isVisible = !editing && !createRepo
        if (editing) {
            binding.editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
            binding.editText.requestFocus()
            binding.editText.selectAll()
            binding.editButton.icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_check_24dp)
            binding.editButton.iconTint =
                ColorStateList.valueOf(itemView.context.getResourceColor(R.attr.colorPrimary))
            showKeyboard()
            if (!createRepo) {
                binding.openStoreButton.icon =
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_delete_24dp)
                binding.openStoreButton.tooltipText = itemView.context.getString(R.string.delete)
                binding.openStoreButton.setOnClickListener {
                    adapter.repoItemListener.onItemDelete(flexibleAdapterPosition)
                    hideKeyboard()
                }
            }
        } else {
            if (!createRepo) {
                binding.openStoreButton.setOnClickListener {
                    adapter.repoItemListener.onLogoClick(flexibleAdapterPosition)
                }
                binding.openStoreButton.tooltipText = itemView.context.getString(R.string.website)
                binding.editButton.icon =
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_edit_24dp)
            } else {
                binding.editButton.icon = null
                binding.openStoreButton.setOnTouchListener { _, _ -> true }
            }
            binding.editText.clearFocus()
            binding.editButton.iconTint =
                ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.gray_button))
            binding.openStoreButton.icon = regularDrawable
        }
    }

    /**
     * Shows a spinner in place of the edit/checkmark button while this repo is being
     * validated over the network, e.g. right after the user confirms an add or rename.
     */
    fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.editProgress.isVisible = loading
        binding.editButton.isInvisible = loading
        binding.editText.isEnabled = !loading
    }

    private fun submitChanges() {
        if (binding.editText.isVisible) {
            if (isLoading) return
            adapter.repoItemListener.onRepoRename(flexibleAdapterPosition, binding.editText.text.toString())
        } else {
            itemView.performClick()
        }
        hideKeyboard()
    }

    private fun showKeyboard() {
        val inputMethodManager = itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(binding.editText, WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
    }

    private fun hideKeyboard() {
        val inputMethodManager = itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.editText.windowToken, 0)
    }
}
