package top.sykesp42.rin.monitor

import java.time.Instant
import java.time.LocalDate
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.data.SettingsRepository

/**
 * Re-arms class-start reminders from the loaded week. Idempotent: every call
 * cancels the old slots first, so opening the app twice cannot stack
 * notifications. Reminders survive 后台服务 being off; they are pure alarms.
 *
 * Only an overview that actually covers today may arm alarms — a cached week
 * from last week still lists classes, and arming those would remind the wrong
 * days.
 */
class ClassReminderAlarms(
    private val alarms: MonitorAlarms,
    private val settings: SettingsRepository
) {
    suspend fun reschedule(overview: ApiScheduleOverview, now: Instant = Instant.now()) {
        val today = now.atZone(ClassWindowPlanner.schoolZone).toLocalDate()
        if (!overview.covers(today)) return
        alarms.cancelReminders()
        // Keep the morning self-heal chain alive even on days with no classes.
        alarms.scheduleDailyRearm(nextDailyRearm(now).toEpochMilli())
        val planned = scheduleClassReminders(overview, now)
        if (planned.isEmpty()) return
        // A reminder already fired for a class must not be re-armed.
        val lastKey = settings.current().lastRemindedClassKey
        planned
            .filterNot { it.key == lastKey }
            .forEachIndexed { index, reminder ->
                alarms.scheduleReminder(
                    slot = index,
                    epochMillis = reminder.at.toEpochMilli(),
                    courseName = reminder.courseName,
                    location = reminder.location,
                    key = reminder.key
                )
            }
    }

    private fun ApiScheduleOverview.covers(day: LocalDate): Boolean {
        val overviewDate = runCatching { LocalDate.parse(date) }.getOrNull()
        if (overviewDate == day) return true
        return days.any { runCatching { LocalDate.parse(it.date) }.getOrNull() == day }
    }
}
