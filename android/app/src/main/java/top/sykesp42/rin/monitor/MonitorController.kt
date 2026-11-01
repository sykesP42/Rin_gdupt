package top.sykesp42.rin.monitor

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.sykesp42.rin.data.AppContainer

/** Shared enable/disable/start actions for the UI and later the settings screen. */
class MonitorController(private val container: AppContainer) {

    /** Same gate as [shouldResumeMonitor]: local mode needs no cloud login. */
    private suspend fun readyToMonitor(settings: top.sykesp42.rin.data.AppSettings): Boolean =
        if (settings.localMode) {
            container.localJwxtStore.load() != null || settings.manualCode.isNotBlank()
        } else {
            settings.serverUrl.isNotBlank() && container.sessionStore.load() != null
        }

    suspend fun enable(): String {
        val settings = container.settings.current()
        if (!readyToMonitor(settings)) {
            return if (settings.localMode) "本地模式要先连接教务或填教室码" else "登录后再开启"
        }
        container.settings.setMonitorEnabled(true)
        return try {
            val now = Instant.now()
            // Mode-aware: local mode plans from the phone's own JWXT pipeline
            // (with cloud failover), not from the cloud server.
            val today = container.monitorApi(settings)?.schedule()?.today.orEmpty()
            val plan = ClassWindowPlanner.plan(today, now)
            if (plan.current != null) {
                MonitorService.start(container.appContext, REASON_WINDOW)
                return "已开启，正在盯当前课堂"
            }
            when (val scheduled = container.scheduler.apply(plan, now)) {
                is ScheduledAlarm.NextWindow -> "已开启，下次课堂 ${format(scheduled.at)}"
                is ScheduledAlarm.DailySync -> "已开启，今天没有更多课；明天 07:30 再查"
                is ScheduledAlarm.Retry -> "已开启"
            }
        } catch (error: Exception) {
            container.scheduler.scheduleDailySync(Instant.now())
            "已开启；拿不到课表，改为每天检查"
        }
    }

    suspend fun disable() {
        container.settings.setMonitorEnabled(false)
        container.alarms.cancelAll()
        MonitorService.stop(container.appContext)
    }

    suspend fun startNow(): String {
        val settings = container.settings.current()
        if (!readyToMonitor(settings)) {
            return if (settings.localMode) "本地模式要先连接教务或填教室码" else "先登录"
        }
        container.settings.setMonitorEnabled(true)
        MonitorService.start(container.appContext, REASON_MANUAL)
        return "已开 30 分钟测试监控"
    }

    fun logout() {
        container.alarms.cancelAll()
        MonitorService.stop(container.appContext)
    }

    private fun format(at: Instant): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(at)
}
