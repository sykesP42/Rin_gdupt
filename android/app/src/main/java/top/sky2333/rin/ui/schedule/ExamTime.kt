package top.sky2333.rin.ui.schedule

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import top.sky2333.rin.data.ManualExam

/**
 * Parsed exam moment. Unknown pieces stay null/blank so the UI can show the raw
 * text instead of inventing a date or a countdown.
 */
data class ExamTime(
    val date: LocalDate? = null,
    val start: String = "",
    val end: String = ""
)

data class ExamItem(
    val exam: ManualExam,
    val time: ExamTime,
    val countdown: String
)

private val DATE = Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?""")
private val COMPACT_DATE = Regex("""(?<!\d)(\d{4})(\d{2})(\d{2})(?!\d)""")
private val CLOCK = Regex("""(?<!\d)([01]?\d|2[0-3]):([0-5]\d)(?!\d)""")
private val COMPACT_CLOCK = Regex("""(?<!\d)([01]\d|2[0-3])([0-5]\d)(?!\d)""")

fun parseExamTime(raw: String): ExamTime {
    val text = raw.trim()
    if (text.isEmpty()) return ExamTime()
    val normalized = text
        .replace('：', ':')
        .replace('～', '-')
        .replace('~', '-')

    var date: LocalDate? = null
    var rest = normalized
    DATE.find(normalized)?.let { match ->
        date = localDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        if (date != null) rest = normalized.removeRange(match.range)
    }
    if (date == null) {
        COMPACT_DATE.find(normalized)?.let { match ->
            date = localDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
            if (date != null) rest = normalized.removeRange(match.range)
        }
    }

    val clocks = CLOCK.findAll(rest)
        .map { clock(it.groupValues[1], it.groupValues[2]) }
        .toList()
        .ifEmpty {
            COMPACT_CLOCK.findAll(rest)
                .map { clock(it.groupValues[1], it.groupValues[2]) }
                .toList()
        }
    return ExamTime(
        date = date,
        start = clocks.getOrElse(0) { "" },
        end = clocks.getOrElse(1) { "" }
    )
}

private fun localDate(year: String, month: String, day: String): LocalDate? =
    runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()

private fun clock(hour: String, minute: String): String =
    "%02d:%02d".format(hour.toInt(), minute.toInt())

fun examCountdownLabel(time: ExamTime, today: LocalDate): String {
    val date = time.date ?: return "时间待确认"
    val days = ChronoUnit.DAYS.between(today, date)
    val start = time.start
    return when {
        days < 0 -> "已结束"
        days == 0L -> if (start.isBlank()) "今天" else "今天 $start"
        days == 1L -> if (start.isBlank()) "明天" else "明天 $start"
        else -> "$days 天后"
    }
}

/**
 * Future/today exams first, then undated ones, then past exams with the most
 * recent first. The next exam is the first dated item in this order.
 */
fun examItems(exams: List<ManualExam>, today: LocalDate): List<ExamItem> {
    val items = exams.map { exam ->
        val time = parseExamTime(exam.time)
        ExamItem(exam, time, examCountdownLabel(time, today))
    }
    return items.sortedWith(
        compareBy<ExamItem> { bucket(it.time.date, today) }
            .thenComparator { a, b ->
                val left = a.time.date
                val right = b.time.date
                when {
                    left == null || right == null -> 0
                    bucket(left, today) == 2 -> right.compareTo(left)
                    else -> left.compareTo(right)
                }
            }
            .thenBy { it.time.start.ifBlank { "99:99" } }
    )
}

fun nextExamItem(items: List<ExamItem>, today: LocalDate): ExamItem? =
    items.firstOrNull { item ->
        item.time.date?.let { !it.isBefore(today) } == true
    }

private fun bucket(date: LocalDate?, today: LocalDate): Int = when {
    date == null -> 1
    date.isBefore(today) -> 2
    else -> 0
}
