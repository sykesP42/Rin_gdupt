package top.sykesp42.rin.ui.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.ApiScheduleOverview

/**
 * Day headers count course entries, not class periods: one course can span two or
 * more sections, so "5 节课" would overstate or understate the day.
 */
/** "回到本周" is only useful when the current week is known and another week is shown. */
fun shouldShowReturnToCurrent(viewedWeek: Int, currentWeek: Int): Boolean =
    currentWeek > 0 && viewedWeek != currentWeek

fun courseCountLabel(count: Int): String =
    if (count > 0) "$count 门课" else "没有课"

/** The overview date anchors "today" on the campus clock. */
fun todayAnchor(overviewDate: String): LocalDate? =
    overviewDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** Class times and the section timetable use the campus local time. */
val campusZone: ZoneId = ZoneId.of("Asia/Shanghai")

/**
 * Minutes since midnight on the campus clock, so the grid's "now" row is anchored to the
 * same zone as [todayAnchor] instead of the device timezone.
 */
fun nowMinutesAt(instant: Instant = Instant.now(), zone: ZoneId = campusZone): Int =
    instant.atZone(zone).let { it.hour * 60 + it.minute }

/** Today's date on the campus clock. */
fun todayAtCampusClock(instant: Instant = Instant.now(), zone: ZoneId = campusZone): LocalDate =
    instant.atZone(zone).toLocalDate()

/**
 * The date to mark as today inside one overview. The campus clock is the
 * authority, so a cached week never labels its own last day as "今天".
 * Weeks that do not contain the current date return null, so nothing is marked.
 */
fun displayedToday(
    dayDates: List<String>,
    campusToday: LocalDate = todayAtCampusClock()
): LocalDate? {
    val dates = dayDates.mapNotNull { todayAnchor(it) }
    return campusToday.takeIf { it in dates }
}

/**
 * The classes belonging to [today]. The overview's own `today` list is only valid
 * on the date the overview was produced; a cached overview can carry yesterday's
 * list, so prefer the week grid's entry for the same date instead.
 */
fun todayEvents(overview: ApiScheduleOverview, today: LocalDate): List<ApiScheduleEvent> {
    val date = today.toString()
    return when {
        overview.date.isBlank() -> overview.today
        overview.date == date -> overview.today
        else -> overview.days.firstOrNull { it.date == date }?.events.orEmpty()
    }
}

/** A class row's live relationship to the current campus time. */
enum class ClassSlotState { UPCOMING, CURRENT, PAST }

/** "08:00" -> 480; blank/malformed -> null. */
fun clockToMinutes(clock: String): Int? {
    val parts = clock.trim().split(':')
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/**
 * Classify one class against `nowMinutes`. An unparseable start is treated as
 * upcoming (never struck through) so a bad clock never hides a real class.
 */
fun classSlotState(start: String, end: String, nowMinutes: Int): ClassSlotState {
    val s = clockToMinutes(start) ?: return ClassSlotState.UPCOMING
    val e = clockToMinutes(end)
    return when {
        nowMinutes < s -> ClassSlotState.UPCOMING
        e != null && nowMinutes > e -> ClassSlotState.PAST
        else -> ClassSlotState.CURRENT
    }
}

/** Index of the first upcoming class, or -1 when none remain today. */
fun nextUpcomingIndex(states: List<ClassSlotState>): Int =
    states.indexOfFirst { it == ClassSlotState.UPCOMING }

/**
 * A quiet-period hint for the campus day at [nowMinutes], or null while a class
 * is in session (or nothing parseable is known). Clocks are `(start, end)`
 * pairs in any order; boundaries match [classSlotState]: `now == end` still
 * belongs to the class, `now > end` is already resting.
 */
fun restHint(clocks: List<Pair<String, String>>, nowMinutes: Int): String? {
    val windows = clocks.mapNotNull { (start, end) ->
        val s = clockToMinutes(start) ?: return@mapNotNull null
        val e = clockToMinutes(end)
        s to e
    }.sortedBy { it.first }
    val first = windows.firstOrNull() ?: return null
    if (nowMinutes < first.first) return "还没开课"
    for (index in 0 until windows.size - 1) {
        val end = windows[index].second
        val nextStart = windows[index + 1].first
        if (end != null && nowMinutes > end && nowMinutes < nextStart) return "课间休息"
    }
    val lastEnd = windows.mapNotNull { it.second }.maxOrNull() ?: return null
    if (nowMinutes > lastEnd) return "今天课上完了"
    return null
}
