package top.sykesp42.rin.monitor

import top.sykesp42.rin.domain.State

enum class OneTapBlocker {
    SESSION_EXPIRED,
    NO_VIEW,
    MISSING_NAME,
    NO_ACTIVITY,
    NOT_STARTED,
    WAITING_THRESHOLD,
    NOT_IN_ROSTER,
    AMBIGUOUS_NAME,
    ENDED,
    CONFIRMED,
    BLOCKED,
    ACCESS_DENIED,
    WECHAT_MISSING
}

data class OneTapReadiness(
    val ready: Boolean,
    val blocker: OneTapBlocker?,
    val message: String
)

enum class OneTapAction { HANDOFF, OPEN_SETTINGS, SHOW_QR, RECONNECT, CHECK }

fun oneTapAction(readiness: OneTapReadiness, qrEligible: Boolean): OneTapAction = when {
    readiness.ready -> OneTapAction.HANDOFF
    readiness.blocker == OneTapBlocker.SESSION_EXPIRED -> OneTapAction.RECONNECT
    readiness.blocker == OneTapBlocker.MISSING_NAME -> OneTapAction.OPEN_SETTINGS
    readiness.blocker == OneTapBlocker.WECHAT_MISSING && qrEligible -> OneTapAction.SHOW_QR
    else -> OneTapAction.CHECK
}

/**
 * Explains whether one-tap handoff can be offered right now. This is UI
 * guidance only; the live target is rechecked again immediately before launch.
 */
fun oneTapReadiness(
    view: MonitorView?,
    rosterName: String,
    wechatInstalled: Boolean,
    requiresRosterName: Boolean = true,
    sessionExpired: Boolean = false
): OneTapReadiness {
    fun blocked(code: OneTapBlocker, message: String) =
        OneTapReadiness(ready = false, blocker = code, message = message)

    if (sessionExpired) {
        return blocked(OneTapBlocker.SESSION_EXPIRED, "教务会话已过期 · 缓存仍可查看")
    }
    if (view == null) return blocked(OneTapBlocker.NO_VIEW, "还没读到当前签到")
    when (view.state) {
        State.NOT_STARTED -> return blocked(OneTapBlocker.NOT_STARTED, "签到还没开始")
        State.WAITING_THRESHOLD ->
            return blocked(OneTapBlocker.WAITING_THRESHOLD, "等待人数达到安全线")
        State.BLOCKED_NOT_IN_ROSTER ->
            return blocked(OneTapBlocker.NOT_IN_ROSTER, "名单里没有找到本人")
        State.BLOCKED_AMBIGUOUS_NAME ->
            return blocked(OneTapBlocker.AMBIGUOUS_NAME, "名单有同名，无法确认本人")
        State.EXPIRED -> return blocked(OneTapBlocker.ENDED, "签到已结束")
        State.CONFIRMED -> return blocked(OneTapBlocker.CONFIRMED, "本次已确认签到")
        State.BLOCKED -> return blocked(OneTapBlocker.BLOCKED, "签到状态读不到")
        State.IDLE -> return blocked(OneTapBlocker.NO_ACTIVITY, "当前没有可交接的签到")
        State.READY -> Unit
    }
    if (requiresRosterName && rosterName.isBlank()) {
        return blocked(OneTapBlocker.MISSING_NAME, "先在设置填写名单姓名")
    }
    if (view.signId.isBlank() || view.roomCode.isBlank()) {
        return blocked(OneTapBlocker.NO_ACTIVITY, "当前没有可交接的签到")
    }
    if (!view.signAccessAllowed) {
        return blocked(OneTapBlocker.ACCESS_DENIED, "名单或阈值条件未满足")
    }
    if (!wechatInstalled) {
        return blocked(OneTapBlocker.WECHAT_MISSING, "未安装微信，可先查看二维码")
    }
    return OneTapReadiness(ready = true, blocker = null, message = "条件已满足，可一键签到")
}
