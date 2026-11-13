package uk.co.ccooper.predictor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.co.ccooper.predictor.data.Espn
import uk.co.ccooper.predictor.data.FIXTURES_PER_WEEK
import uk.co.ccooper.predictor.data.Fixture
import uk.co.ccooper.predictor.data.Outcome
import uk.co.ccooper.predictor.data.Repo
import uk.co.ccooper.predictor.data.UserProfile
import uk.co.ccooper.predictor.data.Week
import uk.co.ccooper.predictor.data.WeekStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ======================= Admin home =======================

@Composable
fun AdminScreen(vm: AppViewModel, onOpenWeek: (String?) -> Unit, onEditPicks: (String, String) -> Unit) {
    val weeks by vm.weeks.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val admins by vm.adminUids.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var player by remember { mutableStateOf<UserProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    val latestWeek = weeks.orEmpty().firstOrNull { !it.isDraft }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenWeek(null) }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.size(6.dp)); Text("New game week")
                }
                OutlinedButton(onClick = { shareApp(context) }) {
                    Icon(Icons.Filled.Share, null); Spacer(Modifier.size(6.dp)); Text("Invite")
                }
            }
        }
        item { SectionTitle("Game weeks") }
        val list = weeks.orEmpty()
        if (list.isEmpty()) item { Hint("No game weeks yet. Tap “New game week” to choose your first 10 games.") }
        items(list, key = { it.id }) { w ->
            Card(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().clickable { onOpenWeek(w.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.title, style = MaterialTheme.typography.titleMedium)
                        Text("Deadline ${Fmt.dayTime(w.deadline)} · ${w.fixtures.size} games",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(w, now)
                }
            }
        }
        item {
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { SectionTitle("Players (${users.size})") }
                TextButton(onClick = { adding = true }) { Text("Add player without the app") }
            }
        }
        if (users.isEmpty()) item { Hint("Nobody has joined yet. Share the app link so friends can register.") }
        items(users.sortedBy { it.teamName.lowercase() }, key = { "u" + it.uid }) { u ->
            Row(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().clickable { player = u }.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(u.teamName, fontWeight = FontWeight.SemiBold)
                    if (u.name.isNotBlank()) Text(u.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (u.uid in admins) Badge("ADMIN", MaterialTheme.colorScheme.primary)
                if (u.manual) Badge("NO APP", MaterialTheme.colorScheme.outline)
            }
            HorizontalDivider(Modifier.widthIn(max = 640.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
        }
    }

    if (adding) AddPlayerDialog(vm, onDismiss = { adding = false })

    player?.let { p ->
        PlayerAdminDialog(
            vm, p, isAdmin = p.uid in admins, isMe = p.uid == vm.user.value?.uid, latestWeek = latestWeek,
            onEditPicks = { w -> player = null; onEditPicks(w, p.uid) }, onDismiss = { player = null },
        )
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 12.dp, start = 4.dp))
}

@Composable
private fun Hint(t: String) {
    Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(4.dp))
}

@Composable
private fun StatusChip(w: Week, now: Long) {
    val (label, color) = when {
        w.isDraft -> "DRAFT" to MaterialTheme.colorScheme.outline
        w.isFinalised -> "FINAL" to MaterialTheme.colorScheme.primary
        now >= w.deadline -> "IN PLAY" to Marks.live
        else -> "OPEN" to MaterialTheme.colorScheme.tertiary
    }
    Badge(label, color)
}

@Composable
private fun AddPlayerDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var team by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val teamError = team.takeIf { it.isNotBlank() }?.let(Repo::validateTeamName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a player without the app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For someone who can't install the app (e.g. on iPhone). They appear in the table, and you enter their picks each week.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Their name") })
                OutlinedTextField(team, { team = it }, singleLine = true, label = { Text("Team name") },
                    isError = teamError != null, supportingText = { teamError?.let { Text(it) } })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { busy = true; vm.task({ Repo.addManualPlayer(name, team); vm.showMessage("${Repo.cleanTeamName(team)} added"); onDismiss() }) { busy = false } },
                enabled = !busy && name.isNotBlank() && team.isNotBlank() && teamError == null,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlayerAdminDialog(
    vm: AppViewModel, p: UserProfile, isAdmin: Boolean, isMe: Boolean, latestWeek: Week?,
    onEditPicks: (String) -> Unit, onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.teamName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (p.name.isNotBlank()) Text("Player: ${p.name}")
                Text("Season points (finalised weeks): ${p.weekPoints.values.sum()}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { renaming = true }) { Text("Rename team") }
                if (latestWeek != null) TextButton(onClick = { onEditPicks(latestWeek.id) }) { Text("Enter / fix picks for ${latestWeek.title}") }
                if (!isMe && !p.manual) TextButton(onClick = {
                    vm.run(if (isAdmin) "Admin rights removed" else "${p.teamName} is now an admin", onDone = onDismiss) {
                        Repo.setAdmin(p.uid, !isAdmin)
                    }
                }) { Text(if (isAdmin) "Remove admin rights" else "Make admin") }
                if (!isMe) TextButton(onClick = { confirmRemove = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove from league") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (renaming) RenameDialog(p.teamName, "Rename ${p.teamName}", onDismiss = { renaming = false }) { n ->
        vm.run("Team renamed", onDone = { renaming = false; onDismiss() }) { Repo.renameTeam(p.uid, p.teamName, n) }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Remove ${p.teamName}?") },
        text = { Text("They'll disappear from the league table and lose their points. They could sign in again and create a new team.") },
        confirmButton = {
            TextButton(onClick = { vm.run("${p.teamName} removed", onDone = { confirmRemove = false; onDismiss() }) { Repo.removePlayer(p) } }) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
    )
}

// ======================= Week editor =======================

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun defaultStart(): LocalDate {
    val today = LocalDate.now()
    return when (today.dayOfWeek) {
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> today
        else -> today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.FRIDAY))
    }
}

private val rangeFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeekEditorScreen(vm: AppViewModel, weekId: String?, onDone: () -> Unit, onEditPicks: (String, String) -> Unit) {
    val weeks by vm.weeks.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val allPicks by vm.allPicks.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val existing = weeks?.firstOrNull { it.id == weekId }
    if (weekId != null && existing == null) { Centered { CircularProgressIndicator() }; return }

    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(existing?.title ?: "Gameweek ${(weeks?.size ?: 0) + 1}") }
    val selected = remember { mutableStateListOf<Fixture>().apply { addAll(existing?.fixtures.orEmpty()) } }
    val overrides = remember { mutableStateMapOf<String, String?>().apply { existing?.fixtures?.forEach { put(it.id, it.override) } } }
    var offsetMin by remember { mutableStateOf<Int?>(if (existing == null) 0 else null) }
    var customDeadline by remember { mutableStateOf(existing?.deadline) }

    var from by remember { mutableStateOf(defaultStart()) }
    var to by remember { mutableStateOf(defaultStart().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))) }
    val leagues = remember { mutableStateListOf("eng.1", "eng.2", "sco.1") }
    var results by remember { mutableStateOf<List<Fixture>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(existing == null) }
    var pickRange by remember { mutableStateOf(false) }
    var pickDeadline by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    if (existing != null) LaunchedEffect(existing.id) { vm.watchPicks(existing.id) }

    val firstKo = selected.minOfOrNull { it.kickoff }
    val deadline = offsetMin?.let { o -> firstKo?.minus(o * 60_000L) } ?: customDeadline ?: firstKo

    /** The fixtures to save: latest stored/live scores, plus this screen's overrides. */
    fun fixturesToSave(): List<Fixture> {
        val current = existing?.let { vm.fixturesOf(it, live) }.orEmpty().associateBy { it.id }
        return selected.sortedBy { it.kickoff }.map { f -> (current[f.id] ?: f.withLive(live[f.id])).copy(override = overrides[f.id]) }
    }

    fun save(status: String, message: String, after: () -> Unit = onDone) {
        val d = deadline ?: return vm.showMessage("Choose some games first")
        busy = true
        vm.task({
            Repo.saveWeek(existing?.id, title.ifBlank { "Game week" }, d, status, fixturesToSave())
            vm.showMessage(message)
            after()
        }) { busy = false }
    }

    fun search() {
        searching = true
        searchError = null
        scope.launch {
            try {
                results = Espn.fixtures(leagues.toList(), from, to)
            } catch (e: Exception) {
                searchError = "Couldn't load fixtures: ${e.message}"
            } finally { searching = false }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val w = Modifier.widthIn(max = 640.dp).fillMaxWidth()
        item {
            OutlinedTextField(title, { title = it }, w, label = { Text("Title") }, singleLine = true)
        }
        if (existing != null) item {
            Row(w, verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ", style = MaterialTheme.typography.bodyMedium)
                StatusChip(existing, now)
                Spacer(Modifier.weight(1f))
                if (existing.isFinalised) Text("Points are in the table", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Selected games ----
        item {
            Row(w.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Games (${selected.size}/$FIXTURES_PER_WEEK)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!showSearch) TextButton(onClick = { showSearch = true }) { Icon(Icons.Filled.Search, null); Text(" Add games") }
            }
        }
        if (selected.isEmpty()) item { Hint("No games chosen yet — find fixtures below and tick 10.") }
        items(selected.sortedBy { it.kickoff }, key = { "s" + it.id }) { f0 ->
            val f = existing?.let { e -> vm.fixturesOf(e, live).firstOrNull { it.id == f0.id } }?.withLive(live[f0.id]) ?: f0
            SelectedFixtureRow(
                f = f, override = overrides[f.id], showResults = existing != null && now >= (deadline ?: Long.MAX_VALUE),
                onOverride = { overrides[f.id] = it },
                onRemove = { selected.removeAll { it.id == f.id }; overrides.remove(f.id) },
            )
        }

        // ---- Fixture finder ----
        if (showSearch) {
            item {
                Card(w, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Find fixtures", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { from = from.minusDays(7); to = to.minusDays(7) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week") }
                            AssistChip(onClick = { pickRange = true }, label = { Text("${from.format(rangeFmt)} – ${to.format(rangeFmt)}") },
                                leadingIcon = { Icon(Icons.Filled.DateRange, null) }, modifier = Modifier.weight(1f))
                            IconButton(onClick = { from = from.plusDays(7); to = to.plusDays(7) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Espn.LEAGUES.forEach { l ->
                                FilterChip(
                                    selected = l.code in leagues,
                                    onClick = { if (l.code in leagues) leagues.remove(l.code) else leagues.add(l.code) },
                                    label = { Text(l.name) },
                                )
                            }
                        }
                        Button(onClick = ::search, enabled = !searching && leagues.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Find fixtures")
                        }
                        searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        results?.let { if (it.isEmpty()) Text("No fixtures found for those dates and competitions (international break?). Try another week.") }
                    }
                }
            }
            results?.takeIf { it.isNotEmpty() }?.let { list ->
                fixtureResults(list, selected.map { it.id }.toSet(), w) { f, checked ->
                    if (checked) { if (selected.size < FIXTURES_PER_WEEK && selected.none { it.id == f.id }) selected.add(f) }
                    else { selected.removeAll { it.id == f.id }; overrides.remove(f.id) }
                }
            }
        }

        // ---- Deadline ----
        item {
            Column(w.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Picks deadline", style = MaterialTheme.typography.titleMedium)
                Text(deadline?.let { Fmt.dayTime(it) } ?: "Set when you pick games", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "At first kick-off", 30 to "30 min before", 60 to "1 hr before", 120 to "2 hrs before").forEach { (m, label) ->
                        FilterChip(selected = offsetMin == m, onClick = { offsetMin = m }, label = { Text(label) }, enabled = firstKo != null)
                    }
                    FilterChip(selected = offsetMin == null, onClick = { pickDeadline = true }, label = { Text("Custom…") })
                }
                if (deadline != null && firstKo != null && deadline > firstKo) Text("Warning: deadline is after the first kick-off.", color = MaterialTheme.colorScheme.error)
            }
        }

        // ---- Submissions ----
        if (existing != null && !existing.isDraft) item {
            val picks = allPicks[existing.id].orEmpty()
            val done = picks.map { it.uid }.toSet()
            val missing = users.filter { it.uid !in done }.sortedBy { it.teamName.lowercase() }
            Column(w.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Submissions: ${done.size} of ${users.size}", style = MaterialTheme.typography.titleMedium)
                if (missing.isNotEmpty()) Text("Still to pick: " + missing.joinToString { it.teamName },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var open by remember { mutableStateOf(false) }
                TextButton(onClick = { open = true }) { Text("Enter / fix picks for a player…") }
                DropdownMenu(open, { open = false }) {
                    users.sortedBy { it.teamName.lowercase() }.forEach { u ->
                        DropdownMenuItem(text = { Text(u.teamName + if (u.uid in done) " ✓" else "") }, onClick = { open = false; onEditPicks(existing.id, u.uid) })
                    }
                }
            }
        }

        // ---- Actions ----
        item {
            Column(w.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val ten = selected.size == FIXTURES_PER_WEEK
                if (existing == null || existing.isDraft) {
                    Button(onClick = { save(WeekStatus.OPEN, "Published — players can now pick") }, enabled = !busy && ten, modifier = Modifier.fillMaxWidth()) {
                        Text(if (ten) "Publish to players" else "Pick ${FIXTURES_PER_WEEK - selected.size} more game(s) to publish")
                    }
                    OutlinedButton(onClick = { save(WeekStatus.DRAFT, "Draft saved") }, enabled = !busy && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("Save as draft")
                    }
                } else {
                    Button(onClick = { save(existing.status, "Changes saved", after = {}) }, enabled = !busy && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("Save changes")
                    }
                    if (now >= existing.deadline) {
                        val undecided = fixturesToSave().count { it.result() == null || it.result()!!.provisional }
                        Button(
                            onClick = { confirm = "finalise" }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        ) { Text(if (existing.isFinalised) "Re-calculate points" else "Finalise week & award points") }
                        if (undecided > 0) Text("$undecided game(s) have no final result yet. Wait for full time, or set a result / Void above.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (existing.isFinalised) OutlinedButton(onClick = { vm.run("Week re-opened") { Repo.setWeekStatus(existing.id, WeekStatus.OPEN) } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Re-open (use live scores again)")
                    } else OutlinedButton(onClick = { vm.run("Hidden from players") { Repo.setWeekStatus(existing.id, WeekStatus.DRAFT) } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Unpublish (back to draft)")
                    }
                }
                if (existing != null) TextButton(onClick = { confirm = "delete" }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete game week") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    // ---- Dialogs ----
    if (pickRange) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        from = Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate()
                        to = Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate()
                        if (to.isAfter(from.plusDays(13))) to = from.plusDays(13)
                    }
                    pickRange = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("Cancel") } },
        ) { DateRangePicker(state, Modifier.weight(1f), title = { Text("Fixture dates", Modifier.padding(16.dp)) }) }
    }
    if (pickDeadline) DeadlinePicker(deadline ?: System.currentTimeMillis(), onDismiss = { pickDeadline = false }) {
        customDeadline = it; offsetMin = null; pickDeadline = false
    }
    when (confirm) {
        "finalise" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Finalise ${existing?.title}?") },
            text = { Text("This works out everyone's points from the results shown and adds them to the season table. You can re-calculate later if a result changes.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    val e = existing ?: return@TextButton
                    busy = true
                    vm.task({
                        // Save title/deadline/overrides first, then award points.
                        Repo.saveWeek(e.id, title, deadline ?: e.deadline, e.status, fixturesToSave())
                        Repo.finalise(e, fixturesToSave(), users)
                        vm.showMessage("Points awarded — league table updated")
                    }) { busy = false }
                }) { Text("Finalise") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete ${existing?.title}?") },
            text = { Text("This deletes the game week and everyone's picks for it. Points already awarded for it will no longer count.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    val e = existing ?: return@TextButton
                    vm.run("Game week deleted", onDone = onDone) { Repo.deleteWeek(e.id) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private fun LazyListScope.fixtureResults(list: List<Fixture>, selectedIds: Set<String>, w: Modifier, onToggle: (Fixture, Boolean) -> Unit) {
    val byDay = list.groupBy { Instant.ofEpochMilli(it.kickoff).atZone(zone).toLocalDate() }
    byDay.forEach { (day, fixtures) ->
        item(key = "d$day") {
            Text(Fmt.day(fixtures.first().kickoff), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, modifier = w.padding(top = 8.dp))
        }
        items(fixtures, key = { "r" + it.id }) { f ->
            val checked = f.id in selectedIds
            val full = selectedIds.size >= FIXTURES_PER_WEEK
            Row(
                w.clickable(enabled = checked || !full) { onToggle(f, !checked) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, { onToggle(f, it) }, enabled = checked || !full)
                Column(Modifier.weight(1f)) {
                    Text("${f.home} v ${f.away}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Fmt.time(f.kickoff)} · ${f.leagueName}" + if (f.state != "pre") " · ${f.detail}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SelectedFixtureRow(f: Fixture, override: String?, showResults: Boolean, onOverride: (String?) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${f.home} v ${f.away}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Fmt.dayTime(f.kickoff)} · ${f.leagueName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showResults || override != null) {
                    val score = if (f.homeScore != null) "${f.homeScore}–${f.awayScore} ${f.detail}" else f.detail.ifBlank { "Not started" }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Result: ", style = MaterialTheme.typography.bodySmall)
                        Box {
                            TextButton(onClick = { menu = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text(if (override == null) "Auto ($score)" else "${Fmt.outcome(override)} (set by you)", style = MaterialTheme.typography.bodySmall)
                            }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Auto — use live score") }, onClick = { menu = false; onOverride(null) })
                                DropdownMenuItem(text = { Text("Home win") }, onClick = { menu = false; onOverride(Outcome.HOME) })
                                DropdownMenuItem(text = { Text("Draw") }, onClick = { menu = false; onOverride(Outcome.DRAW) })
                                DropdownMenuItem(text = { Text("Away win") }, onClick = { menu = false; onOverride(Outcome.AWAY) })
                                DropdownMenuItem(text = { Text("Void (postponed / abandoned)") }, onClick = { menu = false; onOverride(Outcome.VOID) })
                            }
                        }
                    }
                }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlinePicker(initial: Long, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val initialZdt = Instant.ofEpochMilli(initial).atZone(zone)
    var date by remember { mutableStateOf<LocalDate?>(null) }
    if (date == null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = initialZdt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(state) }
    } else {
        val t = rememberTimePickerState(initialZdt.hour, initialZdt.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Deadline time") },
            text = { TimePicker(t) },
            confirmButton = {
                TextButton(onClick = {
                    onPicked(date!!.atTime(LocalTime.of(t.hour, t.minute)).atZone(zone).toInstant().toEpochMilli())
                }) { Text("Set deadline") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
