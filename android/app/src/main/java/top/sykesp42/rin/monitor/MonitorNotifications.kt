package top.sykesp42.rin.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import top.sykesp42.rin.MainActivity
import top.sykesp42.rin.handoff.HandoffActivity

class MonitorNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    // The status bar can only mask alpha, so it keeps the monochrome Rin glyph;
    // the shade's colour slot carries the expression that matches the state.
    private val smallIcon = top.sykesp42.rin.R.drawable.ic_stat_rin_v3

    private fun Notification.Builder.brand(slot: NoticeSlot): Notification.Builder {
        setSmallIcon(smallIcon)
        // The face is decoration on top of the text, so the user may turn it off
        // without losing the notification itself.
        if (NoticeFaces.prefs.value.inShade) {
            setLargeIcon(NoticeIcons.largeIcon(context, NoticeFaces.face(slot)))
        }
        return this
    }

    fun ensureChannels() {
        // Honor caches the first small icon per notification channel. The old
        // channels were created before the Rin mask existed, so recreate them
        // once instead of letting the stale green check survive every update.
        LEGACY_CHANNELS.forEach { runCatching { manager.deleteNotificationChannel(it) } }
        val monitor = NotificationChannel(CHANNEL_MONITOR, "后台监控", NotificationManager.IMPORTANCE_LOW).apply {
            description = "课堂窗口内的常驻通知"
            setShowBadge(false)
            setSound(null, null)
        }
        val ready = NotificationChannel(CHANNEL_READY, "课程提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "快上课时提醒你去哪间教室"
            enableVibration(true)
        }
        val errors = NotificationChannel(CHANNEL_ERRORS, "监控异常", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "登录失效，或后台监控被系统限制"
        }
        val session = NotificationChannel(CHANNEL_SESSION, "教务会话保活", NotificationManager.IMPORTANCE_MIN).apply {
            description = "本地模式下每 10 分钟轻量续期一次教务会话"
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannels(listOf(monitor, ready, errors, session))
    }

    fun buildSession(text: String): Notification {
        val builder = Notification.Builder(context, CHANNEL_SESSION)
            .brand(NoticeSlot.SESSION)
            .setContentTitle("Rin 保持教务会话")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(REQUEST_OPEN_SESSION))
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    fun updateSession(text: String) {
        manager.notify(NOTIFICATION_SESSION, buildSession(text))
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun buildMonitor(text: String, slot: NoticeSlot = NoticeSlot.MONITOR): Notification {
        val builder = Notification.Builder(context, CHANNEL_MONITOR)
            .brand(slot)
            .setContentTitle("Rin正在监控")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(REQUEST_OPEN_MONITOR))
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    fun updateMonitor(text: String, slot: NoticeSlot = NoticeSlot.MONITOR) {
        manager.notify(NOTIFICATION_MONITOR, buildMonitor(text, slot))
    }

    fun showReady(view: MonitorView, canSignPro: Boolean = true) {
        if (!canPost()) return
        val builder = Notification.Builder(context, CHANNEL_READY)
            .brand(NoticeSlot.READY)
            .setContentTitle(if (canSignPro) "可以签到了" else "可以签到了 · 查看名单")
            .setContentText(
                if (canSignPro) MonitorCopy.readyText(view)
                else "已进入签到窗口 · 打开 Rin 查看当前名单"
            )
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(REQUEST_OPEN_READY))
        if (canSignPro && view.roomCode.isNotBlank() && view.signId.isNotBlank()) {
            val handoff = Intent(context, HandoffActivity::class.java)
                .putExtra(HandoffActivity.EXTRA_ROOM_CODE, view.roomCode)
                .putExtra(HandoffActivity.EXTRA_SIGN_ID, view.signId)
                .putExtra(HandoffActivity.EXTRA_COURSE_NAME, view.courseName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context,
                REQUEST_OPEN_HANDOFF,
                handoff,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                top.sykesp42.rin.R.drawable.ic_stat_rin_v3,
                "在微信中打开",
                pending
            )
        }
        manager.notify(NOTIFICATION_READY, builder.build())
    }

    /** New grade(s) published: list up to three course/score lines. */
    fun showGradePublished(lines: List<String>) {
        if (!canPost()) return
        val text = lines.joinToString("；")
        val notification = Notification.Builder(context, CHANNEL_READY)
            .brand(NoticeSlot.GRADES)
            .setContentTitle("新成绩发布")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(REQUEST_OPEN_CONFIRMED))
            .build()
        manager.notify(NOTIFICATION_GRADES, notification)
    }

    /**
     * Schedule-driven class-start reminder. Unlike [showReady] it needs no
     * attendance-API data: the timetable alone proves a class is starting, so
     * this reminder still fires when the school refuses v1 reads.
     */
    fun showClassReminder(courseName: String, location: String) {
        if (!canPost()) return
        val text = listOf(location.takeIf { it.isNotBlank() }, "要上课了，带好东西去教室")
            .filterNotNull()
            .joinToString(" · ")
        val builder = Notification.Builder(context, CHANNEL_READY)
            .brand(NoticeSlot.CLASS_REMINDER)
            .setContentTitle("上课了 · $courseName")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(REQUEST_OPEN_CLASS_REMINDER))
        builder.addAction(
            top.sykesp42.rin.R.drawable.ic_stat_rin_v3,
            "查看详情",
            openApp(REQUEST_OPEN_CLASS_REMINDER)
        )
        manager.notify(NOTIFICATION_CLASS_REMINDER, builder.build())
    }

    fun showScheduleChange(text: String) {
        if (!canPost()) return
        val builder = Notification.Builder(context, CHANNEL_READY)
            .brand(NoticeSlot.SCHEDULE_CHANGE)
            .setContentTitle("课表有变动")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(REQUEST_OPEN_SCHEDULE_CHANGE))
        manager.notify(NOTIFICATION_SCHEDULE_CHANGE, builder.build())
    }

    fun showConfirmed() {
        if (!canPost()) return
        val notification = Notification.Builder(context, CHANNEL_READY)
            .brand(NoticeSlot.CONFIRMED)
            .setContentTitle("已确认签到")
            .setContentText("名单里已有你，不用再签")
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(REQUEST_OPEN_CONFIRMED))
            .build()
        manager.notify(NOTIFICATION_CONFIRMED, notification)
    }

    fun showUnconfirmed(reachable: Boolean = true) {
        if (!canPost()) return
        val copy = MonitorCopy.unconfirmedCopy(reachable)
        val notification = Notification.Builder(context, CHANNEL_ERRORS)
            .brand(if (reachable) NoticeSlot.UNCONFIRMED else NoticeSlot.UNREADABLE)
            .setContentTitle(copy.title)
            .setContentText(copy.text)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(REQUEST_OPEN_UNCONFIRMED))
            .build()
        manager.notify(NOTIFICATION_UNCONFIRMED, notification)
    }

    fun cancelReady() {
        manager.cancel(NOTIFICATION_READY)
    }

    fun showError(title: String, text: String) {
        if (!canPost()) return
        val notification = Notification.Builder(context, CHANNEL_ERRORS)
            .brand(NoticeSlot.ERROR)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(REQUEST_OPEN_ERROR))
            .build()
        manager.notify(NOTIFICATION_ERROR, notification)
    }

    fun cancelError() {
        manager.cancel(NOTIFICATION_ERROR)
    }

    private fun openApp(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_MONITOR = "monitor_v2"
        const val CHANNEL_READY = "sign_ready_v2"
        const val CHANNEL_ERRORS = "errors_v2"
        const val CHANNEL_SESSION = "session_v2"
        private val LEGACY_CHANNELS = listOf("monitor", "sign_ready", "errors", "session")
        const val NOTIFICATION_MONITOR = 1001
        const val NOTIFICATION_READY = 1002
        const val NOTIFICATION_ERROR = 1003
        const val NOTIFICATION_CONFIRMED = 1004
        const val NOTIFICATION_UNCONFIRMED = 1005
        const val NOTIFICATION_GRADES = 1006
        const val NOTIFICATION_CLASS_REMINDER = 1007
        const val NOTIFICATION_SCHEDULE_CHANGE = 1008
        const val NOTIFICATION_SESSION = 1009
        private const val REQUEST_OPEN_MONITOR = 20
        private const val REQUEST_OPEN_READY = 21
        private const val REQUEST_OPEN_ERROR = 22
        private const val REQUEST_OPEN_HANDOFF = 23
        private const val REQUEST_OPEN_CONFIRMED = 24
        private const val REQUEST_OPEN_UNCONFIRMED = 25
        private const val REQUEST_OPEN_CLASS_REMINDER = 26
        private const val REQUEST_OPEN_SCHEDULE_CHANGE = 27
        private const val REQUEST_OPEN_SESSION = 28
    }
}
