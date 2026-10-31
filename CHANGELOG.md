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

### Changed
- The manga edit panel is rewritten as a Compose Material 3 bottom sheet, adapted from Komikku's
  edit dialog. Fields have "restore original" buttons, tags are input chips, and pasting a cover
  from the clipboard moved to the cover's long-press menu. Tapping a tag offers "Move earlier" and
  "Move later" instead of Komikku's drag-reordering.
- Release and beta builds are signed with a stable local key read from the git-ignored
  `keystore.properties`, so updates install over each other.
- The issue templates, About screen and tracker User-Agents link to this fork instead of upstream.

### Fixed
- Leaving the extension repositories screen refreshes the available extensions when the set of enabled repos changed, so adding, removing, enabling or disabling a repo shows up without a manual refresh or restart. Matches Komikku's behavior.

### Technical
- Database version 25 adds `mangas.smart_merge_mode` (INTEGER NOT NULL DEFAULT 0; 0 global, 1 on, 2 off), backed up as `BackupManga` ProtoNumber 812.
- Database version 24 adds `mangas.scanlator_priority` (TEXT, scanlators joined with ` & `, best first). Backups carry it as `BackupManga` ProtoNumber 811; older backups restore with no priority and a restore over an existing manga keeps its local priority.
- Database version 23 adds `chapters.blacklisted` (INTEGER NOT NULL DEFAULT 0). Backups carry it as `BackupChapter` ProtoNumber 802; older backups restore as not hidden and a restore keeps a local hidden flag. `ChapterBlacklistTest` covers the filter and the protobuf field.
- Database version 22 adds `chapters.bookmark_color` (INTEGER NOT NULL DEFAULT 0, ARGB, 0 = theme default). Backups carry it as `BackupChapter` ProtoNumber 801; older backups restore with no color, and a restore keeps an existing local color when the backup has none. `BackupChapterTest` covers the field.
- Manual "Version bump" workflow backed by `.github/scripts/bump_version.py`: bumps `versionName`/`versionCode` in `buildSrc`, rolls a patch bump past .10 over to the next minor, fails on missing or ambiguous declarations, and can tag. The script was run locally; the workflow itself has not run on GitHub.
- The dev flavor no longer tries to upload Crashlytics mapping files, which broke
  `assembleDevRelease` without `google-services.json`.
- Raised the Gradle and Kotlin daemon heaps to 3 GB in `gradle.properties`. A debug build of the app
  ran out of memory in the Kotlin compiler with the previous 2 GB limit.
