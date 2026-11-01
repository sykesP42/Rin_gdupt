package top.sykesp42.rin.monitor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

enum class AlarmMode { EXACT, INEXACT }

interface AlarmSink {
    fun scheduleAt(epochMillis: Long, reason: String)
}

/**
 * Two independent alarms: a periodic watchdog while a run is active, and the
 * next class-window / daily-sync schedule. Both use setExactAndAllowWhileIdle
 * when the platform allows it (verified in the M0 spike) and degrade to
 * setAndAllowWhileIdle without USE_EXACT_ALARM.
 */
class MonitorAlarms(private val context: Context) : AlarmSink {
    private val manager = context.getSystemService(AlarmManager::class.java)

    @Volatile
    var lastMode: AlarmMode? = null
        private set

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    override fun scheduleAt(epochMillis: Long, reason: String) {
        schedule(epochMillis, reason, REQUEST_SCHEDULE, ACTION_SCHEDULE)
    }

    fun scheduleWake(epochMillis: Long) {
        schedule(epochMillis, REASON_WAKE, REQUEST_WAKE, ACTION_WAKE)
    }

    /**
     * Class-start reminder alarm. Independent of the monitor service: the
     * receiver posts the notification itself, so 后台监控 being off or killed
     * does not silence it. [slot] keeps the PendingIntents distinct.
     */
    fun scheduleReminder(
        slot: Int,
        epochMillis: Long,
        courseName: String,
        location: String,
        key: String
    ) {
        val at = maxOf(epochMillis, System.currentTimeMillis() + MIN_DELAY_MILLIS)
        val intent = Intent(context, MonitorAlarmReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_REMIND_COURSE, courseName)
            putExtra(EXTRA_REMIND_LOCATION, location)
            putExtra(EXTRA_REMIND_KEY, key)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_REMIND_BASE + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAt(at, pending)
    }

    /** Daily self-healing re-arm (see [nextDailyRearm]); one alarm, replaced each run. */
    fun scheduleDailyRearm(epochMillis: Long) {
        val at = maxOf(epochMillis, System.currentTimeMillis() + MIN_DELAY_MILLIS)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_REARM,
            Intent(context, MonitorAlarmReceiver::class.java).setAction(ACTION_REARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAt(at, pending)
    }

    fun cancelReminders(max: Int = REMIND_SLOTS) {
        for (slot in 0 until max) {
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_REMIND_BASE + slot,
                Intent(context, MonitorAlarmReceiver::class.java).setAction(ACTION_REMIND),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.cancel(pending)
            pending.cancel()
        }
    }

    fun cancelAll() {
        cancel(REQUEST_SCHEDULE, ACTION_SCHEDULE)
        cancel(REQUEST_WAKE, ACTION_WAKE)
        cancelReminders()
    }

    fun cancelWake() {
        cancel(REQUEST_WAKE, ACTION_WAKE)
    }

    private fun schedule(epochMillis: Long, reason: String, requestCode: Int, action: String) {
        val at = maxOf(epochMillis, System.currentTimeMillis() + MIN_DELAY_MILLIS)
        val pending = pendingIntent(requestCode, action, reason)
        setAt(at, pending)
    }

    /** Exact when allowed, gracefully inexact otherwise; never throws. */
    private fun setAt(at: Long, pending: PendingIntent) {
        val exact = canScheduleExact()
        val mode = try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                AlarmMode.EXACT
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                AlarmMode.INEXACT
            }
        } catch (error: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            AlarmMode.INEXACT
        } catch (error: RuntimeException) {
            Log.w(TAG, "alarm schedule failed: ${error.javaClass.simpleName}")
            return
        }
        lastMode = mode
        Log.i(TAG, "alarm mode=$mode at=$at")
    }

    private fun cancel(requestCode: Int, action: String) {
        val pending = pendingIntent(requestCode, action, null)
        manager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(requestCode: Int, action: String, reason: String?): PendingIntent {
        val intent = Intent(context, MonitorAlarmReceiver::class.java).apply {
            this.action = action
            if (reason != null) putExtra(EXTRA_REASON, reason)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "RinAlarm"
        private const val REQUEST_SCHEDULE = 4101
        private const val REQUEST_WAKE = 4102
        private const val REQUEST_REMIND_BASE = 4200
        private const val REQUEST_REARM = 4301
        private const val REMIND_SLOTS = 48
        private const val MIN_DELAY_MILLIS = 10_000L
    }
}

const val ACTION_WAKE = "top.sykesp42.rin.action.MONITOR_WAKE"
const val ACTION_SCHEDULE = "top.sykesp42.rin.action.MONITOR_SCHEDULE"
const val ACTION_REMIND = "top.sykesp42.rin.action.CLASS_REMIND"
const val ACTION_REARM = "top.sykesp42.rin.action.REMIND_REARM"
const val EXTRA_REASON = "reason"
const val EXTRA_REMIND_COURSE = "remind_course"
const val EXTRA_REMIND_LOCATION = "remind_location"
const val EXTRA_REMIND_KEY = "remind_key"

const val REASON_MANUAL = "manual"
const val REASON_WINDOW = "window"
const val REASON_DAILY = "daily"
const val REASON_WAKE = "wake"
const val REASON_CONFIRM = "confirm"
