package uk.co.ccooper.predictor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.ccooper.predictor.data.Fixture
import uk.co.ccooper.predictor.data.Outcome
import uk.co.ccooper.predictor.data.Picks
import uk.co.ccooper.predictor.data.Repo
import uk.co.ccooper.predictor.data.Scoring
import uk.co.ccooper.predictor.data.UserProfile
import uk.co.ccooper.predictor.data.Week

@Composable
fun PicksScreen(vm: AppViewModel, me: UserProfile, forUid: String = me.uid, fixedWeekId: String? = null) {
    val weeks by vm.weeks.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val allPicks by vm.allPicks.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastRefresh.collectAsStateWithLifecycle()
    val adminUids by vm.adminUids.collectAsStateWithLifecycle()
    val isAdmin = me.uid in adminUids
    val onBehalf = forUid != me.uid

    val all = weeks ?: return Centered { CircularProgressIndicator() }
    val choices = all.filter { !it.isDraft || it.id == fixedWeekId }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val week = choices.firstOrNull { it.id == (fixedWeekId ?: selectedId) } ?: choices.firstOrNull()
    if (week == null) {
        Centered {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No games yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (isAdmin) "Go to Admin to pick this week's 10 games." else "The organiser hasn't picked this week's games yet. Check back soon!",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val server by produceState<AppViewModel.Loaded<Picks?>?>(null, week.id, forUid) {
        value = null
        Repo.picksFlow(week.id, forUid).collect { value = AppViewModel.Loaded(it) }
    }
    var picks by remember(week.id, forUid) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var banker by remember(week.id, forUid) { mutableStateOf<String?>(null) }
    var dirty by remember(week.id, forUid) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(server) {
        val s = server?.value
        if (!dirty) {
            picks = s?.picks.orEmpty()
            banker = s?.banker
        }
    }
    LaunchedEffect(week.id, week.isLocked(now)) { if (week.isLocked(now)) vm.watchPicks(week.id) }

    val locked = week.isLocked(now)
    val editable = (!locked && !week.isDraft) || (onBehalf && isAdmin)
    val fixtures = vm.fixturesOf(week, live)
    val score = server?.value?.let { Scoring.score(it, fixtures) }
    val crowd = if (locked) allPicks[week.id] else null
    val forName = users.firstOrNull { it.uid == forUid }?.teamName

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                WeekHeader(
                    week = week, weeks = if (fixedWeekId == null) choices else emptyList(), now = now,
                    onSelect = { selectedId = it }, lastRefresh = lastRefresh,
                ) {
                    if (onBehalf) Text("Editing picks for ${forName ?: "player"}", fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary)
                    when {
                        !locked && !week.isDraft -> Text(
                            "Pick Home, Draw or Away for every game, and tap the ★ to bank one game for a bonus point.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        server != null && server?.value == null -> Text("You didn't submit picks for this week.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        score != null -> ScoreLine(score.points, score.correct, score.bankerCorrect, score.provisional, week.isFinalised)
                    }
                }
            }
            items(fixtures, key = { it.id }) { f ->
                FixtureCard(
                    f = f, pick = picks[f.id], isBanker = banker == f.id, editable = editable, showResult = locked,
                    crowd = crowd,
                    onPick = { picks = picks + (f.id to it); dirty = true },
                    onBanker = { banker = if (banker == f.id) null else f.id; dirty = true },
                )
            }
            item { Spacer(Modifier.size(8.dp)) }
        }
        if (editable) {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val n = fixtures.count { picks[it.id] != null }
                        Text("$n/${fixtures.size} picked" + if (banker != null) " · banker set" else " · no banker",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                dirty -> "Unsaved changes"
                                server?.value != null -> "Saved — you can change picks until the deadline"
                                else -> "Not saved yet"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dirty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            saving = true
                            val validBanker = banker?.takeIf { b -> fixtures.any { it.id == b } }
                            val clean = picks.filterKeys { k -> fixtures.any { it.id == k } }
                            vm.task({
                                Repo.savePicks(week.id, forUid, clean, validBanker)
                                dirty = false
                                vm.showMessage(if (clean.size < fixtures.size) "Saved — ${fixtures.size - clean.size} still to pick" else "Picks saved ✓")
                            }) { saving = false }
                        },
                        enabled = dirty && !saving && picks.isNotEmpty(),
                    ) { Text(if (saving) "Saving…" else "Save picks") }
                }
            }
        }
    }
}

@Composable
fun ScoreLine(points: Int, correct: Int, bankerCorrect: Boolean?, provisional: Boolean, final: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$points pts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "$correct correct" + when (bankerCorrect) { true -> " · banker ✓"; false -> " · banker ✗"; null -> "" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (provisional) LiveBadge() else if (final) Badge("FINAL", MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun LiveBadge() = Badge("LIVE", Marks.live)

@Composable
fun Badge(text: String, color: Color) {
    Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
fun WeekHeader(
    week: Week, weeks: List<Week>, now: Long, lastRefresh: Long,
    onSelect: (String) -> Unit, extra: @Composable () -> Unit = {},
) {
    Card(Modifier.widthIn(max = 640.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekPicker(week, weeks, onSelect)
            val locked = week.isLocked(now)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (locked) Icon(Icons.Filled.Lock, null, Modifier.size(16.dp))
                Text(
                    when {
                        week.isDraft -> "Draft — players can't see this yet"
                        week.isFinalised -> "Final results"
                        locked -> "Picks closed ${Fmt.dayTime(week.deadline)}"
                        else -> "Picks close ${Fmt.dayTime(week.deadline)} · in ${Fmt.countdown(week.deadline - now)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (locked && !week.isFinalised && lastRefresh > 0) Text("Scores updated ${Fmt.ago(lastRefresh, now)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .7f))
            extra()
        }
    }
}

@Composable
fun WeekPicker(week: Week, weeks: List<Week>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (weeks.size > 1) {
                TextButton(onClick = { open = true }, contentPadding = PaddingValues(0.dp)) {
                    Text(week.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Icon(Icons.Filled.ArrowDropDown, "Choose week", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            } else Text(week.title, style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(open, { open = false }) {
            weeks.forEach { w ->
                DropdownMenuItem(
                    text = { Text("${w.title} · ${Fmt.shortDay(w.deadline)}" + if (w.isDraft) " (draft)" else "") },
                    onClick = { open = false; onSelect(w.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixtureCard(
    f: Fixture, pick: String?, isBanker: Boolean, editable: Boolean, showResult: Boolean,
    crowd: List<Picks>?, onPick: (String) -> Unit, onBanker: () -> Unit,
) {
    val result = if (showResult) f.result() else null
    val hit = result?.takeIf { it.outcome != Outcome.VOID }?.let { pick == it.outcome }
    Card(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isBanker) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .55f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${f.leagueName} · ${Fmt.dayTime(f.kickoff)}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                StatusText(f)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.home, Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (f.homeScore != null && f.awayScore != null) "${f.homeScore} – ${f.awayScore}" else "v",
                        style = if (f.homeScore != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (f.isLive) Marks.live else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(f.away, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val options = listOf(Outcome.HOME to "Home", Outcome.DRAW to "Draw", Outcome.AWAY to "Away")
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    options.forEachIndexed { i, (code, label) ->
                        val selected = pick == code
                        val isResult = result?.outcome == code
                        val active = when {
                            selected && hit == true -> Marks.correct.copy(alpha = .30f)
                            selected && hit == false -> Marks.wrong.copy(alpha = .25f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        SegmentedButton(
                            selected = selected,
                            onClick = { if (editable) onPick(code) },
                            enabled = editable || selected,
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = active,
                                disabledActiveContainerColor = active,
                                disabledActiveContentColor = MaterialTheme.colorScheme.onSurface,
                                disabledInactiveContentColor = if (isResult) Marks.correct else MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                            ),
                            icon = {
                                when {
                                    selected && hit == true -> Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = Marks.correct)
                                    selected && hit == false -> Icon(Icons.Filled.Close, null, Modifier.size(16.dp), tint = Marks.wrong)
                                    selected -> SegmentedButtonDefaults.Icon(true)
                                }
                            },
                            label = {
                                Text(label, fontWeight = if (isResult) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                            },
                        )
                    }
                }
                IconToggleButton(checked = isBanker, onCheckedChange = { if (editable) onBanker() }, enabled = editable || isBanker) {
                    Icon(
                        if (isBanker) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isBanker) "Banker" else "Make this your banker",
                        tint = if (isBanker) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (result != null || (crowd != null && crowd.isNotEmpty())) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (crowd != null && crowd.isNotEmpty()) {
                        val n = crowd.size.toFloat()
                        fun pct(c: String) = (crowd.count { it.picks[f.id] == c } * 100 / n).toInt()
                        Text("Everyone: H ${pct(Outcome.HOME)}% · D ${pct(Outcome.DRAW)}% · A ${pct(Outcome.AWAY)}%",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                    } else Spacer(Modifier.weight(1f))
                    if (result != null) {
                        val pts = if (hit == true) (if (isBanker) 2 else 1) else 0
                        Text(
                            when {
                                result.outcome == Outcome.VOID -> "Void — doesn't count"
                                pick == null -> "No pick"
                                else -> "+$pts" + if (isBanker && hit == true) " (banker)" else ""
                            },
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = when (hit) { true -> Marks.correct; false -> Marks.wrong; null -> MaterialTheme.colorScheme.onSurfaceVariant },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusText(f: Fixture) {
    when {
        f.override == Outcome.VOID -> Text("VOID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        f.isLive -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Marks.live))
            Text(f.detail.ifBlank { "LIVE" }, style = MaterialTheme.typography.labelMedium, color = Marks.live, fontWeight = FontWeight.Bold)
        }
        f.isFinished -> Text(f.detail.ifBlank { "FT" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = if (f.statusName == "STATUS_POSTPONED" || f.statusName == "STATUS_CANCELED") MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {}
    }
}
