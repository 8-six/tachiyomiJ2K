package eu.kanade.tachiyomi

import android.app.Application
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// 8SIX -->

/**
 * Values that change on every build. They live in generated resources instead of `BuildConfig` so that a new commit
 * does not invalidate the Kotlin compilation cache of the whole app.
 */
object BuildInfo {
    private val app: Application get() = Injekt.get()

    val commitCount: String get() = app.getString(R.string.build_commit_count)
    val betaCount: String get() = app.getString(R.string.build_beta_count)
    val commitSha: String get() = app.getString(R.string.build_commit_sha)
    val buildTime: String get() = app.getString(R.string.build_time)
}
// 8SIX <--
