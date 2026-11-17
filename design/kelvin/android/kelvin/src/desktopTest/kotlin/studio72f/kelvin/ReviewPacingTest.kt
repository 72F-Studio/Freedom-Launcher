package studio72f.kelvin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewPacingTest {
    private val day = 24L * 60 * 60 * 1000
    private val p = KelvinReviewPacing

    private fun usesOf(n: Int) = (1..n).fold(KelvinReviewState()) { s, _ -> p.used(s) }

    @Test fun waitsForTheThirdUse() {
        assertFalse(p.isDue(usesOf(1), 0))
        assertFalse(p.isDue(usesOf(2), 0))
        assertTrue(p.isDue(usesOf(3), 0))
    }

    @Test fun firstAskOffersOnlyLaterThenLaterAsksOfferNever() {
        val first = p.asked(usesOf(3), 0)
        assertFalse(p.offersNever(first))
        val second = p.asked(first.copy(uses = 8), 11 * day)
        assertTrue(p.offersNever(second))
        assertTrue(p.offersNever(p.asked(second.copy(uses = 20), 40 * day)))
    }

    @Test fun neverTwiceInOneUse() {
        val asked = p.asked(usesOf(3), 0)
        assertFalse(p.isDue(asked, 365 * day))
    }

    @Test fun maybeNextTimeWaitsForUsesAndDays() {
        val asked = p.asked(usesOf(3), 0)
        // Enough uses, not enough days.
        assertFalse(p.isDue(asked.copy(uses = 8), 9 * day))
        // Enough days, not enough uses.
        assertFalse(p.isDue(asked.copy(uses = 7), 30 * day))
        assertTrue(p.isDue(asked.copy(uses = 8), 10 * day))
    }

    @Test fun finishedNeverAsksAgain() {
        assertFalse(p.isDue(usesOf(50).copy(finished = true), 999 * day))
    }

    @Test fun aUseStartsAfterHalfAnHourAway() {
        assertTrue(p.startsUse(0, 1_000))
        assertFalse(p.startsUse(1_000, 1_000 + 29 * 60 * 1000))
        assertTrue(p.startsUse(1_000, 1_000 + 30 * 60 * 1000))
        // The clock went back: count it rather than stall forever.
        assertTrue(p.startsUse(10_000, 5_000))
        assertEquals(1, p.used(KelvinReviewState()).uses)
    }
}
