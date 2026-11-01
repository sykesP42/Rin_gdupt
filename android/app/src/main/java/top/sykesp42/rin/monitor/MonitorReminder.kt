package top.sykesp42.rin.monitor

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.ApiScheduleOverview

/**
 * A class-start reminder computed purely from the JWXT timetable. It is the
 * one reminder that keeps working when the attendance API is refused (the
 * school can block its v1 endpoints, it cannot block the schedule): "class
 * starts now, sign in by yourself". Deliberately no attempt to claim or fake
 * a sign-in.
 */
data class ClassReminderTarget(
    val key: String,
    val courseName: String,
    val location: String,
    val startTime: String
)

/**
 * The class whose start falls in `[now - grace, now + lead]`, earliest first.
 * Returns null when nothing is starting, when clocks are unparseable, or when
 * every candidate is blank. [key] is stable per class per day so one class can
 * never remind twice.
 */
fun classReminderTarget(
    events: List<ApiScheduleEvent>,
    nowMinutes: Int,
    leadMinutes: Int = 10,
    graceMinutes: Int = 8
): ClassReminderTarget? {
    if (leadMinutes < 0 || graceMinutes < 0) return null
    return events
        .mapNotNull { event ->
            val start = clockMinutes(event.startTime) ?: return@mapNotNull null
            if (start < nowMinutes - graceMinutes || start > nowMinutes + leadMinutes) return@mapNotNull null
            if (event.courseName.isBlank()) return@mapNotNull null
            event to start
        }
        .minByOrNull { it.second }
        ?.let { (event, _) ->
            ClassReminderTarget(
                key = "${event.courseName}|${event.startTime}",
                courseName = event.courseName,
                location = event.location,
                startTime = event.startTime
            )
        }
}

/** One alarm pending at a class start; carries everything the receiver needs. */
data class ScheduledClassReminder(
    val at: Instant,
    val courseName: String,
    val location: String,
    val startTime: String,
    val key: String
)

/**
 * Every remaining class start in the loaded week, not just today: opening the
 * app once arms the whole week, so a morning without an app launch still gets
 * its reminder. Alarms are pure AlarmManager entries and fire whether or not
 * 后台监控 is running.
 */
fun scheduleClassReminders(
    overview: ApiScheduleOverview,
    now: Instant,
    zone: ZoneId = ClassWindowPlanner.schoolZone,
    limit: Int = 40
): List<ScheduledClassReminder> {
    val today = now.atZone(zone).toLocalDate()
    val days = if (overview.days.isNotEmpty()) {
        overview.days.mapNotNull { day ->
            val date = parseDate(day.date) ?: return@mapNotNull null
            date to day.events
        }
    } else {
        listOf(today to overview.today)
    }
    return days
        .filter { (date, _) -> !date.isBefore(today) }
        .flatMap { (date, events) ->
            events.mapNotNull { event -> reminderFor(date, event, now, zone) }
        }
        .sortedBy { it.at }
        .take(limit)
}

private fun reminderFor(
    date: LocalDate,
    event: ApiScheduleEvent,
    now: Instant,
    zone: ZoneId
): ScheduledClassReminder? {
    val startMinutes = clockMinutes(event.startTime) ?: return null
    if (event.courseName.isBlank()) return null
    val at = date.atTime(startMinutes / 60, startMinutes % 60).atZone(zone).toInstant()
    if (!at.isAfter(now)) return null
    val target = ClassReminderTarget(
        key = "${event.courseName}|${event.startTime}",
        courseName = event.courseName,
        location = event.location,
        startTime = event.startTime
    )
    return ScheduledClassReminder(
        at = at,
        courseName = event.courseName,
        location = event.location,
        startTime = event.startTime,
        key = reminderKey(date, target)
    )
}

/**
 * Persisted dedupe key. The day must be part of it: the same course at the
 * same time tomorrow is a new class and has to remind again.
 */
fun reminderKey(day: LocalDate, target: ClassReminderTarget): String =
    "$day|${target.key}"

private fun parseDate(value: String): LocalDate? =
    value.takeIf { it.isNotBlank()}?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * Next 06:30 school-clock instant. One self-perpetuating alarm re-arms the
 * day's reminders from the cached week even when the app was never opened:
 * the alarm fires before the first class, so the reminder chain never depends
 * on a human opening the app that morning.
 */
fun nextDailyRearm(now: Instant, zone: ZoneId = ClassWindowPlanner.schoolZone): Instant {
    val zoned = now.atZone(zone)
    val today = zoned.toLocalDate().atTime(6, 30).atZone(zone)
    return if (today.toInstant().isAfter(now)) today.toInstant() else today.plusDays(1).toInstant()
}

/** "08:00" -> 480; blank or malformed -> null. */
private fun clockMinutes(value: String): Int? {
    val parts = value.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
