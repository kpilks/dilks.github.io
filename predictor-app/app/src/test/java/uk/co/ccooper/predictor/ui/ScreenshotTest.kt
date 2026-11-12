package uk.co.ccooper.predictor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import uk.co.ccooper.predictor.data.Fixture
import uk.co.ccooper.predictor.data.Outcome
import uk.co.ccooper.predictor.data.Picks
import uk.co.ccooper.predictor.data.Scoring
import uk.co.ccooper.predictor.data.UserProfile
import uk.co.ccooper.predictor.data.Week
import uk.co.ccooper.predictor.data.WeekStatus

class ScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 1.0)

    private val sat = 1795264200000L // Sat 21 Nov 2026 12:30 UTC
    private val fx = listOf(
        Fixture("1", "eng.1", "Premier League", sat, "Arsenal", "Tottenham Hotspur"),
        Fixture("2", "eng.1", "Premier League", sat + 9_000_000, "Brighton & Hove Albion", "Brentford"),
        Fixture("3", "eng.2", "Championship", sat + 9_000_000, "Norwich City", "Cardiff City"),
        Fixture("4", "sco.1", "Scottish Premiership", sat + 9_000_000, "Celtic", "Rangers"),
    )
    private val played = listOf(
        fx[0].copy(homeScore = 2, awayScore = 1, statusName = "STATUS_FULL_TIME", state = "post", detail = "FT"),
        fx[1].copy(homeScore = 0, awayScore = 0, statusName = "STATUS_SECOND_HALF", state = "in", detail = "67'"),
        fx[2].copy(homeScore = 1, awayScore = 3, statusName = "STATUS_FULL_TIME", state = "post", detail = "FT"),
        fx[3].copy(override = Outcome.VOID, statusName = "STATUS_POSTPONED", state = "post", detail = "Postponed"),
    )
    private val picks = Picks("me", mapOf("1" to "H", "2" to "D", "3" to "H", "4" to "A"), banker = "1")

    @Composable private fun Frame(dark: Boolean = false, content: @Composable () -> Unit) {
        PredictorThemeFor(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
            }
        }
    }

    @Test fun picksOpen() = paparazzi.snapshot {
        Frame {
            val week = Week("w", "Gameweek 12", sat, WeekStatus.OPEN, fx, 0)
            WeekHeader(week, listOf(week, week.copy(id = "x")), now = sat - 2 * 86_400_000 - 3_600_000, lastRefresh = 0, onSelect = {})
            fx.forEach { f ->
                FixtureCard(f, picks.picks[f.id].takeIf { f.id != "4" }, f.id == "1", editable = true, showResult = false, crowd = null, onPick = {}, onBanker = {})
            }
        }
    }

    @Test fun picksLocked() = paparazzi.snapshot {
        Frame(dark = true) {
            val week = Week("w", "Gameweek 12", sat, WeekStatus.OPEN, played, 0)
            val s = Scoring.score(picks, played)
            WeekHeader(week, emptyList(), now = sat + 10_000_000, lastRefresh = sat + 9_990_000, onSelect = {}) {
                ScoreLine(s.points, s.correct, s.bankerCorrect, s.provisional, false)
            }
            val crowd = listOf(picks, Picks("b", mapOf("1" to "A", "2" to "D", "3" to "A"), null), Picks("c", mapOf("1" to "H", "2" to "H", "3" to "A"), "3"))
            played.forEach { f ->
                FixtureCard(f, picks.picks[f.id], f.id == picks.banker, editable = false, showResult = true, crowd = crowd, onPick = {}, onBanker = {})
            }
        }
    }

    @Test fun leagueTable() = paparazzi.snapshot {
        Frame {
            val names = listOf("Alice Athletic", "Real Mad Lads", "Bob Wanderers", "Dynamo Kyiv-ish", "Inter Your Nan", "Sporting Chance")
            val pts = listOf(54, 51, 51, 47, 40, 12)
            val rows = names.mapIndexed { i, n ->
                Standing(if (i == 2) 2 else i + 1, i == 1 || i == 2, UserProfile("u$i", n, "", emptyMap()), pts[i], 7 - i, live = i < 2)
            }
            Table(rows, rows[3].user, header = { HeaderRow("GW12") }, empty = "", onClick = {}) { s ->
                androidx.compose.material3.Text(s.weekPoints.toString(), Modifier.padding(horizontal = 20.dp))
                androidx.compose.material3.Text(s.points.toString(), Modifier.padding(start = 12.dp))
            }
        }
    }
}
