package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.preference.Preference
import eu.kanade.tachiyomi.data.preference.PreferenceStore
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka

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

    fun mangabakaTitleLanguage() = preferenceStore.getString("mangabaka_title_language", MangaBaka.LANGUAGE_AUTO)

    fun mangabakaCoverMode() = preferenceStore.getString("mangabaka_cover_mode", MangaBaka.COVER_DEFAULT)

    fun mangabakaCoverLanguage() = preferenceStore.getString("mangabaka_cover_language", MangaBaka.LANGUAGE_ANY)

    fun mangabakaCoverType() = preferenceStore.getString("mangabaka_cover_type", MangaBaka.COVER_TYPE_ANY)

    fun mangabakaCoverSafeOnly() = preferenceStore.getBoolean("mangabaka_cover_safe_only", true)

    /** Tag groups to keep, see [MangaBaka.TAG_GENRES] and the groups of MangaBaka's tag tree. Empty is all. */
    fun mangabakaTagCategories() = preferenceStore.getStringSet("mangabaka_tag_categories", emptySet())

    fun mangabakaHideSpoilerTags() = preferenceStore.getBoolean("mangabaka_hide_spoiler_tags", true)

    fun mangabakaHideExplicitTags() = preferenceStore.getBoolean("mangabaka_hide_explicit_tags", false)

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    companion object {
        fun trackUsername(syncId: Int) = Preference.privateKey("pref_mangasync_username_$syncId")

        private fun trackPassword(syncId: Int) = Preference.privateKey("pref_mangasync_password_$syncId")

        private fun trackToken(syncId: Int) = Preference.privateKey("track_token_$syncId")
    }
}
