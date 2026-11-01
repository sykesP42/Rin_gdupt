package top.sky2333.rin.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import top.sky2333.rin.ui.schedule.clockToMinutes
import top.sky2333.rin.ui.schedule.parseSections
import top.sky2333.rin.ui.schedule.sectionBounds

/** Monday of the current week, used the first time the app opens. */
fun defaultTermStart(today: LocalDate): LocalDate =
    today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun termStartDate(settings: AppSettings, today: LocalDate): LocalDate =
    runCatching { LocalDate.parse(settings.termStartDate) }.getOrNull()
        ?: defaultTermStart(today)

fun currentWeekNumber(settings: AppSettings, today: LocalDate): Int {
    val start = termStartDate(settings, today)
    if (today.isBefore(start)) return 1
    val week = (ChronoUnit.DAYS.between(start, today) / 7).toInt() + 1
    return week.coerceIn(1, 30)
}

/**
 * Builds the week the client shows from entries the reader typed in themselves.
 * No network call, no account and no external service is involved.
 */
fun buildWeekOverview(
    payload: ManualPayload,
    settings: AppSettings,
    weekOverride: Int,
    today: LocalDate,
    nowMinutes: Int
): ApiScheduleOverview {
    val start = termStartDate(settings, today)
    val currentWeek = currentWeekNumber(settings, today)
    val week = (weekOverride.takeIf { it > 0 } ?: currentWeek).coerceIn(1, 30)
    val monday = start.plusWeeks((week - 1).toLong())
    val movedAway = payload.moves
        .filter { it.week == week }
        .map { it.courseName.trim() }
        .toSet()

    val days = (1..7).map { weekday ->
        val date = monday.plusDays((weekday - 1).toLong())
        val courses = payload.courses
            .filter { it.weekday == weekday && week in it.firstWeek..it.lastWeek }
            .filterNot { it.name.trim() in movedAway }
            .map { course ->
                event(
                    courseName = course.name,
                    location = course.location,
                    sectionText = course.sectionText
                )
            }
        val movedHere = payload.moves
            .filter { it.week == week && it.weekday == weekday }
            .map { move ->
                val origin = payload.courses.firstOrNull { it.name.trim() == move.courseName.trim() }
                event(
                    courseName = move.courseName,
                    location = origin?.location.orEmpty(),
                    sectionText = move.sectionText
                )
            }
        ApiScheduleDay(
            date = date.toString(),
            weekday = weekday,
            events = (courses + movedHere).sortedBy { clockToMinutes(it.startTime) ?: 0 }
        )
    }

    val todayEvents = if (week == currentWeek) {
        days.firstOrNull { it.date == today.toString() }?.events.orEmpty()
    } else {
        emptyList()
    }
    val current = todayEvents.firstOrNull { event ->
        val startMinute = clockToMinutes(event.startTime) ?: return@firstOrNull false
        val endMinute = clockToMinutes(event.endTime) ?: return@firstOrNull false
        nowMinutes in startMinute..endMinute
    }
    val next = todayEvents.firstOrNull { event ->
        (clockToMinutes(event.startTime) ?: Int.MAX_VALUE) > nowMinutes
    }

    return ApiScheduleOverview(
        term = "",
        week = week,
        date = if (week == currentWeek) today.toString() else monday.toString(),
        current = current,
        next = next,
        today = todayEvents,
        days = days
    )
}

private fun event(courseName: String, location: String, sectionText: String): ApiScheduleEvent {
    val sections = parseSections(sectionText)
    val first = sections.firstOrNull()
    val last = sections.lastOrNull()
    val start = first?.let { sectionBounds[it]?.first }
    val end = last?.let { sectionBounds[it]?.second }
    return ApiScheduleEvent(
        courseName = courseName,
        location = location,
        sectionText = sectionText,
        startTime = start?.let(::clockText).orEmpty(),
        endTime = end?.let(::clockText).orEmpty()
    )
}

private fun clockText(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
