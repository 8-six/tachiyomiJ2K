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

### Technical
- Raised the Gradle and Kotlin daemon heaps to 3 GB in `gradle.properties`. A debug build of the app
  ran out of memory in the Kotlin compiler with the previous 2 GB limit.
