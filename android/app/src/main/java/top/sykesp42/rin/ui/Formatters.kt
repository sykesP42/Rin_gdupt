package top.sykesp42.rin.ui

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.sykesp42.rin.ui.schedule.campusZone

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val historyFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun formatCountdown(until: Instant?, now: Instant): String {
    if (until == null) return "—"
    val remaining = Duration.between(now, until)
    if (remaining.isNegative || remaining.isZero) return "00:00"
    val totalSeconds = remaining.seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.CHINA, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
    }
}

/** Campus times are independent of the phone's own timezone. */
fun formatClock(instant: Instant, zone: ZoneId = campusZone): String =
    clockFormat.withZone(zone).format(instant)

fun formatDateLine(date: LocalDate): String {
    val weekday = weekdays[date.dayOfWeek.value - 1]
    return "${date.monthValue}月${date.dayOfMonth}日 · $weekday"
}

fun extractRoomCode(location: String): String? =
    Regex("(?<!\\d)(\\d{4})(?!\\d)").find(location)?.groupValues?.getOrNull(1)

fun greeting(hour: Int): String = when {
    hour < 6 -> "夜深了"
    hour < 11 -> "早安"
    hour < 14 -> "午间"
    hour < 18 -> "午后"
    else -> "晚上好"
}
