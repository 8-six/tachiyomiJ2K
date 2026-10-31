# Komikku port progress

Reference: `xkana-shii/komikku` (`features.md`, `AGENTS.md`, `FORK.md`). Komikku is a Compose / Metro /
SQLDelight codebase; this app is Conductor + XML with Injekt and storio. Features are re-implemented
natively, never copied as files. Status: `todo`, `wip`, `done`, `partial`, `skipped (reason)`.

## Foundation

| Item | Status | Notes |
|------|--------|-------|
| Anonymous commit identity | done | Repo-local git config; rule in `AGENTS.md` |
| Updater / About / tracker User-Agent point to 8-six | partial | `GITHUB_REPO` already `8-six/tachiyomiJ2K`; remaining `Jays2Kings` links are listed in the plan below |
| Stable release signing | todo | Needs `keystore.properties` signing config; Play Integrity cannot be satisfied by a sideloaded fork |
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

Nothing from the port is implemented yet.
