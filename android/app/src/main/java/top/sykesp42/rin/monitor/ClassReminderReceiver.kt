package top.sykesp42.rin.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.monitor.nextDailyRearm
import java.time.Instant

/**
 * Alarm-only class-reminder receiver, split out of the full client's monitor
 * service. Handles exactly two actions — REMIND (one scheduled course
 * reminder) and REARM (the morning self-heal loop) — and knows nothing about
 * the sign-in monitor.
 */
class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_REMIND) {
            showClassReminder(context, intent)
            return
        }
        if (intent?.action == ACTION_REARM) {
            rearmReminders(context)
            return
        }
    }

    /**
     * Alarm-only reminder path: no foreground service, no network poll. The
     * dedupe key was persisted at schedule time, so a late (inexact) alarm
     * still cannot double-notify a class already reminded.
     */
    private fun showClassReminder(context: Context, intent: Intent) {
        val course = intent.getStringExtra(EXTRA_REMIND_COURSE).orEmpty()
        val location = intent.getStringExtra(EXTRA_REMIND_LOCATION).orEmpty()
        val key = intent.getStringExtra(EXTRA_REMIND_KEY).orEmpty()
        if (course.isBlank() || key.isBlank()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = AppContainer.get(context)
                val settings = container.settings.current()
                if (settings.lastRemindedClassKey == key) return@launch
                container.notifications.ensureChannels()
                container.notifications.showClassReminder(course, location)
                container.settings.setLastRemindedClassKey(key)
                runCatching {
                    container.history.append(HistoryKind.CLASS_REMINDER, course, "", "alarm")
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Self-heal loop: every morning re-arm today's reminders from the cached
     * week; when the cache is stale, one read-only JWXT refresh pulls the
     * current week so the chain survives week boundaries.
     */
    private fun rearmReminders(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = AppContainer.get(context)
                val todayStr = java.time.LocalDate.now(ClassWindowPlanner.schoolZone).toString()
                var overview = runCatching {
                    container.localCache.read(
                        "schedule_week0",
                        ApiScheduleOverview.serializer()
                    )?.second
                }.getOrNull()
                val coversToday = overview?.let { cached ->
                    cached.date == todayStr || cached.days.any { it.date == todayStr }
                } == true
                val settings = runCatching { container.settings.current() }.getOrNull()
                if (!coversToday && settings != null) {
                    val fresh = runCatching {
                        container.withJwxt(settings) { api -> api.overview() }
                    }.getOrNull()
                    if (fresh != null && fresh.days.isNotEmpty()) {
                        runCatching {
                            container.localCache.write(
                                "schedule_week0",
                                ApiScheduleOverview.serializer(),
                                fresh
                            )
                        }
                        overview = fresh
                    }
                }
                if (overview != null) {
                    runCatching { container.classReminders.reschedule(overview) }
                }
                container.alarms.scheduleDailyRearm(nextDailyRearm(Instant.now()).toEpochMilli())
            } finally {
                pending.finish()
            }
        }
    }
}
