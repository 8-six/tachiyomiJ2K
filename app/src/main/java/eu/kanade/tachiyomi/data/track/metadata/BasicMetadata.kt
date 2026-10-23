package eu.kanade.tachiyomi.data.track.metadata

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch

/** For trackers that only give what a search result has: title, summary, cover, status and type. */
fun TrackSearch.toBasicMetadata(): TrackMangaMetadata =
    TrackMangaMetadata(
        remoteId = media_id,
        title = title.takeIf { it.isNotBlank() },
        coverUrl = cover_url.takeIf { it.isNotBlank() },
        description = MetadataText.cleanDescription(summary),
        status = MetadataText.status(publishing_status),
        seriesType = TrackMangaMetadata.SeriesType.from(publishing_type),
    )
