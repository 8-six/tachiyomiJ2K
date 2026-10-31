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

### Changed
- Release and beta builds are signed with a stable local key read from the git-ignored
  `keystore.properties`, so updates install over each other.
- The issue templates, About screen and tracker User-Agents link to this fork instead of upstream.

### Technical
- The dev flavor no longer tries to upload Crashlytics mapping files, which broke
  `assembleDevRelease` without `google-services.json`.
- Raised the Gradle and Kotlin daemon heaps to 3 GB in `gradle.properties`. A debug build of the app
  ran out of memory in the Kotlin compiler with the previous 2 GB limit.
