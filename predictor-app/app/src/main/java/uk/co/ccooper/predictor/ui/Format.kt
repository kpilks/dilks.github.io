package uk.co.ccooper.predictor.ui

import uk.co.ccooper.predictor.data.Outcome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val zone get() = ZoneId.systemDefault()
    private val dayTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.UK)
    private val day = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)
    private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

    fun dayTime(ms: Long): String = dayTime.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun day(ms: Long): String = day.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun shortDay(ms: Long): String = shortDay.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun time(ms: Long): String = time.format(Instant.ofEpochMilli(ms).atZone(zone))

    /** "2d 4h", "3h 12m", "45m" — for countdowns. */
    fun countdown(ms: Long): String {
        if (ms <= 0) return "now"
        val m = ms / 60_000
        val d = m / (60 * 24)
        val h = (m / 60) % 24
        val min = m % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${min}m"
            else -> "${maxOf(min, 1)}m"
        }
    }

    fun ago(ms: Long, now: Long): String {
        val s = (now - ms) / 1000
        return when {
            ms == 0L -> "never"
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            else -> time(ms)
        }
    }

    fun outcome(code: String?) = when (code) {
        Outcome.HOME -> "Home"
        Outcome.DRAW -> "Draw"
        Outcome.AWAY -> "Away"
        Outcome.VOID -> "Void"
        else -> "–"
    }
}
