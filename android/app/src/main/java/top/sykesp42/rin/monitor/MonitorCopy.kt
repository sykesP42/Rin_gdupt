package top.sykesp42.rin.monitor

import top.sykesp42.rin.domain.State

/**
 * Notification copy is deliberately free of URLs, roster names and course
 * names: lock screens leak. The in-app screens show details.
 */
object MonitorCopy {
    fun monitorText(view: MonitorView): String = when (view.state) {
        State.IDLE -> when {
            view.courseName.isNotBlank() -> "本节课暂无签到"
            view.recommendationStatus == "day_done" -> "今天的课已结束"
            view.recommendationStatus == "no_course_today" -> "今天没有课"
            view.recommendationStatus == "unknown_room" -> "教室码还没记住 · 到点会提醒"
            view.recommendationStatus == "unavailable" -> "课表暂时拿不到 · 下拉刷新"
            else -> "到点会提醒你签到"
        }
        State.NOT_STARTED -> "快上课了，正在准备监控"
        State.WAITING_THRESHOLD -> {
            val label = if (view.threshold > view.signedCount) {
                "还差 ${view.threshold - view.signedCount} 人"
            } else {
                "等签到入口开放"
            }
            if (view.rosterCount > 0) "$label · 已签 ${view.signedCount}/${view.rosterCount}" else label
        }
        State.READY -> when {
            view.autoAllowed -> "人数到安全线了，可以签到"
            view.threshold == 0 -> "自动跳转关闭，手动签到"
            else -> "可以签到，打开 Rin 处理"
        }
        State.CONFIRMED -> "本人已签到"
        State.EXPIRED -> "本次签到已结束"
        State.BLOCKED_NOT_IN_ROSTER -> "本人不在本次名单"
        State.BLOCKED_AMBIGUOUS_NAME -> "名单有同名，需人工确认"
        State.BLOCKED -> errorText(view.errorCode)
    }

    fun readyText(view: MonitorView): String = when {
        view.threshold > 0 -> "已签 ${view.signedCount} 人，安全线 ${view.threshold} 人"
        else -> "现在可以签到"
    }

    /**
     * "未确认" is a claim about the roster. When the roster was never read,
     * say that instead of implying the student is absent from it.
     */
    data class UnconfirmedCopy(val title: String, val text: String)

    fun unconfirmedCopy(reachable: Boolean): UnconfirmedCopy =
        if (reachable) {
            UnconfirmedCopy("未确认签到", "名单里还没看到你；可以再试一次，或手动签到")
        } else {
            UnconfirmedCopy("签到结果未能确认", "名单读取失败；打开 Rin 刷新名单看看")
        }

    fun errorText(code: String): String = when (code) {
        "UNAUTHORIZED" -> "登录已失效，打开 Rin 重新登录"
        "JWXT_ACCOUNT_REQUIRED" -> "先在网页端连接教务系统"
        "RATE_LIMITED" -> "请求太频繁，稍后自动重试"
        "UPSTREAM_RESTRICTED" -> "教务系统没响应，稍后自动重试"
        "OFFLINE", "HTTP_ERROR" -> "网络不可用，稍后自动重试"
        else -> "考勤接口暂不可用 · 课表和提醒正常"
    }

    fun isAuthError(code: String): Boolean = code == "UNAUTHORIZED"
}
