package eu.kanade.tachiyomi.ui.reader

import android.os.SystemClock
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 8SIX -->

/**
 * Hands-free reading: scrolls webtoons continuously and turns paged viewers on a timer. Runs only
 * while started, stops at the end of the reader or after the configured duration.
 */
class ReaderAutomation(
    private val scope: CoroutineScope,
    private val preferences: PreferencesHelper,
    private val viewer: () -> BaseViewer?,
    private val onStopped: () -> Unit,
) {
    private var job: Job? = null

    val isRunning get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        job =
            scope.launch {
                val startedAt = SystemClock.elapsedRealtime()
                val limitMs = preferences.automationDuration().get() * 60_000L
                var carry = 0f
                var sinceFlip = 0L
                var last = startedAt
                while (isActive) {
                    delay(FRAME_MS)
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = now - last
                    last = now
                    val current = viewer() ?: break
                    if (limitMs > 0 && now - startedAt >= limitMs) break
                    if (current.isAtEndOfReader()) break

                    carry += preferences.autoScrollSpeed().get() * SPEED_STEP_DP.dpToPx * elapsed / 1000f
                    val dy = carry.toInt()
                    if (dy > 0 && current.autoScrollBy(dy)) {
                        carry -= dy
                        sinceFlip = 0
                    } else if (dy > 0) {
                        // Paged viewers do not scroll continuously; turn the page on the interval
                        carry = 0f
                        sinceFlip += elapsed
                        if (sinceFlip >= preferences.autoFlipInterval().get() * 1000L) {
                            sinceFlip = 0
                            current.moveToNext()
                        }
                    }
                }
                stop()
            }
    }

    fun stop() {
        val wasRunning = isRunning
        job?.cancel()
        job = null
        if (wasRunning) onStopped()
    }

    private companion object {
        const val FRAME_MS = 16L
        const val SPEED_STEP_DP = 20
    }
}
// 8SIX <--
