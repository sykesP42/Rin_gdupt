package top.sykesp42.rin.handoff

import top.sykesp42.rin.data.ApiCourse

enum class HandoffReject { NO_COURSE, NO_ACTIVITY, SIGN_CHANGED, NOT_STARTED, ACCESS_DENIED }

sealed interface HandoffTargetCheck {
    data object Ready : HandoffTargetCheck
    data class Rejected(
        val code: HandoffReject,
        val title: String,
        val message: String
    ) : HandoffTargetCheck
}

/**
 * Rechecks the live course immediately before handoff. The page snapshot may be
 * seconds old; a rotated signId or a closed window must not open WeChat with a
 * QR that belongs to a different activity.
 */
internal fun checkHandoffTarget(course: ApiCourse?, expectedSignId: String): HandoffTargetCheck {
    if (course == null) {
        return HandoffTargetCheck.Rejected(
            HandoffReject.NO_COURSE,
            "读不到当前课堂",
            "回Rin刷新后再试。"
        )
    }
    val activity = course.activity
    if (activity == null || activity.signId.isBlank()) {
        return HandoffTargetCheck.Rejected(
            HandoffReject.NO_ACTIVITY,
            "当前没有签到活动",
            "回Rin刷新后再试。"
        )
    }
    if (activity.signId != expectedSignId) {
        return HandoffTargetCheck.Rejected(
            HandoffReject.SIGN_CHANGED,
            "签到活动已变化",
            "回Rin刷新后再发起。"
        )
    }
    if (activity.status != "STARTED") {
        return HandoffTargetCheck.Rejected(
            HandoffReject.NOT_STARTED,
            "签到未开始或已结束",
            "回Rin查看当前状态。"
        )
    }
    if (!course.signAccess.allowed) {
        val message = when (course.signAccess.reason) {
            "not_in_roster" -> "名单里没有找到本人姓名。"
            "ambiguous_name" -> "名单有同名，无法确认本人。"
            "roster_below_threshold" -> "签到人数还没到安全线。"
            else -> "名单或阈值条件未满足。"
        }
        return HandoffTargetCheck.Rejected(
            HandoffReject.ACCESS_DENIED,
            "还不满足签到条件",
            message
        )
    }
    return HandoffTargetCheck.Ready
}
