package uk.co.ccooper.predictor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.ccooper.predictor.data.Fixture
import uk.co.ccooper.predictor.data.Outcome
import uk.co.ccooper.predictor.data.Picks
import uk.co.ccooper.predictor.data.Scoring

class ScoringTest {
    private fun ft(id: String, h: Int, a: Int, status: String = "STATUS_FULL_TIME", state: String = "post") =
        Fixture(id, "eng.1", "PL", 0, "H$id", "A$id", homeScore = h, awayScore = a, statusName = status, state = state)

    private val ten = (1..10).map { ft("$it", 1, 0) } // all home wins

    @Test fun perfectWeekWithBankerScoresEleven() {
        val p = Picks("u", ten.associate { it.id to Outcome.HOME }, banker = "3")
        val s = Scoring.score(p, ten)
        assertEquals(11, s.points)
        assertEquals(10, s.correct)
        assertEquals(true, s.bankerCorrect)
    }

    @Test fun wrongBankerGetsNoBonus() {
        val fx = ten.toMutableList().also { it[2] = ft("3", 0, 0) } // game 3 a draw
        val p = Picks("u", ten.associate { it.id to Outcome.HOME }, banker = "3")
        val s = Scoring.score(p, fx)
        assertEquals(9, s.points)
        assertEquals(false, s.bankerCorrect)
    }

    @Test fun noPicksScoresZeroAndNotSubmitted() {
        val s = Scoring.score(null, ten)
        assertEquals(0, s.points)
        assertFalse(s.submitted)
    }

    @Test fun missingPickJustScoresNothing() {
        val p = Picks("u", mapOf("1" to Outcome.HOME), banker = null)
        assertEquals(1, Scoring.score(p, ten).points)
    }

    @Test fun voidedGameCountsForNobodyEvenAsBanker() {
        val fx = ten.toMutableList().also { it[0] = it[0].copy(override = Outcome.VOID) }
        val p = Picks("u", ten.associate { it.id to Outcome.HOME }, banker = "1")
        val s = Scoring.score(p, fx)
        assertEquals(9, s.points)
        assertNull(s.bankerCorrect)
    }

    @Test fun adminOverrideBeatsScore() {
        val f = ft("1", 2, 0).copy(override = Outcome.AWAY)
        assertEquals(Outcome.AWAY, f.result()!!.outcome)
    }

    @Test fun extraTimeAndPenaltiesCountAsDraw() {
        assertEquals(Outcome.DRAW, ft("1", 2, 1, "STATUS_FINAL_AET").result()!!.outcome)
        assertEquals(Outcome.DRAW, ft("1", 1, 1, "STATUS_FINAL_PEN").result()!!.outcome)
        assertFalse(ft("1", 2, 1, "STATUS_FINAL_AET").result()!!.provisional)
    }

    @Test fun liveGameIsProvisional() {
        val f = ft("1", 0, 1, "STATUS_SECOND_HALF", "in")
        val r = f.result()!!
        assertEquals(Outcome.AWAY, r.outcome)
        assertTrue(r.provisional)
        val s = Scoring.score(Picks("u", mapOf("1" to Outcome.AWAY), null), listOf(f))
        assertTrue(s.provisional)
        assertEquals(1, s.points)
    }

    @Test fun notStartedOrPostponedHasNoResult() {
        assertNull(Fixture("1", "eng.1", "PL", 0, "a", "b").result())
        assertNull(ft("1", 0, 0, "STATUS_POSTPONED", "post").result())
    }

    @Test fun fixtureSurvivesFirestoreRoundTrip() {
        val f = ft("9", 3, 2).copy(override = Outcome.HOME, detail = "FT")
        // Firestore hands numbers back as Long
        val m = f.toMap().mapValues { (_, v) -> if (v is Int) v.toLong() else v }
        assertEquals(f, Fixture.from(m))
    }

    @Test fun liveDataKeepsAdminOverride() {
        val stored = Fixture("1", "eng.1", "PL", 0, "a", "b", override = Outcome.VOID)
        val merged = stored.withLive(ft("1", 1, 0))
        assertEquals(Outcome.VOID, merged.override)
        assertEquals(1, merged.homeScore)
    }
}
