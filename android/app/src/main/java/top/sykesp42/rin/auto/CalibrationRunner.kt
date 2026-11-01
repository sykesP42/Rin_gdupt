package top.sykesp42.rin.auto

/**
 * Dry run of the WeCom album-scan chain with a harmless link, so the user
 * can verify on their own device that the current WeCom version still
 * matches the chain before relying on auto mode in a real class.
 */
class CalibrationRunner(
    private val gates: AutoGates,
    private val driverProvider: () -> UiDriver?,
    private val qrSource: () -> ByteArray?,
    private val publisher: suspend (ByteArray) -> Boolean,
    private val historySink: suspend (String) -> Unit = {}
) {
    sealed interface Result {
        data object Success : Result
        data class Blocked(val reason: String) : Result
        data class ChainFailed(val step: String) : Result
    }

    suspend fun run(): Result {
        if (!gates.weComInstalled()) return blocked(REASON_NO_WECOM)
        if (!gates.driverConnected()) return blocked(REASON_NO_ACCESSIBILITY)
        val driver = driverProvider() ?: return blocked(REASON_NO_ACCESSIBILITY)
        if (!gates.screenInteractive() || gates.keyguardLocked()) return blocked(REASON_LOCKED)
        val png = qrSource() ?: return blocked(REASON_QR_ENCODE)
        if (!publisher(png)) return blocked(REASON_PUBLISH)
        return when (val outcome = driver.runChain(WeComChain.wecomAlbumScanChain())) {
            ChainOutcome.Success -> {
                historySink("OK")
                Result.Success
            }
            ChainOutcome.Busy -> blocked(REASON_BUSY)
            is ChainOutcome.Interrupted -> {
                historySink(outcome.reason.take(24))
                Result.Blocked(outcome.reason)
            }
            is ChainOutcome.StepFailed -> {
                historySink(outcome.step.take(24))
                Result.ChainFailed(outcome.step)
            }
        }
    }

    private suspend fun blocked(reason: String): Result.Blocked {
        historySink("BLOCKED_$reason")
        return Result.Blocked(reason)
    }

    companion object {
        /** Opens harmlessly in WeCom's browser; encoded into the calibration QR. */
        const val CALIBRATION_URL = "https://example.com/suisign-calibration"

        const val REASON_NO_WECOM = "NO_WECOM"
        const val REASON_NO_ACCESSIBILITY = "NO_ACCESSIBILITY"
        const val REASON_LOCKED = "LOCKED"
        const val REASON_BUSY = "BUSY"
        const val REASON_QR_ENCODE = "QR_ENCODE"
        const val REASON_PUBLISH = "PUBLISH"
    }
}
