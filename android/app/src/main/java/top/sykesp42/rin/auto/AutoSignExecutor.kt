package top.sykesp42.rin.auto

import java.time.Instant
import kotlinx.coroutines.delay

/** What the auto mode acts on; deliberately free of monitor types. */
data class AutoTarget(
    val roomCode: String,
    val courseName: String,
    val signId: String,
    val effectiveSignEndAt: Instant?
)

/** Device preconditions; implemented with Android services in production. */
interface AutoGates {
    fun driverConnected(): Boolean
    fun screenInteractive(): Boolean
    fun keyguardLocked(): Boolean
    fun weChatInstalled(): Boolean
    fun weComInstalled(): Boolean
    fun now(): Instant
}

/** Side effects owned by the service layer: QR fetch/publish, dispatch, escalation. */
interface AutoSink {
    /** Fresh dynamic sign QR PNG at dispatch moment, or null when fetch/validation fails. */
    suspend fun fetchSignQr(roomCode: String): ByteArray?

    /** Insert the QR into the device gallery so WeCom's album shows it as newest. */
    suspend fun publishQrImage(png: ByteArray): Boolean

    /** The chain ran to the end: mark dispatched and start roster confirmation. */
    suspend fun recordDispatch(target: AutoTarget)

    /** Screen locked/off: keep the manual fallback alive and wait for unlock. */
    suspend fun recordLockedDeferral(target: AutoTarget)

    /** All attempts exhausted (or a hard stop): hand back to the human path. */
    suspend fun recordFailure(target: AutoTarget, reason: String)
}

sealed interface AutoOutcome {
    data object Dispatched : AutoOutcome
    data object DeferredLocked : AutoOutcome
    data class Failed(val reason: String) : AutoOutcome
}

/**
 * Orchestrates one auto sign dispatch: preconditions -> human-like jitter ->
 * up to [AutoPolicy.MAX_ATTEMPTS] chain runs, each with a freshly fetched
 * URL -> confirmation handoff or escalation to the notification path.
 *
 * Pure Kotlin: gates, driver, sink and time are injected so the whole state
 * machine is unit-testable on the JVM.
 */
class AutoSignExecutor(
    private val gates: AutoGates,
    private val driverProvider: () -> UiDriver?,
    private val sink: AutoSink,
    private val randomUnit: () -> Double = { Math.random() },
    private val delayFn: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun attempt(target: AutoTarget, deferred: Boolean = false): AutoOutcome {
        if (deadlinePassed(target)) {
            sink.recordFailure(target, REASON_EXPIRED)
            return AutoOutcome.Failed(REASON_EXPIRED)
        }
        if (!gates.weComInstalled()) {
            sink.recordFailure(target, REASON_NO_WECOM)
            return AutoOutcome.Failed(REASON_NO_WECOM)
        }
        val driver = driverProvider()
        if (!gates.driverConnected() || driver == null) {
            sink.recordFailure(target, REASON_NO_ACCESSIBILITY)
            return AutoOutcome.Failed(REASON_NO_ACCESSIBILITY)
        }

        delayFn(AutoPolicy.jitterMs(randomUnit(), deferred))

        var lastReason = REASON_UNKNOWN
        for (index in 0 until AutoPolicy.MAX_ATTEMPTS) {
            // The screen may have locked again while jittering.
            if (!gates.screenInteractive() || gates.keyguardLocked()) {
                sink.recordLockedDeferral(target)
                return AutoOutcome.DeferredLocked
            }
            if (deadlinePassed(target)) {
                sink.recordFailure(target, REASON_EXPIRED)
                return AutoOutcome.Failed(REASON_EXPIRED)
            }

            val png = sink.fetchSignQr(target.roomCode)
            when {
                png == null -> lastReason = REASON_URL_FETCH
                !sink.publishQrImage(png) -> lastReason = REASON_PUBLISH
                else -> when (val result = driver.runChain(WeComChain.wecomAlbumScanChain())) {
                    ChainOutcome.Success -> {
                        sink.recordDispatch(target)
                        return AutoOutcome.Dispatched
                    }
                    ChainOutcome.Busy -> lastReason = REASON_BUSY
                    is ChainOutcome.Interrupted -> {
                        sink.recordFailure(target, result.reason)
                        return AutoOutcome.Failed(result.reason)
                    }
                    is ChainOutcome.StepFailed -> lastReason = "STEP_${result.step}"
                }
            }
            if (index < AutoPolicy.MAX_ATTEMPTS - 1) {
                delayFn(AutoPolicy.RETRY_BACKOFF_MS)
            }
        }
        sink.recordFailure(target, lastReason)
        return AutoOutcome.Failed(lastReason)
    }

    private fun deadlinePassed(target: AutoTarget): Boolean {
        val endAt = target.effectiveSignEndAt ?: return false
        return !gates.now().plusMillis(AutoPolicy.DEADLINE_MARGIN_MS).isBefore(endAt)
    }

    companion object {
        const val REASON_EXPIRED = "EXPIRED"
        const val REASON_NO_WECOM = "NO_WECOM"
        const val REASON_NO_ACCESSIBILITY = "NO_ACCESSIBILITY"
        const val REASON_URL_FETCH = "URL_FETCH"
        const val REASON_PUBLISH = "PUBLISH"
        const val REASON_BUSY = "BUSY"
        const val REASON_UNKNOWN = "UNKNOWN"
    }
}
