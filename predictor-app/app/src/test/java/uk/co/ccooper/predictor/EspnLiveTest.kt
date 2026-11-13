package uk.co.ccooper.predictor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.ccooper.predictor.data.Espn
import java.time.LocalDate

/** Hits the real ESPN feed — checks the parsing still matches what ESPN sends. */
class EspnLiveTest {
    @Test fun loadsUpcomingFixtures() = runBlocking {
        val list = Espn.fixtures(listOf("eng.1", "eng.2", "sco.1"), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23))
        println("Found ${list.size} fixtures, e.g.:")
        list.take(5).forEach { println("  ${it.leagueName}: ${it.home} v ${it.away} @ ${java.time.Instant.ofEpochMilli(it.kickoff)} [${it.state}]") }
        assertTrue(list.size >= 10)
        assertTrue(list.all { it.home.isNotBlank() && it.away.isNotBlank() && it.kickoff > 0 })
        assertEquals(list.sortedBy { it.kickoff }, list)
    }

    @Test fun refreshesFinishedScores() = runBlocking {
        val past = Espn.fixtures(listOf("eng.1"), LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 8))
        assertTrue(past.isNotEmpty())
        val live = Espn.live(past)
        assertEquals(past.map { it.id }.toSet(), live.keys)
        val f = live.values.first()
        println("Live: ${f.home} ${f.homeScore}-${f.awayScore} ${f.away} ${f.statusName} ${f.detail} -> ${f.result()}")
        assertTrue(live.values.all { it.isFinished && it.homeScore != null && it.result() != null })
    }

    @Test fun summaryFallbackFindsMissingEvent() = runBlocking {
        val past = Espn.fixtures(listOf("eng.1"), LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 8)).first()
        // Pretend it was rescheduled a week later, so the scoreboard lookup misses and the summary is used.
        val moved = past.copy(kickoff = past.kickoff + 7 * 86_400_000L)
        val live = Espn.live(listOf(moved))
        assertEquals(past.homeScore, live[past.id]?.homeScore)
    }
}
