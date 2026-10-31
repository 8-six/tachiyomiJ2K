package eu.kanade.tachiyomi.util.chapter

import java.util.concurrent.TimeUnit

// KMK -->

/**
 * Estimates when the next chapter of a series will appear from its own upload history: the median
 * gap between recent release days added to the latest release. It is a guess, never a promise.
 */
object ReleasePrediction {
    private const val MIN_RELEASE_DAYS = 4
    private const val MAX_GAPS = 10
    private const val MAX_INTERVAL_DAYS = 90L

    /**
     * @param uploadDates chapter upload timestamps in millis; 0 or negative values are ignored.
     * @param now the current time in millis.
     * @return the expected timestamp, or null when there is too little history, the series is
     * released too rarely, or the expected date has already passed (the pattern did not hold).
     */
    fun predictNext(
        uploadDates: List<Long>,
        now: Long,
    ): Long? {
        val dayMillis = TimeUnit.DAYS.toMillis(1)
        val releaseDays =
            uploadDates
                .filter { it > 0 }
                .map { it / dayMillis }
                .distinct()
                .sorted()
        if (releaseDays.size < MIN_RELEASE_DAYS) return null

        val gaps =
            releaseDays
                .takeLast(MAX_GAPS + 1)
                .zipWithNext { a, b -> b - a }
                .sorted()
        val median =
            if (gaps.size % 2 == 1) {
                gaps[gaps.size / 2].toDouble()
            } else {
                (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2.0
            }
        if (median > MAX_INTERVAL_DAYS) return null

        val expected = ((releaseDays.last() + median) * dayMillis).toLong()
        return expected.takeIf { it > now }
    }
}
// KMK <--
