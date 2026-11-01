package top.sykesp42.rin.domain

import java.time.Duration
import java.time.Instant

enum class SignAccessLevel {
    UNKNOWN,
    NONE,
    PENDING,
    ACTIVE,
    STALE,
    REJECTED,
    REVOKED,
    ERROR
}

data class SignAccessState(
    val status: SignAccessLevel = SignAccessLevel.UNKNOWN,
    val appliedVersion: String = "",
    val verifiedAt: Instant? = null,
    val error: String = ""
) {
    val proEnabled: Boolean get() = status == SignAccessLevel.ACTIVE
    val reminderOnly: Boolean get() = !proEnabled
    val modeLabel: String get() = if (proEnabled) "完整模式" else "提醒模式"

    val bannerText: String?
        get() = when (status) {
            SignAccessLevel.PENDING -> "完整功能审核中"
            SignAccessLevel.STALE -> "需要联网确认授权"
            SignAccessLevel.REJECTED -> "申请未通过"
            SignAccessLevel.REVOKED -> "完整功能已停用"
            SignAccessLevel.ERROR -> "授权状态暂时无法确认"
            SignAccessLevel.NONE -> "提醒模式 · 仅提醒与名单"
            else -> null
        }
}

const val SIGN_ACCESS_CACHE_TTL_HOURS = 24L

fun signAccessFromRemote(status: String, appliedVersion: String, verifiedAt: Instant): SignAccessState =
    SignAccessState(
        status = remoteLevel(status),
        appliedVersion = appliedVersion,
        verifiedAt = verifiedAt
    )

fun signAccessFromCache(
    status: String,
    appliedVersion: String,
    checkedAt: Instant,
    now: Instant
): SignAccessState {
    val level = remoteLevel(status)
    if (level != SignAccessLevel.ACTIVE) {
        return SignAccessState(level, appliedVersion, checkedAt)
    }
    val age = Duration.between(checkedAt, now)
    val stale = age.isNegative || age > Duration.ofHours(SIGN_ACCESS_CACHE_TTL_HOURS)
    return SignAccessState(
        status = if (stale) SignAccessLevel.STALE else SignAccessLevel.ACTIVE,
        appliedVersion = appliedVersion,
        verifiedAt = checkedAt
    )
}

private fun remoteLevel(status: String): SignAccessLevel = when (status.lowercase()) {
    "none", "" -> SignAccessLevel.NONE
    "pending" -> SignAccessLevel.PENDING
    "active" -> SignAccessLevel.ACTIVE
    "rejected" -> SignAccessLevel.REJECTED
    "revoked" -> SignAccessLevel.REVOKED
    else -> SignAccessLevel.UNKNOWN
}

sealed interface SignAccessApplyOutcome {
    data class Applied(val state: SignAccessState) : SignAccessApplyOutcome
    data class Failed(val code: String) : SignAccessApplyOutcome
}
