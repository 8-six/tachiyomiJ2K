package eu.kanade.tachiyomi.ui.setting

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackPreferences
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.metadata.MetadataApply
import eu.kanade.tachiyomi.data.track.metadata.MetadataOptions
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy
import java.util.Locale

// KMK -->

/** What tracker details are synced and how, split off from the tracking settings. */
class SettingsTrackerDetailsController : SettingsController() {
    private val trackManager: TrackManager by injectLazy()
    private val trackPreferences: TrackPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.tracker_details

            preferenceCategory {
                titleRes = R.string.tracker_details_sync

                switchPreference {
                    key = trackPreferences.autoSyncMetadata().key()
                    titleRes = R.string.auto_sync_tracker_details
                    summaryRes = R.string.auto_sync_tracker_details_summary
                    defaultValue = false
                }
                multiSelectListPreferenceMat(activity) {
                    key = trackPreferences.metadataSyncFields().key()
                    titleRes = R.string.tracker_details_to_sync
                    entriesRes =
                        arrayOf(
                            R.string.title,
                            R.string.author,
                            R.string.artist,
                            R.string.description,
                            R.string.cover,
                            R.string.tags,
                            R.string.status,
                        )
                    entryValues = TrackMangaMetadata.Field.entries.map { it.key }
                    allSelectionRes = R.string.all
                    defaultValue = emptySet<String>()
                }
                listPreference(activity) {
                    key = trackPreferences.preferredMetadataTracker().key()
                    titleRes = R.string.tracker_details_preferred
                    val services = trackManager.metadataPriority
                    entries =
                        listOf(context.getString(R.string.tracker_details_preferred_auto)) +
                        services.map { context.getString(it.nameRes()) }
                    entryValues = listOf(TrackPreferences.PREFERRED_AUTO) + services.map { it.id.toString() }
                    defaultValue = TrackPreferences.PREFERRED_AUTO
                }
                switchPreference {
                    key = trackPreferences.metadataFillGaps().key()
                    titleRes = R.string.tracker_details_fill_gaps
                    summaryRes = R.string.tracker_details_fill_gaps_summary
                    defaultValue = true
                }
                switchPreference {
                    key = trackPreferences.autoSyncMetadataOnRefresh().key()
                    titleRes = R.string.auto_sync_tracker_details_on_refresh
                    summaryRes = R.string.auto_sync_tracker_details_on_refresh_summary
                    defaultValue = false
                }
                switchPreference {
                    key = trackPreferences.metadataOverwriteEdits().key()
                    titleRes = R.string.tracker_details_overwrite_edits
                    summaryRes = R.string.tracker_details_overwrite_edits_summary
                    defaultValue = false
                }
            }
            preferenceCategory {
                titleRes = R.string.cover

                listPreference(activity) {
                    key = trackPreferences.metadataCoverSelection().key()
                    titleRes = R.string.tracker_details_cover_selection
                    summaryRes = R.string.tracker_details_cover_selection_summary
                    entriesRes = arrayOf(R.string.tracker_details_cover_best_quality, R.string.tracker_details_cover_first_tracker)
                    entryValues = listOf(TrackPreferences.COVER_BEST_QUALITY, TrackPreferences.COVER_FIRST_TRACKER)
                    defaultValue = TrackPreferences.COVER_BEST_QUALITY
                }
                switchPreference {
                    key = trackPreferences.comickLatestCover().key()
                    titleRes = R.string.comick_latest_cover
                    summaryRes = R.string.comick_latest_cover_summary
                    defaultValue = false
                }
            }
            preferenceCategory {
                titleRes = R.string.tracker_details_tags_titles

                listPreference(activity) {
                    key = trackPreferences.metadataTagsMode().key()
                    titleRes = R.string.tracker_details_tags_mode
                    entriesRes = arrayOf(R.string.tracker_details_tags_replace, R.string.tracker_details_tags_add)
                    entryValues = listOf(MetadataApply.TAGS_REPLACE, MetadataApply.TAGS_ADD)
                    defaultValue = MetadataApply.TAGS_REPLACE
                }
                switchPreference {
                    key = trackPreferences.metadataSeriesTypeTag().key()
                    titleRes = R.string.tracker_details_series_type_tag
                    summaryRes = R.string.tracker_details_series_type_tag_summary
                    defaultValue = true
                }
                switchPreference {
                    key = trackPreferences.metadataAlternativeTitles().key()
                    titleRes = R.string.tracker_details_alternative_titles
                    defaultValue = false
                }
                listPreference(activity) {
                    key = trackPreferences.trackerTitleStyle().key()
                    titleRes = R.string.tracker_title_style
                    summaryRes = R.string.tracker_title_style_summary
                    entriesRes =
                        arrayOf(
                            R.string.tracker_title_style_auto,
                            R.string.tracker_title_style_english,
                            R.string.tracker_title_style_romaji,
                            R.string.tracker_title_style_native,
                        )
                    entryValues =
                        listOf(
                            MetadataOptions.TITLE_AUTO,
                            MetadataOptions.TITLE_ENGLISH,
                            MetadataOptions.TITLE_ROMAJI,
                            MetadataOptions.TITLE_NATIVE,
                        )
                    defaultValue = MetadataOptions.TITLE_AUTO
                }
                switchPreference {
                    key = trackPreferences.trackerHideSpoilerTags().key()
                    titleRes = R.string.tracker_hide_spoiler_tags
                    summaryRes = R.string.tracker_hide_spoiler_tags_summary
                    defaultValue = true
                }
                sliderPreference {
                    key = trackPreferences.anilistMinTagRank().key()
                    defaultValue = trackPreferences.anilistMinTagRank().defaultValue()
                    titleRes = R.string.anilist_min_tag_rank
                    summaryRes = R.string.anilist_min_tag_rank_summary
                    entryValues = (0..90 step 10).toList()
                    valueFormatter = { "$it%" }
                }
            }
            preferenceCategory {
                titleRes = R.string.tracker_details_per_tracker

                trackManager.metadataPriority.forEach { service ->
                    val name = context.getString(service.nameRes())
                    switchPreference {
                        key = trackPreferences.metadataTrackerEnabled(service.id).key()
                        title = context.getString(R.string.tracker_details_use_tracker_, name)
                        defaultValue = true
                    }
                }
                infoPreference(R.string.tracker_details_per_tracker_info)
            }
            preferenceCategory {
                titleRes = R.string.mangabaka_details

                listPreference(activity) {
                    key = trackPreferences.mangabakaTitleLanguage().key()
                    titleRes = R.string.title_language
                    val languages = TITLE_LANGUAGES.map { it to languageName(it) }
                    entries =
                        listOf(
                            context.getString(R.string.mangabaka_language_auto),
                            context.getString(R.string.mangabaka_title_native),
                            context.getString(R.string.mangabaka_title_romanized),
                        ) + languages.map { it.second }
                    entryValues =
                        listOf(MangaBaka.LANGUAGE_AUTO, MangaBakaItem.TITLE_NATIVE, MangaBakaItem.TITLE_ROMANIZED) +
                        languages.map { it.first }
                    defaultValue = MangaBaka.LANGUAGE_AUTO
                }
                listPreference(activity) {
                    key = trackPreferences.mangabakaCoverMode().key()
                    titleRes = R.string.mangabaka_cover
                    entriesRes =
                        arrayOf(
                            R.string.mangabaka_cover_default,
                            R.string.mangabaka_cover_latest,
                            R.string.mangabaka_cover_first,
                        )
                    entryValues = listOf(MangaBaka.COVER_DEFAULT, MangaBaka.COVER_LATEST, MangaBaka.COVER_FIRST)
                    defaultValue = MangaBaka.COVER_LATEST
                }
                val coverFilters = mutableListOf<androidx.preference.Preference>()
                coverFilters +=
                    listPreference(activity) {
                        key = trackPreferences.mangabakaCoverLanguage().key()
                        titleRes = R.string.mangabaka_cover_language
                        val languages = COVER_LANGUAGES.map { it to languageName(it) }
                        entries =
                            listOf(
                                context.getString(R.string.mangabaka_cover_language_any),
                                context.getString(R.string.mangabaka_cover_language_original),
                            ) + languages.map { it.second }
                        entryValues = listOf(MangaBaka.LANGUAGE_ANY, MangaBaka.LANGUAGE_ORIGINAL) + languages.map { it.first }
                        defaultValue = MangaBaka.LANGUAGE_ANY
                    }
                coverFilters +=
                    listPreference(activity) {
                        key = trackPreferences.mangabakaCoverType().key()
                        titleRes = R.string.mangabaka_cover_type
                        entriesRes =
                            arrayOf(
                                R.string.mangabaka_cover_type_any,
                                R.string.mangabaka_cover_type_volume,
                                R.string.mangabaka_cover_type_season,
                            )
                        entryValues = listOf(MangaBaka.COVER_TYPE_ANY, "volume", "season")
                        defaultValue = MangaBaka.COVER_TYPE_ANY
                    }
                coverFilters +=
                    switchPreference {
                        key = trackPreferences.mangabakaCoverSafeOnly().key()
                        titleRes = R.string.mangabaka_cover_safe_only
                        defaultValue = true
                    }
                trackPreferences
                    .mangabakaCoverMode()
                    .changes()
                    .onEach { mode -> coverFilters.forEach { it.isVisible = mode != MangaBaka.COVER_DEFAULT } }
                    .launchIn(viewScope)

                multiSelectListPreferenceMat(activity) {
                    key = trackPreferences.mangabakaTagCategories().key()
                    titleRes = R.string.mangabaka_tag_categories
                    entries = MangaBaka.TAG_CATEGORIES
                    entryValues = MangaBaka.TAG_CATEGORIES
                    allSelectionRes = R.string.all
                    defaultValue = emptySet<String>()
                }
                switchPreference {
                    key = trackPreferences.mangabakaHideSpoilerTags().key()
                    titleRes = R.string.mangabaka_hide_spoiler_tags
                    defaultValue = true
                }
                switchPreference {
                    key = trackPreferences.mangabakaHideExplicitTags().key()
                    titleRes = R.string.mangabaka_hide_explicit_tags
                    defaultValue = false
                }
                infoPreference(R.string.mangabaka_details_info)
            }
        }

    private fun languageName(code: String): String =
        Locale.forLanguageTag(code).let { it.getDisplayName(it).replaceFirstChar(Char::uppercaseChar) } + " ($code)"
}
// KMK <--

private val TITLE_LANGUAGES =
    listOf(
        "en",
        "ja-Latn",
        "ja",
        "ko-Latn",
        "ko",
        "zh-Latn",
        "zh",
        "es",
        "pt-br",
        "fr",
        "de",
        "it",
        "id",
        "ru",
        "th",
        "vi",
        "pl",
        "tr",
        "ar",
    )

private val COVER_LANGUAGES = listOf("en", "ja", "ko", "zh", "es", "pt", "fr", "de", "it", "id", "ru", "th", "vi", "pl", "tr", "ar")
