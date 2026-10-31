package eu.kanade.tachiyomi.ui.source.feed

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.bluelinelabs.conductor.Router
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceFeed
import eu.kanade.tachiyomi.source.SourceFeeds
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.source.searchhistory.applicableTo
import eu.kanade.tachiyomi.ui.source.searchhistory.applyTo
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.ComposeBottomSheetDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy

// KMK -->

sealed interface FeedState {
    data object Loading : FeedState

    data class Error(
        val message: String,
    ) : FeedState

    data class Success(
        val manga: List<SManga>,
    ) : FeedState
}

/**
 * Feed list plus a loading state per feed. Every feed loads in its own job with its own fresh
 * filter list, so one slow or failing source never blocks or alters another, and a refresh cancels
 * the feed's previous request instead of letting it overwrite the newer result.
 */
class FeedsModel(
    private val activity: ComponentActivity,
) {
    private val preferences: PreferencesHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val store = SourceFeeds(preferences)
    private val jobs = mutableMapOf<Long, Job>()

    val feeds = mutableStateListOf<SourceFeed>()
    val states = mutableStateMapOf<Long, FeedState>()

    init {
        store.pruneSavedSearches(
            preferences
                .savedSearches()
                .get()
                .map { it.id }
                .toSet(),
        )
        feeds.addAll(store.all())
        refreshAll()
    }

    fun refreshAll() = feeds.toList().forEach(::refresh)

    fun refresh(feed: SourceFeed) {
        jobs[feed.id]?.cancel()
        states[feed.id] = FeedState.Loading
        jobs[feed.id] =
            activity.lifecycleScope.launch {
                states[feed.id] =
                    try {
                        FeedState.Success(withIOContext { load(feed) })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        FeedState.Error(e.message ?: e.toString())
                    }
            }
    }

    fun add(feed: SourceFeed) {
        if (store.add(feed)) {
            feeds.add(feed)
            refresh(feed)
        } else {
            activity.toast(R.string.feed_already_added)
        }
    }

    fun remove(feed: SourceFeed) {
        jobs.remove(feed.id)?.cancel()
        states.remove(feed.id)
        feeds.remove(feed)
        store.remove(feed.id)
    }

    fun move(
        feed: SourceFeed,
        offset: Int,
    ) {
        store.move(feed.id, offset)
        feeds.clear()
        feeds.addAll(store.all())
    }

    fun sourceName(feed: SourceFeed) = sourceManager.get(feed.sourceId)?.name ?: feed.sourceId.toString()

    fun typeLabel(feed: SourceFeed): String =
        when (feed.type) {
            SourceFeed.Type.LATEST -> activity.getString(R.string.latest)
            SourceFeed.Type.POPULAR -> activity.getString(R.string.popular)
            SourceFeed.Type.SAVED_SEARCH ->
                preferences
                    .savedSearches()
                    .get()
                    .find { it.id == feed.savedSearchId }
                    ?.let { it.name ?: it.query } ?: activity.getString(R.string.feed_search_missing)
        }

    fun sources() = sourceManager.getCatalogueSources().sortedBy { it.name.lowercase() }

    fun savedSearches(source: CatalogueSource) = preferences.savedSearches().get().applicableTo(source.id)

    private suspend fun load(feed: SourceFeed): List<SManga> {
        val source =
            sourceManager.get(feed.sourceId) as? CatalogueSource
                ?: error(activity.getString(R.string.feed_source_missing))
        return when (feed.type) {
            SourceFeed.Type.LATEST -> source.getLatestUpdates(1)
            SourceFeed.Type.POPULAR -> source.getPopularManga(1)
            SourceFeed.Type.SAVED_SEARCH -> {
                val entry =
                    preferences.savedSearches().get().find { it.id == feed.savedSearchId }
                        ?: error(activity.getString(R.string.feed_search_missing))
                val filters = source.getFilterList()
                entry.filters.applyTo(filters, strict = entry.sourceId == source.id)
                source.getSearchManga(1, entry.query, filters)
            }
        }.mangas
    }
}

class FeedsSheet(
    private val activity: ComponentActivity,
    private val router: Router,
) {
    private val db: DatabaseHelper by injectLazy()

    fun show() {
        val model = FeedsModel(activity)
        val dialog = ComposeBottomSheetDialog(activity)
        dialog.setContent {
            FeedsContent(
                model = model,
                onAdd = { addFeed(model) },
                onOpen = { manga, sourceId ->
                    activity.lifecycleScope.launch {
                        val local = withIOContext { localManga(manga, sourceId) }
                        dialog.dismiss()
                        router.pushController(MangaDetailsController(local, true).withFadeTransaction())
                    }
                },
            )
        }
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    private fun localManga(
        sManga: SManga,
        sourceId: Long,
    ): Manga {
        db.getManga(sManga.url, sourceId).executeAsBlocking()?.let { return it }
        val manga = Manga.create(sManga.url, sManga.title, sourceId)
        manga.copyFrom(sManga)
        manga.id = db.insertManga(manga).executeAsBlocking().insertedId()
        return manga
    }

    private fun addFeed(model: FeedsModel) {
        val sources = model.sources()
        activity
            .materialAlertDialog()
            .setTitle(R.string.feed_pick_source)
            .setItems(sources.map { it.name }.toTypedArray()) { _, which -> pickType(model, sources[which]) }
            .show()
    }

    private fun pickType(
        model: FeedsModel,
        source: CatalogueSource,
    ) {
        val options = mutableListOf<Pair<String, SourceFeed>>()
        val now = System.currentTimeMillis()
        if (source.supportsLatest) {
            options += activity.getString(R.string.latest) to SourceFeed(now, source.id, SourceFeed.Type.LATEST)
        }
        options += activity.getString(R.string.popular) to SourceFeed(now + 1, source.id, SourceFeed.Type.POPULAR)
        model.savedSearches(source).forEachIndexed { index, entry ->
            options += (entry.name ?: entry.query) to SourceFeed(now + 2 + index, source.id, SourceFeed.Type.SAVED_SEARCH, entry.id)
        }
        activity
            .materialAlertDialog()
            .setTitle(R.string.feed_pick_type)
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> model.add(options[which].second) }
            .show()
    }
}

@Composable
private fun FeedsContent(
    model: FeedsModel,
    onAdd: () -> Unit,
    onOpen: (SManga, Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.feeds), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = model::refreshAll) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.feed_refresh_all))
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_feed))
            }
        }
        if (model.feeds.isEmpty()) {
            Text(stringResource(R.string.feed_empty), modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(model.feeds, key = { it.id }) { feed ->
                FeedRow(model, feed, onOpen)
            }
        }
    }
}

@Composable
private fun FeedRow(
    model: FeedsModel,
    feed: SourceFeed,
    onOpen: (SManga, Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(model.sourceName(feed), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(model.typeLabel(feed), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { model.move(feed, -1) }) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.feed_move_up))
            }
            IconButton(onClick = { model.move(feed, 1) }) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.feed_move_down))
            }
            IconButton(onClick = { model.remove(feed) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.feed_remove))
            }
        }
        when (val state = model.states[feed.id] ?: FeedState.Loading) {
            FeedState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
            is FeedState.Error ->
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { model.refresh(feed) }) { Text(stringResource(R.string.retry)) }
                }
            is FeedState.Success ->
                if (state.manga.isEmpty()) {
                    Text(stringResource(R.string.feed_no_results), modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    LazyRow(
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.manga, key = { it.url }) { manga ->
                            FeedCover(manga, feed.sourceId) { onOpen(manga, feed.sourceId) }
                        }
                    }
                }
        }
    }
}

@Composable
private fun FeedCover(
    sManga: SManga,
    sourceId: Long,
    onClick: () -> Unit,
) {
    Column(Modifier.width(96.dp).clickable(onClick = onClick)) {
        AndroidView(
            factory = { context ->
                android.widget.ImageView(context).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP }
            },
            update = { view ->
                val manga: Manga =
                    Manga.create(sManga.url, sManga.title, sourceId).also { it.copyFrom(sManga) }
                view.loadManga(manga)
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
        )
        Text(sManga.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
// KMK <--
