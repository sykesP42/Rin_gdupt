package top.sykesp42.rin.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import kotlinx.coroutines.delay
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.InternalGate
import top.sykesp42.rin.data.HistoryEvent
import top.sykesp42.rin.data.TodayFrameStore
import top.sykesp42.rin.data.rosterNameError
import top.sykesp42.rin.data.withManualEntries
import top.sykesp42.rin.domain.SignAccessLevel
import top.sykesp42.rin.domain.State
import top.sykesp42.rin.handoff.HandoffActivity
import top.sykesp42.rin.handoff.HandoffRuntime
import top.sykesp42.rin.handoff.handoffOutcomeText
import top.sykesp42.rin.handoff.pendingRemainingSeconds
import top.sykesp42.rin.jwxt.JwxtLoginActivity
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.components.LocalCourseOverrides
import top.sykesp42.rin.ui.components.PageActionBar
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.RinMasthead
import top.sykesp42.rin.ui.components.SuiAlertDialog
import top.sykesp42.rin.ui.components.RinSheet
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.pet.PetEvent
import top.sykesp42.rin.ui.pet.PetPerch
import top.sykesp42.rin.ui.RoomShortcut
import top.sykesp42.rin.ui.TodayUiState
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.formatDateLine
import top.sykesp42.rin.ui.schedule.clockToMinutes
import top.sykesp42.rin.ui.schedule.coursesOnDate
import top.sykesp42.rin.ui.schedule.parseExamTime
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.schedule.sectionStart
import top.sykesp42.rin.ui.schedule.schoolZone
import top.sykesp42.rin.monitor.MonitorView
import top.sykesp42.rin.monitor.OneTapAction
import top.sykesp42.rin.monitor.OneTapBlocker
import top.sykesp42.rin.monitor.OneTapReadiness
import top.sykesp42.rin.monitor.oneTapAction
import top.sykesp42.rin.monitor.oneTapReadiness
import top.sykesp42.rin.monitor.qrEligible
import top.sykesp42.rin.ui.schedule.todayAnchor
import top.sykesp42.rin.ui.schedule.todayAtSchoolClock
import top.sykesp42.rin.ui.schedule.todayEvents
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.ThemeMode
import top.sykesp42.rin.ui.today.CompanionAction
import top.sykesp42.rin.ui.today.RinCompanionCard
import top.sykesp42.rin.ui.today.RinCompanionProfile
import top.sykesp42.rin.ui.today.TodayCardId
import top.sykesp42.rin.ui.today.TodayCards
import top.sykesp42.rin.ui.today.companionLine
import kotlinx.coroutines.launch

private fun weekdayLabel(value: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(value - 1) { "—" }

private fun sectionStart(text: String): String {
    val raw = Regex("\\d+").find(text)?.value.orEmpty()
    val first = when {
        raw.length >= 4 && raw.length % 2 == 0 -> raw.take(2).toIntOrNull() ?: 1
        else -> raw.toIntOrNull() ?: 1
    }
    val minutes = sectionBounds[first]?.first ?: 8 * 60
    return "%02d:%02d".format(minutes / 60, minutes % 60)
}

/**
 * Today is one A2 paper page: masthead, sky hero, day timeline, one action dock
 * and a quiet footer. The details sheet owns the "why" information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: AppViewModel,
    themeMode: ThemeMode,
    avatarText: String,
    onToggleTheme: () -> Unit,
    onOpenSchedule: () -> Unit,
    minimal: Boolean = false,
    signOnly: Boolean = false,
    onOpenHistory: (() -> Unit)? = null,
    onOpenProfile: () -> Unit = {},
    onOpenSign: () -> Unit = {},
) {
    val today by vm.today.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val grades by vm.grades.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val signQr by vm.signQr.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val roomShortcuts by vm.roomShortcuts.collectAsStateWithLifecycle()
    val petWeather by vm.petWeather.collectAsStateWithLifecycle()
    val signAccess by vm.signAccess.collectAsStateWithLifecycle()
    val signPro = signAccess.proEnabled
    val reminders by vm.courseReminders.payload.collectAsStateWithLifecycle()
    val manual by vm.manualSchedule.payload.collectAsStateWithLifecycle()
    val campusNews by vm.campusNews.collectAsStateWithLifecycle()
    val appSettings = settings ?: AppSettings()
    val petEnabled = settings?.petEnabled == true
    val petContextAware = settings?.petContextAware == true
    val petWeatherEnabled = settings?.petWeatherEnabled == true
    val narrow = LocalConfiguration.current.screenWidthDp < 380
    val appearance = LocalVisualStyle.current
    val themeSpec = appearance.spec
    // Rin sits astride the cover rim, so a smaller body still reads clearly
    // without covering the theme illustration.
    val basePetSize = when {
        narrow -> 52.dp
        themeSpec.hasArt -> 58.dp
        else -> 64.dp
    }
    // The user's scale is capped against the live window width, so the sprite
    // cannot outgrow the cover no matter how far the slider is pushed.
    val petSize = (basePetSize * (settings?.petScale ?: 1f))
        .coerceIn(30.dp, LocalConfiguration.current.screenWidthDp.dp * 0.42f)
    val context = LocalContext.current
    // Spec 2.2: 24dp page inset, tightened to 20dp on narrow phones.
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp

    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(today.view?.serverTime, today.viewReceivedAt) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000)
        }
    }
    val serverNow = remember(today.view?.serverTime, today.viewReceivedAt, tick) {
        val server = today.view?.serverTime
        val received = today.viewReceivedAt
        // A persisted snapshot has no receipt timestamp, so its own time is not
        // "now"; fall back to the school clock instead of replaying cache time.
        if (server != null && received != null) {
            server.plusMillis(tick - received.toEpochMilli())
        } else {
            Instant.ofEpochMilli(tick)
        }
    }
    // The 60s handoff window is a local receipt; its deadline is stamped with
    // the device clock, so the countdown must not be measured against the
    // school clock (which can drift by minutes or, in mock runs, by days).
    val deviceNow = remember(tick) { Instant.ofEpochMilli(tick) }

    LaunchedEffect(Unit) {
        vm.ensureTodayLoaded()
        vm.ensureScheduleLoaded()
        vm.ensureCampusNewsLoaded()
        vm.ensureSignAccessLoaded()
    }
    LaunchedEffect(signOnly) {
        if (signOnly) vm.loadHistory()
    }
    LaunchedEffect(signOnly, signAccess.status) {
        if (!signOnly || signAccess.status != SignAccessLevel.PENDING) return@LaunchedEffect
        while (true) {
            delay(60_000)
            vm.refreshSignAccess(force = true)
        }
    }
    LaunchedEffect(signOnly, minimal) {
        if (signOnly || minimal) return@LaunchedEffect
        while (true) {
            vm.refreshPetWeather()
            delay(30 * 60_000L)
        }
    }

    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var rosterOpen by rememberSaveable { mutableStateOf(false) }
    var qrOpen by rememberSaveable { mutableStateOf(false) }
    var quickRoomOpen by rememberSaveable { mutableStateOf(false) }
    var rosterNameOpen by rememberSaveable { mutableStateOf(false) }
    var openedCourseEvent by remember { mutableStateOf<ApiScheduleEvent?>(null) }
    var rosterNameDraft by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(signPro) {
        if (!signPro) {
            qrOpen = false
            quickRoomOpen = false
        }
    }
    LaunchedEffect(rosterNameOpen) {
        if (rosterNameOpen) rosterNameDraft = appSettings.rosterName
    }
    val rosterNameProblem = rosterNameError(rosterNameDraft)
    LaunchedEffect(minimal) {
        if (minimal) detailsOpen = false
    }
    LaunchedEffect(today.view?.signId) {
        rosterOpen = false
        qrOpen = false
    }
    LaunchedEffect(qrOpen) {
        if (qrOpen) vm.loadSignQr() else vm.clearSignQr()
    }
    LaunchedEffect(today.view == null) {
        if (today.view == null) detailsOpen = false
    }

    // Spec 4.2: the server overview date anchors "today"; the fallback is the school
    // clock, not the device timezone, so this page and the week grid can never disagree.
    // School clock is always "now" — a cached overview from yesterday
    // would incorrectly anchor the date line to yesterday.
    val todayDate = todayAtSchoolClock(serverNow)
    val overview = remember(schedule.overview, manual) {
        schedule.overview?.let { withManualEntries(it, manual) }
    }
    val todayClasses = remember(overview, todayDate) {
        overview?.let { todayEvents(it, todayDate) }.orEmpty()
    }
    val week = overview?.week ?: 0
    val tomorrowDate = todayDate.plusDays(1)
    val nowHour = remember(serverNow) { serverNow.atZone(schoolZone).hour }
    val schoolNowMinutes = remember(serverNow) { nowMinutesAt(serverNow) }
    val tomorrowCourses = remember(overview, grades.termCourses, tomorrowDate) {
        val overview = overview ?: return@remember emptyList<TomorrowCourse>()
        val date = tomorrowDate.toString()
        val day = overview.days.firstOrNull { it.date == date }
        val events = if (day != null) {
            day.events.map {
                TomorrowCourse(
                    name = it.courseName,
                    start = it.startTime.ifBlank { sectionStart(it.sectionText) },
                    location = it.location
                )
            }
        } else {
            coursesOnDate(grades.termCourses, tomorrowDate.dayOfWeek.value, overview.week + 1)
                .map {
                    TomorrowCourse(
                        name = it.courseName,
                        start = sectionStart(it.sectionText),
                        location = it.location
                    )
                }
        }
        events.sortedBy { it.start }
    }
    val currentEvent = todayClasses.firstOrNull { event ->
        val start = clockToMinutes(event.startTime) ?: return@firstOrNull false
        val end = clockToMinutes(event.endTime) ?: start
        schoolNowMinutes in start..end
    }
    val nextEvent = todayClasses
        .filter { (clockToMinutes(it.startTime) ?: 0) > schoolNowMinutes }
        .minByOrNull { clockToMinutes(it.startTime) ?: 0 }
    val firstEvent = todayClasses.minByOrNull { clockToMinutes(it.startTime) ?: 0 }
    val lastEnd = todayClasses.maxOfOrNull { clockToMinutes(it.endTime) ?: 0 } ?: -1
    val busiestWeekday = overview?.days?.maxByOrNull { it.events.size }?.weekday?.let { weekdayLabel(it) }
    val freeWeekdays = overview?.days?.count { it.weekday <= 5 && it.events.isEmpty() } ?: 0
    val nextExam = grades.exams.mapNotNull { exam ->
        val date = parseExamTime(exam.time).date ?: return@mapNotNull null
        val days = java.time.temporal.ChronoUnit.DAYS.between(todayDate, date)
        if (days < 0) null else Triple(exam, date, days)
    }.minByOrNull { it.third }
    val companionProfile = remember(
        todayClasses,
        currentEvent,
        nextEvent,
        firstEvent,
        nextExam,
        campusNews.items.firstOrNull(),
        manual.todos,
        petWeather,
        tomorrowCourses,
        todayDate,
        schoolNowMinutes,
        overview
    ) {
        RinCompanionProfile(
            todayCourses = todayClasses.size,
            firstCourse = firstEvent?.courseName.orEmpty(),
            firstStart = firstEvent?.startTime.orEmpty(),
            nextCourse = nextEvent?.courseName.orEmpty(),
            nextStart = nextEvent?.startTime.orEmpty(),
            nextRoom = nextEvent?.location.orEmpty(),
            nextDayCourse = tomorrowCourses.firstOrNull()?.name.orEmpty(),
            nextDayStart = tomorrowCourses.firstOrNull()?.start.orEmpty(),
            currentCourse = currentEvent?.courseName.orEmpty(),
            afterLast = todayClasses.isNotEmpty() && schoolNowMinutes > lastEnd,
            isWeekend = todayDate.dayOfWeek.value >= 6,
            minutesNow = schoolNowMinutes,
            week = week,
            termWeeks = grades.termCourses.maxOfOrNull { it.lastWeek } ?: week,
            examName = nextExam?.first?.courseName.orEmpty(),
            examDays = nextExam?.third?.toInt() ?: -1,
            newsTitle = campusNews.items.firstOrNull()?.title.orEmpty(),
            todoCount = manual.todos.count { !it.done && it.due == todayDate.toString() },
            busiestWeekday = busiestWeekday.orEmpty(),
            freeDays = freeWeekdays,
            temp = petWeather?.temperatureC?.let { "$it°" }.orEmpty(),
            humidity = petWeather?.humidity?.let { "$it%" }.orEmpty(),
            weather = petWeather?.weather ?: top.sykesp42.rin.data.PetWeather.UNKNOWN
        )
    }
    var companionSeed by remember { mutableIntStateOf(kotlin.random.Random.nextInt()) }
    LaunchedEffect(today.viewReceivedAt, todayDate, nowHour) {
        companionSeed = kotlin.random.Random.nextInt()
    }
    val companion = remember(companionProfile, companionSeed) {
        companionLine(companionProfile, companionSeed)
    }
    val frameStore = remember(context) { TodayFrameStore(context) }
    var frameNonce by remember { mutableIntStateOf(0) }
    val frameBitmap = remember(frameNonce) { frameStore.decode() }
    val scope = rememberCoroutineScope()
    val framePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (frameStore.install(uri)) {
                frameNonce++
                vm.notify("相框已更新")
            } else {
                vm.notify("照片读取失败，换一张试试")
            }
        }
    }
    val latestNewsUrl = campusNews.items.firstOrNull()?.url.orEmpty()
    // The cover already carries the week; the masthead keeps only the date so
    // 1.3x fonts on 360dp phones never truncate it behind the header actions.
    val dateLine = formatDateLine(todayDate)

    val pendingHandoff by HandoffRuntime.pending.collectAsStateWithLifecycle()
    val handoffOutcome by HandoffRuntime.outcome.collectAsStateWithLifecycle()
    val wechatInstalled = remember {
        runCatching { context.packageManager.getPackageInfo("com.tencent.mm", 0) }.isSuccess
    }
    val view = today.view
    val sessionExpired = appSettings.localMode && appSettings.jwxtNeedsLogin
    val rosterPending = view != null && view.signId.isNotBlank() && !view.roster.known
    val cacheAt = remember(schedule.cachedAt, today.viewReceivedAt) {
        schedule.cachedAt.takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: today.viewReceivedAt
    }
    val readiness = remember(
        view,
        appSettings.rosterName,
        appSettings.localMode,
        wechatInstalled,
        sessionExpired
    ) {
        // Cloud mode resolves "me" server-side, so it must not require the
        // locally typed roster name that only local matching uses.
        oneTapReadiness(
            view,
            appSettings.rosterName,
            wechatInstalled,
            requiresRosterName = appSettings.localMode,
            sessionExpired = sessionExpired
        )
    }
    // A cached poll from yesterday must not offer today's sign-in.
    val signViewIsToday = view?.serverTime?.let { todayAtSchoolClock(it) == todayDate } ?: true
    val readyView = view?.takeIf {
        signViewIsToday && it.state == State.READY &&
            it.roomCode.isNotBlank() && it.signId.isNotBlank()
    }
    var petCelebrateUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(view?.signId, view?.state) {
        if (view?.state == State.CONFIRMED) {
            petCelebrateUntil = System.currentTimeMillis() + 20_000
        }
    }
    val petNowMinutes = nowMinutesAt(serverNow)
    val petEvent = when {
        readyView != null -> PetEvent.SIGN_READY
        tick < petCelebrateUntil -> PetEvent.SIGN_SUCCESS
        todayClasses.any { event ->
            val start = clockToMinutes(event.startTime) ?: return@any false
            start > petNowMinutes && start - petNowMinutes <= 15
        } -> PetEvent.RUSH_TO_CLASS
        else -> PetEvent.NONE
    }
    val loadFailed = view == null && today.errorMessage.isNotBlank()
    val primaryAction = if (signPro) {
        oneTapAction(
            readiness,
            qrEligible = readyView != null && qrEligible(readyView)
        )
    } else {
        OneTapAction.CHECK
    }
    val primaryLabel = when {
        // Free mode can only refresh the roster, never start a sign-in.
        !signPro -> if (loadFailed) "重新读取" else "刷新名单"
        // A failed load is a read problem, not a reason to switch monitoring on.
        loadFailed -> "重新读取"
        primaryAction == OneTapAction.HANDOFF -> if (signOnly) "一键签到" else "在微信中打开"
        primaryAction == OneTapAction.OPEN_SETTINGS -> "填写名单姓名"
        primaryAction == OneTapAction.SHOW_QR -> "查看签到二维码"
        primaryAction == OneTapAction.RECONNECT -> "重连教务"
        else -> if (appSettings.monitorEnabled) "检查签到" else "开启后台监控"
    }
    val openJwxtReconnect: () -> Unit = {
        context.startActivity(JwxtLoginActivity.intent(context))
    }
    val stopAt = today.stopAt
    val lastCheckedAt = today.viewReceivedAt
    // Minimal mode hides the TodayFooter, so the waiting/not-running line keeps
    // the actual last-check receipt time here instead; running mode already carries
    // its own detail and the normal-mode footer prints the clock, never both.
    val monitorDetail = when {
        today.running && appSettings.monitorEnabled && stopAt != null ->
            "运行中 · 本次至 ${formatClock(stopAt)}"
        today.running && appSettings.monitorEnabled -> "运行中 · 课堂窗口内检查"
        minimal -> lastCheckedAt?.let { "最近检查 ${formatClock(it)}" } ?: "等待首次检查"
        else -> null
    }
    val monitorDetailRequiresEnabled = today.running && appSettings.monitorEnabled
    val onPrimary: () -> Unit = {
        if (!signPro) {
            vm.refreshToday()
        } else if (loadFailed) {
            vm.refreshToday()
        } else {
            when (primaryAction) {
                OneTapAction.HANDOFF -> {
                    val ready = readyView
                    if (ready != null) {
                        context.startActivity(
                            HandoffActivity.intent(
                                context,
                                ready.roomCode,
                                ready.signId,
                                ready.courseName
                            )
                        )
                    } else {
                        vm.refreshToday()
                    }
                }
                OneTapAction.OPEN_SETTINGS -> rosterNameOpen = true
                OneTapAction.SHOW_QR -> qrOpen = true
                OneTapAction.RECONNECT -> openJwxtReconnect()
                OneTapAction.CHECK ->
                    if (appSettings.monitorEnabled) vm.refreshToday() else vm.enableMonitoring()
            }
        }
    }

    PaperPage(
        skyWash = true,
        ambientSpec = if (minimal) null else themeSpec
    ) {
        PullToRefreshBox(
            // Initial loading is shown by the hero pill; the pull indicator is
            // reserved for a refresh that already has content on screen.
            isRefreshing = today.loading && today.view != null,
            onRefresh = vm::refreshToday,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = pageInset,
                    end = pageInset,
                    top = 8.dp,
                    bottom = 92.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item(key = "masthead") {
                    RinMasthead(
                        onAvatarClick = onOpenProfile,
                        dateLine = if (signOnly) "本人签到进度" else dateLine,
                        title = if (signOnly) "签到" else "Rin",
                        avatarText = avatarText,
                        onRefresh = vm::refreshToday,
                        onTheme = onToggleTheme,
                        compact = minimal
                    )
                }
                if (sessionExpired || schedule.fromCache || rosterPending) {
                    item(key = "source-status") {
                        TodaySourceStrip(
                            sessionExpired = sessionExpired,
                            fromCache = schedule.fromCache,
                            cachedAt = cacheAt,
                            rosterPending = rosterPending
                        )
                    }
                }
                if (!signOnly && !minimal) {
                    item(key = "mood-cover") {
                        Box {
                            TodayMoodCover(
                                spec = themeSpec,
                                dateLine = dateLine,
                                week = week,
                                // -1 means the schedule itself is unknown, which is
                                // not the same as a day with zero classes.
                                courseCount = if (schedule.overview != null) {
                                    todayClasses.size
                                } else {
                                    -1
                                },
                                weather = petWeather
                            )
                            PetPerch(
                                enabled = petEnabled,
                                contextAware = petContextAware,
                                weather = petWeather.takeIf { petWeatherEnabled },
                                event = petEvent,
                                motion = appSettings.animationsEnabled,
                                hero = themeSpec.hero,
                                artFocus = themeSpec.artFocus,
                                petSize = petSize,
                                offsetX = settings?.petOffsetX ?: 0f,
                                offsetY = settings?.petOffsetY ?: 0f,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                }
                if (!signOnly && !minimal && InternalGate.isOn) {
                    item(key = "sign-chat") {
                        TodaySignChatCard(
                            view = view,
                            loadFailed = loadFailed,
                            sessionExpired = sessionExpired,
                            ready = readyView != null,
                            hasCoursesToday = overview?.let { todayClasses.isNotEmpty() },
                            viewIsToday = signViewIsToday,
                            signPro = signPro,
                            onOpenSign = onOpenSign
                        )
                    }
                    for (card in TodayCards.enabled(appSettings)) {
                        when (card) {
                            TodayCardId.COMPANION -> item(key = "card-companion") {
                                RinCompanionCard(
                                    line = companion,
                                    onAction = { action ->
                                        when (action) {
                                            CompanionAction.SCHEDULE -> onOpenSchedule()
                                            CompanionAction.SIGN -> onOpenSign()
                                            CompanionAction.NEWS -> if (latestNewsUrl.isNotBlank()) {
                                                runCatching {
                                                    context.startActivity(
                                                        android.content.Intent(
                                                            android.content.Intent.ACTION_VIEW,
                                                            android.net.Uri.parse(latestNewsUrl)
                                                        )
                                                    )
                                                }
                                            }
                                            CompanionAction.NONE -> Unit
                                        }
                                    }
                                )
                            }
                            TodayCardId.WEATHER -> item(key = "card-weather") {
                                TodayHourlyWeatherCard(
                                    snapshot = petWeather,
                                    today = todayDate,
                                    nowHour = nowHour
                                )
                            }
                            TodayCardId.WEATHER_DENSE -> item(key = "card-weather-dense") {
                                TodayDenseWeatherCard(
                                    snapshot = petWeather,
                                    today = todayDate,
                                    nowHour = nowHour
                                )
                            }
                            TodayCardId.WEATHER_DAY -> item(key = "card-weather-day") {
                                TodayWeatherDayCard(
                                    snapshot = petWeather,
                                    today = todayDate
                                )
                            }
                            TodayCardId.TOMORROW -> if (nowHour >= 22) item(key = "card-tomorrow") {
                                TomorrowPreviewCard(
                                    tomorrow = tomorrowDate,
                                    courses = tomorrowCourses,
                                    weather = petWeather,
                                    reminders = reminders.reminders,
                                    todos = manual.todos
                                )
                            }
                            TodayCardId.TIMELINE -> if (todayClasses.isNotEmpty()) item(key = "card-timeline") {
                                TodayTimeline(
                                    events = todayClasses,
                                    nowMinutes = schoolNowMinutes,
                                    onCourseTap = { event ->
                                        openedCourseEvent = event
                                        vm.openCourseDetail(
                                            event.courseName,
                                            event.location,
                                            occurrenceDate = todayDate.toString(),
                                            occurrenceStart = event.startTime,
                                            occurrenceEnd = event.endTime
                                        )
                                    }
                                )
                            }
                            TodayCardId.MEMO -> item(key = "card-memo") {
                                TodayMemoCard(
                                    todos = manual.todos,
                                    today = todayDate.toString(),
                                    onAdd = { title ->
                                        vm.addManualTodo(
                                            top.sykesp42.rin.data.ManualTodo(
                                                id = java.util.UUID.randomUUID().toString(),
                                                title = title,
                                                due = todayDate.toString()
                                            )
                                        )
                                    },
                                    onToggle = vm::toggleManualTodo,
                                    onRemove = vm::removeManualTodo,
                                    onClearDone = {
                                        manual.todos
                                            .filter { it.done && it.due == todayDate.toString() }
                                            .forEach { vm.removeManualTodo(it.id) }
                                    }
                                )
                            }
                            TodayCardId.EXAM_COUNTDOWN -> item(key = "card-exam") {
                                ExamCountdownCard(grades.exams, todayDate)
                            }
                            TodayCardId.CAMPUS_NEWS -> item(key = "card-news") {
                                CampusNewsCard(campusNews.items) { url ->
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url)
                                            )
                                        )
                                    }
                                }
                            }
                            TodayCardId.QUICK_LINKS -> item(key = "card-links") {
                                QuickLinksCard(
                                    settings = appSettings,
                                    newsUrl = latestNewsUrl,
                                    onSchedule = onOpenSchedule,
                                    onSign = onOpenSign
                                )
                            }
                            TodayCardId.FILE_HUB -> item(key = "card-files") {
                                FileHubCard(onMessage = vm::notify)
                            }
                            TodayCardId.MINI_DAY -> item(key = "card-mini-day") {
                                MiniTimetableDayCard(overview, todayDate, onOpenSchedule)
                            }
                            TodayCardId.MINI_WEEK -> item(key = "card-mini-week") {
                                MiniTimetableWeekCard(overview, onOpenSchedule)
                            }
                            TodayCardId.MINI_TABLE -> item(key = "card-mini-table") {
                                MiniTimetableTableCard(
                                    overview = overview,
                                    today = todayDate,
                                    nowMinutes = schoolNowMinutes,
                                    onOpen = onOpenSchedule
                                )
                            }
                            TodayCardId.RIN_SHOWCASE -> item(key = "card-showcase") {
                                RinShowcaseCard()
                            }
                            TodayCardId.PHOTO_FRAME -> item(key = "card-frame") {
                                PhotoFrameCard(
                                    bitmap = frameBitmap,
                                    onPick = {
                                        runCatching {
                                            framePicker.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        }.onFailure { vm.notify("这台设备没有可用的图片选择器") }
                                    },
                                    onClear = {
                                        frameStore.clear()
                                        frameNonce++
                                    }
                                )
                            }
                        }
                    }
                }
                val pendingForView = pendingHandoff?.takeIf {
                    it.signId == view?.signId && view?.state != State.CONFIRMED
                }
                if (signOnly && signPro && pendingForView != null) {
                    item(key = "pending-handoff") {
                        val remaining = pendingRemainingSeconds(pendingForView.deadline, deviceNow)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (remaining > 0) {
                                    "已发起签到，等待名单确认 · 剩余 ${remaining}s"
                                } else {
                                    "确认窗口已结束，刷新名单查看结果"
                                },
                                color = LocalSuiSignColors.current.textSecondary,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = vm::refreshToday) {
                                Text("刷新名单")
                            }
                        }
                    }
                }
                val outcomeText = handoffOutcomeText(handoffOutcome, view?.signId.orEmpty())
                if (signOnly && signPro && outcomeText != null) {
                    item(key = "handoff-outcome") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = outcomeText.text,
                                color = if (outcomeText.positive) {
                                    LocalSuiSignColors.current.signal
                                } else {
                                    LocalSuiSignColors.current.warning
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (!outcomeText.positive) {
                                TextButton(onClick = vm::refreshToday) {
                                    Text("刷新名单")
                                }
                            }
                        }
                    }
                }
                if (today.errorMessage.isNotBlank()) {
                    item(key = "error") {
                        TodayErrorRow(
                            today.errorMessage,
                            onRetry = vm::refreshToday,
                            showAction = view != null
                        )
                    }
                }
                if (signOnly && !signPro) {
                    item(key = "sign-access-mode") {
                        TodayAccessModeStrip(signAccess)
                    }
                }
                if (signOnly) {
                    item(key = "sign-pass") {
                        SignPass(
                            today = today,
                            serverNow = serverNow,
                            monitorEnabled = appSettings.monitorEnabled,
                            readiness = readiness,
                            wechatInstalled = wechatInstalled,
                            sessionExpired = sessionExpired,
                            primaryLabel = primaryLabel,
                            onPrimary = onPrimary,
                            onMonitorChange = { next ->
                                if (next) vm.enableMonitoring() else vm.disableMonitoring()
                            },
                            monitorDetail = monitorDetail,
                            monitorDetailRequiresEnabled = monitorDetailRequiresEnabled,
                            onShowDetails = { detailsOpen = true },
                            onClearManual = vm::clearManualCode,
                            onOpenRoster = { rosterOpen = true },
                            onRefresh = vm::refreshToday,
                            signPro = signPro
                        )
                    }
                    // The QR is a permanent destination: it must exist before a
                    // sign goes live, and explain itself when none is available.
                    val qrView = view?.takeIf { it.signId.isNotBlank() && it.roster.known }
                    val qrReady = qrView != null && qrEligible(qrView)
                    val qrStatus = when {
                        qrView == null -> "暂无签到"
                        qrReady -> if (qrView.qrType == "STATIC") "固定码" else "自动刷新"
                        else -> "还没准备好"
                    }
                    // The primary card already opens the QR when that is the
                    // only available path; a second "查看" row right below it
                    // would be the same action twice.
                    if (signPro && primaryAction != OneTapAction.SHOW_QR) {
                        item(key = "qr-entry") {
                            TodayQrEntry(
                                status = qrStatus,
                                ready = qrReady,
                                onOpen = { qrOpen = true }
                            )
                        }
                    }
                }
                val rosterView = view?.takeIf {
                    it.signId.isNotBlank() && it.roster.known
                }
                if (signOnly) {
                    if (rosterView != null) {
                        item(key = "roster") {
                            TodayRosterCard(
                                view = rosterView,
                                lastCheckedAt = lastCheckedAt,
                                onOpen = { rosterOpen = true },
                                onRefresh = vm::refreshToday
                            )
                        }
                    } else {
                        val cachedToday = today.cachedRoster?.takeIf { snapshot ->
                            runCatching { Instant.parse(snapshot.savedAt) }.getOrNull()
                                ?.let { todayAtSchoolClock(it) == todayAtSchoolClock(serverNow) } == true
                        }
                        if (cachedToday != null) {
                            item(key = "roster-cache") {
                                TodayCachedRosterCard(
                                    snapshot = cachedToday,
                                    onRefresh = vm::refreshToday
                                )
                            }
                        }
                    }
                }
                if (signOnly && signPro) {
                    item(key = "actions") {
                        TodayActions(
                            readiness = readiness,
                            view = view,
                            wechatInstalled = wechatInstalled,
                            sessionExpired = sessionExpired,
                            showReadiness = false,
                            primaryLabel = primaryLabel,
                            onPrimary = onPrimary,
                            monitorEnabled = appSettings.monitorEnabled,
                            onMonitorChange = { next ->
                                if (next) vm.enableMonitoring() else vm.disableMonitoring()
                            },
                            monitorDetail = monitorDetail,
                            monitorDetailRequiresEnabled = monitorDetailRequiresEnabled
                        )
                    }
                }
                if (signOnly && signPro && !minimal) {
                    item(key = "quick-room") {
                        TodayQuickRoomRow(
                            manualCode = today.manualCode,
                            onClick = { quickRoomOpen = true }
                        )
                    }
                }
                if (signOnly && signPro) {
                    item(key = "sign-note") {
                        Text(
                            text = "打开链接不等于签到成功",
                            color = LocalSuiSignColors.current.textSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (signOnly) {
                    item(key = "sign-footer") {
                        TodayFooter(
                            running = today.running,
                            serverNow = serverNow,
                            lastCheckedAt = lastCheckedAt,
                            monitorEnabled = appSettings.monitorEnabled
                        )
                    }
                }
                if (signOnly && signPro) {
                    item(key = "sign-recent") {
                        RecentSignRecords(history.events)
                    }
                }
                if (signOnly && signPro && onOpenHistory != null) {
                    item(key = "history-entry") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenHistory)
                                .heightIn(min = 48.dp)
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "签到记录",
                                color = LocalSuiSignColors.current.text,
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "本机记录",
                                color = LocalSuiSignColors.current.textSecondary,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "查看",
                                color = LocalSuiSignColors.current.text,
                                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
                if (minimal) {
                    // Live pick: the class in session, or the next one coming.
                    val nowMinutes = nowMinutesAt(serverNow)
                    val nextClass = todayClasses
                        .sortedBy { it.startTime }
                        .firstOrNull { event ->
                            val start = clockToMinutes(event.startTime)
                            val end = clockToMinutes(event.endTime)
                            (start != null && nowMinutes >= start && (end == null || nowMinutes <= end)) ||
                                (start != null && nowMinutes < start)
                        }
                    if (nextClass != null) {
                        item(key = "minimal-next") { MinimalNextClass(nextClass) }
                    }
                } else if (!signOnly) {
                    item(key = "campus-clock") {
                        Text(
                            text = "校园时间 ${formatClock(serverNow)}",
                            color = LocalSuiSignColors.current.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }

    if (detailsOpen && !minimal) {
        view?.let { current ->
            TodayDetailsSheet(
                view = current,
                running = today.running,
                manualCode = today.manualCode,
                serverNow = serverNow,
                lastCheckedAt = lastCheckedAt,
                onDismiss = { detailsOpen = false },
                onClearManual = vm::clearManualCode,
                onOpenSchedule = onOpenSchedule
            )
        }
    }

    if (rosterOpen && !minimal) {
        view?.let { current ->
            TodayRosterSheet(
                view = current,
                lastCheckedAt = lastCheckedAt,
                onRefresh = vm::refreshToday,
                onDismiss = { rosterOpen = false }
            )
        }
    }

    if (signPro && quickRoomOpen && !minimal) {
        QuickRoomSheet(
            currentCode = today.manualCode,
            shortcuts = roomShortcuts,
            onUse = { code ->
                vm.useRoom(code)
                quickRoomOpen = false
            },
            onClear = {
                vm.clearManualCode()
                quickRoomOpen = false
            },
            onDismiss = { quickRoomOpen = false }
        )
    }

    if (signPro && qrOpen && !minimal) {
        view?.let { current ->
            TodayQrSheet(
                state = signQr,
                now = serverNow,
                onRefresh = vm::loadSignQr,
                onOpenWechat = {
                    qrOpen = false
                    context.startActivity(
                        HandoffActivity.intent(
                            context,
                            current.roomCode,
                            current.signId,
                            current.courseName
                        )
                    )
                },
                onDismiss = { qrOpen = false }
            )
        }
    }

    if (openedCourseEvent != null && grades.courseDetail.courseName.isNotBlank()) {
        CourseDetailSheet(
            detail = grades.courseDetail,
            currentWeek = schedule.overview?.week ?: 0,
            fallbackLocation = openedCourseEvent?.location.orEmpty(),
            fallbackSectionText = openedCourseEvent?.sectionText.orEmpty(),
            override = LocalCourseOverrides.current[grades.courseDetail.courseName]
                ?: top.sykesp42.rin.data.CourseOverride(),
            onOverride = { alias, color ->
                vm.setCourseOverride(grades.courseDetail.courseName, alias, color)
            },
            onDismiss = {
                openedCourseEvent = null
                vm.dismissCourseDetail()
            },
            onMonitor = { code ->
                vm.useRoom(code)
                openedCourseEvent = null
                vm.dismissCourseDetail()
            },
            onOpenFreeRooms = { _, _ ->
                openedCourseEvent = null
                vm.dismissCourseDetail()
                onOpenSchedule()
            },
            checklist = grades.courseDetail.checklist,
            onSaveChecklist = vm::saveChecklist,
            reminderPrefs = reminders.prefs,
            reminders = reminders.reminders,
            onSaveReminder = vm::saveCourseReminder,
            onDeleteReminder = vm::removeCourseReminder,
            signAccessEnabled = signPro
        )
    }

    if (rosterNameOpen) {
        SuiAlertDialog(
            onDismissRequest = { rosterNameOpen = false },
            title = { Text("填写名单姓名") },
            text = {
                Column {
                    Text(
                        "需与教务名单姓名一致",
                        color = LocalSuiSignColors.current.textSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rosterNameDraft,
                        onValueChange = { rosterNameDraft = it },
                        singleLine = true,
                        isError = rosterNameProblem != null,
                        label = { Text("名单姓名") },
                        supportingText = {
                            if (rosterNameProblem != null) Text(rosterNameProblem)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rosterNameProblem == null,
                    onClick = {
                        vm.saveRosterNameAndRefresh(rosterNameDraft.trim())
                        rosterNameOpen = false
                    }
                ) {
                    Text("保存并检查")
                }
            },
            dismissButton = {
                TextButton(onClick = { rosterNameOpen = false }) {
                    Text("取消", color = LocalSuiSignColors.current.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun RecentSignRecords(events: List<HistoryEvent>) {
    if (events.isEmpty()) return
    val colors = LocalSuiSignColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "最近记录",
                color = colors.text,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "本机记录",
                color = colors.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }
        events.sortedByDescending { it.at }.take(3).forEach { event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = formatClock(event.at),
                    color = colors.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Box(
                    Modifier
                        .padding(start = 9.dp, top = 7.dp)
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(dotColor(event.kind))
                )
                Column(modifier = Modifier.weight(1f).padding(start = 9.dp)) {
                    Text(
                        text = historyTitle(event.kind),
                        color = colors.text,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                    )
                    val meta = listOfNotNull(
                        historyBody(event.kind).takeIf { it.isNotBlank() },
                        event.courseName.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            color = colors.textSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border)
            )
        }
    }
}

@Composable
private fun SignPass(
    today: TodayUiState,
    serverNow: Instant,
    monitorEnabled: Boolean,
    readiness: OneTapReadiness,
    wechatInstalled: Boolean,
    sessionExpired: Boolean,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onMonitorChange: (Boolean) -> Unit,
    monitorDetail: String?,
    monitorDetailRequiresEnabled: Boolean,
    onShowDetails: () -> Unit,
    onClearManual: () -> Unit,
    onOpenRoster: () -> Unit,
    onRefresh: () -> Unit,
    signPro: Boolean
) {
    val colors = LocalSuiSignColors.current
    val view = today.view
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .padding(16.dp)
    ) {
        Text(
            text = "校园时间 ${formatClock(serverNow, schoolZone)}",
            color = colors.textSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp
        )
        TodayHero(
            today = today,
            serverNow = serverNow,
            monitorEnabled = monitorEnabled,
            minimal = false,
            onShowDetails = onShowDetails,
            onClearManual = onClearManual,
            signPro = signPro
        )
        // The action comes before the roster card: on a 900dp-tall phone the
        // roster details used to push the only sign button below the fold in
        // the middle of an eight-minute window.
        TodayActions(
            readiness = readiness,
            view = view,
            wechatInstalled = wechatInstalled,
            sessionExpired = sessionExpired,
            showReadiness = signPro,
            showPreflight = signPro,
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
            monitorEnabled = monitorEnabled,
            onMonitorChange = onMonitorChange,
            monitorDetail = monitorDetail,
            monitorDetailRequiresEnabled = monitorDetailRequiresEnabled,
            // On the dedicated sign page the action must survive 360dp at 1.3x:
            // preflight chips used to push it below the fold.
            actionFirst = true
        )
        if (view != null && view.signId.isNotBlank() && view.roster.known) {
            TodayRosterCard(
                view = view,
                lastCheckedAt = today.viewReceivedAt,
                onOpen = onOpenRoster,
                onRefresh = onRefresh
            )
        }
    }
}

@Composable
private fun TodayActions(
    readiness: OneTapReadiness,
    view: MonitorView?,
    wechatInstalled: Boolean,
    sessionExpired: Boolean,
    showReadiness: Boolean,
    showPreflight: Boolean = true,
    primaryLabel: String,
    onPrimary: () -> Unit,
    monitorEnabled: Boolean,
    onMonitorChange: (Boolean) -> Unit,
    monitorDetail: String?,
    monitorDetailRequiresEnabled: Boolean,
    actionFirst: Boolean = false
) {
    if (actionFirst) {
        PageActionBar(
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
            monitorEnabled = monitorEnabled,
            onMonitorChange = onMonitorChange,
            monitorDetail = monitorDetail,
            monitorDetailRequiresEnabled = monitorDetailRequiresEnabled
        )
        Spacer(Modifier.height(10.dp))
    }
    if (showPreflight && (view != null || sessionExpired)) {
        OneTapPreflight(
            view = view,
            wechatInstalled = wechatInstalled,
            sessionExpired = sessionExpired
        )
        Spacer(Modifier.height(10.dp))
    }
    if (showReadiness) {
        Text(
            text = readiness.message,
            color = when {
                readiness.ready -> LocalSuiSignColors.current.signal
                readiness.blocker == OneTapBlocker.WECHAT_MISSING ->
                    LocalSuiSignColors.current.warning
                else -> LocalSuiSignColors.current.textSecondary
            },
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
        )
    }
    if (!actionFirst) {
        Spacer(Modifier.height(10.dp))
        PageActionBar(
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
            monitorEnabled = monitorEnabled,
            onMonitorChange = onMonitorChange,
            monitorDetail = monitorDetail,
            monitorDetailRequiresEnabled = monitorDetailRequiresEnabled
        )
    }
}

@Composable
private fun TodayQuickRoomRow(manualCode: String, onClick: () -> Unit) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "手动教室码",
            color = colors.text,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (manualCode.isBlank()) "最近 / 收藏教室" else "手动 $manualCode",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = "  ›",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickRoomSheet(
    currentCode: String,
    shortcuts: List<RoomShortcut>,
    onUse: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    var code by rememberSaveable(currentCode) { mutableStateOf(currentCode.take(4)) }
    RinSheet(onDismiss = onDismiss) {
        Text("手动教室码", style = MaterialTheme.typography.titleLarge)
        Text(
            "只影响检查目标",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(4) },
            label = { Text("四位教室码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        if (shortcuts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "最近 / 收藏",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                shortcuts.forEach { shortcut ->
                    TextButton(onClick = { code = shortcut.code }) {
                        Text(
                            "${shortcut.name} · ${shortcut.code}",
                            color = colors.signal,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SuiPrimaryButton(
            text = if (code.length == 4) "用 $code 开始监控" else "输入四位码后开始",
            onClick = { if (code.length == 4) onUse(code) },
            enabled = code.length == 4,
            modifier = Modifier.fillMaxWidth()
        )
        if (currentCode.isNotBlank()) {
            TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 44.dp)) {
                Text("恢复自动识别", color = colors.textSecondary)
            }
        }
    }
}
