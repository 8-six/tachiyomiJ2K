# Changelog

All notable changes to this fork (8-six/tachiyomiJ2K) are documented in this file. Changes that come
from upstream TachiyomiJ2K are not repeated here.

- `Added` - new features.
- `Changed` - changes in existing functionality.
- `Fixed` - bug fixes.
- `Removed` - removed features.
- `Technical` - build, migrations, tests and other internal changes.

## [Unreleased]
### Added
- `AGENTS.md` with the rules for working on this fork (anonymous local commits, feature branches, no
  pushing, fork markers, verification commands, changelog upkeep), adapted from Komikku's guide.

- Reader automation: a start/stop item and a settings dialog in the reader toolbar overflow menu.
  Webtoons scroll continuously, paged modes turn the page on a timer. Speed, seconds per page and a
  stop-after duration are saved. It stops at the end of the reader, when the app is paused, when the
  reading mode changes or when the duration runs out, and keeps the screen on while running. Adapted
  from Komikku's automation feature, not its code.
- Optional webhook (Settings > Advanced): library updates post new chapters as JSON to a URL you set,
  with a Discord embed when it is a Discord webhook. Delivery uses a small bounded queue in the
  background, failures are only logged, and the URL is stored as a private preference so it is not in
  backups. Adapted from Komikku's webhooks; Discord Rich Presence is not ported.
- Storage usage in Settings > Downloads: lists the downloads folder per source with its size and deletes the sources you tick after a confirmation. Only folders inside the downloads directory are listed. Simpler than Komikku's storage screen: no per-type categories and no merged-manga handling.
- Date headings on the Recents Updates view show how many different manga that day had, so three chapters of one series count once. Adapted from Komikku's History screen; the plain History view has no date headings in this app.
- Ongoing series with enough release history show "next around <date>" next to the chapter count on the manga page. It is the median gap between recent release days added to the latest one, hidden once the date passes or when releases are rarer than every 90 days. It is a guess, not a source-confirmed date. Adapted from Komikku's release prediction idea; the algorithm is this fork's own.
- Source categories (Settings > Browse): group sources under names, and limit Global Search to one category. It takes precedence over "pinned only" and only includes enabled, visible sources in enabled languages. Categories are stored as a JSON preference, so they are included in preference backups. Adapted from Komikku; there is no category filter on the Sources list itself, and the category is chosen in settings rather than inside Global Search.
- Webtoon "Disable pinch to zoom" option in the reader sheet and Settings > Reader (adapted from Komikku). Not device-tested.
- Webtoon "Gap between pages" slider (0-32dp) in Settings > Reader, drawn as an item decoration between page items. Not device-tested; not in the reader sheet.
- Edit panel switches to a panorama cover header when the cover is wide (width/height >= 1.2). Not device-tested.
- Tracker sheet marks a tracker whose last refresh failed with an error-colored title and warning icon until a later refresh succeeds (in memory only). Not device-tested.
- Colored chapter bookmarks: long-press a chapter on the manga page > "Bookmark color" picks one of 8 colors (also bookmarks the chapter). The color tints the bookmark in the chapter list and reader chapter list. Adapted from Komikku. Not device-tested; no color picker in the reader yet.
- Chapter blacklist: long-press a chapter > "Hide chapter" removes it from the manga chapter list, reader navigation, next-unread lookups and Recents without deleting it, its history or bookmarks. "Show hidden chapters" in the manga overflow menu reveals them (not persisted) so they can be unhidden. Adapted from Komikku. Library read/unread/bookmark counts and the Recents new-chapters list ignore hidden chapters. Not device-tested.
- Scanlator priority and smart merge: the manga overflow menu gets "Scanlator priority" (pick scanlators in order of preference), and Settings > Library gets "Smart scanlator merge" (off by default). When on, each chapter number shows a single release, the one from the best ranked scanlator, then the earliest in the source list; excluded scanlators and hidden chapters are applied first and stay hidden. Chapters without a recognized number are never merged, and nothing is deleted. Adapted from Komikku. The manga overflow menu has a per-manga "Smart scanlator merge" override (follow global / always on / always off), and library unread/read counts follow the merge. Not device-tested.
- Source category chooser: the Global Search overflow menu can switch the active source category for that search, and the Sources list overflow menu filters the list to one category (new `browse_source_category` preference). Adapted from Komikku. Not device-tested.

- Auto Reread (adapted from Komikku): Settings > Library > Auto Reread (off / always ask / reset automatically). Opening the first chapter of a fully read series offers to mark every chapter unread (or does it automatically). When a reread starts, linked AniList, MangaBaka, Hikka and Shikimori entries are set to Rereading with progress restarted (other trackers are left unchanged).

- Library search by bookmark color (adapted from Komikku): `bookmark_color:red` (also `bmcolor:`, colors default/red/orange/yellow/green/blue/purple/pink) finds manga with a visible chapter bookmarked in that color; an empty value finds manga without bookmarks. Supports negation like other field terms.

- Related manga (adapted from Komikku): manga tracked on AniList or MangaBaka (either or both; at most 12 MangaBaka relations are looked up) get a "Related manga" overflow-menu action listing sequels, prequels, side stories and similar relations; tapping one opens Global Search for it. Shown as a dialog list, not a horizontal row; MangaBaka relations are not included.

- Nested categories (adapted from Komikku): a category can be given a parent in Manage category. Child categories show as "Parent › Child" in the library header and category manager. Parent choices exclude the category itself and its descendants.
- Smart categories: a category can be bound to a tracker status (reading, completed, plan to read) and manga move into or out of it when their tracking changes; bound subcategories only apply to manga already in the parent branch. Each category can also have its own automatic update interval (6h to weekly).
- Source feeds: Sources > Feeds opens a Material 3 sheet with persistent feeds (latest, popular or a saved search of a source). Each feed loads, fails and retries on its own, can be reordered or removed, and Refresh all reloads every feed.

- Per-manga preferred tracker: choose which tracker supplies metadata autofill for a manga (overflow menu, shown when more than one tracker with metadata is linked), overriding the global preference.

- Reader chapter sheet: long-press a chapter's bookmark button to choose its bookmark color (also bookmarks the chapter).

- Smart categories can also be bound to the tracker statuses On hold and Dropped (services without those statuses are ignored).

- Library search filter sheet: Bookmark color field (red, orange, yellow, green, blue, purple, pink) that builds the `bmcolor:` search term.
- "Edit all trackers" sheet (Compose Material 3) in the manga overflow menu: status, chapter progress and score for every linked tracker in one place (adapted from Komikku's unified tracker editor).
- Edit panel: a cover picker (grid like search results) with the current cover, the source's own and every cover the linked trackers offer, including all MangaBaka covers; picking one downloads it. Each field (title, author, artist, description, status, tags) has a sync button that asks the linked trackers and shows a preview of what each has before applying it.
- Manga details: a "Suggestions" row of covers under the description with related manga found in the manga's own source by searching the keywords of its title (like Komikku's related manga; the extension related-manga API of Komikku does not exist in this app); tapping a cover opens that manga, and the arrow opens the full list. It replaces the "Related manga" menu entry. The title and arrow are now drawn in the theme colors instead of black.
- Edit panel cover picker: 2-column grid with the outline of the library covers, and MangaBaka covers show the kind (volume or season and its number), the language as a badge and the uploader's note (like "Cover from Naver Webtoon").

### Changed
- About and the overflow menu show the build commit SHA instead of the version number.
- Removed the "next chapter around <date>" estimate from the manga page chapter header.
- The edit panel opens as a half-height drawer with a fixed header (drag handle, title, Save/Cancel) instead of covering the whole screen, and stops below the status bar when expanded.
- Tracking settings are reorganized: a short main page (progress, services, enhanced services) and a separate "Tracker details" page grouped into Syncing, Cover, Tags and titles, Trackers used and MangaBaka. The per-tracker "details from" pickers were removed from the UI (existing values still apply); each tracker keeps its on/off switch, and any tracker can still be picked by hand when editing a series.
- Source browsing sends each request a snapshot of the selected filters, so changing filters while a request is loading cannot alter it (adapted from Komikku).
- The manga edit panel is rewritten as a Compose Material 3 bottom sheet, adapted from Komikku's
  edit dialog. Fields have "restore original" buttons, tags are input chips, and pasting a cover
  from the clipboard moved to the cover's long-press menu. Tapping a tag offers "Move earlier" and
  "Move later" instead of Komikku's drag-reordering.
- Release and beta builds are signed with a stable local key read from the git-ignored
  `keystore.properties`, so updates install over each other.
- The issue templates, About screen and tracker User-Agents link to this fork instead of upstream.

### Fixed
- Edit panel: after scrolling the content down it can be scrolled back up without closing the sheet (nested scrolling is enabled for Compose bottom sheets).
- Recents query no longer fails: the "newly added manga" branch was missing the `bookmark_color` and `blacklisted` chapter columns added by this fork, which made the UNION invalid.
- Leaving the extension repositories screen refreshes the available extensions when the set of enabled repos changed, so adding, removing, enabling or disabling a repo shows up without a manual refresh or restart. Matches Komikku's behavior.

### Technical
- Build/CI: commit count, commit SHA and build time moved from `BuildConfig` to generated string resources read through `BuildInfo`, so a new commit no longer invalidates the Kotlin compilation of the whole app; the nightly workflow reads/writes the Gradle cache only on `master` with cleanup, and the CI Gradle properties use 4 workers, parallel execution and the build cache.
- The CI release workflow (`build_push.yml`) no longer runs on push; it is manual-only for now, so nightly builds are the only automatic releases.
- The Nightly workflow now runs on every push to `master` (ignoring docs/markdown-only changes) instead of the daily schedule; manual runs still work.
- `SqlDumpTest` writes the real schema, v22-v28 migration and raw query SQL when `SQL_DUMP_DIR` is set, so they can be run against `sqlite3` (verified: migrated v21 schema equals a fresh install, all raw queries run on both).
- Reader auto-scroll steps once per display frame using the screen refresh rate instead of a fixed 16 ms tick.
- Database version 28 adds `mangas.preferred_tracker`; backups carry it in `BackupManga` ProtoNumber 813 (existing local value is kept on restore when the backup has none).
- Added `BackupCategoryTest` for category hierarchy, tracker status and update interval backup fields (including old backups without them). Source refresh was reviewed and keeps bookmark color and hidden flags of existing chapters.
- Feeds are stored as one JSON string preference (`source_feeds`), so they travel with app preference backups; saved-search feeds reference the saved search by id and are dropped when it is deleted. No database change. `SourceFeedsTest` covers ordering, de-duplication and serialization.
- Database version 27 adds `categories.tracker_status`, `update_interval` and `update_last_run`. Backups carry the first two as `BackupCategory` ProtoNumber 804 and 805. `SmartCategorySyncTest` covers branch scoping and schedules.
- Database version 26 adds `categories.parent_id`; `getCategories()` now uses a raw query joining the parent name. Backups carry the parent by name (`BackupCategory` ProtoNumber 803) and restore re-links parents after inserting categories.
- Database version 25 adds `mangas.smart_merge_mode` (INTEGER NOT NULL DEFAULT 0; 0 global, 1 on, 2 off), backed up as `BackupManga` ProtoNumber 812.
- Database version 24 adds `mangas.scanlator_priority` (TEXT, scanlators joined with ` & `, best first). Backups carry it as `BackupManga` ProtoNumber 811; older backups restore with no priority and a restore over an existing manga keeps its local priority.
- Database version 23 adds `chapters.blacklisted` (INTEGER NOT NULL DEFAULT 0). Backups carry it as `BackupChapter` ProtoNumber 802; older backups restore as not hidden and a restore keeps a local hidden flag. `ChapterBlacklistTest` covers the filter and the protobuf field.
- Database version 22 adds `chapters.bookmark_color` (INTEGER NOT NULL DEFAULT 0, ARGB, 0 = theme default). Backups carry it as `BackupChapter` ProtoNumber 801; older backups restore with no color, and a restore keeps an existing local color when the backup has none. `BackupChapterTest` covers the field.
- Manual "Version bump" workflow backed by `.github/scripts/bump_version.py`: bumps `versionName`/`versionCode` in `buildSrc`, rolls a patch bump past .10 over to the next minor, fails on missing or ambiguous declarations, and can tag. The script was run locally; the workflow itself has not run on GitHub.
- The dev flavor no longer tries to upload Crashlytics mapping files, which broke
  `assembleDevRelease` without `google-services.json`.
- Raised the Gradle and Kotlin daemon heaps to 3 GB in `gradle.properties`. A debug build of the app
  ran out of memory in the Kotlin compiler with the previous 2 GB limit.
