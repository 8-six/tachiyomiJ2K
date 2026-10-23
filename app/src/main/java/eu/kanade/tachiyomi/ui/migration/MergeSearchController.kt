package eu.kanade.tachiyomi.ui.migration

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import androidx.core.os.bundleOf
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchCardAdapter
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Search through all sources to pick the manga to merge with [manga]. Tapping a result picks it and goes back to the
 * details of [manga] (the target controller), long pressing opens its details.
 */
class MergeSearchController(
    private var manga: Manga? = null,
) : GlobalSearchController(
        manga?.originalTitle,
        bundle = bundleOf(MANGA to manga?.id),
    ) {
    init {
        setHasOptionsMenu(true)
    }

    constructor(mangaId: Long) : this(Injekt.get<DatabaseHelper>().getManga(mangaId).executeAsBlocking())

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getLong(MANGA))

    override val presenter = SearchPresenter(initialQuery, manga!!)

    // picking a series to merge isn't a browse search, keep it out of the history
    override val supportsSearchHistory: Boolean = false

    override fun onMangaClick(manga: Manga) {
        val target = targetController as? MangaDetailsController
        router.popCurrentController()
        target?.onMergePicked(manga)
    }

    override fun onMangaLongClick(
        position: Int,
        adapter: GlobalSearchCardAdapter,
    ) {
        // Look at it before deciding
        val manga = adapter.getItem(position)?.manga ?: return
        super.onMangaClick(manga)
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.catalogue_new_list, menu)

        setOnQueryTextChangeListener(activityBinding?.searchToolbar?.searchView, onlyOnSubmit = true, hideKbOnSubmit = true) {
            presenter.search(it ?: "")
            setTitle()
            true
        }
    }

    companion object {
        const val MANGA = "manga"
    }
}
