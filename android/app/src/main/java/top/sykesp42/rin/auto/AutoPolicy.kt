package top.sykesp42.rin.auto

/**
 * Tunables for the on-device auto sign mode. Kept in one pure object so the
 * timing budget (jitter + retries + chain timeout) can be reasoned about and
 * tested without Android.
 */
object AutoPolicy {
    const val MAX_ATTEMPTS = 3

    /** Human-like delay before the first dispatch: never be the first signer. */
    const val JITTER_MIN_MS = 5_000L
    const val JITTER_MAX_MS = 20_000L

    /** The user already waited behind the lock screen; keep only a small settle delay. */
    const val DEFERRED_JITTER_MAX_MS = 2_000L

    /** Pause between two attempts of the same sign activity. */
    const val RETRY_BACKOFF_MS = 4_000L

    /** Hard budget for one full WeChat tap chain. */
    const val CHAIN_TOTAL_TIMEOUT_MS = 30_000L

    /** Do not start a chain this close to effectiveSignEndAt. */
    const val DEADLINE_MARGIN_MS = 5_000L

    fun jitterMs(randomUnit: Double, deferred: Boolean): Long {
        val unit = randomUnit.coerceIn(0.0, 1.0)
        return if (deferred) {
            (unit * DEFERRED_JITTER_MAX_MS).toLong()
        } else {
            JITTER_MIN_MS + (unit * (JITTER_MAX_MS - JITTER_MIN_MS)).toLong()
        }
    }
}
