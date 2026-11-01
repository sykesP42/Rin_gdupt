package top.sykesp42.rin.monitor

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.sykesp42.rin.auto.AndroidAutoGates
import top.sykesp42.rin.auto.AutoOutcome
import top.sykesp42.rin.auto.AutoRuntime
import top.sykesp42.rin.auto.AutoSignExecutor
import top.sykesp42.rin.auto.AutoSink
import top.sykesp42.rin.auto.AutoTarget
import top.sykesp42.rin.data.ApiError
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.HistoryKind
import top.sykesp42.rin.domain.State
import top.sykesp42.rin.handoff.HandoffOutcomeStatus
import top.sykesp42.rin.handoff.HandoffRuntime
import top.sykesp42.rin.handoff.PendingConfirmation
import top.sykesp42.rin.handoff.handoffOutcomeFor
import kotlin.time.Duration.Companion.minutes

class MonitorService : Service() {
    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null
    private var watchdogAt: Instant? = null
    private var finished = false
    private var lastReadyHistoryId: String? = null
    private var lastConfirmedHistoryId: String? = null
    private var autoExecutor: AutoSignExecutor? = null
    private var autoAttemptJob: Job? = null
    private var autoDispatchedSignId: String? = null
    private var autoFallbackView: MonitorView? = null
    private var deferredTarget: AutoTarget? = null
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.get(this)
        container.notifications.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: REASON_DAILY
        if (loop?.isActive != true) {
            finished = false
            if (reason == REASON_CONFIRM && HandoffRuntime.current() == null) {
                // The process may have been killed mid-window; resume the
                // journaled confirmation instead of dropping the receipt.
                container.handoffJournal.loadResumable()?.let { HandoffRuntime.begin(it) }
            }
            val confirming = reason == REASON_CONFIRM
            startInForeground(if (confirming) "确认签到结果" else "准备监控中")
            MonitorRuntime.started(stopAt = if (confirming) HandoffRuntime.current()?.deadline else null)
            container.notifications.cancelError()
            loop = scope.launch {
                if (confirming) runConfirmation() else run(reason)
            }
        } else {
            if (reason == REASON_WAKE) watchdogAt = null
            wake.trySend(Unit)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        if (::container.isInitialized) container.alarms.cancelWake()
        disarmAutoWait()
        MonitorRuntime.stopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startInForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(
            this,
            MonitorNotifications.NOTIFICATION_MONITOR,
            container.notifications.buildMonitor(text),
            type
        )
    }

    private suspend fun run(reason: String) {
        val settings = container.settings.current()
        // A service started at boot has no ViewModel alive to publish these.
        NoticeFaces.publish(settings)
        if (!settings.monitorEnabled) {
            finish(cancelAll = true)
            return
        }
        // Local mode needs no cloud session: the phone talks to the school.
        if (!settings.localMode && container.sessionStore.load() == null) {
            notifyAuth(reason)
            finish(cancelAll = true)
            return
        }
        val api = container.monitorApi(settings)
        if (api == null) {
            container.notifications.showError("服务地址无效", "打开Rin改服务地址")
            finish(cancelAll = true)
            return
        }

        val engine = MonitorEngine(api)
        if (settings.localMode) checkGradePublish()
        val manual = reason == REASON_MANUAL
        val startNow = Instant.now()
        var stopAt = startNow.plusMillis(MANUAL_DURATION.inWholeMilliseconds)
        // Kept for the schedule-driven class reminder: it must work even when
        // the attendance API is refused, and the timetable is the only honest
        // source for "a class is starting right now".
        var todayEvents: List<top.sykesp42.rin.data.ApiScheduleEvent> = emptyList()
        try {
            val overview = api.schedule()
            todayEvents = overview.today
            // Daily sync also refreshes the alarm-only reminders for today.
            runCatching { container.classReminders.reschedule(overview) }
            runCatching { top.sykesp42.rin.widget.RinWidgets.refreshAll(this@MonitorService) }
            if (settings.localMode && reason == REASON_DAILY) {
                // Headless maintenance: keep the offline cache useful while the
                // phone acts as the scanner; TTL-gated, read-only.
                runCatching { container.warmOfflineCaches(settings, overview.term) }
            }
            val plan = ClassWindowPlanner.plan(todayEvents, startNow)
            container.scheduler.apply(plan, startNow)
            if (!manual) {
                val current = plan.current
                if (current == null) {
                    if (settings.localMode && settings.manualCode.isNotBlank()) {
                        // No local JWXT schedule yet (L3): a manual room code
                        // still opens a bounded watch window.
                        stopAt = startNow.plusMillis(MANUAL_DURATION.inWholeMilliseconds)
                        container.notifications.updateMonitor("本地模式，按手动教室码监控")
                    } else {
                        container.notifications.updateMonitor("等下一节课", NoticeSlot.WAITING)
                        finish(cancelAll = false)
                        return
                    }
                } else {
                    stopAt = current.end
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is ApiError && (error.status == 401 || MonitorCopy.isAuthError(error.code))) {
                notifyAuth(reason)
                finish(cancelAll = true)
                return
            }
            if (error is ApiError && error.code == "JWXT_ACCOUNT_REQUIRED" && settings.manualCode.isBlank()) {
                container.notifications.showError("需要连接教务", MonitorCopy.errorText(error.code))
                container.scheduler.scheduleDailySync(Instant.now())
                finish(cancelAll = false)
                return
            }
            container.scheduler.scheduleRetry(startNow, RETRY_AFTER_MILLIS)
            if (!manual) stopAt = startNow.plusMillis(FALLBACK_DURATION.inWholeMilliseconds)
            Log.i(TAG, "schedule unavailable, bounded fallback type=${error.javaClass.simpleName}")
        }

        MonitorRuntime.started(stopAt)
        var failures = 0
        try {
            while (currentCoroutineContext().isActive) {
                val prefs = container.settings.current()
                if (!prefs.monitorEnabled) {
                    finish(cancelAll = true)
                    return
                }
                val now = Instant.now()
                if (!now.isBefore(stopAt)) break

                container.notifications.updateMonitor("检查签到状态")
                val view = try {
                    val value = engine.pollOnce(prefs.manualCode, prefs.lastHandledSignId)
                    failures = 0
                    publishView(value)
                    container.notifications.updateMonitor(MonitorCopy.monitorText(value), NoticeSlot.forState(value.state))
                    handleReady(value, prefs.lastNotifiedSignId)
                    maybeAutoDispatch(value, prefs)
                    checkPendingConfirmation(value)
                    value
                } catch (error: CancellationException) {
                    throw error
                } catch (error: ApiError) {
                    if (error.status == 401 || MonitorCopy.isAuthError(error.code)) {
                        notifyAuth(reason)
                        finish(cancelAll = true)
                        return
                    }
                    failures++
                    blocked(error.code.ifBlank { "HTTP_${error.status}" })
                } catch (error: Exception) {
                    failures++
                    blocked("OFFLINE")
                }

                armWatchdog(stopAt)
                // Independent of the poll result: a class that is starting
                // gets one reminder per class per day, so a blocked or idle
                // attendance API can never silence the user's only cue.
                if (!manual) maybeRemindClass(todayEvents, view, prefs)
                val waitMillis = delayUntilNextPoll(view, failures, stopAt)
                if (waitMillis <= 0) break
                withTimeoutOrNull(waitMillis) { wake.receive() }
            }
        } finally {
            if (!finished) finish(cancelAll = false)
        }
    }

    /**
     * One schedule-driven reminder per class start. Skipped when the engine
     * already produced a better prompt (ready / waiting / confirmed), so the
     * user never gets a redundant buzz after the real sign-in notification.
     */
    private suspend fun maybeRemindClass(
        events: List<top.sykesp42.rin.data.ApiScheduleEvent>,
        view: MonitorView?,
        prefs: AppSettings
    ) {
        if (events.isEmpty()) return
        if (view != null && (
            view.state == State.READY ||
                view.state == State.WAITING_THRESHOLD ||
                view.state == State.CONFIRMED
            )
        ) {
            return
        }
        val nowMinutes = Instant.now()
            .atZone(ClassWindowPlanner.schoolZone)
            .let { it.hour * 60 + it.minute }
        val target = classReminderTarget(events, nowMinutes) ?: return
        val key = reminderKey(java.time.LocalDate.now(ClassWindowPlanner.schoolZone), target)
        if (key == prefs.lastRemindedClassKey) return
        if (!container.notifications.canPost()) return
        container.notifications.showClassReminder(target.courseName, target.location)
        runCatching { container.settings.setLastRemindedClassKey(key) }
        runCatching {
            container.history.append(HistoryKind.CLASS_REMINDER, target.courseName, "", target.startTime)
        }
    }

    /** Publish and persist the roster receipt so it survives a restart. */
    private fun publishView(view: MonitorView) {
        MonitorRuntime.published(view)
        runCatching { container.rosterStore.save(view) }
        runCatching { container.attendance.record(view) }
    }

    private fun blocked(code: String): MonitorView {
        val view = MonitorView(state = State.BLOCKED, errorCode = code, serverTime = Instant.now())
        publishView(view)
        container.notifications.updateMonitor(MonitorCopy.monitorText(view), NoticeSlot.forState(view.state))
        return view
    }

    private suspend fun handleReady(view: MonitorView, lastNotified: String) {
        if (view.signId.isNotBlank() && view.state == State.READY && view.signId != lastReadyHistoryId) {
            lastReadyHistoryId = view.signId
            runCatching {
                container.history.append(HistoryKind.READY, view.courseName, view.roomCode, view.state.name)
            }
        }
        recordConfirmedOnce(view)
        if (view.state != State.READY) {
            container.notifications.cancelReady()
            return
        }
        if (view.signId.isNotBlank() && view.signId != lastNotified && container.notifications.canPost()) {
            container.notifications.showReady(view, container.signAccess.proEnabled())
            container.settings.setLastNotifiedSignId(view.signId)
        }
    }

    private suspend fun recordConfirmedOnce(view: MonitorView) {
        if (view.signId.isNotBlank() && view.state == State.CONFIRMED && view.signId != lastConfirmedHistoryId) {
            lastConfirmedHistoryId = view.signId
            disarmAutoWait()
            runCatching {
                container.history.append(HistoryKind.CONFIRMED, view.courseName, view.roomCode, view.state.name)
            }
        }
    }

    private suspend fun checkPendingConfirmation(view: MonitorView) {
        val pending = HandoffRuntime.current() ?: return
        val outcome = handoffOutcomeFor(pending, view, Instant.now()) ?: return
        when (outcome.status) {
            HandoffOutcomeStatus.CONFIRMED -> {
                container.notifications.cancelReady()
                container.notifications.showConfirmed()
            }
            HandoffOutcomeStatus.UNCONFIRMED -> {
                runCatching {
                    container.history.append(
                        HistoryKind.HANDOFF_UNCONFIRMED,
                        pending.courseName,
                        pending.roomCode,
                        "UNCONFIRMED"
                    )
                }
                container.notifications.showUnconfirmed()
            }
        }
        HandoffRuntime.finish(outcome)
        container.handoffJournal.clear()
    }

    /**
     * Daily grade-publish check: JWXT notices diff against the last seen id;
     * one read-only request per monitor start, silent on any failure.
     */
    private suspend fun checkGradePublish() {
        runCatching {
            val settings = container.settings.current()
            val notices = container.withJwxt(settings) { api -> api.fetchNotices() }
                ?: return
            if (notices.isEmpty()) return
            val newest = notices.first()
            val lastSeen = settings.lastSeenNoticeId
            if (lastSeen.isBlank()) {
                // First run: baseline silently, or the whole recent list would
                // surface as one spurious "成绩发布" notification.
                runCatching { container.settings.setLastSeenNoticeId(newest.courseName) }
                return
            }
            if (newest.courseName == lastSeen) return
            val fresh = notices.takeWhile { it.courseName != lastSeen }.take(3)
            if (fresh.isEmpty()) {
                runCatching { container.settings.setLastSeenNoticeId(newest.courseName) }
                return
            }
            container.notifications.showGradePublished(
                fresh.map { notice ->
                    val point = notice.gradePoint.takeIf { it.isNotBlank() }?.let { " · 绩点$it" }.orEmpty()
                    "${notice.courseName} ${notice.score}分$point"
                }
            )
            runCatching { container.settings.setLastSeenNoticeId(newest.courseName) }
        }
    }

    /**
     * Auto sign dispatch: when the user opted in and the engine says the
     * activity is ready for automation, run the on-device WeChat chain. The
     * poll loop keeps running in parallel; single-flight is guarded by
     * [autoDispatchedSignId] per sign activity.
     */
    private fun maybeAutoDispatch(view: MonitorView, prefs: AppSettings) {
        if (!prefs.autoSignEnabled) return
        if (!container.signAccess.proEnabled()) return
        if (view.state != State.READY || !view.autoAllowed) return
        val signId = view.signId
        if (signId.isBlank() || view.roomCode.isBlank()) return
        if (signId == autoDispatchedSignId) return
        autoDispatchedSignId = signId
        autoFallbackView = view
        val target = AutoTarget(
            roomCode = view.roomCode,
            courseName = view.courseName,
            signId = signId,
            effectiveSignEndAt = view.effectiveSignEndAt
        )
        autoAttemptJob?.cancel()
        autoAttemptJob = scope.launch {
            when (autoExecutor().attempt(target)) {
                AutoOutcome.Dispatched, is AutoOutcome.Failed -> disarmAutoWait()
                AutoOutcome.DeferredLocked -> {
                    deferredTarget = target
                    armUnlockReceiver()
                }
            }
        }
    }

    private fun autoExecutor(): AutoSignExecutor {
        if (autoExecutor == null) {
            autoExecutor = AutoSignExecutor(
                gates = AndroidAutoGates(this),
                driverProvider = { AutoRuntime.driver() },
                sink = AutoSinkImpl()
            )
        }
        return autoExecutor!!
    }

    /**
     * While deferred behind a lock screen, wake on unlock (or screen-on with
     * no keyguard) and continue the attempt with only a small settle delay.
     */
    private fun armUnlockReceiver() {
        if (unlockReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                        val target = deferredTarget ?: return
                        autoAttemptJob?.cancel()
                        autoAttemptJob = scope.launch {
                            delay(1200)
                            val current = deferredTarget ?: return@launch
                            when (autoExecutor().attempt(current, deferred = true)) {
                                AutoOutcome.Dispatched, is AutoOutcome.Failed -> disarmAutoWait()
                                AutoOutcome.DeferredLocked -> Unit // still locked; keep waiting
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        unlockReceiver = runCatching {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver
        }.getOrNull()
    }

    private fun disarmAutoWait() {
        deferredTarget = null
        unlockReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
            unlockReceiver = null
        }
    }

    /** Auto-mode side effects: QR fetch/publish, dispatch bookkeeping, escalation. */
    private inner class AutoSinkImpl : AutoSink {

        override suspend fun fetchSignQr(roomCode: String): ByteArray? {
            val settings = runCatching { container.settings.current() }.getOrNull() ?: return null
            // 云端与本地同时出发，哪边先铸出可用的码就用哪边（hedged request）。
            // 本地侧保留一次重试；整场竞速限时 12s，防止双方都卡死拖垮监控循环。
            val primary = container.monitorApi(settings)
            val cloud = container.client(settings.serverUrl)
            if (primary == null && cloud == null) return null
            return coroutineScope {
                val winner = CompletableDeferred<ByteArray?>()
                val jobs = listOfNotNull(
                    primary?.let { api ->
                        launch {
                            repeat(2) { attempt ->
                                val png = runCatching {
                                    withContext(Dispatchers.IO) { api.qrPng(roomCode) }
                                }.getOrNull()
                                if (png != null && looksLikePng(png)) {
                                    winner.complete(png)
                                    return@launch
                                }
                                if (attempt == 0) delay(1200)
                            }
                        }
                    },
                    cloud?.let { api ->
                        launch {
                            val png = runCatching {
                                withContext(Dispatchers.IO) { api.qrCode(roomCode) }
                            }.getOrNull()
                            if (png != null && looksLikePng(png)) winner.complete(png)
                        }
                    }
                )
                val got = withTimeoutOrNull(12_000) { winner.await() }
                jobs.forEach { it.cancel() }
                got
            }
        }

        override suspend fun publishQrImage(png: ByteArray): Boolean =
            runCatching { container.gallery.publish(png) }.getOrDefault(false)

        override suspend fun recordDispatch(target: AutoTarget) {
            // Same bookkeeping as the manual handoff so the confirmation loop,
            // idempotency and history rules stay identical.
            val pending = PendingConfirmation(
                roomCode = target.roomCode,
                signId = target.signId,
                courseName = target.courseName,
                deadline = Instant.now().plus(Duration.ofSeconds(60))
            )
            HandoffRuntime.begin(pending)
            container.handoffJournal.save(pending)
            runCatching { container.settings.setLastHandledSignId(target.signId) }
            runCatching {
                container.history.append(HistoryKind.AUTO_STARTED, target.courseName, target.roomCode, "AUTO")
            }
            container.gallery.deleteOwn()
            MonitorService.start(this@MonitorService, REASON_CONFIRM)
        }

        override suspend fun recordLockedDeferral(target: AutoTarget) {
            runCatching {
                container.history.append(HistoryKind.AUTO_DEFERRED, target.courseName, target.roomCode, "LOCKED")
            }
            autoFallbackView?.let { container.notifications.showReady(it, container.signAccess.proEnabled()) }
        }

        override suspend fun recordFailure(target: AutoTarget, reason: String) {
            runCatching {
                container.history.append(
                    HistoryKind.AUTO_FAILED,
                    target.courseName,
                    target.roomCode,
                    reason.take(24)
                )
            }
            container.gallery.deleteOwn()
            // Hand back to the human path: the ready notification stays the
            // one-tap fallback while the monitor loop is still running.
            autoFallbackView?.let { container.notifications.showReady(it, container.signAccess.proEnabled()) }
        }

        private fun looksLikePng(bytes: ByteArray): Boolean =
            bytes.size > 100 && bytes[0] == PNG_MAGIC_0 && bytes[1] == PNG_MAGIC_1

        private val PNG_MAGIC_0: Byte = 0x89.toByte()
        private val PNG_MAGIC_1: Byte = 0x50.toByte()
    }

    private suspend fun runConfirmation() {
        val pending = HandoffRuntime.current()
        if (pending == null) {
            Log.i(TAG, "confirm skip: no pending")
            container.handoffJournal.clear()
            finish(cancelAll = false)
            return
        }
        val settings = container.settings.current()
        if (!settings.localMode && container.sessionStore.load() == null) {
            Log.i(TAG, "confirm stop: cloud login missing")
            notifyAuth(REASON_MANUAL)
            HandoffRuntime.clear()
            container.handoffJournal.clear()
            finish(cancelAll = true)
            return
        }
        val api = container.monitorApi(settings)
        if (api == null) {
            Log.i(TAG, "confirm stop: no monitor api")
            container.notifications.showError("服务地址无效", "打开Rin改服务地址")
            HandoffRuntime.clear()
            container.handoffJournal.clear()
            finish(cancelAll = true)
            return
        }

        val engine = MonitorEngine(api)
        var confirmed: MonitorView? = null
        var authFailed = false
        var cancelled = false
        var reachable = false
        try {
            if (!Instant.now().isBefore(pending.deadline)) {
                // Resumed from the journal after the window (the process was
                // killed mid-confirmation): one roster check decides the
                // receipt instead of silently reporting failure.
                Log.i(TAG, "confirm resume past deadline: one roster check")
                try {
                    val view = engine.pollOnce(pending.roomCode, settings.lastHandledSignId)
                    if (view.roster.known) reachable = true
                    publishView(view)
                    if (view.state == State.CONFIRMED &&
                        (pending.signId.isBlank() || pending.signId == view.signId)
                    ) {
                        confirmed = view
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: ApiError) {
                    if (error.status == 401 || MonitorCopy.isAuthError(error.code)) {
                        notifyAuth(REASON_MANUAL)
                        authFailed = true
                    }
                } catch (_: Exception) {
                    // Offline resume: the timeout arm below stays honest.
                }
            } else {
                while (currentCoroutineContext().isActive && Instant.now().isBefore(pending.deadline)) {
                    try {
                        val view = engine.pollOnce(pending.roomCode, settings.lastHandledSignId)
                        if (view.roster.known) reachable = true
                        publishView(view)
                        if (view.state == State.CONFIRMED &&
                            (pending.signId.isBlank() || pending.signId == view.signId)
                        ) {
                            confirmed = view
                            break
                        }
                        val remaining = Duration.between(Instant.now(), pending.deadline).seconds.coerceAtLeast(0)
                        container.notifications.updateMonitor("等名单确认 · 剩 ${remaining}s")
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: ApiError) {
                        if (error.status == 401 || MonitorCopy.isAuthError(error.code)) {
                            notifyAuth(REASON_MANUAL)
                            authFailed = true
                            break
                        }
                        // The final verdict must reflect the freshest attempt,
                        // not an earlier successful read.
                        reachable = false
                        container.notifications.updateMonitor("确认请求失败，会继续重试")
                    } catch (_: Exception) {
                        reachable = false
                        container.notifications.updateMonitor("断网了，继续等名单")
                    }
                    val remaining = Duration.between(Instant.now(), pending.deadline).toMillis()
                    if (remaining <= 0) break
                    delay(minOf(CONFIRM_POLL_MILLIS, remaining))
                }
            }
        } catch (error: CancellationException) {
            cancelled = true
            throw error
        } finally {
            if (!cancelled) {
                val outcome = handoffOutcomeFor(pending, confirmed, Instant.now())?.let {
                    if (it.status == HandoffOutcomeStatus.UNCONFIRMED && !reachable) {
                        it.copy(reachable = false)
                    } else {
                        it
                    }
                }
                when {
                    outcome == null -> HandoffRuntime.clear()
                    outcome.status == HandoffOutcomeStatus.CONFIRMED -> {
                        confirmed?.let { recordConfirmedOnce(it) }
                        container.notifications.cancelReady()
                        container.notifications.showConfirmed()
                        HandoffRuntime.finish(outcome)
                    }
                    authFailed -> HandoffRuntime.clear()
                    else -> {
                        runCatching {
                            container.history.append(
                                HistoryKind.HANDOFF_UNCONFIRMED,
                                pending.courseName,
                                pending.roomCode,
                                "UNCONFIRMED"
                            )
                        }
                        container.notifications.showUnconfirmed(outcome.reachable)
                        HandoffRuntime.finish(outcome)
                    }
                }
                container.handoffJournal.clear()
                Log.i(
                    TAG,
                    "confirm done status=${outcome?.status?.name ?: "CLEARED"} " +
                        "sign=${outcome?.signId.orEmpty()} reachable=$reachable authFailed=$authFailed"
                )
                if (!finished) finish(cancelAll = authFailed)
            }
        }
    }

    private suspend fun notifyAuth(reason: String) {
        if (runCatching { container.settings.current().localMode }.getOrDefault(false)) {
            // Local mode never needs the cloud login; a stale failover token
            // must not surface as "登录已失效" or flip the runtime auth flag.
            MonitorRuntime.clearAuthExpired()
            return
        }
        MonitorRuntime.markAuthExpired()
        if (reason != REASON_DAILY) {
            container.notifications.showError("登录已失效", "打开Rin重新登录，监控会恢复")
        }
    }

    private fun armWatchdog(stopAt: Instant) {
        val now = Instant.now()
        val existing = watchdogAt
        if (existing != null && now.isBefore(existing)) return
        val tenMinutes = now.plusMillis(WATCHDOG_INTERVAL.inWholeMilliseconds)
        val afterStop = stopAt.plusSeconds(60)
        val at = if (tenMinutes.isBefore(afterStop)) tenMinutes else afterStop
        watchdogAt = at
        container.alarms.scheduleWake(at.toEpochMilli())
    }

    private fun delayUntilNextPoll(view: MonitorView, failures: Int, stopAt: Instant): Long {
        val base = if (view.errorCode.isEmpty()) {
            MonitorPolicy.delay(view.state, failures, rateLimited = false)
        } else if (view.errorCode == "RATE_LIMITED") {
            MonitorPolicy.delay(state = null, failures, rateLimited = true)
        } else {
            MonitorPolicy.delay(state = null, failures, rateLimited = false)
        }
        val remaining = Duration.between(Instant.now(), stopAt).toMillis()
        return minOf(base.inWholeMilliseconds, remaining)
    }

    private fun finish(cancelAll: Boolean) {
        if (finished) return
        finished = true
        disarmAutoWait()
        if (cancelAll) container.alarms.cancelAll() else container.alarms.cancelWake()
        MonitorRuntime.stopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "RinMonitor"
        private val MANUAL_DURATION = 30.minutes
        private val FALLBACK_DURATION = 15.minutes
        private val WATCHDOG_INTERVAL = 10.minutes
        private const val RETRY_AFTER_MILLIS = 30 * 60 * 1000L
        private const val CONFIRM_POLL_MILLIS = 5_000L

        fun intent(context: Context, reason: String): Intent =
            Intent(context, MonitorService::class.java).putExtra(EXTRA_REASON, reason)

        fun start(context: Context, reason: String) {
            ContextCompat.startForegroundService(context, intent(context, reason))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}

class MonitorAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_REMIND) {
            showClassReminder(context, intent)
            return
        }
        if (intent?.action == ACTION_REARM) {
            rearmReminders(context)
            return
        }
        val reason = when (intent?.action) {
            ACTION_WAKE -> REASON_WAKE
            ACTION_SCHEDULE -> intent.getStringExtra(EXTRA_REASON) ?: REASON_DAILY
            else -> return
        }
        try {
            MonitorService.start(context, reason)
        } catch (error: Exception) {
            // Android 12+ may refuse a background FGS start, e.g. when the
            // exact-alarm permission is missing and the alarm is inexact.
            Log.w(TAG, "FGS start blocked type=${error.javaClass.simpleName} reason=$reason")
            val container = AppContainer.get(context)
            container.notifications.ensureChannels()
            container.notifications.showError("监控没起来", "系统限制了后台启动，打开Rin手动检查")
            container.alarms.scheduleAt(System.currentTimeMillis() + RETRY_MILLIS, REASON_WINDOW)
        }
    }

    /**
     * Alarm-only reminder path: no foreground service, no network poll. The
     * dedupe key was persisted at schedule time, so a late (inexact) alarm
     * still cannot double-notify a class the service already reminded.
     */
    private fun showClassReminder(context: Context, intent: Intent) {
        val course = intent.getStringExtra(EXTRA_REMIND_COURSE).orEmpty()
        val location = intent.getStringExtra(EXTRA_REMIND_LOCATION).orEmpty()
        val key = intent.getStringExtra(EXTRA_REMIND_KEY).orEmpty()
        if (course.isBlank() || key.isBlank()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = AppContainer.get(context)
                val settings = container.settings.current()
                if (settings.lastRemindedClassKey == key) return@launch
                container.notifications.ensureChannels()
                container.notifications.showClassReminder(course, location)
                container.settings.setLastRemindedClassKey(key)
                runCatching {
                    container.history.append(HistoryKind.CLASS_REMINDER, course, "", "alarm")
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Self-healing loop: every morning before the first class, re-arm today's
     * reminders from the cached week (no app launch, no monitor), then schedule
     * tomorrow's re-arm. When the cache is from another day/week, one read-only
     * JWXT refresh pulls the current week so the chain also survives week
     * boundaries while the user never opens the app (needs stored credentials).
     */
    private fun rearmReminders(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = AppContainer.get(context)
                val todayStr = java.time.LocalDate.now(ClassWindowPlanner.schoolZone).toString()
                var overview = runCatching {
                    container.localCache.read(
                        "schedule_week0",
                        top.sykesp42.rin.data.ApiScheduleOverview.serializer()
                    )?.second
                }.getOrNull()
                val coversToday = overview?.let { cached ->
                    cached.date == todayStr || cached.days.any { it.date == todayStr }
                } == true
                val settings = runCatching { container.settings.current() }.getOrNull()
                var touchedSession = false
                if (!coversToday && settings != null) {
                    val fresh = runCatching {
                        container.withJwxt(settings) { api -> api.overview() }
                    }.getOrNull()
                    if (fresh != null && fresh.days.isNotEmpty()) {
                        runCatching {
                            container.localCache.write(
                                "schedule_week0",
                                top.sykesp42.rin.data.ApiScheduleOverview.serializer(),
                                fresh
                            )
                        }
                        overview = fresh
                        touchedSession = true
                    }
                }
                // The timetable already covers today: still touch the JWXT
                // session once a day so a headless user does not meet an
                // expired cookie at the next class. No-op without credentials.
                if (!touchedSession && settings?.localMode == true) {
                    runCatching { container.keepJwxtSession(settings) }
                }
                if (overview != null) {
                    runCatching { container.classReminders.reschedule(overview!!) }
                }
                runCatching { top.sykesp42.rin.widget.RinWidgets.refreshAll(context) }
                container.alarms.scheduleDailyRearm(nextDailyRearm(Instant.now()).toEpochMilli())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "RinAlarm"
        private const val RETRY_MILLIS = 15 * 60 * 1000L
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val container = AppContainer.get(context)
                val settings = container.settings.current()
                SessionKeeper.sync(context, settings.localMode && settings.monitorEnabled)
                val hasLocal = container.localJwxtStore.load() != null
                val hasCloud = container.sessionStore.load() != null
                // Class reminders are alarm-only and must survive reboots even
                // when 后台监控 is off, so they are re-armed before the monitor
                // gate below.
                val overview = runCatching { container.monitorApi(settings)?.schedule() }.getOrNull()
                overview?.let { todayOverview ->
                    if (todayOverview.today.isNotEmpty()) {
                        runCatching { container.classReminders.reschedule(todayOverview) }
                    }
                }
                // The daily self-heal chain must survive reboots even on days
                // with no classes (reschedule() only arms its re-arm alarm when
                // the overview covers today), so re-arm it unconditionally.
                runCatching {
                    container.alarms.scheduleDailyRearm(nextDailyRearm(Instant.now()).toEpochMilli())
                }
                if (!shouldResumeMonitor(settings, hasLocal, hasCloud)) return@launch
                // Rebooting mid-class must not lose the window: resume now when
                // the timetable says a class is running, otherwise ask the
                // service to re-plan in a minute (it schedules the next alarm).
                val now = Instant.now()
                val plan = overview?.let { ClassWindowPlanner.plan(it.today, now) }
                if (plan?.current != null) {
                    runCatching { MonitorService.start(context, REASON_WINDOW) }
                        .onFailure {
                            container.alarms.scheduleAt(
                                System.currentTimeMillis() + BOOT_SYNC_DELAY_MILLIS,
                                REASON_WINDOW
                            )
                        }
                } else {
                    container.alarms.scheduleAt(
                        System.currentTimeMillis() + BOOT_SYNC_DELAY_MILLIS,
                        REASON_DAILY
                    )
                }
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        private const val BOOT_SYNC_DELAY_MILLIS = 60_000L
    }
}

/**
 * Persistence gate for the reboot path: the old check required a cloud server
 * URL, so local-mode users silently lost monitoring after every reboot. Pure
 * and internal so the JVM tests can pin both modes.
 */
internal fun shouldResumeMonitor(
    settings: AppSettings,
    hasLocalJwxtSession: Boolean,
    hasCloudSession: Boolean
): Boolean {
    if (!settings.monitorEnabled) return false
    return if (settings.localMode) {
        hasLocalJwxtSession || settings.manualCode.isNotBlank()
    } else {
        hasCloudSession
    }
}
