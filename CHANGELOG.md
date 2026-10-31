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

### Changed
- The manga edit panel is rewritten as a Compose Material 3 bottom sheet, adapted from Komikku's
  edit dialog. Fields have "restore original" buttons, tags are input chips, and pasting a cover
  from the clipboard moved to the cover's long-press menu. Tapping a tag offers "Move earlier" and
  "Move later" instead of Komikku's drag-reordering.
- Release and beta builds are signed with a stable local key read from the git-ignored
  `keystore.properties`, so updates install over each other.
- The issue templates, About screen and tracker User-Agents link to this fork instead of upstream.

### Technical
- Manual "Version bump" workflow backed by `.github/scripts/bump_version.py`: bumps `versionName`/`versionCode` in `buildSrc`, rolls a patch bump past .10 over to the next minor, fails on missing or ambiguous declarations, and can tag. The script was run locally; the workflow itself has not run on GitHub.
- The dev flavor no longer tries to upload Crashlytics mapping files, which broke
  `assembleDevRelease` without `google-services.json`.
- Raised the Gradle and Kotlin daemon heaps to 3 GB in `gradle.properties`. A debug build of the app
  ran out of memory in the Kotlin compiler with the previous 2 GB limit.
