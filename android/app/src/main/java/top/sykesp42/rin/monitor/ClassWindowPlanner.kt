package top.sykesp42.rin.monitor

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import top.sykesp42.rin.data.ApiScheduleEvent

data class MonitorWindow(val start: Instant, val end: Instant) {
    fun contains(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)
}

data class WindowPlan(val current: MonitorWindow?, val next: MonitorWindow?)

/**
 * Turns today's timetable into foreground-service windows. The service starts
 * before class and stops after the confirmation grace period, so it is not a
 * permanent background resident (design section 12/13).
 */
object ClassWindowPlanner {
    val lead = 10.minutes
    val trail = 5.minutes

    /**
     * Class times, sign windows and alarms are all school-local time. Pinning
     * the zone stops a phone with a wrong (or manually changed) timezone from
     * shifting every window by hours.
     */
    val schoolZone: ZoneId = ZoneId.of("Asia/Shanghai")

    fun plan(
        blocks: List<ApiScheduleEvent>,
        now: Instant,
        zone: ZoneId = schoolZone
    ): WindowPlan {
        val date = now.atZone(zone).toLocalDate()
        val windows = blocks.mapNotNull { block ->
            val start = parseTime(block.startTime) ?: return@mapNotNull null
            val end = parseTime(block.endTime) ?: return@mapNotNull null
            if (!end.isAfter(start)) return@mapNotNull null
            MonitorWindow(
                start = date.atTime(start).atZone(zone).toInstant().minusMillis(lead.inWholeMilliseconds),
                end = date.atTime(end).atZone(zone).toInstant().plusMillis(trail.inWholeMilliseconds)
            )
        }.sortedBy { it.start }

        val merged = mutableListOf<MonitorWindow>()
        for (window in windows) {
            val last = merged.lastOrNull()
            if (last != null && !window.start.isAfter(last.end)) {
                if (window.end.isAfter(last.end)) {
                    merged[merged.lastIndex] = last.copy(end = window.end)
                }
            } else {
                merged += window
            }
        }

        return WindowPlan(
            current = merged.firstOrNull { it.contains(now) },
            next = merged.firstOrNull { it.start.isAfter(now) }
        )
    }

    private fun parseTime(text: String): LocalTime? {
        val parts = text.trim().split(':')
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }
}
