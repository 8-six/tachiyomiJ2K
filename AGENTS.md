# TachiyomiJ2K (8-six fork) – AI Agent Guide

**8-six/tachiyomiJ2K** is a personal fork of **TachiyomiJ2K** (`Jays2Kings/tachiyomiJ2K`), an Android
manga reader (Conductor + XML views, with Compose / Material 3 added incrementally) derived from
Tachiyomi / Mihon. The in-app updater, About links and tracker User-Agents point to this fork.

- **Direct upstream:** `Jays2Kings/tachiyomiJ2K`. Port its commits with `git cherry-pick -x`.
- **Feature reference:** `xkana-shii/komikku` (a Komikku fork; Komikku itself derives from Mihon and
  TachiyomiSY). Komikku uses a different architecture (Metro DI, AndroidX ViewModels, Voyager,
  SQLDelight). **Do not copy its files wholesale**; re-implement behavior natively on this codebase
  and keep the Komikku commit/feature in the commit message for provenance.
- Komikku's `AGENTS.md`, `FORK.md` and `features.md` are the model for this file. `features.md` there
  is a behavior spec, not code that exists here.

## Mandatory rules for AI agents

**Read this section before every change.**

### Git

| Rule | Required behavior |
|------|-------------------|
| Identity | Every commit is authored **and** committed as `anonymous <anonymous@users.noreply.github.com>`. The repo-local `user.name`/`user.email` are set to this; verify with `git log -1 --format='%an <%ae> / %cn <%ce>'` before finishing. Never add `Co-authored-by` trailers or the user's real name/email. |
| Branch | Work on a **feature branch** (`git checkout -b <type>/<short-description>`, e.g. `feat/manga-edit-panel`). Never commit directly to `master` unless the user explicitly asks. |
| Push | **Never push** until the user explicitly asks. Everything stays local. |
| Commits | One coherent change per commit, imperative subject. Do not rewrite or force-push shared history. |
| Unfamiliar changes | Treat unexpected uncommitted files as the user's work. Look before deleting or overwriting. |

### Fork markers

Code ported from or inspired by Komikku and code authored for this fork is marked so the tree can be
diffed against each source:

```kotlin
// KMK -->
// code ported/adapted from Komikku
// KMK <--

// 8SIX -->
// code authored for this fork
// 8SIX <--
```

- XML uses `<!-- KMK -->` / `<!-- 8SIX -->`.
- Never mass-rename markers. Nest an `8SIX` block inside a `KMK` block for fork-specific deltas.
- If a change cannot be enclosed sensibly (one-line change, whole-file rewrite), say so in the commit
  message instead.
- Upstream J2K code needs no marker.

### Strings

- New strings go in `app/src/main/res/values/strings.xml` (base). Reuse existing entries where they fit.
- Weblate is upstream's. Do not hand-edit other locale files except to accompany a new base string
  with a translation you can provide confidently.

### Formatting & build verification

"Build passes" is not enough. After Kotlin/XML edits, run before marking work done:

```bash
./gradlew formatKotlin          # fix formatting (kotlinter)
./gradlew lintKotlin            # must pass
./gradlew :app:assembleDevDebug --build-cache
./gradlew :app:testDevDebugUnitTest --build-cache
./gradlew :app:assembleDevRelease --build-cache   # signed with keystore.properties
```

Check the task names with `./gradlew tasks --all` if a variant rename breaks them. Never report a
task as tested when the build or tests were not run; say what was not verified.

### Build caching (always)

- Use JDK 21 to build: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk` (JDK 17 on this machine has no `javac`).
- Always pass `--build-cache` (already `org.gradle.caching=true`) and do **not** use `--configuration-cache` (the oss-licenses plugin is incompatible).
  Keep the Gradle daemon alive between runs; do not `--no-daemon` or `clean` unless a build is
  provably corrupt.
- Do not delete `~/.gradle`, `.gradle/`, `.kotlin/` or `app/build/` to "fix" an error without trying
  `./gradlew --stop` and a targeted rerun first.
- Android SDK lives in `~/Android/Sdk` (`local.properties` is git-ignored; never commit it).

### Signing and secrets

- Never print, commit or push keystores, `keystore.properties`, `google-services.json`, tokens or
  passwords. They are git-ignored; keep them that way.
- Release/beta builds sign with the key from `keystore.properties` or the matching env vars (see the
  signing block in `app/build.gradle.kts`). Keep the **same key** across releases: the updater and
  Play Integrity's app-recognition both depend on a stable certificate.
- Be honest about Play Integrity: sideloaded builds cannot receive a `MEETS_*`/`PLAY_RECOGNIZED`
  app verdict. A stable signing key and an unchanged `applicationId` are the most a fork can do.

---

## Tech stack

- **UI:** XML views + [Conductor](https://github.com/bluelinelabs/Conductor) controllers; Jetpack
  Compose + Material 3 for newer screens; Material Components (`com.google.android.material`) for views.
- **DI:** Injekt (`uy.kohesive.injekt`), `by injectLazy()` / `Injekt.get()`. Not Hilt/Koin/Metro.
- **Database:** storio + SQLite (`eu.kanade.tachiyomi.data.database`). Schema changes need a migration
  in the DB helper plus backup/restore updates.
- **Network:** OkHttp 5 with DNS-over-HTTPS. **Serialization:** Kotlinx Serialization.
- **Async:** Kotlin coroutines/Flow for new code; RxJava 1 remains for extension compatibility.
- **Images:** Coil 2 (`coil.*`). Reader uses a patched subsampling-scale-image-view.
- **Toolchain:** JDK 21 to build, Java 17 bytecode, Kotlin 2.3.x, Gradle wrapper, single `:app` module
  with flavors `standard` (updater + Firebase) and `dev`, build types `debug` / `release` / `beta`.

## Layout

| Path | Purpose |
|------|---------|
| `app/src/main/java/eu/kanade/tachiyomi/ui/` | Controllers/presenters per area (`library`, `manga`, `reader`, `source`, `setting`, …) |
| `app/src/main/java/eu/kanade/tachiyomi/data/` | DB, backup, download, track, updater, preferences |
| `app/src/main/java/eu/kanade/tachiyomi/source/` | Extension `Source` API – **avoid breaking extension ABI** |
| `app/src/main/java/eu/kanade/presentation/` | Compose UI, if present for the area |
| `app/src/main/res/` | Layouts, menus, strings, themes |
| `app/src/test/` | JVM unit tests (JUnit 4, MockWebServer) |
| `buildSrc/` | Versions (`Dependencies.kt`) and build helpers |

## Porting a Komikku feature

1. Read the feature's section in Komikku's `features.md`; inspect what this app already has. Do not
   duplicate or downgrade existing behavior.
2. Check whether the UI should be Compose + Material 3 (new screens) or an existing Conductor/XML
   screen (small changes). Prefer Compose M3 components for new surfaces; match the existing theme
   (`dynamic colors`, cover-based theming).
3. Persistence: preference, DB migration, backup/restore, and sync where the feature owns user data.
4. Strings in base `strings.xml`; markers around fork-authored code.
5. Tests for the acceptance scenarios; run the verification commands above.
6. Update `CHANGELOG.md` and `docs/komikku-port-progress.md` (below) in the same commit.

## Mandatory CHANGELOG.md maintenance

Keep a complete, accurate `CHANGELOG.md` at the repo root; create it if missing, never a lowercase
duplicate. Preserve existing entries.

- Maintain an `Unreleased` section. Group entries under **Added**, **Changed**, **Fixed**, **Removed**
  and **Technical** (migrations, backup compatibility, build/CI, tests).
- Describe what changed and why it matters, in plain language. Include small fixes and settings/default
  changes. Record DB migrations and backup/sync format changes explicitly.
- Do not list pre-existing J2K/Mihon functionality as new. Distinguish new fork code from Komikku
  adaptations.
- Do not claim features, tests or performance gains that did not happen. Do not rewrite past releases.
- Commit the changelog with the source change it describes. Do not add recursive entries for editing
  the changelog itself.

### Workflow per batch

1. Implement. 2. Add/update tests. 3. Run validation. 4. Update `CHANGELOG.md`.
5. Commit (anonymous identity, feature branch, no push). 6. Update `docs/komikku-port-progress.md`
(feature → status: done / partial / skipped + reason). 7. Next batch.

Before declaring a task complete, compare the new commits against `CHANGELOG.md` and report
anything not verified.

## Common issues

- **Gradle OOM** – raise `org.gradle.jvmargs` in `gradle.properties` (currently `-Xmx3g`, plus `kotlin.daemon.jvmargs`), then
  `./gradlew --stop` and retry.
- **`javac` missing** – you are on JDK 17 JRE; set `JAVA_HOME` to the JDK 21 path above.
- **SDK not found** – check `local.properties` (`sdk.dir=/home/mauricio/Android/Sdk`).
- **Missing `google-services.json`** – required only for the `standard` flavor's Firebase plugin; use the
  `dev` flavor for local builds if it is absent.
