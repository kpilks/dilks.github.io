package uk.co.ccooper.predictor.data

import com.google.firebase.Timestamp

/** Pick / result codes. */
object Outcome {
    const val HOME = "H"
    const val DRAW = "D"
    const val AWAY = "A"
    const val VOID = "V" // admin-only: match doesn't count (postponed, abandoned…)
}

object WeekStatus {
    const val DRAFT = "draft"         // only admins can see it
    const val OPEN = "open"           // visible, picks allowed until the deadline
    const val FINALISED = "finalised" // points written to the league table
}

const val FIXTURES_PER_WEEK = 10

data class Fixture(
    val id: String,
    val league: String,
    val leagueName: String,
    val kickoff: Long,
    val home: String,
    val away: String,
    val homeShort: String = home,
    val awayShort: String = away,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val statusName: String = "STATUS_SCHEDULED",
    val state: String = "pre", // pre | in | post (ESPN)
    val detail: String = "",
    /** Admin override: H/D/A forces a result, V voids the match. Null = use the live score. */
    val override: String? = null,
) {
    val isLive get() = state == "in"
    val isFinished get() = state == "post"

    /** Copy the live ESPN data over the stored data, keeping admin fields. */
    fun withLive(live: Fixture?): Fixture = if (live == null) this else copy(
        homeScore = live.homeScore, awayScore = live.awayScore,
        statusName = live.statusName, state = live.state, detail = live.detail,
        kickoff = live.kickoff,
    )

    /**
     * The result that counts for scoring, or null if not yet known.
     * Predictions are on the 90-minute result, so any match decided in
     * extra time or on penalties counts as a draw.
     */
    fun result(): Result? {
        override?.let { return Result(it, provisional = false) }
        val h = homeScore ?: return null
        val a = awayScore ?: return null
        return when (statusName) {
            "STATUS_FULL_TIME", "STATUS_FINAL" -> Result(outcomeOf(h, a), false)
            "STATUS_FINAL_AET", "STATUS_FINAL_PEN", "STATUS_END_OF_REGULATION",
            "STATUS_END_OF_EXTRATIME", "STATUS_SHOOTOUT", "STATUS_OVERTIME", "STATUS_EXTRA_TIME",
            "STATUS_HALFTIME_ET" -> Result(Outcome.DRAW, statusName.startsWith("STATUS_FINAL").not())
            else -> if (state == "in") Result(outcomeOf(h, a), provisional = true) else null
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id, "league" to league, "leagueName" to leagueName, "kickoff" to kickoff,
        "home" to home, "away" to away, "homeShort" to homeShort, "awayShort" to awayShort,
        "homeScore" to homeScore, "awayScore" to awayScore, "statusName" to statusName,
        "state" to state, "detail" to detail, "override" to override,
    )

    companion object {
        fun outcomeOf(h: Int, a: Int) = when {
            h > a -> Outcome.HOME
            h < a -> Outcome.AWAY
            else -> Outcome.DRAW
        }

        fun from(m: Map<*, *>): Fixture = Fixture(
            id = m["id"].toString(),
            league = m["league"] as? String ?: "",
            leagueName = m["leagueName"] as? String ?: "",
            kickoff = (m["kickoff"] as? Number)?.toLong() ?: 0L,
            home = m["home"] as? String ?: "",
            away = m["away"] as? String ?: "",
            homeShort = m["homeShort"] as? String ?: (m["home"] as? String ?: ""),
            awayShort = m["awayShort"] as? String ?: (m["away"] as? String ?: ""),
            homeScore = (m["homeScore"] as? Number)?.toInt(),
            awayScore = (m["awayScore"] as? Number)?.toInt(),
            statusName = m["statusName"] as? String ?: "STATUS_SCHEDULED",
            state = m["state"] as? String ?: "pre",
            detail = m["detail"] as? String ?: "",
            override = m["override"] as? String,
        )
    }
}

data class Result(val outcome: String, val provisional: Boolean)

data class Week(
    val id: String,
    val title: String,
    val deadline: Long,
    val status: String,
    val fixtures: List<Fixture>,
    val createdAt: Long,
) {
    val isDraft get() = status == WeekStatus.DRAFT
    val isFinalised get() = status == WeekStatus.FINALISED
    fun isLocked(now: Long = System.currentTimeMillis()) = now >= deadline || isFinalised

    companion object {
        fun from(id: String, m: Map<String, Any?>): Week = Week(
            id = id,
            title = m["title"] as? String ?: "Game week",
            deadline = (m["deadline"] as? Timestamp)?.toDate()?.time ?: 0L,
            status = m["status"] as? String ?: WeekStatus.DRAFT,
            fixtures = (m["fixtures"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(Fixture::from) }
                ?: emptyList(),
            createdAt = (m["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
        )
    }
}

data class Picks(
    val uid: String,
    val picks: Map<String, String>,
    val banker: String?,
    val updatedAt: Long = 0L,
) {
    companion object {
        fun from(uid: String, m: Map<String, Any?>): Picks = Picks(
            uid = uid,
            picks = (m["picks"] as? Map<*, *>)?.entries
                ?.associate { it.key.toString() to it.value.toString() } ?: emptyMap(),
            banker = m["banker"] as? String,
            updatedAt = (m["updatedAt"] as? Timestamp)?.toDate()?.time ?: 0L,
        )
    }
}

data class UserProfile(
    val uid: String,
    val teamName: String,
    val name: String,
    val weekPoints: Map<String, Int>,
    /** Added by an admin for someone without the app; the admin enters their picks. */
    val manual: Boolean = false,
) {
    companion object {
        fun from(uid: String, m: Map<String, Any?>): UserProfile = UserProfile(
            uid = uid,
            manual = m["manual"] == true,
            teamName = m["teamName"] as? String ?: "Unnamed",
            name = m["name"] as? String ?: "",
            weekPoints = (m["weekPoints"] as? Map<*, *>)?.entries
                ?.associate { it.key.toString() to ((it.value as? Number)?.toInt() ?: 0) } ?: emptyMap(),
        )
    }
}

/** One player's score for one week. */
data class WeekScore(
    val points: Int,
    val correct: Int,
    val bankerCorrect: Boolean?, // null = no banker / not decided yet
    val provisional: Boolean,    // includes in-play results
    val submitted: Boolean,
)

object Scoring {
    fun score(picks: Picks?, fixtures: List<Fixture>): WeekScore {
        if (picks == null) return WeekScore(0, 0, null, false, submitted = false)
        var points = 0
        var correct = 0
        var provisional = false
        var bankerCorrect: Boolean? = null
        for (f in fixtures) {
            val r = f.result() ?: continue
            if (r.outcome == Outcome.VOID) continue
            if (r.provisional) provisional = true
            val hit = picks.picks[f.id] == r.outcome
            if (hit) {
                points++
                correct++
            }
            if (picks.banker == f.id) {
                bankerCorrect = hit
                if (hit) points++
            }
        }
        return WeekScore(points, correct, bankerCorrect, provisional, submitted = true)
    }
}

data class League(val code: String, val name: String)
