package top.sykesp42.rin.ui.schedule

import kotlin.math.roundToInt
import top.sykesp42.rin.data.ManualCourse

/** One line of the semester report; `value` is already formatted for display. */
data class ReportRow(val label: String, val value: String)

/** A headline number, split so the UI can set the unit in a smaller type. */
data class ReportStat(val label: String, val value: String, val unit: String)

/** One recurring teaching slot, flattened from the reader's own courses. */
data class SemesterSlot(
    val courseName: String,
    val weekday: Int,
    val firstWeek: Int,
    val lastWeek: Int,
    val sections: Int,
    val location: String = "",
    val firstSection: Int = 1,
    val teacher: String = ""
)

data class WeekLoad(val week: Int, val sections: Int, val sessions: Int)

data class WeekdayLoad(val weekday: Int, val sections: Int, val sessions: Int)

data class PeriodLoad(val label: String, val sessions: Int)

data class CourseLoad(
    val name: String,
    val sessions: Int,
    val sections: Int,
    val firstWeek: Int,
    val lastWeek: Int,
    val meetingsPerWeek: Int,
    val sectionsPerWeek: Int
)

/**
 * A term-wide report: every teaching week, every course, computed from the
 * recurring slots rather than from whatever week happens to be on screen.
 */
data class SemesterReport(
    val title: String,
    val subtitle: String,
    val stats: List<ReportStat>,
    val weeks: List<WeekLoad>,
    val weekdays: List<WeekdayLoad>,
    val periods: List<PeriodLoad>,
    val topCourses: List<CourseLoad>,
    val rows: List<ReportRow>,
    val note: String,
    val currentWeek: Int,
    val totalWeeks: Int
) {
    val peakWeek: WeekLoad? get() = weeks.maxByOrNull { it.sessions }
}

fun semesterSlots(manualCourses: List<ManualCourse>): List<SemesterSlot> {
    val manual = manualCourses.map {
        val first = it.firstWeek.coerceAtLeast(1)
        SemesterSlot(
            courseName = it.name,
            weekday = it.weekday.coerceIn(1, 7),
            firstWeek = first,
            lastWeek = it.lastWeek.coerceAtLeast(first),
            sections = sectionSpan(it.sectionText),
            location = it.location,
            firstSection = firstSection(it.sectionText),
            teacher = it.teacher
        )
    }
    return manual
        .filter { it.courseName.isNotBlank() }
        .distinctBy {
            listOf(
                it.courseName.trim(),
                it.weekday,
                it.firstWeek,
                it.lastWeek,
                it.sections,
                it.firstSection,
                it.location.trim(),
                it.teacher.trim()
            )
        }
}

/**
 * Sections covered by a timetable cell: `0102` → 2, `1-2` → 2, `1,2,5` → 3.
 * Used for the semester totals, so a two-section class never counts as one.
 */
fun sectionSpan(text: String): Int {
    if (text.isBlank()) return 1
    val segments = text.replace("节", "").replace(" ", "")
        .split(',', '，', '、', ';', '；')
        .filter { it.isNotBlank() }
    val total = segments.sumOf { segment ->
        val numbers = Regex("\\d+").findAll(segment).map { it.value }.toList()
        when {
            numbers.size >= 2 && segment.any { it == '-' || it == '~' || it == '—' } -> {
                val first = numbers.first().toIntOrNull() ?: 1
                val last = numbers.last().toIntOrNull() ?: first
                (last - first + 1).coerceAtLeast(1)
            }
            numbers.size >= 2 -> numbers.size
            numbers.size == 1 -> {
                val digits = numbers.first()
                if (digits.length in 4..8 && digits.length % 2 == 0) digits.length / 2 else 1
            }
            else -> 1
        }
    }
    return total.coerceIn(1, 12)
}

/** First section of a cell: `0102` → 1, `3-4` → 3, `12` → 12. */
internal fun firstSection(text: String): Int {
    val digits = Regex("\\d+").find(text)?.value.orEmpty()
    if (digits.isEmpty()) return 99
    return if (digits.length >= 4 && digits.length % 2 == 0) {
        digits.take(2).toIntOrNull() ?: 99
    } else {
        digits.toIntOrNull() ?: 99
    }
}

fun buildSemesterReport(
    termLabel: String,
    currentWeek: Int,
    slots: List<SemesterSlot>,
    examCount: Int = 0,
    todoCount: Int = 0
): SemesterReport {
    val maxLastWeek = slots.maxOfOrNull { it.lastWeek } ?: 0
    val totalWeeks = maxOf(maxLastWeek, currentWeek, 1)
    val inTerm = slots
        .filter { it.firstWeek <= totalWeeks }
        .distinctBy {
            listOf(
                it.courseName.trim(),
                it.weekday,
                it.firstWeek,
                it.lastWeek,
                it.sections,
                it.firstSection,
                it.location.trim(),
                it.teacher.trim()
            )
        }

    fun activeWeeks(slot: SemesterSlot): Int =
        (slot.lastWeek.coerceAtMost(totalWeeks) - slot.firstWeek + 1).coerceAtLeast(0)

    val totalSections = inTerm.sumOf { activeWeeks(it) * it.sections }
    val totalSessions = inTerm.sumOf { activeWeeks(it) }
    val remainingMeetings = inTerm.sumOf { slot ->
        (slot.lastWeek.coerceAtMost(totalWeeks) - maxOf(slot.firstWeek, currentWeek) + 1)
            .coerceAtLeast(0)
    }
    val averaged = if (totalWeeks > 0) totalSections.toFloat() / totalWeeks else 0f
    val average = Math.round(averaged).coerceAtLeast(0)

    val weeks = (1..totalWeeks).map { week ->
        val active = inTerm.filter { week in it.firstWeek..it.lastWeek }
        WeekLoad(
            week = week,
            sections = active.sumOf { it.sections },
            sessions = active.size
        )
    }
    val days = (1..7).map { weekday ->
        val active = inTerm.filter { it.weekday == weekday }
        WeekdayLoad(
            weekday = weekday,
            sections = active.sumOf { activeWeeks(it) * it.sections },
            sessions = active.sumOf { activeWeeks(it) }
        )
    }
    val periods = listOf(
        "上午" to (1..4),
        "下午" to (5..8),
        "晚上" to (9..12)
    ).map { (label, range) ->
        PeriodLoad(
            label = label,
            sessions = inTerm.filter { it.firstSection in range }.sumOf { activeWeeks(it) }
        )
    }
    val referenceWeek = currentWeek.coerceIn(1, totalWeeks)
    val grouped = inTerm.groupBy { it.courseName.trim() }
    val topCourses = grouped.map { (name, rows) ->
        val weekly = rows.filter { referenceWeek in it.firstWeek..it.lastWeek }
        CourseLoad(
            name = name,
            sessions = rows.sumOf { activeWeeks(it) },
            sections = rows.sumOf { activeWeeks(it) * it.sections },
            firstWeek = rows.minOf { it.firstWeek },
            lastWeek = rows.maxOf { it.lastWeek },
            meetingsPerWeek = weekly.size,
            sectionsPerWeek = weekly.sumOf { it.sections }
        )
    }.sortedWith(compareByDescending<CourseLoad> { it.sessions }.thenByDescending { it.sections })
        .take(8)

    val earlyMeetings = inTerm.filter { it.firstSection == 1 }.sumOf { activeWeeks(it) }
    val lateMeetings = inTerm.filter { it.firstSection >= 9 }.sumOf { activeWeeks(it) }
    val weekendMeetings = inTerm.filter { it.weekday >= 6 }.sumOf { activeWeeks(it) }
    val earlyThisWeek = inTerm.count {
        referenceWeek in it.firstWeek..it.lastWeek && it.firstSection == 1
    }
    val lateThisWeek = inTerm.count {
        referenceWeek in it.firstWeek..it.lastWeek && it.firstSection >= 9
    }
    val weekendThisWeek = inTerm.count {
        referenceWeek in it.firstWeek..it.lastWeek && it.weekday >= 6
    }
    val longest = inTerm.maxOfOrNull { it.sections } ?: 0
    val rooms = inTerm.mapNotNull { it.location.takeIf(String::isNotBlank) }.distinct().size
    val teachers = inTerm.mapNotNull { it.teacher.takeIf(String::isNotBlank) }.distinct().size
    val activeWeekdays = days.count { it.sessions > 0 }
    val freeWeekdays = if (inTerm.isEmpty()) 0 else days.count { it.weekday <= 5 && it.sessions == 0 }
    val averageSessions = if (totalWeeks > 0) {
        (totalSessions.toFloat() / totalWeeks).roundToInt()
    } else {
        0
    }

    val stats = listOf(
        ReportStat("学期课次", totalSessions.toString(), "次"),
        ReportStat("总课时", totalSections.toString(), "节"),
        ReportStat("周均课时", average.toString(), "节"),
        ReportStat("剩余课次", remainingMeetings.toString(), "次")
    )

    val rows = buildList {
        if (earlyMeetings > 0) {
            add(ReportRow("本周早八", "$earlyThisWeek 次"))
            add(ReportRow("本学期早八", "$earlyMeetings 次"))
        }
        if (lateMeetings > 0) {
            add(ReportRow("本周晚课", "$lateThisWeek 次"))
            add(ReportRow("本学期晚课", "$lateMeetings 次"))
        }
        if (weekendMeetings > 0) {
            add(ReportRow("本周周末课", "$weekendThisWeek 次"))
            add(ReportRow("本学期周末课", "$weekendMeetings 次"))
        }
        if (longest > 1) add(ReportRow("最长连堂", "$longest 节"))
        if (averageSessions > 0) add(ReportRow("周均课次", "$averageSessions 次"))
        if (activeWeekdays > 0) add(ReportRow("有课星期", "$activeWeekdays 天"))
        if (freeWeekdays > 0) add(ReportRow("空闲工作日", "$freeWeekdays 天"))
        if (rooms > 0) add(ReportRow("上课地点", "$rooms 处"))
        if (teachers > 0) add(ReportRow("授课教师", "$teachers 位"))
        if (examCount > 0) add(ReportRow("考试", "$examCount 场"))
        if (todoCount > 0) add(ReportRow("待办", "$todoCount 条"))
    }

    val peak = weeks.maxByOrNull { it.sessions }
    val note = when {
        slots.isEmpty() -> "还没有学期课程数据"
        totalSessions == 0 -> "本学期还没有排课"
        peak == null -> "本学期还没有排课"
        else -> "第 ${peak.week} 周最忙 · ${peak.sessions} 次课 · 周均 $average 课时"
    }

    val subtitle = listOfNotNull(
        termDisplay(termLabel).takeIf(String::isNotBlank),
        "共 $totalWeeks 周",
        "第 $referenceWeek 周",
        "已排 $totalSessions 次课"
    ).joinToString(" · ")

    return SemesterReport(
        title = "学期报告",
        subtitle = subtitle,
        stats = stats,
        weeks = weeks,
        weekdays = days,
        periods = periods,
        topCourses = topCourses,
        rows = rows,
        note = note,
        currentWeek = referenceWeek,
        totalWeeks = totalWeeks
    )
}

/** `2026-2027-1` → `2026-2027 学年第一学期`; anything else is left alone. */
fun termDisplay(label: String): String {
    val match = Regex("""(\d{4})-(\d{4})-?([12])""").find(label) ?: return label
    val term = if (match.groupValues[3] == "1") "第一学期" else "第二学期"
    return "${match.groupValues[1]}-${match.groupValues[2]} 学年$term"
}
