package top.sykesp42.rin.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.sykesp42.rin.data.AppContainer

/**
 * Keeps the local JWXT session warm while the phone is the scanner. The school
 * drops an idle session in well under an hour; a once-a-day touch never reached
 * the next class. This service is deliberately separate from MonitorService:
 * monitoring only runs inside class windows, while the session needs attention
 * between them.
 */
class SessionKeeper : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.get(this)
        container.notifications.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground("每 10 分钟续期一次")
        if (intent?.action == ACTION_POKE) wake.trySend(Unit)
        if (job?.isActive != true) {
            job = scope.launch { runHeartbeat() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private suspend fun runHeartbeat() {
        while (currentCoroutineContext().isActive) {
            val settings = runCatching { container.settings.current() }.getOrNull()
            if (settings == null || !settings.localMode || !settings.monitorEnabled) {
                stopSelf()
                return
            }
            val text = if (settings.jwxtNeedsLogin) {
                "等待网页重连 · 暂不请求教务"
            } else {
                val ok = runCatching { container.keepJwxtSession(settings) }
                    .onFailure {
                        if (it is CancellationException) throw it
                    }
                    .getOrDefault(false)
                if (ok) {
                    "上次续期 ${clock()} · 会话正常"
                } else {
                    "上次续期 ${clock()} · 需要重连"
                }
            }
            runCatching { container.notifications.updateSession(text) }
            withTimeoutOrNull(INTERVAL_MS) { wake.receive() }
        }
    }

    private fun startInForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            MonitorNotifications.NOTIFICATION_SESSION,
            container.notifications.buildSession(text),
            type
        )
    }

    private fun clock(): String = LocalTime.now().format(CLOCK)

    companion object {
        // The observed session dies around 49 minutes after login. Ten minutes
        // leaves room for one missed beat without keeping the radio awake all day.
        const val INTERVAL_MS = 10 * 60 * 1000L
        const val ACTION_POKE = "top.sykesp42.rin.monitor.SESSION_POKE"
        private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

        fun sync(context: Context, enabled: Boolean) {
            if (enabled) start(context) else stop(context)
        }

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SessionKeeper::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, SessionKeeper::class.java)) }
        }

        fun poke(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SessionKeeper::class.java).setAction(ACTION_POKE)
                )
            }
        }
    }
}
