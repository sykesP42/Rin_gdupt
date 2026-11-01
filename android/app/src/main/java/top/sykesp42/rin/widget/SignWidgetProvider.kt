package top.sykesp42.rin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import top.sykesp42.rin.MainActivity
import top.sykesp42.rin.R
import top.sykesp42.rin.data.LocalCache
import top.sykesp42.rin.data.LocalJwxtApi
import top.sykesp42.rin.data.TermExtractor

/**
 * Home-screen widget: next class + countdown + automation status.
 * Reads the cached schedule (LocalCache) — works offline, zero network.
 * Tapping opens the app.
 */
class SignWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Allow the app to force a refresh after data changes.
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SignWidgetProvider::class.java))
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context))
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "top.sykesp42.rin.action.WIDGET_REFRESH"

        fun refresh(context: Context) {
            context.sendBroadcast(
                Intent(context, SignWidgetProvider::class.java).setAction(ACTION_REFRESH)
            )
        }
    }
}

private fun buildViews(context: Context): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_sign)
    val openApp = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_course_name, openApp)

    val settings = runCatching {
        kotlinx.coroutines.runBlocking {
            top.sykesp42.rin.data.SettingsRepository(context).current()
        }
    }.getOrNull()

    // Monitor status chip.
    views.setTextViewText(
        R.id.widget_monitor_status,
        when {
            settings?.monitorEnabled == true -> "提醒已开启"
            else -> "提醒关闭"
        }
    )

    // Cache info.
    val cache = LocalCache(context)
    val cached = cache.read(
        "schedule_week0",
        top.sykesp42.rin.data.ApiScheduleOverview.serializer()
    )
    if (cached == null) {
        views.setTextViewText(R.id.widget_course_name, "打开Rin加载课表")
        views.setTextViewText(R.id.widget_course_meta, "")
        views.setTextViewText(R.id.widget_countdown, "")
        views.setTextViewText(R.id.widget_footer, footerText(settings))
        return views
    }
    val overview = cached.second
    val todayString = LocalDate.now().toString()
    val todayEvents = overview.days
        .firstOrNull { it.date == todayString }
        ?.events.orEmpty()
        .sortedBy { it.startTime }

    val now = LocalTime.now()
    val nowMinutes = now.hour * 60 + now.minute

    if (todayEvents.isEmpty()) {
        views.setTextViewText(R.id.widget_course_name, "今天没有课")
        views.setTextViewText(R.id.widget_course_meta, "")
        views.setTextViewText(R.id.widget_countdown, "没有安排")
        views.setTextViewText(R.id.widget_footer, footerText(settings))
        return views
    }

    val current = todayEvents.lastOrNull { event ->
        val start = clockMinutes(event.startTime)
        val end = clockMinutes(event.endTime)
        start <= nowMinutes && nowMinutes <= end
    }
    val next = todayEvents.firstOrNull { clockMinutes(it.startTime) > nowMinutes }

    when {
        current != null -> {
            views.setTextViewText(R.id.widget_label, "正在上课")
            views.setTextViewText(R.id.widget_course_name, current.courseName)
            views.setTextViewText(
                R.id.widget_course_meta,
                listOf(current.location, "${current.startTime}–${current.endTime}")
                    .filter { it.isNotBlank() }.joinToString(" · ")
            )
            val remaining = clockMinutes(current.endTime) - nowMinutes
            views.setTextViewText(R.id.widget_countdown, "进行中 · 记得扫码签到")
        }
        next != null -> {
            views.setTextViewText(R.id.widget_label, "下一节课")
            views.setTextViewText(R.id.widget_course_name, next.courseName)
            views.setTextViewText(
                R.id.widget_course_meta,
                listOf(next.location, next.startTime)
                    .filter { it.isNotBlank() }.joinToString(" · ")
            )
            val minutesUntil = clockMinutes(next.startTime) - nowMinutes
            views.setTextViewText(R.id.widget_countdown, when {
                minutesUntil <= 15 -> formatCountdown(minutesUntil) + " · 记得签到"
                else -> formatCountdown(minutesUntil)
            })
        }
        else -> {
            // All past.
            val last = todayEvents.last()
            views.setTextViewText(R.id.widget_label, "今天课程结束")
            views.setTextViewText(R.id.widget_course_name, "共 ${todayEvents.size} 节课")
            views.setTextViewText(R.id.widget_course_meta, "")
            views.setTextViewText(R.id.widget_countdown, "明天见")
        }
    }
    views.setTextViewText(R.id.widget_footer, footerText(settings))
    return views
}

private fun formatCountdown(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60} 小时 ${minutes % 60} 分钟后"
    minutes > 0 -> "$minutes 分钟后"
    else -> "即将开始"
}

private fun footerText(settings: top.sykesp42.rin.data.AppSettings?): String {
    if (settings == null) return ""
    return buildList {
        if (settings.localMode) add("本地模式")
        else add("云端模式")
        if (settings.jwxtConnectedAt.isNotBlank()) add("教务已连接")
    }.joinToString(" · ")
}

private fun clockMinutes(clock: String): Int {
    val parts = clock.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}
