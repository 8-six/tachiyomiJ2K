package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.hikka.HikkaApi
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBakaApi
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.metadata.MetadataApply
import eu.kanade.tachiyomi.data.track.metadata.MetadataOptions
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.preference.TrackLoginDialog
import eu.kanade.tachiyomi.widget.preference.TrackLogoutDialog
import eu.kanade.tachiyomi.widget.preference.TrackerPreference
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Locale
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsTrackingController :
    SettingsController(),
    TrackLoginDialog.Listener,
    TrackLogoutDialog.Listener {
    private val trackManager: TrackManager by injectLazy()
    val trackPreferences: TrackPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.tracking

            switchPreference {
                key = Keys.autoUpdateTrack
                titleRes = R.string.update_tracking_after_reading
                defaultValue = true
            }
            switchPreference {
                key = Keys.autoSyncProgressFromTrackers
                titleRes = R.string.auto_sync_progress_from_trackers
                summaryRes = R.string.auto_sync_progress_from_trackers_summary
                defaultValue = true
            }
            switchPreference {
                key = Keys.trackMarkedAsRead
                titleRes = R.string.update_tracking_marked_read
                defaultValue = false
            }
            preferenceCategory {
                titleRes = R.string.services

                trackPreference(trackManager.myAnimeList) {
                    activity?.openInBrowser(MyAnimeListApi.authUrl(), trackManager.myAnimeList.getLogoColor(), true)
                }
                trackPreference(trackManager.aniList) {
                    activity?.openInBrowser(AnilistApi.authUrl(), trackManager.aniList.getLogoColor(), true)
                }
                preference {
                    key = "update_anilist_scoring"
                    isPersistent = false
                    isIconSpaceReserved = true
                    title = context.getString(R.string.update_tracking_scoring_type, context.getString(R.string.anilist))

                    preferences
                        .getStringPref(trackManager.aniList.getUsername())
                        .asImmediateFlowIn(viewScope) {
                            isVisible = it.isNotEmpty()
                        }

                    onClick {
                        viewScope.launchIO {
                            val (result, error) = trackManager.aniList.updatingScoring()
                            if (result) {
                                view?.snack(R.string.scoring_type_updated)
                            } else {
                                view?.snack(
                                    context.getString(
                                        R.string.could_not_update_scoring_,
                                        error?.localizedMessage.orEmpty(),
                                    ),
                                )
                            }
                        }
                    }
                }
                trackPreference(trackManager.kitsu) {
                    val dialog = TrackLoginDialog(trackManager.kitsu, R.string.email)
                    dialog.targetController = this@SettingsTrackingController
                    dialog.showDialog(router)
                }
                trackPreference(trackManager.mangaUpdates) {
                    val dialog = TrackLoginDialog(trackManager.mangaUpdates, R.string.username)
                    dialog.targetController = this@SettingsTrackingController
                    dialog.showDialog(router)
                }
                trackPreference(trackManager.shikimori) {
                    activity?.openInBrowser(ShikimoriApi.authUrl(), trackManager.shikimori.getLogoColor(), true)
                }
                trackPreference(trackManager.bangumi) {
                    activity?.openInBrowser(BangumiApi.authUrl(), trackManager.bangumi.getLogoColor(), true)
                }
                trackPreference(trackManager.hikka) {
                    activity?.openInBrowser(HikkaApi.authUrl(), trackManager.hikka.getLogoColor(), true)
                }
                trackPreference(trackManager.mangaBaka) {
                    activity?.openInBrowser(MangaBakaApi.authUrl(), trackManager.mangaBaka.getLogoColor(), true)
                }
                infoPreference(R.string.tracking_info)
            }
            preferenceCategory {
                titleRes = R.string.tracker_details

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
            preferenceCategory {
                titleRes = R.string.enhanced_services
                val sourceManager = Injekt.get<SourceManager>()
                val enhancedTrackers =
                    trackManager.services
                        .filter { service ->
                            if (service !is EnhancedTrackService) return@filter false
                            sourceManager.getCatalogueSources().any { service.accept(it) }
                        }
                enhancedTrackers.forEach { trackPreference(it) }
                infoPreference(R.string.enhanced_tracking_info)
            }
        }

    private fun languageName(code: String): String =
        Locale.forLanguageTag(code).let { it.getDisplayName(it).replaceFirstChar(Char::uppercaseChar) } + " ($code)"

    private inline fun PreferenceGroup.trackPreference(
        service: TrackService,
        crossinline login: () -> Unit = { },
    ): TrackerPreference =
        add(
            TrackerPreference(context).apply {
                key = trackPreferences.trackUsername(service).key()
                title = context.getString(service.nameRes())
                iconRes = service.getLogo()
                iconColor = service.getLogoColor()
                onClick {
                    if (service.isLogged) {
                        if (service is EnhancedTrackService) {
                            service.logout()
                            updatePreference(service)
                        } else {
                            val dialog = TrackLogoutDialog(service)
                            dialog.targetController = this@SettingsTrackingController
                            dialog.showDialog(router)
                        }
                    } else {
                        if (service is EnhancedTrackService) {
                            service.loginNoop()
                            updatePreference(service)
                        } else {
                            login()
                        }
                    }
                }
            },
        )

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        updatePreference(trackManager.myAnimeList)
        updatePreference(trackManager.aniList)
        updatePreference(trackManager.shikimori)
        updatePreference(trackManager.bangumi)
        updatePreference(trackManager.hikka)
        updatePreference(trackManager.mangaBaka)
    }

    private fun updatePreference(service: TrackService) {
        val pref = findPreference(trackPreferences.trackUsername(service).key()) as? TrackerPreference
        pref?.notifyChanged()
    }

    override fun trackLoginDialogClosed(service: TrackService) {
        updatePreference(service)
    }

    override fun trackLogoutDialogClosed(service: TrackService) {
        updatePreference(service)
    }
}

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
