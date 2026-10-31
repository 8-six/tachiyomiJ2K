package eu.kanade.tachiyomi.ui.manga.chapter

import android.app.Activity
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R

// KMK -->

/** Picks the color of a chapter bookmark from a fixed palette; 0 is the theme default. */
object BookmarkColorDialog {
    private val palette =
        listOf(
            R.string.bookmark_color_default to 0,
            R.string.bookmark_color_red to Color.rgb(0xE5, 0x39, 0x35),
            R.string.bookmark_color_orange to Color.rgb(0xFB, 0x8C, 0x00),
            R.string.bookmark_color_yellow to Color.rgb(0xFD, 0xD8, 0x35),
            R.string.bookmark_color_green to Color.rgb(0x43, 0xA0, 0x47),
            R.string.bookmark_color_blue to Color.rgb(0x1E, 0x88, 0xE5),
            R.string.bookmark_color_purple to Color.rgb(0x8E, 0x24, 0xAA),
            R.string.bookmark_color_pink to Color.rgb(0xEC, 0x40, 0x7A),
        )

    fun show(
        activity: Activity,
        current: Int,
        onPick: (Int) -> Unit,
    ) {
        val labels =
            palette
                .map { (nameRes, color) ->
                    val text = "● " + activity.getString(nameRes)
                    SpannableString(text).apply {
                        if (color != 0) setSpan(ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.bookmark_color)
            .setSingleChoiceItems(labels, palette.indexOfFirst { it.second == current }.coerceAtLeast(0)) { dialog, which ->
                onPick(palette[which].second)
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
// KMK <--
