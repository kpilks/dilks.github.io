package uk.co.ccooper.predictor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.ccooper.predictor.data.Outcome
import uk.co.ccooper.predictor.data.Picks
import uk.co.ccooper.predictor.data.UserProfile
import uk.co.ccooper.predictor.data.Week

@Composable
fun LeagueScreen(vm: AppViewModel, me: UserProfile) {
    val weeks by vm.weeks.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val allPicks by vm.allPicks.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastRefresh.collectAsStateWithLifecycle()
    val refreshError by vm.refreshError.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var selectedWeek by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Pair<UserProfile, Week>?>(null) }

    val all = weeks ?: return Centered { CircularProgressIndicator() }
    // Weeks with results to show: started or finished.
    val started = all.filter { !it.isDraft && (it.isFinalised || now >= it.deadline) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Season") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Week by week") })
        }
        val status = when {
            refreshError != null -> refreshError!!
            lastRefresh > 0 -> "Live scores updated ${Fmt.ago(lastRefresh, now)}"
            else -> null
        }
        if (status != null) Text(status, style = MaterialTheme.typography.bodySmall,
            color = if (refreshError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

        if (tab == 0) {
            val table = vm.seasonTable(users, all, allPicks, live, now)
            val latest = started.maxByOrNull { it.deadline }
            Table(
                rows = table, me = me,
                header = { HeaderRow(wkLabel = latest?.title?.let { shortTitle(it) } ?: "Wk") },
                empty = "No players yet",
                onClick = { s -> latest?.let { w -> if (allPicks[w.id] != null) viewing = s.user to w } },
            ) { s ->
                Text(s.weekPoints?.toString() ?: "–", Modifier.width(56.dp), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.points.toString(), Modifier.width(48.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            }
        } else {
            if (started.isEmpty()) {
                Centered { Text("No results yet — the table fills up once the first deadline passes.",
                    Modifier.padding(32.dp), textAlign = TextAlign.Center) }
                return@Column
            }
            val week = started.firstOrNull { it.id == selectedWeek } ?: started.first()
            LaunchedEffect(week.id) { vm.watchPicks(week.id) }
            val picks = allPicks[week.id]
            Column(Modifier.padding(horizontal = 12.dp)) {
                WeekPicker(week, started) { selectedWeek = it }
            }
            if (picks == null) { Centered { CircularProgressIndicator() }; return@Column }
            val table = vm.weekTable(week, users, picks, live)
            Table(
                rows = table, me = me,
                header = { HeaderRow(wkLabel = "Banker", ptsLabel = "Pts") },
                empty = "Nobody picked this week",
                onClick = { s -> if (s.score?.submitted == true) viewing = s.user to week },
            ) { s ->
                val sc = s.score
                Row(Modifier.width(56.dp), horizontalArrangement = Arrangement.Center) {
                    when {
                        sc?.submitted != true -> Text("no picks", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        sc.bankerCorrect == true -> Icon(Icons.Filled.Star, "Banker correct", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                        sc.bankerCorrect == false -> Text("✗", color = Marks.wrong)
                        else -> Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(if (sc?.submitted == true) s.points.toString() else "–", Modifier.width(48.dp),
                    textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            }
        }
    }

    viewing?.let { (u, w) ->
        PlayerPicksDialog(u, w, vm.fixturesOf(w, live), allPicks[w.id]?.firstOrNull { it.uid == u.uid }) { viewing = null }
    }
}

private fun shortTitle(t: String) = Regex("(\\d+)").find(t)?.let { "GW${it.value}" } ?: "Wk"

@Composable
internal fun HeaderRow(wkLabel: String, ptsLabel: String = "Pts") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val style = MaterialTheme.typography.labelMedium
        val c = MaterialTheme.colorScheme.onSurfaceVariant
        Text("#", Modifier.width(40.dp), style = style, color = c)
        Text("Team", Modifier.weight(1f), style = style, color = c)
        Text(wkLabel, Modifier.width(56.dp), style = style, color = c, textAlign = TextAlign.Center)
        Text(ptsLabel, Modifier.width(48.dp), style = style, color = c, textAlign = TextAlign.End)
    }
}

@Composable
internal fun Table(
    rows: List<Standing>, me: UserProfile, header: @Composable () -> Unit, empty: String,
    onClick: (Standing) -> Unit, trailing: @Composable (Standing) -> Unit,
) {
    if (rows.isEmpty()) { Centered { Text(empty) }; return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Column(Modifier.widthIn(max = 640.dp)) { header(); HorizontalDivider() } }
        items(rows, key = { it.user.uid }) { s ->
            val mine = s.user.uid == me.uid
            Column(Modifier.widthIn(max = 640.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (mine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else MaterialTheme.colorScheme.background)
                        .clickable { onClick(s) }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.width(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (s.position == 1 && s.points > 0) Icon(Icons.Filled.EmojiEvents, "Top", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                        else Text((if (s.tied) "=" else "") + s.position, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(s.user.teamName + if (mine) " (you)" else "", fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (s.live) LiveBadge()
                    }
                    trailing(s)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
            }
        }
    }
}

@Composable
fun PlayerPicksDialog(user: UserProfile, week: Week, fixtures: List<uk.co.ccooper.predictor.data.Fixture>, picks: Picks?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${user.teamName} · ${week.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (picks == null) Text("No picks submitted.")
                else fixtures.forEach { f ->
                    val r = f.result()
                    val pick = picks.picks[f.id]
                    val hit = r?.takeIf { it.outcome != Outcome.VOID }?.let { it.outcome == pick }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${f.homeShort} v ${f.awayShort}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (f.homeScore != null) Text("${f.homeScore}–${f.awayScore} ${f.detail}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (picks.banker == f.id) Icon(Icons.Filled.Star, "Banker", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(Fmt.outcome(pick), Modifier.width(52.dp), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold,
                            color = when (hit) { true -> Marks.correct; false -> Marks.wrong; null -> MaterialTheme.colorScheme.onSurface })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
