package top.sykesp42.rin.auto

/**
 * Contract between the auto executor (monitor side) and the accessibility
 * service (UI driver). The service registers itself in [AutoRuntime] when
 * connected; everything stays in one process.
 */
interface UiDriver {
    /**
     * Run one full tap chain. Returns [ChainOutcome.StepFailed] with a
     * content-free step label; never returns or logs node text.
     */
    suspend fun runChain(steps: List<ChainStep>): ChainOutcome
}

sealed interface ChainOutcome {
    data object Success : ChainOutcome
    data object Busy : ChainOutcome

    /** step/label and reason codes only; no window content. */
    data class StepFailed(val step: String, val reason: String) : ChainOutcome

    /** e.g. the accessibility service was disabled mid-chain. */
    data class Interrupted(val reason: String) : ChainOutcome
}

/** Debug-only active-tree serializer; implemented by WeChatAutoService's driver. */
interface TreeDumper {
    /** One line per node, depth-indented, text truncated; never leaves the device. */
    fun dumpActive(): String
}

/** Process-wide handle to the live accessibility driver, if connected. */
object AutoRuntime {
    @Volatile
    private var current: UiDriver? = null

    fun attach(driver: UiDriver) {
        current = driver
    }

    fun detach(driver: UiDriver) {
        if (current === driver) current = null
    }

    fun driver(): UiDriver? = current

    fun isConnected(): Boolean = current != null
}
