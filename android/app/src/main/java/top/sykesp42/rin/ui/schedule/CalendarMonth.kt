package top.sykesp42.rin.ui.schedule

import java.time.LocalDate
import java.time.YearMonth
import top.sykesp42.rin.data.JwxtCalendarDay

/**
 * Month grid with Monday as the first column. Missing dates stay null so the
 * UI can draw blanks without inventing calendar days.
 */
fun calendarMonthRows(
    month: YearMonth,
    days: Map<LocalDate, JwxtCalendarDay>
): List<List<JwxtCalendarDay?>> {
    val cells = MutableList<JwxtCalendarDay?>(month.atDay(1).dayOfWeek.value - 1) { null }
    for (day in 1..month.lengthOfMonth()) {
        val date = month.atDay(day)
        cells += days[date] ?: JwxtCalendarDay(
            date = date.toString(),
            week = "",
            status = JwxtCalendarDay.UNKNOWN
        )
    }
    while (cells.size % 7 != 0) cells += null
    return cells.chunked(7)
}

fun calendarDaysByDate(days: List<JwxtCalendarDay>): Map<LocalDate, JwxtCalendarDay> =
    days.mapNotNull { day ->
        runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day }
    }.toMap()

/** Months with at least one full week of data, sorted; drops tiny edge months. */
fun calendarMonthsOf(dayMap: Map<LocalDate, JwxtCalendarDay>): List<YearMonth> =
    dayMap.keys.groupBy(YearMonth::from)
        .filterValues { it.size >= 7 }
        .keys
        .sorted()

fun calendarMonthDays(
    dayMap: Map<LocalDate, JwxtCalendarDay>,
    month: YearMonth
): List<JwxtCalendarDay> =
    dayMap.values.filter { YearMonth.from(LocalDate.parse(it.date)) == month }.sortedBy { it.date }

/** The current month, but only when it is one the grid actually renders. */
fun calendarTodayMonth(
    dayMap: Map<LocalDate, JwxtCalendarDay>,
    today: LocalDate
): YearMonth? {
    val month = YearMonth.from(today)
    return if (month in calendarMonthsOf(dayMap)) month else null
}
