package top.sykesp42.rin.handoff

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.sykesp42.rin.domain.State
import top.sykesp42.rin.monitor.MonitorView

data class PendingConfirmation(
    val roomCode: String,
    val signId: String,
    val courseName: String,
    val deadline: Instant
)

/** Builds a confirmation window only for a complete handoff target. */
internal fun pendingConfirmation(
    roomCode: String,
    signId: String,
    courseName: String,
    now: Instant,
    window: Duration = Duration.ofSeconds(60)
): PendingConfirmation? {
    val room = roomCode.trim()
    val sign = signId.trim()
    if (room.isEmpty() || sign.isEmpty()) return null
    return PendingConfirmation(
        roomCode = room,
        signId = sign,
        courseName = courseName.trim(),
        deadline = now.plus(window)
    )
}

internal fun pendingRemainingSeconds(deadline: Instant, now: Instant): Long =
    Duration.between(now, deadline).seconds.coerceAtLeast(0)

enum class HandoffOutcomeStatus { CONFIRMED, UNCONFIRMED }

data class HandoffOutcome(
    val signId: String,
    val courseName: String,
    val status: HandoffOutcomeStatus,
    val at: Instant,
    /**
     * False when the roster could not be read at all during the window: the
     * user may well have signed, so the receipt must not claim "未确认".
     */
    val reachable: Boolean = true
)

data class HandoffOutcomeText(val text: String, val positive: Boolean)

/**
 * Terminal result of a confirmation window. Only a matching roster
 * CONFIRMED snapshot counts as success; a different sign never does.
 */
internal fun handoffOutcomeFor(
    pending: PendingConfirmation,
    view: MonitorView?,
    now: Instant
): HandoffOutcome? {
    val confirmedNow = view != null && view.state == State.CONFIRMED &&
        (pending.signId.isBlank() || pending.signId == view.signId)
    return when {
        confirmedNow -> HandoffOutcome(
            signId = view.signId.ifBlank { pending.signId },
            courseName = pending.courseName,
            status = HandoffOutcomeStatus.CONFIRMED,
            at = now
        )
        !now.isBefore(pending.deadline) -> HandoffOutcome(
            signId = pending.signId,
            courseName = pending.courseName,
            status = HandoffOutcomeStatus.UNCONFIRMED,
            at = now
        )
        else -> null
    }
}


fun handoffOutcomeText(outcome: HandoffOutcome?, currentSignId: String): HandoffOutcomeText? {
    if (outcome == null || currentSignId.isBlank() || outcome.signId != currentSignId) return null
    return when (outcome.status) {
        HandoffOutcomeStatus.CONFIRMED ->
            HandoffOutcomeText("本次一键签到已确认", positive = true)
        HandoffOutcomeStatus.UNCONFIRMED ->
            if (outcome.reachable) {
                HandoffOutcomeText("本次一键签到未确认，请刷新名单或手动签到", positive = false)
            } else {
                HandoffOutcomeText("本次签到结果未能确认（名单读取失败），请刷新名单", positive = false)
            }
    }
}

/** In-process handoff state shared by HandoffActivity and MonitorService. */
object HandoffRuntime {
    private val _pending = MutableStateFlow<PendingConfirmation?>(null)
    val pending: StateFlow<PendingConfirmation?> = _pending.asStateFlow()
    private val _outcome = MutableStateFlow<HandoffOutcome?>(null)
    val outcome: StateFlow<HandoffOutcome?> = _outcome.asStateFlow()

    fun begin(pending: PendingConfirmation) {
        _pending.value = pending
        _outcome.value = null
    }

    fun finish(outcome: HandoffOutcome) {
        _pending.value = null
        _outcome.value = outcome
    }

    fun clear() {
        _pending.value = null
    }

    fun clearOutcome() {
        _outcome.value = null
    }

    fun current(): PendingConfirmation? = _pending.value
}
