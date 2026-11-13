package uk.co.ccooper.predictor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Fixtures and live scores from ESPN's public scoreboard feed (no API key needed).
 * The feed only accepts one day per request, so ranges are fetched day by day.
 */
object Espn {
    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"
    private val DAY = DateTimeFormatter.BASIC_ISO_DATE
    private val limiter = Semaphore(6)

    val LEAGUES = listOf(
        League("eng.1", "Premier League"),
        League("eng.2", "Championship"),
        League("eng.3", "League One"),
        League("eng.4", "League Two"),
        League("eng.5", "National League"),
        League("sco.1", "Scottish Premiership"),
        League("sco.2", "Scottish Championship"),
        League("eng.fa", "FA Cup"),
        League("eng.league_cup", "Carabao Cup"),
        League("sco.tennents", "Scottish Cup"),
        League("uefa.champions", "Champions League"),
        League("uefa.europa", "Europa League"),
        League("uefa.europa.conf", "Conference League"),
        League("esp.1", "La Liga"),
        League("ger.1", "Bundesliga"),
        League("ita.1", "Serie A"),
        League("fra.1", "Ligue 1"),
        League("ned.1", "Eredivisie"),
        League("por.1", "Primeira Liga"),
    )
    private val names = LEAGUES.associate { it.code to it.name }
    fun leagueName(code: String) = names[code] ?: code

    /** All fixtures for the given leagues between [from] and [to] inclusive, sorted by kick-off. */
    suspend fun fixtures(leagues: Collection<String>, from: LocalDate, to: LocalDate): List<Fixture> = coroutineScope {
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        leagues.flatMap { l -> days.map { d -> async { limiter.withPermit { scoreboard(l, d) } } } }
            .awaitAll().flatten()
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.kickoff }, { it.leagueName }, { it.home }))
    }

    /** Latest data for the given fixtures, keyed by fixture id. Missing ones are simply absent. */
    suspend fun live(fixtures: List<Fixture>): Map<String, Fixture> = coroutineScope {
        if (fixtures.isEmpty()) return@coroutineScope emptyMap()
        val wanted = fixtures.associateBy { it.id }
        // ESPN days are US-based, so look at the UTC day and the day before.
        val keys = fixtures.flatMap { f ->
            val d = Instant.ofEpochMilli(f.kickoff).atOffset(ZoneOffset.UTC).toLocalDate()
            listOf(f.league to d, f.league to d.minusDays(1))
        }.toSet()
        val found = keys.map { (l, d) -> async { limiter.withPermit { runCatching { scoreboard(l, d) }.getOrDefault(emptyList()) } } }
            .awaitAll().flatten().filter { it.id in wanted }.associateBy { it.id }.toMutableMap()
        // Fallback for anything the scoreboard didn't return (e.g. rescheduled matches).
        wanted.values.filter { it.id !in found }.map { f ->
            async { limiter.withPermit { runCatching { summary(f.league, f.id) }.getOrNull() } }
        }.awaitAll().filterNotNull().forEach { found[it.id] = it }
        found
    }

    private suspend fun scoreboard(league: String, day: LocalDate): List<Fixture> {
        val json = get("$BASE/$league/scoreboard?dates=${day.format(DAY)}&limit=200")
        val events = json.optJSONArray("events") ?: return emptyList()
        return (0 until events.length()).mapNotNull { i ->
            val e = events.getJSONObject(i)
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return@mapNotNull null
            parse(league, e.getString("id"), e.optString("date"), comp, e.optJSONObject("status"))
        }
    }

    private suspend fun summary(league: String, id: String): Fixture? {
        val json = get("$BASE/$league/summary?event=$id")
        val comp = json.optJSONObject("header")?.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        return parse(league, id, comp.optString("date"), comp, comp.optJSONObject("status"))
    }

    private fun parse(league: String, id: String, date: String, comp: JSONObject, status: JSONObject?): Fixture? {
        val teams = comp.optJSONArray("competitors") ?: return null
        var home: JSONObject? = null
        var away: JSONObject? = null
        for (i in 0 until teams.length()) {
            val t = teams.getJSONObject(i)
            if (t.optString("homeAway") == "home") home = t else away = t
        }
        if (home == null || away == null) return null
        val type = (status ?: comp.optJSONObject("status"))?.optJSONObject("type")
        val state = type?.optString("state") ?: "pre"
        fun score(t: JSONObject): Int? =
            if (state == "pre") null else t.optString("score").toIntOrNull()
        fun team(t: JSONObject) = t.optJSONObject("team")
        return Fixture(
            id = id,
            league = league,
            leagueName = leagueName(league),
            kickoff = runCatching { OffsetDateTime.parse(date).toInstant().toEpochMilli() }.getOrDefault(0L),
            home = team(home)?.optString("displayName").orEmpty(),
            away = team(away)?.optString("displayName").orEmpty(),
            homeShort = team(home)?.optString("shortDisplayName").orEmpty()
                .ifBlank { team(home)?.optString("displayName").orEmpty() },
            awayShort = team(away)?.optString("shortDisplayName").orEmpty()
                .ifBlank { team(away)?.optString("displayName").orEmpty() },
            homeScore = score(home),
            awayScore = score(away),
            statusName = type?.optString("name") ?: "STATUS_SCHEDULED",
            state = state,
            detail = type?.optString("shortDetail").orEmpty(),
        )
    }

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "PredictionLeague/1.0 (Android)")
            val code = c.responseCode
            if (code !in 200..299) {
                // A 400 here normally just means "no fixtures that day".
                if (code == 400) return@withContext JSONObject()
                error("Fixture service error $code")
            }
            JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }
}
