package uk.co.ccooper.predictor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.ccooper.predictor.data.Config
import uk.co.ccooper.predictor.data.Espn
import uk.co.ccooper.predictor.data.Fixture
import uk.co.ccooper.predictor.data.Picks
import uk.co.ccooper.predictor.data.Repo
import uk.co.ccooper.predictor.data.Scoring
import uk.co.ccooper.predictor.data.UserProfile
import uk.co.ccooper.predictor.data.Week
import uk.co.ccooper.predictor.data.WeekScore

sealed interface Boot {
    data object Loading : Boot
    data class Failed(val message: String) : Boot
    data object Ready : Boot
}

/** One row of a league table. */
data class Standing(
    val position: Int,
    val tied: Boolean,
    val user: UserProfile,
    val points: Int,
    val weekPoints: Int?,
    val live: Boolean,
    val score: WeekScore? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val _boot = MutableStateFlow<Boot>(Boot.Loading)
    val boot = _boot.asStateFlow()

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user = _user.asStateFlow()

    /** null while loading; Loaded(null) means signed in but no team yet. */
    data class Loaded<T>(val value: T)
    private val _profile = MutableStateFlow<Loaded<UserProfile?>?>(null)
    val profile = _profile.asStateFlow()

    private val _adminUids = MutableStateFlow<Set<String>>(emptySet())
    val adminUids = _adminUids.asStateFlow()

    private val _users = MutableStateFlow<List<UserProfile>>(emptyList())
    val users = _users.asStateFlow()

    private val _weeks = MutableStateFlow<List<Week>?>(null)
    val weeks = _weeks.asStateFlow()

    /** Live ESPN data keyed by fixture id. */
    private val _live = MutableStateFlow<Map<String, Fixture>>(emptyMap())
    val live = _live.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _lastRefresh = MutableStateFlow(0L)
    val lastRefresh = _lastRefresh.asStateFlow()
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError = _refreshError.asStateFlow()

    /** Everyone's picks for weeks we're watching. */
    private val _allPicks = MutableStateFlow<Map<String, List<Picks>>>(emptyMap())
    val allPicks = _allPicks.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun showMessage(m: String?) { _message.value = m }

    private var sessionJobs = mutableListOf<Job>()
    private val picksJobs = mutableMapOf<String, Job>()
    private val refreshLock = Mutex()
    @Volatile private var foreground = true

    init { start() }

    fun start() {
        _boot.value = Boot.Loading
        viewModelScope.launch {
            val err = Config.init(getApplication())
            if (err != null) { _boot.value = Boot.Failed(err); return@launch }
            _boot.value = Boot.Ready
            onReady()
        }
    }

    fun applyPastedConfig(json: String) {
        val err = Config.applyPasted(getApplication(), json)
        if (err != null) _boot.value = Boot.Failed(err) else { _boot.value = Boot.Ready; onReady() }
    }

    private var readyStarted = false
    private fun onReady() {
        if (readyStarted) return
        readyStarted = true
        viewModelScope.launch {
            Repo.authState().collect { u ->
                if (u?.uid != _user.value?.uid || (u == null)) startSession(u)
                _user.value = u
            }
        }
        // Clock tick: drives countdowns and noticing deadlines passing.
        viewModelScope.launch {
            while (true) {
                _now.value = System.currentTimeMillis()
                ensurePicksWatchers()
                delay(15_000)
            }
        }
        // Live-score polling loop.
        viewModelScope.launch {
            while (true) {
                val wait = if (foreground && _user.value != null && _weeks.value != null) {
                    runCatching { refreshLive(force = false) }
                    nextPollDelay()
                } else 10_000L
                delay(wait)
            }
        }
    }

    private fun startSession(u: FirebaseUser?) {
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
        picksJobs.values.forEach { it.cancel() }
        picksJobs.clear()
        _allPicks.value = emptyMap()
        _profile.value = null
        _weeks.value = null
        _users.value = emptyList()
        _adminUids.value = emptySet()
        if (u == null) return
        sessionJobs += viewModelScope.launch { Repo.profileFlow(u.uid).collect { _profile.value = Loaded(it) } }
        sessionJobs += viewModelScope.launch { Repo.adminUidsFlow().collect { _adminUids.value = it; ensurePicksWatchers() } }
        sessionJobs += viewModelScope.launch { Repo.usersFlow().collect { _users.value = it } }
        sessionJobs += viewModelScope.launch {
            Repo.weeksFlow().collect {
                _weeks.value = it
                ensurePicksWatchers()
            }
        }
    }

    fun setForeground(fg: Boolean) {
        foreground = fg
        if (fg) {
            _now.value = System.currentTimeMillis()
            viewModelScope.launch { runCatching { refreshLive(force = false) } }
        }
    }

    val isAdmin get() = _user.value?.uid?.let { it in _adminUids.value } == true

    /** Weeks players can see (admins also see drafts). */
    fun visibleWeeks(weeks: List<Week>, admin: Boolean) = weeks.filter { admin || !it.isDraft }

    /** Fixtures with live scores merged in. Finalised weeks are frozen. */
    fun fixturesOf(week: Week, live: Map<String, Fixture> = _live.value): List<Fixture> =
        if (week.isFinalised) week.fixtures else week.fixtures.map { it.withLive(live[it.id]) }

    /** Weeks that have started but aren't finalised: their points come from live scores. */
    private fun activeWeeks(weeks: List<Week>, now: Long) =
        weeks.filter { !it.isDraft && !it.isFinalised && now >= it.deadline }

    // ---------- picks watchers ----------

    /** Start watching everyone's picks for a week (allowed once its deadline has passed). */
    fun watchPicks(weekId: String) {
        if (picksJobs[weekId]?.isActive == true) return
        val week = _weeks.value?.firstOrNull { it.id == weekId } ?: return
        if (!isAdmin && System.currentTimeMillis() < week.deadline) return
        picksJobs[weekId] = viewModelScope.launch {
            Repo.allPicksFlow(weekId) { picksJobs.remove(weekId)?.cancel() }
                .collect { list -> _allPicks.update { it + (weekId to list) } }
        }
    }

    private fun ensurePicksWatchers() {
        val weeks = _weeks.value ?: return
        activeWeeks(weeks, System.currentTimeMillis()).forEach { watchPicks(it.id) }
    }

    // ---------- league tables ----------

    fun seasonTable(
        users: List<UserProfile>, weeks: List<Week>, allPicks: Map<String, List<Picks>>,
        live: Map<String, Fixture>, now: Long,
    ): List<Standing> {
        val finalised = weeks.filter { it.isFinalised }
        val active = activeWeeks(weeks, now)
        val latest = weeks.filter { !it.isDraft && (it.isFinalised || now >= it.deadline) }.maxByOrNull { it.deadline }
        val activeScores = active.associate { w ->
            val fx = fixturesOf(w, live)
            val byUser = allPicks[w.id].orEmpty().associateBy { it.uid }
            w.id to users.associate { u -> u.uid to Scoring.score(byUser[u.uid], fx) }
        }
        val rows = users.map { u ->
            val stored = finalised.sumOf { u.weekPoints[it.id] ?: 0 }
            val livePts = activeScores.values.sumOf { it[u.uid]?.points ?: 0 }
            val isLive = activeScores.values.any { it[u.uid]?.provisional == true }
            val wk = latest?.let { w ->
                if (w.isFinalised) u.weekPoints[w.id] else activeScores[w.id]?.get(u.uid)?.points
            }
            Triple(u, stored + livePts, wk to isLive)
        }.sortedWith(compareByDescending<Triple<UserProfile, Int, Pair<Int?, Boolean>>> { it.second }
            .thenBy { it.first.teamName.lowercase() })
        return rank(rows.map { Standing(0, false, it.first, it.second, it.third.first, it.third.second) })
    }

    fun weekTable(week: Week, users: List<UserProfile>, picks: List<Picks>?, live: Map<String, Fixture>): List<Standing> {
        val fx = fixturesOf(week, live)
        val byUser = picks.orEmpty().associateBy { it.uid }
        val rows = users.map { u ->
            val s = Scoring.score(byUser[u.uid], fx)
            Standing(0, false, u, s.points, s.points, s.provisional, s)
        }.sortedWith(compareByDescending<Standing> { it.score?.submitted == true }
            .thenByDescending { it.points }
            .thenByDescending { it.score?.bankerCorrect == true }
            .thenBy { it.user.teamName.lowercase() })
        return rank(rows)
    }

    private fun rank(sorted: List<Standing>): List<Standing> {
        var lastPts = Int.MIN_VALUE
        var lastPos = 0
        return sorted.mapIndexed { i, s ->
            val pos = if (s.points == lastPts) lastPos else i + 1
            lastPts = s.points
            lastPos = pos
            s.copy(position = pos)
        }.let { list ->
            val counts = list.groupingBy { it.position }.eachCount()
            list.map { it.copy(tied = (counts[it.position] ?: 0) > 1) }
        }
    }

    // ---------- live scores ----------

    private fun pollTargets(force: Boolean): List<Fixture> {
        val now = System.currentTimeMillis()
        val weeks = _weeks.value.orEmpty().filter { !it.isDraft && !it.isFinalised }
        return weeks.flatMap { w -> fixturesOf(w) }.filter { f ->
            force && f.kickoff <= now + 12 * 3600_000 || (f.kickoff <= now + 120_000 && !f.isFinished)
        }.distinctBy { it.id }
    }

    private fun nextPollDelay(): Long {
        val now = System.currentTimeMillis()
        val pending = _weeks.value.orEmpty().filter { !it.isDraft && !it.isFinalised }.flatMap { fixturesOf(it) }
        if (pending.any { it.isLive || (it.kickoff <= now && !it.isFinished) }) return 60_000L
        val next = pending.filter { it.kickoff > now }.minOfOrNull { it.kickoff } ?: return 15 * 60_000L
        return (next - now).coerceIn(30_000L, 15 * 60_000L)
    }

    fun refreshNow() {
        viewModelScope.launch { refreshLive(force = true) }
    }

    private suspend fun refreshLive(force: Boolean) = refreshLock.withLock {
        val targets = pollTargets(force)
        if (targets.isEmpty()) { if (force) _lastRefresh.value = System.currentTimeMillis(); return@withLock }
        _refreshing.value = true
        try {
            val fresh = Espn.live(targets)
            _live.update { it + fresh }
            _lastRefresh.value = System.currentTimeMillis()
            _refreshError.value = null
            if (isAdmin) persistResults()
        } catch (e: Exception) {
            _refreshError.value = "Couldn't update scores: ${e.message ?: "network error"}"
        } finally {
            _refreshing.value = false
        }
    }

    /** The admin's phone saves new scores to the database, so everyone has a record of them. */
    private suspend fun persistResults() {
        for (w in _weeks.value.orEmpty().filter { !it.isDraft && !it.isFinalised }) {
            val merged = fixturesOf(w)
            val changed = merged.zip(w.fixtures).any { (a, b) ->
                a.homeScore != b.homeScore || a.awayScore != b.awayScore || a.statusName != b.statusName || a.kickoff != b.kickoff
            }
            if (changed) runCatching { Repo.setFixtures(w.id, merged) }
        }
    }

    // ---------- actions with error handling ----------

    fun run(success: String? = null, onDone: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (success != null) _message.value = success
                onDone()
            } catch (e: Exception) {
                _message.value = friendly(e)
            }
        }
    }

    /** Run [block] in the ViewModel's scope (survives screen changes); errors become a message. */
    fun task(block: suspend () -> Unit, onFinish: () -> Unit = {}) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { _message.value = friendly(e) } finally { onFinish() }
        }
    }

    companion object {
        fun friendly(e: Throwable): String {
            val m = e.message.orEmpty()
            return when {
                "PERMISSION_DENIED" in m || "permission" in m.lowercase() ->
                    "Not allowed — the deadline may have passed, or you don't have permission."
                "password is invalid" in m || "INVALID_LOGIN_CREDENTIALS" in m || "credential is incorrect" in m ->
                    "Wrong email or password."
                "no user record" in m -> "No account with that email."
                "already in use" in m -> "An account with that email already exists. Try signing in."
                "badly formatted" in m -> "That email address doesn't look right."
                "at least 6 characters" in m || "WEAK_PASSWORD" in m -> "Password must be at least 6 characters."
                "network" in m.lowercase() || "UNAVAILABLE" in m -> "No connection — check your internet and try again."
                m.isNotBlank() -> m
                else -> "Something went wrong."
            }
        }
    }
}
