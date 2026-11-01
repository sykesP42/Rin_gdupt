package top.sykesp42.rin.domain

import java.time.Instant

enum class State {
    IDLE, NOT_STARTED, WAITING_THRESHOLD, READY, EXPIRED,
    BLOCKED_NOT_IN_ROSTER, BLOCKED_AMBIGUOUS_NAME, BLOCKED, CONFIRMED
}

enum class ActivityStatus { NOT_STARTED, STARTED, END }

data class Activity(
    val signId: String,
    val status: ActivityStatus,
    val effectiveSignEndAt: Instant
)

data class SignAccess(
    val allowed: Boolean,
    val reason: String,
    val requiresConfirmation: Boolean,
    val signedCount: Int,
    val rosterCount: Int
)

data class Snapshot(
    val now: Instant,
    val threshold: Int,
    val activity: Activity?,
    val signAccess: SignAccess,
    val lastHandledSignID: String = "",
    val selfSigned: Boolean = false
)

data class Decision(
    val state: State,
    val autoAllowed: Boolean,
    val manualRequiresConfirmation: Boolean,
    val signId: String
)

/**
 * Mirror of contracts.Evaluate. Both clients are tested against the same
 * JSON fixtures so the two platforms cannot drift.
 */
fun evaluate(s: Snapshot): Decision {
    val activity = s.activity
    if (s.selfSigned) return Decision(State.CONFIRMED, false, false, activity?.signId ?: "")
    if (activity == null) return Decision(State.IDLE, false, false, "")

    if (activity.status == ActivityStatus.END || !s.now.isBefore(activity.effectiveSignEndAt)) {
        return Decision(State.EXPIRED, false, false, activity.signId)
    }
    if (activity.status == ActivityStatus.NOT_STARTED) {
        return Decision(State.NOT_STARTED, false, false, activity.signId)
    }
    if (!s.signAccess.allowed) {
        val state = when (s.signAccess.reason) {
            "not_in_roster" -> State.BLOCKED_NOT_IN_ROSTER
            "ambiguous_name" -> State.BLOCKED_AMBIGUOUS_NAME
            else -> State.BLOCKED
        }
        return Decision(state, false, false, activity.signId)
    }
    if (s.signAccess.requiresConfirmation) {
        return Decision(State.WAITING_THRESHOLD, false, true, activity.signId)
    }
    val auto = s.threshold > 0 &&
        s.signAccess.signedCount >= s.threshold &&
        activity.signId != s.lastHandledSignID
    return Decision(State.READY, auto, false, activity.signId)
}
