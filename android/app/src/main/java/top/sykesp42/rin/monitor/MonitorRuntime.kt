package top.sykesp42.rin.monitor

import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process monitor state. The service owns it; the Activity only observes. */
object MonitorRuntime {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _view = MutableStateFlow<MonitorView?>(null)
    val view: StateFlow<MonitorView?> = _view.asStateFlow()

    private val _stopAt = MutableStateFlow<Instant?>(null)
    val stopAt: StateFlow<Instant?> = _stopAt.asStateFlow()

    private val _authExpired = MutableStateFlow(false)
    val authExpired: StateFlow<Boolean> = _authExpired.asStateFlow()

    internal fun started(stopAt: Instant?) {
        _running.value = true
        _stopAt.value = stopAt
    }

    internal fun published(view: MonitorView) {
        _view.value = view
    }

    internal fun stopped() {
        _running.value = false
        _stopAt.value = null
    }

    internal fun markAuthExpired() {
        _authExpired.value = true
    }

    fun clearAuthExpired() {
        _authExpired.value = false
    }

    internal fun resetView() {
        _view.value = null
    }
}
