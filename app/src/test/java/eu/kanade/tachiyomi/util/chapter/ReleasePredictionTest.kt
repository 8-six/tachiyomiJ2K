package eu.kanade.tachiyomi.util.chapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReleasePredictionTest {
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun days(vararg d: Long) = d.map { it * day + day / 2 }

    @Test
    fun weeklyReleasesPredictOneWeekAfterTheLatest() {
        val uploads = days(100, 107, 114, 121, 128)
        val predicted = ReleasePrediction.predictNext(uploads, now = 130 * day)
        assertEquals(135L, predicted!! / day)
    }

    @Test
    fun tooLittleHistoryGivesNoPrediction() {
        assertNull(ReleasePrediction.predictNext(days(100, 107, 114), now = 115 * day))
    }

    @Test
    fun chaptersOnTheSameDayCountOnce() {
        assertNull(ReleasePrediction.predictNext(days(100, 100, 100, 107, 107), now = 108 * day))
    }

    @Test
    fun missedPredictionGivesNoPrediction() {
        assertNull(ReleasePrediction.predictNext(days(100, 107, 114, 121, 128), now = 140 * day))
    }

    @Test
    fun veryRareReleasesAreNotPredicted() {
        assertNull(ReleasePrediction.predictNext(days(0, 100, 200, 300, 400).drop(1), now = 401 * day))
    }

    @Test
    fun medianIgnoresOneLongBreak() {
        val uploads = days(100, 107, 114, 150, 157, 164)
        val predicted = ReleasePrediction.predictNext(uploads, now = 165 * day)
        assertEquals(171L, predicted!! / day)
    }
}
