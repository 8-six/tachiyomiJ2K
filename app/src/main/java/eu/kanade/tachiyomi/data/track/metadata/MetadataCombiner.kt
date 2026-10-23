package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.Field
import kotlinx.coroutines.CancellationException

/**
 * Asks the trackers of a series for its details in turn and combines what they say: the first tracker that has a detail
 * provides it. Only as many trackers are asked as it takes.
 */
object MetadataCombiner {
    class Source<T>(
        val tracker: T,
        /** The details this tracker may provide, according to the settings. */
        val fields: Set<Field>,
    )

    class Result(
        /** What the trackers said together, or null if none could be asked. */
        val metadata: TrackMangaMetadata?,
        /** Which trackers couldn't be asked and why. */
        val failures: List<Pair<Int, String>>,
    )

    /**
     * @param sources in the order they should be asked.
     * @param wanted the details that are to be synced at all.
     * @param fillGaps whether trackers after the first are asked for what the first lacks. If not, only one is asked.
     * @param wantAllCovers whether every tracker with a cover is asked, as the best of all covers is wanted and not the
     *   first. Trackers are then asked even when everything else is there already.
     * @param fetch gets the details of a tracker, already limited to what it may provide. Its failure is kept in
     *   [Result.failures] and doesn't stop the others.
     * @param idOf the id that failures are reported by.
     */
    suspend fun <T> combine(
        sources: List<Source<T>>,
        wanted: Set<Field>,
        fillGaps: Boolean,
        wantAllCovers: Boolean,
        idOf: (T) -> Int,
        fetch: suspend (T) -> TrackMangaMetadata,
    ): Result {
        var merged: TrackMangaMetadata? = null
        val failures = mutableListOf<Pair<Int, String>>()
        var askedOne = false

        for (source in sources) {
            // What this tracker could add that isn't there yet
            val missing = wanted.filter { merged?.has(it) != true }.toSet()
            val providesCover = wantAllCovers && Field.COVER in wanted && Field.COVER in source.fields
            val useful = providesCover || source.fields.any { it in missing }
            if (!useful) continue
            // Without filling gaps the first one is all there is, apart from covers if they are compared
            if (askedOne && !fillGaps && !providesCover) continue

            try {
                val metadata = fetch(source.tracker)
                merged = merged?.fillGapsFrom(metadata) ?: metadata
                askedOne = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failures += idOf(source.tracker) to (e.message ?: e.javaClass.simpleName)
            }
        }
        return Result(merged, failures)
    }
}
