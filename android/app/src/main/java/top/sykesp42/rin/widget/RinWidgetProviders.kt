package top.sykesp42.rin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import top.sykesp42.rin.MainActivity
import top.sykesp42.rin.R

/**
 * 2×2 mini widget: next class, location and a live countdown chronometer
 * (API 31+ counts down on its own with zero updates in between; older
 * versions fall back to the static "N 分钟后" text).
 */
class WidgetCompactProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_compact)
            val openApp = PendingIntent.getActivity(
                context, 30,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_compact_root, openApp)
            val info = RinWidgets.readNextClass(context)
            if (info == null) {
                views.setTextViewText(R.id.widget_compact_label, "Rin")
                views.setTextViewText(R.id.widget_compact_course, "打开Rin加载课表")
                views.setTextViewText(R.id.widget_compact_meta, "")
                views.setViewVisibility(R.id.widget_compact_chrono, android.view.View.GONE)
                views.setTextViewText(R.id.widget_compact_countdown, "")
                return views
            }
            views.setTextViewText(R.id.widget_compact_label, info.label)
            views.setTextViewText(R.id.widget_compact_course, info.courseName)
            views.setTextViewText(R.id.widget_compact_meta, info.meta)
            val start = info.startEpochMillis
            if (start != null && Build.VERSION.SDK_INT >= 31) {
                // Countdown chronometer: ticks on its own until the class starts.
                views.setViewVisibility(R.id.widget_compact_chrono, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_compact_countdown, android.view.View.GONE)
                views.setChronometer(
                    R.id.widget_compact_chrono,
                    SystemClock.elapsedRealtime() + (start - System.currentTimeMillis()),
                    "还有 %s",
                    true
                )
                if (Build.VERSION.SDK_INT >= 31) {
                    views.setChronometerCountDown(R.id.widget_compact_chrono, true)
                }
            } else {
                views.setViewVisibility(R.id.widget_compact_chrono, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_compact_countdown, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_compact_countdown, info.countdownText)
            }
            return views
        }
    }
}

/** 4×1 single-line widget: next class at a glance. */
class WidgetBarProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bar)
            val openApp = PendingIntent.getActivity(
                context, 31,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_bar_root, openApp)
            val info = RinWidgets.readNextClass(context)
            if (info == null) {
                views.setTextViewText(R.id.widget_bar_text, "打开Rin加载课表")
                return views
            }
            views.setTextViewText(
                R.id.widget_bar_text,
                listOfNotNull(
                    info.label,
                    info.courseName,
                    info.meta.takeIf { it.isNotBlank() },
                    info.countdownText.takeIf { info.label == "下一节课" }
                ).joinToString(" · ")
            )
            return views
        }
    }
}
