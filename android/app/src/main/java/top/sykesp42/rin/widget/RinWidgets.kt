package top.sykesp42.rin.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.LocalCache

object WidgetShortcuts {
    const val EXTRA_TAB = "rin.tab"
    const val TAB_SCHEDULE = "schedule"
    const val TAB_SEARCH = "search"
}

/** The class a widget should present right now: in-progress or next. */
data class NextClassInfo(
    val label: String,
    val courseName: String,
    val meta: String,
    val countdownText: String,
    val startEpochMillis: Long?
)

object RinWidgets {

    private val schoolZone: ZoneId = ZoneId.of("Asia/Shanghai")

    fun readNextClass(context: Context): NextClassInfo? {
        val cached = runCatching {
            LocalCache(context).read(
                "schedule_week0",
                top.sykesp42.rin.data.ApiScheduleOverview.serializer()
            )?.second
        }.getOrNull() ?: return null
        val todayString = LocalDate.now(schoolZone).toString()
        val todayEvents = cached.days
            .firstOrNull { it.date == todayString }
            ?.events.orEmpty()
            .sortedBy { it.startTime }
        if (todayEvents.isEmpty()) return null
        val now = LocalDateTime.now(schoolZone)
        val nowMinutes = now.hour * 60 + now.minute
        val current = todayEvents.lastOrNull { event ->
            val start = clockMinutes(event.startTime)
            val end = clockMinutes(event.endTime)
            start <= nowMinutes && nowMinutes <= end
        }
        val next = todayEvents.firstOrNull { clockMinutes(it.startTime) > nowMinutes }
        return when {
            current != null -> NextClassInfo(
                label = "正在上课",
                courseName = current.courseName,
                meta = listOf(current.location, "${current.startTime}–${current.endTime}")
                    .filter { it.isNotBlank() }.joinToString(" · "),
                countdownText = "进行中 · 记得扫码签到",
                startEpochMillis = null
            )
            next != null -> {
                val minutesUntil = clockMinutes(next.startTime) - nowMinutes
                NextClassInfo(
                    label = "下一节课",
                    courseName = next.courseName,
                    meta = listOf(next.location, next.startTime)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    countdownText = when {
                        minutesUntil <= 15 -> formatCountdown(minutesUntil) + " · 记得签到"
                        else -> formatCountdown(minutesUntil)
                    },
                    startEpochMillis = startEpoch(now, next)
                )
            }
            else -> NextClassInfo(
                label = "今天课程结束",
                courseName = "共 ${todayEvents.size} 节课",
                meta = "",
                countdownText = "明天见",
                startEpochMillis = null
            )
        }
    }

    fun clockMinutes(clock: String): Int {
        val parts = clock.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    fun formatCountdown(minutes: Int): String = when {
        minutes >= 60 -> "${minutes / 60} 小时 ${minutes % 60} 分钟后"
        minutes > 0 -> "$minutes 分钟后"
        else -> "即将开始"
    }

    private fun startEpoch(now: LocalDateTime, event: ApiScheduleEvent): Long? {
        val parts = event.startTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val start = now.toLocalDate().atTime(hour, minute).atZone(schoolZone)
        return start.toInstant().toEpochMilli()
    }

    /** Push new data into every widget surface. */
    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val compact = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, WidgetCompactProvider::class.java))
        WidgetCompactProvider().also { it.onUpdate(context, manager, compact) }
        val bar = manager.getAppWidgetIds(ComponentName(context, WidgetBarProvider::class.java))
        WidgetBarProvider().also { it.onUpdate(context, manager, bar) }
        val sign = manager.getAppWidgetIds(ComponentName(context, SignWidgetProvider::class.java))
        SignWidgetProvider().also { it.onUpdate(context, manager, sign) }
    }
}
