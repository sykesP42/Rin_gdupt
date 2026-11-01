package top.sykesp42.rin.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single runtime switch between the clean public surface and the full
 * client. Everything sign-related reads this at its entry point; when it is
 * off the sign code paths never run and no sign wording reaches the screen.
 * The persisted flag lives in SettingsRepository (internalMode); the license
 * client turns it on after a successful verify and off when the daily status
 * check reports a revoked entitlement.
 */
object InternalGate {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val isOn: Boolean get() = _enabled.value

    fun set(value: Boolean) {
        _enabled.value = value
    }
}
