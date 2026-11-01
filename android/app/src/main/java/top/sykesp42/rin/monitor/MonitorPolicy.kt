package top.sykesp42.rin.monitor

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import top.sykesp42.rin.domain.State

/** Adaptive polling delays, retry backoff and jitter (design section 13). */
object MonitorPolicy {
    val activeInterval = 10.seconds
    val prepInterval = 30.seconds
    val idleInterval = 60.seconds
    val rateLimitInterval = 60.seconds

    fun intervalFor(state: State): Duration = when (state) {
        State.READY, State.WAITING_THRESHOLD -> activeInterval
        State.NOT_STARTED, State.BLOCKED, State.BLOCKED_NOT_IN_ROSTER,
        State.BLOCKED_AMBIGUOUS_NAME -> prepInterval
        State.IDLE, State.CONFIRMED, State.EXPIRED -> idleInterval
    }

    fun backoffFor(failures: Int): Duration = when {
        failures <= 0 -> 15.seconds
        failures == 1 -> 30.seconds
        else -> 60.seconds
    }

    /** Delay after a poll. A 429 always backs off to the server-friendly floor. */
    fun delay(
        state: State?,
        failures: Int,
        rateLimited: Boolean,
        random: Random = Random.Default
    ): Duration {
        val base = when {
            rateLimited -> rateLimitInterval
            state == null -> backoffFor(failures)
            else -> intervalFor(state)
        }
        return jitter(base, random)
    }

    /** Spreads a delay by up to +/-20% so clients do not poll in lockstep. */
    fun jitter(base: Duration, random: Random = Random.Default): Duration {
        val factor = 0.8 + 0.4 * random.nextDouble()
        val spread = (base.inWholeMilliseconds * factor).toLong().milliseconds
        return if (spread < 1.seconds) 1.seconds else spread
    }
}
