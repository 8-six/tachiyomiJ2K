# Komikku port progress

Reference: `xkana-shii/komikku` (`features.md`, `AGENTS.md`, `FORK.md`). Komikku is a Compose / Metro /
SQLDelight codebase; this app is Conductor + XML with Injekt and storio. Features are re-implemented
natively, never copied as files. Status: `todo`, `wip`, `done`, `partial`, `skipped (reason)`.

## Foundation

| Item | Status | Notes |
|------|--------|-------|
| Anonymous commit identity | done | Repo-local git config; rule in `AGENTS.md` |
| Updater / About / tracker User-Agent point to 8-six | done | `GITHUB_REPO` already `8-six/tachiyomiJ2K`; About, issue templates and tracker User-Agents changed |
| Stable release signing | done | `fork` signing config from `keystore.properties`; Play Integrity cannot be satisfied by a sideloaded fork |
| Build environment | done | SDK in `~/Android/Sdk`, JDK 21, build cache on. Configuration cache is rejected by the oss-licenses plugin |

## Plan (batches, in order)

1. **Manga edit panel** (Komikku `ui/manga/EditMangaDialog.kt`): Compose Material 3 sheet replacing the
   XML dialog. Must keep: custom/original title, author, artist, description with hints and
   restore-original, status and series type, local-source language, cover change/paste/reset,
   tag add/remove/reset with custom-tag colors, tracker autofill. Komikku additions to evaluate: tag
   reordering, cover ratio switch, clearer header.
2. **Material 3 polish**: use Compose M3 components for new surfaces and align shapes, spacing, and
   containers with the existing cover-based theme.
3. Library/category features from `features.md` section 1 (nested categories, Smart Categories) - needs a
   DB migration and backup fields; assess against J2K's existing dynamic categories first.
4. Chapter features (colored bookmarks, chapter blacklist, scanlator priority) - DB migration plus backup.
5. Tracker and source-feed features (unified tracker editor, feeds) - assess against what J2K already has.
6. Reader automation (auto scroll / page flip).

Batches 3-6 are exploratory: each gets a gap analysis here before any code, and items that do not fit this
architecture are recorded as `skipped` with the reason.

## Done so far

| Item | Status | Notes |
|------|--------|-------|
| Manga edit panel (Compose M3 sheet) | partial | All previous behaviors kept, restore-original buttons added. Tags reorder via a tap menu (move earlier/later), not drag. Not done: cover ratio switch. Built (dev debug) but not tested on a device. |
| Material 3 polish | partial | Only the Compose pieces used by the edit panel; no app-wide restyle |
| Reader automation (auto-scroll / auto page flip, duration, keep awake) | partial | Not done: refresh-rate handling, webtoon pinch-zoom switch, gap preference. Built (dev debug) but not tested on a device. |
| Webhooks (s18.1) | done | New-chapter events from library updates; generic JSON or Discord embed; bounded queue; URL is a private pref. Built, not tested against a real endpoint. |
| Discord Rich Presence (s18.2) | skipped | Needs Discord account token handling and an RPC client; Komikku says its exact current behavior is unverified |
| Version bump workflow (s22) | done | Script tested locally (patch, rollover, failure); workflow untested on GitHub. Spotless/formatting checks not applicable (kotlinter fails on existing code) |
| Storage overview (s19.1) | partial | Per-source size list + delete in Downloads settings. No per-type categories; merged manga do not exist in J2K. Built, not device-tested (SAF folders unverified). |
| History distinct-manga count (s13.1) | partial | Shown in Recents Updates date headings; History view has no date headings. Blacklist/Smart Merge-aware continue-reading waits for those features. |
| Historical / removed items (s24, s25) | skipped | Verification-only lists (reverted or unverified fork experiments); nothing to port |
| Release interval prediction (s14.2) | partial | Per-title median-gap estimate on the manga page (unit tested). Not used for library update scheduling. |
| Extension repo management (s12) | done | J2K already had repo enable/disable, metadata, icons, source prefs; added refresh of the extension list after repo changes. Built, not device-tested. |
| Source categories + scoped Global Search (s10.1, s10.2) | done (core) | Manage in Settings > Browse; picker in Global Search menu and Sources list menu. Filter-state isolation (s10.3) not done. Built, not device-tested. |
| Webtoon disable pinch zoom | partial | Preference + switch; page gap slider in Settings > Reader; scale-type/refresh-rate options not ported |
| Tracker error indicator | partial | In-memory flag in tracker sheet; preferred tracker/unified editor not ported |
| Colored chapter bookmarks | partial | DB v22 + backup 801 + picker on chapter long-press; reader picker/Fillermarks not ported |
| Auto Reread | partial | Setting + prompt/automatic reset when starting a fully read series; no tracker reread coordination or category/tracking automation |
| Library bookmark-color filter | partial | Search term `bookmark_color:<name>` only; no filter-sheet control. Source language grouping/search not analysed |
| Related manga (sequel/prequel) | partial | AniList only, overflow-menu dialog; no horizontal row on the manga page, no MangaBaka, no cover cards |
| Nested categories | partial | Parent assignment, "Parent › Child" labels, backup/restore; no tree tabs/chips, no include/exclude-descendant filters |
| Smart categories / update schedules | partial | reading/completed/plan-to-read buckets only; schedule applies to automatic updates only |
| Chapter blacklist | done (core) | DB v23 + backup 802; hide/reveal, reader/recents/next-unread/library counts aware; URL+scanlator identity fallback not needed (hidden by chapter row id) |
| Scanlator priority + Smart Merge | done (core) | DB v24/v25 + backup 811/812, priority dialog, global toggle + per-manga override, library counts; deep-link resolve to the merged representative not done |
| Unified tracker editor / feeds | skipped (for now) | Needs DB schema and backup work |
