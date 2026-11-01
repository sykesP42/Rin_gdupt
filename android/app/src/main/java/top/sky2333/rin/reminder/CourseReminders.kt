package top.sky2333.rin.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.provider.CalendarContract
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import top.sky2333.rin.MainActivity
import top.sky2333.rin.data.AppContainer
import top.sky2333.rin.data.AppSettings
import top.sky2333.rin.data.CourseReminder
import top.sky2333.rin.ui.faces.NoticeFaces
import top.sky2333.rin.ui.faces.NoticeIcons
import top.sky2333.rin.ui.faces.NoticeSlot

/** Arms one exact one-off alarm; no service and no network are required. */
object CourseReminderScheduler {
    const val ACTION_FIRE = "top.sky2333.rin.action.COURSE_REMINDER"

    fun schedule(context: Context, reminder: CourseReminder): Boolean {
        val trigger = reminder.triggerAt() ?: return false
        if (!trigger.isAfter(LocalDateTime.now())) return false
        val at = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = fireIntent(context, reminder)
        runCatching {
            val exact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
            if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
        return true
    }

    fun cancel(context: Context, reminder: CourseReminder) {
        runCatching {
            context.getSystemService(AlarmManager::class.java)
                .cancel(fireIntent(context, reminder))
        }
    }

    fun rearmAll(context: Context, reminders: List<CourseReminder>) {
        reminders.forEach { reminder ->
            if (reminder.triggerAt()?.isAfter(LocalDateTime.now()) == true) {
                schedule(context, reminder)
            }
        }
    }

    private fun fireIntent(context: Context, reminder: CourseReminder): PendingIntent {
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, reminder.id)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val EXTRA_ID = "course_reminder_id"
}

/** Opens the system calendar with a prefilled event; no calendar permission is needed. */
object CourseReminderCalendar {
    fun insert(context: Context, reminder: CourseReminder): Boolean = runCatching {
        val begin = reminder.classAt() ?: return@runCatching false
        val end = reminder.endAt() ?: begin.plusMinutes(95)
        val zone = ZoneId.systemDefault()
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.atZone(zone).toInstant().toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.atZone(zone).toInstant().toEpochMilli())
            .putExtra(CalendarContract.Events.TITLE, "${reminder.courseName} 上课")
            .putExtra(CalendarContract.Events.EVENT_LOCATION, reminder.location)
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Rin 课程提醒 · 提前 ${reminder.leadMinutes} 分钟"
            )
            .putExtra(CalendarContract.Events.HAS_ALARM, 1)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

/** Posts the notification and, for alarms, makes sure it can actually be heard. */
class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != CourseReminderScheduler.ACTION_FIRE) return
        val id = intent.getStringExtra(CourseReminderScheduler.EXTRA_ID) ?: return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val container = AppContainer.get(context)
                val reminder = container.courseReminders.payload.value.reminders.firstOrNull { it.id == id }
                if (reminder != null) {
                    val settings = container.settings.settings.first()
                    if (reminder.alarm) prepareAlarmAudio(context, reminder, settings)
                    CourseReminderNotifier.show(context, reminder, settings)
                    container.courseReminders.remove(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun prepareAlarmAudio(context: Context, reminder: CourseReminder, settings: AppSettings) {
        val audio = context.getSystemService(AudioManager::class.java)
        val prefs = context.getSharedPreferences(AUDIO_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("saved", false)) {
            prefs.edit()
                .putBoolean("saved", true)
                .putInt("ringer", audio.ringerMode)
                .putInt("volume", audio.getStreamVolume(AudioManager.STREAM_ALARM))
                .apply()
        }
        val volume = if (reminder.volumeEnabled) reminder.volume else settings.reminderVolume
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, max * volume / 100, 0)
        }
        if (reminder.forceUnmute) {
            val policy = context.getSystemService(NotificationManager::class.java)
            if (policy.isNotificationPolicyAccessGranted) {
                runCatching { audio.ringerMode = AudioManager.RINGER_MODE_NORMAL }
            }
        }
        scheduleRestore(context)
    }
}

/** Puts the ringer and alarm volume back after the reminder has had time to ring. */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(AUDIO_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("saved", false)) return
        val audio = context.getSystemService(AudioManager::class.java)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                prefs.getInt("volume", audio.getStreamVolume(AudioManager.STREAM_ALARM)),
                0
            )
            audio.ringerMode = prefs.getInt("ringer", audio.ringerMode)
        }
        prefs.edit().clear().apply()
    }
}

/** Re-arms future reminders after a reboot or app update. */
class CourseReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = AppContainer.get(context)
                container.courseReminders.prune()
                CourseReminderScheduler.rearmAll(
                    context,
                    container.courseReminders.payload.value.reminders
                )
            } finally {
                pending.finish()
            }
        }
    }
}

private const val AUDIO_PREFS = "course_reminder_audio"
private const val ACTION_RESTORE = "top.sky2333.rin.action.RESTORE_RINGER"

private fun scheduleRestore(context: Context) {
    val at = System.currentTimeMillis() + 90_000L
    val intent = Intent(context, ReminderRestoreReceiver::class.java).setAction(ACTION_RESTORE)
    val pending = PendingIntent.getBroadcast(
        context,
        9101,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    runCatching {
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
}

object CourseReminderNotifier {
    fun show(context: Context, reminder: CourseReminder, settings: AppSettings) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val vibrate = settings.reminderVibrate ||
            (reminder.forceUnmute && reminder.vibrate)
        val channel = ensureChannel(context, settings, vibrate)
        val text = buildList {
            reminder.startTime.takeIf { it.isNotBlank() }?.let(::add)
            reminder.location.takeIf { it.isNotBlank() }?.let(::add)
            add("提前 ${reminder.leadMinutes} 分钟")
            if (reminder.signReminder) add("记得留意签到")
        }.joinToString(" · ")
        val open = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(top.sky2333.rin.R.drawable.ic_stat_rin_v3)
            .setContentTitle(
                (if (reminder.signReminder) "签到提醒" else "课程提醒") + " · ${reminder.courseName}"
            )
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
        if (settings.noticeInShade) {
            runCatching {
                builder.setLargeIcon(
                    NoticeIcons.largeIcon(context, NoticeFaces.face(NoticeSlot.REMINDER))
                )
            }
        }
        manager.notify(reminder.id.hashCode(), builder.build())
    }

    private fun ensureChannel(context: Context, settings: AppSettings, vibrate: Boolean): String {
        val soundKey = settings.reminderSound
        val mode = settings.reminderVibrationMode
        val headsUp = settings.reminderHeadsUp
        val importance = if (headsUp) {
            NotificationManager.IMPORTANCE_HIGH
        } else {
            NotificationManager.IMPORTANCE_DEFAULT
        }
        val id = "course_reminder_${soundKey}_${vibrate}_${mode}_$headsUp"
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(id, "课程提醒", importance).apply {
            description = "重要课程的一次性提醒"
            setShowBadge(true)
            val sound = when (soundKey) {
                "silent" -> null
                "alarm" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            if (sound != null) {
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                setSound(null, null)
            }
            enableVibration(vibrate)
            if (vibrate) {
                vibrationPattern = when (mode) {
                    "short" -> longArrayOf(0, 120)
                    "long" -> longArrayOf(0, 650, 250, 650)
                    "pulse" -> longArrayOf(0, 200, 100, 200, 100, 200)
                    else -> longArrayOf(0, 320, 180, 320)
                }
            }
        }
        runCatching { manager.createNotificationChannel(channel) }
        return id
    }
}
