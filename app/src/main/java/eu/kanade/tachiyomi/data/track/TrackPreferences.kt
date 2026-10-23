package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.preference.Preference
import eu.kanade.tachiyomi.data.preference.PreferenceStore
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import eu.kanade.tachiyomi.data.track.metadata.MetadataApply
import eu.kanade.tachiyomi.data.track.metadata.MetadataOptions

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun trackUsername(sync: TrackService) = preferenceStore.getString(trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = preferenceStore.getString(trackPassword(sync.id), "")

    fun setCredentials(
        sync: TrackService,
        username: String,
        password: String,
    ) {
        trackUsername(sync).set(username)
        trackPassword(sync).set(password)
    }

    fun trackToken(sync: TrackService) = preferenceStore.getString(trackToken(sync.id), "")

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun mangabakaScoreType() = preferenceStore.getString("mangabaka_score_type", MangaBaka.STEP_1)

    /** Which details are synced from trackers, see [eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata.Field]. Empty is all. */
    fun metadataSyncFields() = preferenceStore.getStringSet("tracker_metadata_sync_fields", emptySet())

    fun autoSyncMetadata() = preferenceStore.getBoolean("tracker_auto_sync_metadata", false)

    /** Also sync details whenever tracking is refreshed, not only when a tracker is added. */
    fun autoSyncMetadataOnRefresh() = preferenceStore.getBoolean("tracker_auto_sync_metadata_on_refresh", false)

    /** For automatic syncs: replace details the user edited too, instead of only filling in what's empty. */
    fun metadataOverwriteEdits() = preferenceStore.getBoolean("tracker_metadata_overwrite_edits", false)

    /** Which tracker is asked first, a tracker id or [PREFERRED_AUTO]. */
    fun preferredMetadataTracker() = preferenceStore.getString("tracker_preferred_metadata_tracker", PREFERRED_AUTO)

    /** Take what a tracker doesn't have from the next ones. */
    fun metadataFillGaps() = preferenceStore.getBoolean("tracker_metadata_fill_gaps", true)

    fun metadataTagsMode() = preferenceStore.getString("tracker_metadata_tags_mode", MetadataApply.TAGS_REPLACE)

    fun metadataSeriesTypeTag() = preferenceStore.getBoolean("tracker_metadata_series_type_tag", true)

    fun metadataAlternativeTitles() = preferenceStore.getBoolean("tracker_metadata_alternative_titles", false)

    fun mangabakaTitleLanguage() = preferenceStore.getString("mangabaka_title_language", MangaBaka.LANGUAGE_AUTO)

    fun mangabakaCoverMode() = preferenceStore.getString("mangabaka_cover_mode", MangaBaka.COVER_LATEST)

    fun mangabakaCoverLanguage() = preferenceStore.getString("mangabaka_cover_language", MangaBaka.LANGUAGE_ANY)

    fun mangabakaCoverType() = preferenceStore.getString("mangabaka_cover_type", MangaBaka.COVER_TYPE_ANY)

    fun mangabakaCoverSafeOnly() = preferenceStore.getBoolean("mangabaka_cover_safe_only", true)

    /** Tag groups to keep, see [MangaBaka.TAG_GENRES] and the groups of MangaBaka's tag tree. Empty is all. */
    fun mangabakaTagCategories() = preferenceStore.getStringSet("mangabaka_tag_categories", emptySet())

    fun mangabakaHideSpoilerTags() = preferenceStore.getBoolean("mangabaka_hide_spoiler_tags", true)

    fun mangabakaHideExplicitTags() = preferenceStore.getBoolean("mangabaka_hide_explicit_tags", false)

    /** How titles from AniList, MyAnimeList and Kitsu are picked, see [MetadataOptions]. */
    fun trackerTitleStyle() = preferenceStore.getString("tracker_title_style", MetadataOptions.TITLE_AUTO)

    fun trackerHideSpoilerTags() = preferenceStore.getBoolean("tracker_hide_spoiler_tags", true)

    /** AniList only: how relevant (0 to 100) a tag must be to be kept. */
    fun anilistMinTagRank() = preferenceStore.getInt("anilist_min_tag_rank", 50)

    fun metadataOptions() =
        MetadataOptions(
            titleStyle = trackerTitleStyle().get(),
            hideSpoilerTags = trackerHideSpoilerTags().get(),
            minTagRank = anilistMinTagRank().get(),
        )

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    companion object {
        const val PREFERRED_AUTO = "auto"

        fun trackUsername(syncId: Int) = Preference.privateKey("pref_mangasync_username_$syncId")

        private fun trackPassword(syncId: Int) = Preference.privateKey("pref_mangasync_password_$syncId")

        private fun trackToken(syncId: Int) = Preference.privateKey("track_token_$syncId")
    }
}
