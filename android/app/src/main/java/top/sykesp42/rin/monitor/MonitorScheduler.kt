package top.sykesp42.rin.monitor

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

sealed class ScheduledAlarm {
    data class NextWindow(val at: Instant) : ScheduledAlarm()
    data class DailySync(val at: Instant) : ScheduledAlarm()
    data class Retry(val at: Instant) : ScheduledAlarm()
}

/**
 * Pure schedule arithmetic over an AlarmSink so tests can assert when the next
 * service start lands without touching AlarmManager.
 */
class MonitorScheduler(
    private val sink: AlarmSink,
    // School clock: the 07:30 daily sync must land on the same wall clock as
    // the class windows even when the device timezone is wrong.
    private val zone: ZoneId = ClassWindowPlanner.schoolZone
) {
    fun apply(plan: WindowPlan, now: Instant): ScheduledAlarm {
        val next = plan.next
        if (next != null && next.start.isAfter(now)) {
            sink.scheduleAt(next.start.toEpochMilli(), REASON_WINDOW)
            return ScheduledAlarm.NextWindow(next.start)
        }
        return scheduleDailySync(now)
    }

    fun scheduleDailySync(now: Instant): ScheduledAlarm {
        val today = now.atZone(zone).toLocalDate().atTime(DAILY_SYNC).atZone(zone)
        val target = if (today.toInstant().isAfter(now)) today else today.plusDays(1)
        sink.scheduleAt(target.toInstant().toEpochMilli(), REASON_DAILY)
        return ScheduledAlarm.DailySync(target.toInstant())
    }

    fun scheduleRetry(now: Instant, delayMillis: Long): ScheduledAlarm {
        val at = now.plusMillis(delayMillis)
        sink.scheduleAt(at.toEpochMilli(), REASON_WINDOW)
        return ScheduledAlarm.Retry(at)
    }

    companion object {
        val DAILY_SYNC: LocalTime = LocalTime.of(7, 30)
    }
}
