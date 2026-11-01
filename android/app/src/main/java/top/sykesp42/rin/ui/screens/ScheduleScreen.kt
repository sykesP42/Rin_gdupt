package top.sykesp42.rin.ui.screens

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.data.JwxtGrade
import top.sykesp42.rin.data.withManualEntries
import top.sykesp42.rin.ui.Identity
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.components.EmptyState
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.RinMasthead
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.components.SuiSurfaceCard
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.formatDateLine
import top.sykesp42.rin.ui.schedule.calendarMonthDays
import top.sykesp42.rin.ui.schedule.calendarMonthsOf
import top.sykesp42.rin.ui.schedule.calendarTodayMonth
import top.sykesp42.rin.ui.schedule.calendarDaysByDate
import top.sykesp42.rin.ui.schedule.coursesOnDate
import top.sykesp42.rin.ui.schedule.DayCourse
import top.sykesp42.rin.ui.schedule.displayedToday
import top.sykesp42.rin.ui.schedule.examItems
import top.sykesp42.rin.ui.schedule.parseExamTime
import top.sykesp42.rin.ui.schedule.refreshWeekTarget
import top.sykesp42.rin.ui.schedule.shouldShowReturnToCurrent
import top.sykesp42.rin.data.groupNewsByDate
import top.sykesp42.rin.data.JwxtCalendarDay
import top.sykesp42.rin.ui.schedule.weekNumberFromLabel
import top.sykesp42.rin.ui.schedule.todayAtSchoolClock
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.courseAccent
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.theme.ThemeMode
import top.sykesp42.rin.ui.schedule.buildSemesterReport
import top.sykesp42.rin.ui.schedule.semesterSlots
import top.sykesp42.rin.ui.schedule.ExportPalette
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.schedule.ShareFormat
import top.sykesp42.rin.ui.schedule.ShareStyle

/**
 * A2 day-first schedule. The masthead and flat text tabs sit on one paper page;
 * segment 0 is a dated day timeline, while 空教室 / 考试 / 成绩 reuse their data
 * through paper sections. Course detail and manual room are transient sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    vm: AppViewModel,
    themeMode: ThemeMode,
    avatarText: String,
    onToggleTheme: () -> Unit,
    onGoToday: () -> Unit = {},
    academicOnly: Boolean = false,
    initialSegment: Int = 1,
    onBack: (() -> Unit)? = null,
    onOpenProfile: () -> Unit = {},
) {
    val context = LocalContext.current
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shareContext = LocalContext.current
    val shareStyle = Identity.shareStyle(settings)
    val shareFormat = ShareFormat.entries.firstOrNull { it.name.equals(settings?.shareFormat, true) }
        ?: ShareFormat.COLUMNS
    val shareOwner = Identity.shareOwner(settings, avatarText)
    val appVersion = remember {
        runCatching {
            shareContext.packageManager.getPackageInfo(shareContext.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val today by vm.today.collectAsStateWithLifecycle()
    val gradesState by vm.grades.collectAsStateWithLifecycle()
    val courseOverrides by vm.courseOverrides.collectAsStateWithLifecycle()
    val catalog by vm.classroomCatalog.collectAsStateWithLifecycle()
    val calendar by vm.calendar.collectAsStateWithLifecycle()
    val campusNews by vm.campusNews.collectAsStateWithLifecycle()
    val colors = LocalSuiSignColors.current
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp
    val jwxtNeedsLogin = settings?.jwxtNeedsLogin == true
    val needsReconnect = jwxtNeedsLogin || gradesState.authExpired
    fun reconnectJwxt() {
        runCatching {
            context.startActivity(top.sykesp42.rin.jwxt.JwxtLoginActivity.intent(context))
        }.onFailure { vm.notify("打不开教务登录") }
    }
    var segment by rememberSaveable {
        mutableIntStateOf(if (academicOnly) initialSegment.coerceIn(1, 4) else 0)
    }
    var termView by rememberSaveable { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }
    // 周视图默认形态来自设置（表格/列表、全屏/内联）；首次组合后仍由本地状态接管。
    var weekView by rememberSaveable { mutableStateOf(false) }
    var tableMode by rememberSaveable(settings?.weekDefaultTable) {
        mutableStateOf(settings?.weekDefaultTable ?: false)
    }
    var fullscreenWeek by rememberSaveable(settings?.weekDefaultFullscreen) {
        mutableStateOf(false)
    }
    var openedCourseEvent by remember { mutableStateOf<ApiScheduleEvent?>(null) }
    var baseWeek by rememberSaveable { mutableIntStateOf(0) }
    var dayFocus by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(segment) {
        when (segment) {
            1 -> vm.ensureClassroomCatalogLoaded()
            2 -> {
                vm.ensureCalendarLoaded()
                vm.ensureCampusNewsLoaded()
                vm.loadTermCourses()
            }
            3, 4 -> if (!gradesState.loading &&
                gradesState.grades.isEmpty() &&
                gradesState.exams.isEmpty() &&
                gradesState.error.isBlank()
            ) {
                vm.loadGrades()
            }
        }
    }

    LaunchedEffect(Unit) { vm.ensureScheduleLoaded() }

    val manual by vm.manualSchedule.payload.collectAsStateWithLifecycle()
    val reminders by vm.courseReminders.payload.collectAsStateWithLifecycle()
    val overview = remember(schedule.overview, manual) {
        schedule.overview?.let { withManualEntries(it, manual) }
    }
    // The first week the page shows is the current week. When loadSchedule's
    // offline cache path cannot fill schedule.currentWeek, this keeps the
    // "回到本周" capsule and viewed-week refresh coherent.
    LaunchedEffect(overview?.week) {
        if (baseWeek == 0) {
            overview?.week?.takeIf { it > 0 }?.let { baseWeek = it }
        }
    }
    val effectiveCurrentWeek = schedule.currentWeek.takeIf { it > 0 } ?: baseWeek
    val viewedToday = overview?.let { displayedToday(it.days.map { day -> day.date }) }
    // Calendar rings and exam countdowns answer "today" too, so a cached
    // overview from another week must not drag them back to its snapshot date.
    val schoolToday = todayAtSchoolClock()
    val examRows = remember(gradesState.exams, schoolToday) {
        examItems(gradesState.exams, schoolToday)
    }
    val calDayMap = remember(calendar.days) { calendarDaysByDate(calendar.days) }
    val calMonths = remember(calDayMap) { calendarMonthsOf(calDayMap) }
    val calMarks = remember(manual.dayMarks) { manual.dayMarks.associateBy { it.date } }
    val calExamsByDate = remember(gradesState.exams) {
        gradesState.exams.mapNotNull { exam -> parseExamTime(exam.time).date?.let { it to exam } }
            .groupBy({ it.first }, { it.second })
    }
    val calNewsByDate = remember(campusNews.items) { groupNewsByDate(campusNews.items) }
    var openedCalDay by remember { mutableStateOf<JwxtCalendarDay?>(null) }
    val listState = rememberLazyListState()
    var calScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(segment, calendar.days) {
        if (segment == 2 && !calScrolled && calendar.days.isNotEmpty()) {
            val todayMonth = calendarTodayMonth(calDayMap, schoolToday)
            val idx = calMonths.indexOf(todayMonth)
            // Masthead and tabs are pinned above the list now, so the
            // months start at index 1 (0 is the calendar head).
            if (idx >= 0) listState.scrollToItem(1 + idx)
            calScrolled = true
        }
    }
    // The date and week already identify the page; the full term string is too
    // long for the masthead and used to ellipsize next to the action icons.
    // Only show a date when it belongs to the viewed week.
    val weekDateLine = overview?.let {
        listOfNotNull(
            viewedToday?.let(::formatDateLine),
            "第 ${it.week} 周"
        ).joinToString(" · ")
    } ?: formatDateLine(todayAtSchoolClock())

    fun openWeb() {
        val url = settings?.serverUrl.orEmpty()
        if (url.isBlank()) {
            vm.notify("先在「我的」里登录")
            return
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { vm.notify("打不开浏览器") }
    }

    // Browsing the timetable is read-only. A tap opens the course information;
    // switching the live monitor target is an explicit action inside that sheet,
    // so scrolling an old week can never re-arm the wrong room.
    fun openCourse(event: ApiScheduleEvent) {
        openedCourseEvent = event
        val day = overview?.days?.firstOrNull { candidate ->
            candidate.events.any {
                it == event ||
                    (it.courseName == event.courseName &&
                        it.sectionText == event.sectionText &&
                        it.startTime == event.startTime)
            }
        }
        val occurrenceDate = day?.date?.takeIf { it.isNotBlank() } ?: run {
            // Manual-only schedules have no dated cache. Anchor the viewed week
            // to the school clock so the reminder still names a real day.
            if (day == null || overview == null) {
                ""
            } else {
                val today = todayAtSchoolClock()
                val currentMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                currentMonday
                    .plusWeeks((overview.week - effectiveCurrentWeek).toLong())
                    .plusDays((day.weekday - 1).toLong())
                    .toString()
            }
        }
        vm.openCourseDetail(
            event.courseName,
            event.location,
            occurrenceDate = occurrenceDate,
            occurrenceStart = event.startTime,
            occurrenceEnd = event.endTime
        )
    }

    /**
     * One day per swipe. With 跨周 off the week edge is a wall: the swipe is
     * swallowed instead of silently taking the reader to another week.
     */
    fun stepDayFocus(delta: Int) {
        val week = overview ?: return
        val from = dayFocus.takeIf { it in 1..7 } ?: initialDayFocus(week)
        val step = stepDayWithinWeek(from, delta, settings?.daySwipeCrossWeek ?: true) ?: return
        val (weekday, weekDelta) = step
        dayFocus = weekday
        if (weekDelta != 0) vm.switchWeek(weekDelta)
    }

    fun refreshCurrent() {
        vm.retryJwxtNow()
        when (segment) {
            0 -> {
                val viewedWeek = overview?.week ?: 0
                val target = refreshWeekTarget(viewedWeek, effectiveCurrentWeek)
                vm.loadSchedule(target)
            }
            1 -> vm.loadFreeRooms(
                gradesState.freeRoomDay.takeIf { it > 0 } ?: 1,
                gradesState.freeRoomSections.ifBlank { "0102" }
            )
            2 -> {
                vm.loadCalendar()
                vm.refreshCampusNews()
            }
            else -> vm.loadGrades()
        }
    }

    fun selectSegment(index: Int) {
        segment = index
        if (index == 2) {
            vm.ensureCalendarLoaded()
            vm.ensureCampusNewsLoaded()
            vm.loadTermCourses()
        }
        if (index >= 2 && !gradesState.loading && gradesState.grades.isEmpty() &&
            gradesState.exams.isEmpty() && gradesState.error.isBlank()
        ) {
            vm.loadGrades()
        }
        if (index == 1 && !gradesState.freeRoomLoading && gradesState.freeRooms.isEmpty()) {
            vm.loadFreeRooms(todayAtSchoolClock().dayOfWeek.value, "0102")
        }
    }

    PaperPage {
        PullToRefreshBox(
            isRefreshing = schedule.loading || gradesState.loading || gradesState.freeRoomLoading,
            onRefresh = ::refreshCurrent,
            modifier = Modifier.fillMaxSize()
        ) {
            if (segment == 0 && weekView && !termView && overview != null) {
                val weekHasData = hasScheduleData(overview)
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!tableMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = pageInset, end = pageInset, top = 8.dp)
                        ) {
                            RinMasthead(
                                onAvatarClick = onOpenProfile,
                                dateLine = weekDateLine,
                                title = "课表",
                                avatarText = avatarText,
                                onRefresh = ::refreshCurrent,
                                onTheme = onToggleTheme
                            )
                            Spacer(Modifier.height(20.dp))
                            if (schedule.error.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                ScheduleErrorRow(schedule.error, onRetry = ::refreshCurrent)
                            } else if (jwxtNeedsLogin) {
                                Spacer(Modifier.height(6.dp))
                                ScheduleErrorRow(
                                    cachedLabel(
                                        fromCache = true,
                                        cachedAt = schedule.cachedAt,
                                        needsLogin = true
                                    ),
                                    onRetry = { reconnectJwxt() },
                                    actionLabel = "重连"
                                )
                            }
                        }
                    }
                    ScheduleWeekView(
                        overview = overview,
                        onCourseTap = ::openCourse,
                        onClose = { weekView = false },
                        onSwipeWeek = vm::switchWeek,
                        onFullscreen = { fullscreenWeek = true },
                        tableMode = tableMode,
                        onToggleTableMode = { tableMode = !tableMode },
                        currentWeek = effectiveCurrentWeek,
                        onBackToCurrentWeek = { vm.switchWeek(effectiveCurrentWeek - overview.week) },
                        shareOwner = Identity.shareOwner(settings, avatarText),
                        shareStyle = Identity.shareStyle(settings),
                        appVersion = appVersion,
                        onShareStyleChange = { style -> vm.setShareStyle(style.name.lowercase()) },
                        shareFormat = shareFormat,
                        onShareFormatChange = { fmt -> vm.setShareFormat(fmt.name.lowercase()) },
                        modifier = Modifier.weight(1f),
                        emptyContent = if (!weekHasData) {
                            {
                                EmptyState(
                                    title = "这一周还没有课程",
                                    body = "左右滑切换周次"
                                )
                            }
                        } else {
                            null
                        }
                    )
                    // Cache/footer stays visible in the agenda view; table mode
                    // gives the space to the grid instead.
                    if (!tableMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = pageInset,
                                    end = pageInset,
                                    top = 6.dp,
                                    bottom = 8.dp
                                )
                        ) {
                            ScheduleFoot(
                                fromCache = schedule.fromCache,
                                cachedAt = schedule.cachedAt,
                                needsLogin = jwxtNeedsLogin
                            )
                        }
                    }
                }
            } else {
                // Pinned header: the calendar auto-scrolls to the current month
                // and the grades list runs long — the segment switcher must
                // never scroll out of reach. Mirrors the week-view branch.
                Column(modifier = Modifier.fillMaxSize().imePadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.canvas)
                        .padding(start = pageInset, end = pageInset, top = 8.dp)
                ) {
                    if (onBack != null) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "← 返回",
                                color = colors.text,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    RinMasthead(
                        dateLine = if (academicOnly) {
                            "空教室 · 校历 · 考试 · 成绩"
                        } else {
                            weekDateLine
                        },
                        title = if (academicOnly) "校务" else "课表",
                        avatarText = avatarText,
                        onRefresh = ::refreshCurrent,
                        onTheme = onToggleTheme
                    )
                    if (academicOnly) {
                        ScheduleTabs(
                            labels = listOf("空教室", "校历", "考试", "成绩"),
                            selected = segment - 1,
                            onSelect = { index -> selectSegment(index + 1) }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .daySwipe(
                        enabled = segment == 0 && !weekView && !termView,
                        onStep = ::stepDayFocus
                    ),
                contentPadding = PaddingValues(
                    start = pageInset,
                    end = pageInset,
                    top = 4.dp,
                    bottom = 92.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (segment) {
                    0 -> {
                        if (schedule.error.isNotBlank()) {
                            item(key = "schedule-error") {
                                ScheduleErrorRow(schedule.error, onRetry = ::refreshCurrent)
                            }
                        } else if (jwxtNeedsLogin) {
                            item(key = "jwxt-expired") {
                                ScheduleErrorRow(
                                    cachedLabel(
                                        fromCache = true,
                                        cachedAt = schedule.cachedAt,
                                        needsLogin = true
                                    ),
                                    onRetry = { reconnectJwxt() },
                                    actionLabel = "重连"
                                )
                            }
                        }
                        if (overview == null) {
                            item(key = "schedule-empty") {
                                if (schedule.loading) {
                                    ScheduleLoadingSurface()
                                } else {
                                    EmptyState(
                                        title = "课表还是空的",
                                        body = "连上教务后自动读取"
                                    )
                                }
                            }
                        } else {
                            if (schedule.loading) {
                                item(key = "schedule-progress") {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = colors.signal,
                                        trackColor = colors.border
                                    )
                                }
                            }
                            item(key = "week-nav") {
                                WeekNavigation(
                                    week = overview.week,
                                    termView = termView,
                                    showReturn = !termView && shouldShowReturnToCurrent(
                                        overview.week,
                                        effectiveCurrentWeek
                                    ),
                                    showWeekAction = hasScheduleData(overview),
                                    onPrev = { vm.switchWeek(-1) },
                                    onNext = { vm.switchWeek(1) },
                                    onReturn = { vm.loadSchedule() },
                                    onToggleTerm = {
                                        termView = !termView
                                        if (termView) vm.loadTermCourses()
                                    },
                                    onOpenWeek = {
                                        tableMode = false
                                        if (settings?.weekDefaultFullscreen == true) {
                                            fullscreenWeek = true
                                        } else {
                                            weekView = true
                                        }
                                    },
                                    onOpenTable = {
                                        tableMode = true
                                        if (settings?.weekDefaultFullscreen == true) {
                                            fullscreenWeek = true
                                        } else {
                                            weekView = true
                                        }
                                    }
                                )
                            }
                            if (termView) {
                                item(key = "semester-report") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SuiSecondaryButton(
                                            text = "学期总览",
                                            onClick = { reportOpen = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                        SuiSecondaryButton(
                                            text = "手动数据",
                                            onClick = { manualOpen = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                item(key = "term-overview") {
                                    TermOverview(
                                        courses = gradesState.termCourses,
                                        loading = gradesState.termLoading,
                                        currentWeek = overview.week,
                                        onOpen = { course ->
                                            openedCourseEvent = null
                                            vm.openCourseDetail(course)
                                        }
                                    )
                                }
                            } else if (hasScheduleData(overview)) {
                            item(key = "day-view") {
                                ScheduleDayView(
                                    overview = overview,
                                    pickedWeekday = dayFocus,
                                    onWeekdayPick = { dayFocus = it },
                                    onCourseTap = ::openCourse
                                )
                            }
                            } else {
                                item(key = "schedule-empty") {
                                    EmptyState(
                                        title = "课表还是空的",
                                        body = "连上教务后会自动读取；之前看过的课表会留一份在本机。"
                                    )
                                }
                            }
                            item(key = "schedule-foot") {
                                ScheduleFoot(
                                    fromCache = schedule.fromCache,
                                    cachedAt = schedule.cachedAt,
                                    needsLogin = jwxtNeedsLogin
                                )
                            }
                        }
                        if (!schedule.jwxtConfigured) {
                            item(key = "connect-jwxt") {
                                ConnectJwxtSection(onOpenWeb = ::openWeb)
                            }
                        }
                    }

                    1 -> {
                        if (gradesState.freeRoomLoading) {
                            item(key = "free-progress") {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.signal,
                                    trackColor = colors.border
                                )
                            }
                        }
                        item(key = "free-header") {
                            Column {
                                SectionHeader(
                                    title = "空教室",
                                    meta = overview?.let { "第 ${it.week} 周" }
                                )
                            }
                        }
                        item(key = "free-filters") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (catalog.campuses.isNotEmpty()) {
                                    PaperSelectorRow(
                                        options = listOf("全部" to "") +
                                            catalog.campuses.map { it to it },
                                        selectedValue = catalog.selectedCampus,
                                        onSelect = vm::selectCampus
                                    )
                                }
                                PaperSelectorRow(
                                    options = (1..7).map { day ->
                                        "一二三四五六日"[day - 1].toString() to day.toString()
                                    },
                                    selectedValue = gradesState.freeRoomDay
                                        .takeIf { it in 1..7 }?.toString().orEmpty(),
                                    onSelect = { value ->
                                        vm.loadFreeRooms(
                                            value.toIntOrNull() ?: 1,
                                            gradesState.freeRoomSections.ifBlank { "0102" }
                                        )
                                    }
                                )
                                PaperSelectorRow(
                                    options = listOf(
                                        "1-2" to "0102",
                                        "3-4" to "0304",
                                        "5-6" to "0506",
                                        "7-8" to "0708",
                                        "9-10" to "0910"
                                    ),
                                    selectedValue = gradesState.freeRoomSections.ifBlank { "0102" },
                                    onSelect = { value ->
                                        vm.loadFreeRooms(
                                            gradesState.freeRoomDay.takeIf { it > 0 } ?: 1,
                                            value
                                        )
                                    }
                                )
                            }
                        }
                        if (gradesState.freeRoomError.isNotBlank()) {
                            item(key = "free-error") {
                                Text(
                                    text = gradesState.freeRoomError,
                                    color = colors.warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                        val rooms = gradesState.freeRooms
                            .filter { catalog.selectedCampus.isBlank() || it.campus == catalog.selectedCampus }
                            .take(60)
                        if (rooms.isEmpty() && !gradesState.freeRoomLoading && gradesState.freeRoomError.isBlank()) {
                            item(key = "free-empty") {
                                Text(
                                    text = "没查到空教室，换个星期或节次",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        items(rooms) { room ->
                            FreeRoomRow(room)
                        }
                    }

                    2 -> {
                        item(key = "calendar-head") {
                            CalendarHead(
                                state = calendar,
                                markedCount = manual.dayMarks.size,
                                onRetry = {
                                    if (needsReconnect) reconnectJwxt() else vm.loadCalendar()
                                },
                                actionLabel = if (needsReconnect) "重新连接" else "重试"
                            )
                        }
                        if (calendar.days.isNotEmpty()) {
                            items(calMonths, key = { "cal-month-$it" }) { month ->
                                MonthGrid(
                                    month = month,
                                    days = calendarMonthDays(calDayMap, month),
                                    today = schoolToday,
                                    examsByDate = calExamsByDate,
                                    marks = calMarks,
                                    onDayClick = { openedCalDay = it }
                                )
                            }
                            item(key = "calendar-foot") {
                                ScheduleFoot(calendar.fromCache, calendar.cachedAt)
                            }
                        }
                    }

                    3 -> {
                        if (gradesState.loading) {
                            item(key = "exam-progress") {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.signal,
                                    trackColor = colors.border
                                )
                            }
                        }
                        if (!needsReconnect && gradesState.error.isNotBlank()) {
                            item(key = "exam-error") {
                                ScheduleErrorRow(gradesState.error, onRetry = { vm.loadGrades(force = true) })
                            }
                        }
                        if (needsReconnect) {
                            item(key = "exam-stale") {
                                ScheduleErrorRow(
                                    if (examRows.isEmpty()) {
                                        "教务登录已失效，需重连"
                                    } else {
                                        "教务登录已失效 · 下面是缓存"
                                    },
                                    onRetry = { reconnectJwxt() },
                                    actionLabel = "重新连接"
                                )
                            }
                        } else if (gradesState.fromCache) {
                            item(key = "exam-cache") {
                                Text(
                                    text = "缓存 · 可能不是最新",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                        item(key = "exam-header") {
                            SectionHeader(
                                title = "考试安排",
                                meta = if (examRows.isEmpty() &&
                                    (needsReconnect || gradesState.error.isNotBlank())
                                ) "—" else "${examRows.size} 场"
                            )
                        }
                        if (examRows.isEmpty()) {
                            if (gradesState.loading && !needsReconnect) {
                                item(key = "exam-empty") {
                                    Text(
                                        text = "正在读取…",
                                        color = colors.textSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            } else if (!needsReconnect && gradesState.error.isBlank()) {
                                item(key = "exam-empty") {
                                    EmptyState(
                                        title = "暂时没有考试安排",
                                        body = "学校发布后会显示在这里。"
                                    )
                                }
                            }
                        } else {
                            item(key = "exam-next") {
                                ExamCountdownHero(examRows, schoolToday)
                            }
                            items(examRows) { item ->
                                ExamRow(item)
                            }
                        }
                    }

                    else -> {
                        val gradesUnavailable = gradesState.grades.isEmpty() &&
                            gradesState.gpa == null &&
                            (needsReconnect || gradesState.error.isNotBlank())
                        if (gradesState.loading && !gradesUnavailable) {
                            item(key = "grade-progress") {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.signal,
                                    trackColor = colors.border
                                )
                            }
                        }
                        if (!needsReconnect && gradesState.error.isNotBlank()) {
                            item(key = "grade-error") {
                                ScheduleErrorRow(gradesState.error, onRetry = { vm.loadGrades(force = true) })
                            }
                        }
                        if (needsReconnect) {
                            item(key = "grade-login") {
                                ScheduleErrorRow(
                                    if (gradesState.grades.isEmpty()) {
                                        "教务登录已失效，需重连"
                                    } else {
                                        "教务登录已失效 · 下面是缓存"
                                    },
                                    onRetry = { reconnectJwxt() },
                                    actionLabel = "重新连接"
                                )
                            }
                        }
                        if (!gradesUnavailable) {
                            item(key = "grade-summary") {
                                GradesSummary(
                                    gpa = gradesState.gpa,
                                    count = gradesState.grades.size,
                                    fromCache = gradesState.fromCache,
                                    cachedAt = gradesState.cachedAt
                                )
                            }
                        }
                        if (!gradesUnavailable && gradesState.creditsByCategory.isNotEmpty()) {
                            item(key = "credits-header") {
                                SectionHeader(
                                    title = "已修学分",
                                    meta = "%.1f".format(
                                        gradesState.creditsByCategory.sumOf { it.second }
                                    )
                                )
                            }
                            item(key = "credits") {
                                CreditsList(gradesState.creditsByCategory)
                            }
                        }
                        if (!gradesUnavailable && gradesState.gpaByTerm.size >= 2) {
                            item(key = "gpa-trend-header") {
                                SectionHeader(title = "学期绩点趋势")
                            }
                            item(key = "gpa-trend") {
                                GpaTrendList(gradesState.gpaByTerm)
                            }
                        }
                if (!gradesUnavailable && gradesState.notices.isNotEmpty()) {
                            item(key = "notices-header") {
                                SectionHeader(title = "最新成绩发布")
                            }
                            item(key = "notices") {
                                NoticesList(gradesState.notices.map { notice ->
                                    "${notice.courseName} · ${notice.score} 分（绩点 ${notice.gradePoint}）"
                                })
                            }
                        }
                        if (!gradesUnavailable) {
                            item(key = "grade-list-header") {
                                SectionHeader(title = "成绩明细")
                            }
                            if (gradesState.grades.isEmpty()) {
                                item(key = "grades-empty") {
                                    Text(
                                        text = if (gradesState.loading) "正在读取成绩…" else "还没有成绩数据",
                                        color = colors.textSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            }
                            items(gradesState.grades) { grade ->
                                GradeRow(
                                    grade = grade,
                                    expanded = gradesState.detailFor == grade.recordId &&
                                        grade.recordId.isNotBlank(),
                                    detail = gradesState.detail?.takeIf {
                                        gradesState.detailFor == grade.recordId
                                    },
                                    onToggle = { vm.toggleGradeDetail(grade.recordId) }
                                )
                            }
                        } else {
                            item(key = "grades-unavailable") {
                                Text(
                                    text = "重新连接后显示成绩明细。",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
                }
                }
            }
        }
    }

    openedCalDay?.let { day ->
        val dayDate = runCatching { java.time.LocalDate.parse(day.date) }.getOrNull()
        val dayWeek = weekNumberFromLabel(day.week)
        val dayWeekday = dayDate?.dayOfWeek?.value
        val manualHere = if (dayWeekday != null && dayWeek != null) {
            manual.courses
                .filter { it.weekday == dayWeekday && dayWeek in it.firstWeek..it.lastWeek }
                .map { DayCourse(it.name, it.sectionText, it.location, it.teacher) }
        } else {
            emptyList()
        }
        val termHere = if (dayWeekday != null && dayWeek != null) {
            coursesOnDate(gradesState.termCourses, dayWeekday, dayWeek)
        } else {
            emptyList()
        }
        CalendarDaySheet(
            day = day,
            date = dayDate,
            courses = (manualHere + termHere).distinctBy { it.courseName.trim() to it.sectionText.trim() },
            exams = dayDate?.let { calExamsByDate[it] }.orEmpty(),
            news = dayDate?.let { calNewsByDate[it] }.orEmpty(),
            today = schoolToday,
            mark = calMarks[day.date],
            onMark = { kind -> vm.setManualDayMark(day.date, kind) },
            onDismiss = { openedCalDay = null },
            onOpenNews = { uri ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                    .onFailure { vm.notify("打不开浏览器") }
            }
        )
    }

    val courseDetail = gradesState.courseDetail
    if (courseDetail.courseName.isNotBlank()) {
        CourseDetailSheet(
            detail = courseDetail,
            currentWeek = overview?.week ?: 0,
            fallbackLocation = openedCourseEvent?.location.orEmpty(),
            fallbackSectionText = openedCourseEvent?.sectionText.orEmpty(),
            override = courseOverrides[courseDetail.courseName] ?: top.sykesp42.rin.data.CourseOverride(),
            onOverride = { alias, color -> vm.setCourseOverride(courseDetail.courseName, alias, color) },
            onDismiss = {
                openedCourseEvent = null
                vm.dismissCourseDetail()
            },
            onMonitor = null,
            onOpenFreeRooms = { day, sections ->
                openedCourseEvent = null
                vm.dismissCourseDetail()
                segment = 1
                vm.loadFreeRooms(day, sections)
            },
            checklist = gradesState.courseDetail.checklist,
            onSaveChecklist = vm::saveChecklist,
            reminderPrefs = reminders.prefs,
            reminders = reminders.reminders,
            onSaveReminder = vm::saveCourseReminder,
            onDeleteReminder = vm::removeCourseReminder,
        )
    }

    if (manualOpen) {
        val crawledNames = buildSet {
            overview?.days?.forEach { day -> day.events.forEach { add(it.courseName) } }
            gradesState.termCourses.forEach { add(it.courseName) }
        }
        ManualDataSheet(
            payload = manual,
            crawledNames = crawledNames,
            onAddCourse = vm::addManualCourse,
            onRemoveCourse = vm::removeManualCourse,
            onAddExam = vm::addManualExam,
            onRemoveExam = vm::removeManualExam,
            onAddTodo = vm::addManualTodo,
            onToggleTodo = vm::toggleManualTodo,
            onRemoveTodo = vm::removeManualTodo,
            onAddMove = vm::addManualMove,
            onRemoveMove = vm::removeManualMove,
            onDismiss = { manualOpen = false }
        )
    }

    if (reportOpen && overview != null) {
        val report = remember(calendar.termLabel, overview.week, gradesState.termCourses, manual, gradesState.exams) {
            buildSemesterReport(
                termLabel = calendar.termLabel.ifBlank { overview.term },
                currentWeek = overview.week,
                slots = semesterSlots(gradesState.termCourses, manual.courses),
                examCount = gradesState.exams.size + manual.exams.size,
                todoCount = manual.todos.count { !it.done }
            )
        }
        SemesterReportSheet(
            report = report,
            palette = ExportPalette(
                canvas = colors.canvas.toArgb(),
                surface = colors.surface.toArgb(),
                text = colors.text.toArgb(),
                textSecondary = colors.textSecondary.toArgb(),
                signal = colors.signal.toArgb(),
                border = colors.border.toArgb(),
                dark = LocalVisualStyle.current.spec.dark
            ),
            shareStyle = shareStyle,
            shareOwner = shareOwner,
            appVersion = appVersion,
            onDismiss = { reportOpen = false }
        )
    }

    if (fullscreenWeek && overview != null) {
        FullWeekOverlay(
            overview = overview,
            onClose = { fullscreenWeek = false },
            onSwipeWeek = vm::switchWeek,
            tableMode = tableMode,
            onToggleTableMode = { tableMode = !tableMode },
            currentWeek = effectiveCurrentWeek,
            onBackToCurrentWeek = { vm.switchWeek(effectiveCurrentWeek - overview.week) },
            shareOwner = Identity.shareOwner(settings, avatarText),
            shareStyle = Identity.shareStyle(settings),
            appVersion = appVersion,
            onShareStyleChange = { style -> vm.setShareStyle(style.name.lowercase()) },
            shareFormat = shareFormat,
            onShareFormatChange = { fmt -> vm.setShareFormat(fmt.name.lowercase()) },
            onCourseTap = { event ->
                fullscreenWeek = false
                openCourse(event)
            }
        )
    }
}

@Composable
private fun FullWeekOverlay(
    overview: ApiScheduleOverview,
    onClose: () -> Unit,
    onSwipeWeek: (Int) -> Unit,
    tableMode: Boolean,
    onToggleTableMode: () -> Unit,
    currentWeek: Int,
    onBackToCurrentWeek: () -> Unit,
    shareOwner: String,
    shareStyle: ShareStyle,
    appVersion: String,
    onShareStyleChange: (ShareStyle) -> Unit,
    shareFormat: ShareFormat,
    onShareFormatChange: (ShareFormat) -> Unit,
    onCourseTap: (ApiScheduleEvent) -> Unit
) {
    val colors = LocalSuiSignColors.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setBackgroundDrawable(ColorDrawable(colors.canvas.toArgb()))
            window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            window?.setDimAmount(0f)
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
        ) {
            ScheduleWeekView(
                overview = overview,
                onCourseTap = onCourseTap,
                onClose = onClose,
                onSwipeWeek = onSwipeWeek,
                fullscreen = true,
                tableMode = tableMode,
                onToggleTableMode = onToggleTableMode,
                currentWeek = currentWeek,
                onBackToCurrentWeek = onBackToCurrentWeek,
                shareOwner = shareOwner,
                shareStyle = shareStyle,
                appVersion = appVersion,
                onShareStyleChange = onShareStyleChange,
                shareFormat = shareFormat,
                onShareFormatChange = onShareFormatChange,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ScheduleTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(index) },
                        role = Role.Tab
                    )
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = if (isSelected) colors.text else colors.textSecondary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .background(
                            if (isSelected) colors.signal else Color.Transparent,
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun WeekNavigation(
    week: Int,
    termView: Boolean,
    showReturn: Boolean,
    showWeekAction: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReturn: () -> Unit,
    onToggleTerm: () -> Unit,
    onOpenWeek: () -> Unit,
    onOpenTable: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Week stepping only exists in the normal schedule view; the term
            // overview is not week-bound.
            if (!termView) {
                IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                    Text(
                        text = "‹",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { contentDescription = "上一周" }
                    )
                }
            }
            Text(
                text = "第 $week 周",
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showReturn) {
                ReturnToCurrentCapsule(onClick = onReturn)
            }
            if (!termView) {
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Text(
                        text = "›",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { contentDescription = "下一周" }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onToggleTerm,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (termView) "返回课表" else "学期",
                    color = colors.text,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.weight(1f))
            // 日 is this timeline; 周 / 表 open the same week page in agenda
            // or canvas form. A week with no data would open a blank grid, so
            // the two entries stay hidden until the overview has content.
            if (!termView) {
                ModeTab("日", selected = true, onClick = {})
                if (showWeekAction) {
                    ModeTab("周", selected = false, onClick = onOpenWeek)
                    ModeTab("表", selected = false, onClick = onOpenTable)
                }
            }
        }
    }
}

@Composable
private fun ReturnToCurrentCapsule(onClick: () -> Unit) {
    val colors = LocalSuiSignColors.current
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "回到本周",
            color = colors.text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(colors.accent.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = 11.dp, vertical = 5.dp)
        )
    }
}

/**
 * Page-level day paging: a horizontal drag anywhere over the day tab moves one
 * day, including the empty space under a day with no classes. Vertical drags and
 * taps are untouched — the detector only claims horizontal slop.
 */
private fun Modifier.daySwipe(enabled: Boolean, onStep: (Int) -> Unit): Modifier {
    if (!enabled) return this
    // composed + a stable key keeps the detector alive across recompositions
    // while still calling the newest handler (which closes over the week).
    return this.composed {
        val step by rememberUpdatedState(onStep)
        Modifier.pointerInput(Unit) {
            var accum = 0f
            detectHorizontalDragGestures(
                onDragStart = { accum = 0f },
                onHorizontalDrag = { change, amount ->
                    accum += amount
                    change.consume()
                },
                onDragEnd = {
                    if (accum < -THRESHOLD) step(1) else if (accum > THRESHOLD) step(-1)
                }
            )
        }
    }
}

private const val THRESHOLD = 140f

@Composable
private fun ScheduleLoadingSurface() {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = colors.signal
        )
        Text(
            text = "正在读取课表…",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
internal fun ScheduleErrorRow(message: String, onRetry: () -> Unit, actionLabel: String = "重试") {
    val colors = LocalSuiSignColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.06f))
    ) {
        Hairline()
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = message,
                color = colors.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = colors.text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Hairline()
    }
}

@Composable
internal fun ScheduleFoot(fromCache: Boolean, cachedAt: String, needsLogin: Boolean = false) {
    val colors = LocalSuiSignColors.current
    val label = cachedLabel(fromCache, cachedAt, needsLogin)
    if (label.isBlank()) return
    Text(
        text = label,
        color = colors.textSecondary,
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
private fun ConnectJwxtSection(onOpenWeb: () -> Unit) {
    val colors = LocalSuiSignColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "连接教务")
        Text(
            text = "连上后自动识别教室",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 9.dp)
        )
        TextButton(
            onClick = onOpenWeb,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
        ) {
            Text(
                text = "打开网页端",
                color = colors.text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun PaperSelectorRow(
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (label, value) ->
            val selected = value == selectedValue
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(value) },
                        role = Role.Tab
                    )
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = if (selected) colors.text else colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .background(
                            if (selected) colors.signal else Color.Transparent,
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun FreeRoomRow(room: top.sykesp42.rin.data.JwxtFreeRoom) {
    val colors = LocalSuiSignColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.roomName,
                    color = colors.text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(room.campus, room.building, room.area, "容纳 ${room.capacity} 人")
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(colors.signal)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "空闲",
                color = colors.text,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Hairline()
    }
}

@Composable
private fun GradesSummary(gpa: Double?, count: Int, fromCache: Boolean, cachedAt: String) {
    val colors = LocalSuiSignColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "成绩", meta = "共 $count 门")
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = gpa?.let { "%.2f".format(it) } ?: "--",
                color = colors.text,
                style = MaterialTheme.typography.displayMedium,
                maxLines = 1
            )
            Text(
                text = "  加权绩点",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        val cacheNote = cachedLabel(fromCache, cachedAt)
        if (cacheNote.isNotBlank()) {
            Text(
                text = cacheNote,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun CreditsList(credits: List<Pair<String, Double>>) {
    val colors = LocalSuiSignColors.current
    val dark = colors.canvas.luminance() < 0.4f
    val total = credits.sumOf { it.second }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(118.dp)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2 + 1.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                val gap = 3.dp.toPx()
                val sweepTotal = 360f - gap * credits.size.coerceAtLeast(1)
                var start = -90f
                drawArc(
                    color = colors.border,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                credits.forEach { (category, credit) ->
                    val sweep = if (total > 0) sweepTotal * (credit / total).toFloat() else 0f
                    drawArc(
                        color = courseAccent(category, dark),
                        startAngle = start,
                        sweepAngle = sweep.coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    start += sweep + gap
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (total > 0) "%.0f".format(total) else "0",
                    color = colors.text,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "总学分",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            credits.forEach { (category, credit) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(courseAccent(category, dark), CircleShape)
                    )
                    Text(
                        text = category,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "%.1f".format(credit),
                        color = colors.text,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun GpaTrendList(gpaByTerm: List<Pair<String, Double>>) {
    val colors = LocalSuiSignColors.current
    val animations = LocalAnimationsEnabled.current
    val textMeasurer = rememberTextMeasurer()
    val progress = remember { androidx.compose.animation.core.Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(gpaByTerm) {
        if (animations) {
            progress.snapTo(0f)
            progress.animateTo(1f, androidx.compose.animation.core.tween(700))
        } else {
            progress.snapTo(1f)
        }
    }
    val shortLabel: (String) -> String = { term -> shortTermLabel(term) }
    val tickStyle = MaterialTheme.typography.labelSmall
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .padding(top = 4.dp)
    ) {
        if (gpaByTerm.isEmpty()) return@Canvas
        val points = gpaByTerm.map { it.second }
        val minValue = (points.min() - 0.15).coerceAtLeast(0.0)
        val maxValue = (points.max() + 0.15).coerceAtMost(4.0)
        val span = (maxValue - minValue).takeIf { it > 0.01 } ?: 1.0
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 26.dp.toPx()
        val stepX = if (gpaByTerm.size > 1) (right - left) / (gpaByTerm.size - 1) else 0f
        fun y(gpa: Double) = (bottom - ((gpa - minValue) / span * (bottom - top))).toFloat()
        for (line in listOf(2.0, 3.0, 4.0)) {
            if (line > minValue && line < maxValue) {
                drawLine(
                    color = colors.border,
                    start = Offset(left, y(line)),
                    end = Offset(right, y(line)),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        val centers = gpaByTerm.indices.map { left + stepX * it }
        val revealed = (gpaByTerm.size - 1) * progress.value
        for (index in 0 until gpaByTerm.size - 1) {
            if (index + 1 > revealed + 0.001f) break
            val t = ((revealed - index).coerceIn(0f, 1f))
            drawLine(
                color = colors.signal,
                start = Offset(centers[index], y(gpaByTerm[index].second)),
                end = Offset(
                    centers[index] + (centers[index + 1] - centers[index]) * t,
                    y(gpaByTerm[index].second) + (y(gpaByTerm[index + 1].second) - y(gpaByTerm[index].second)) * t
                ),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        gpaByTerm.forEachIndexed { index, (term, gpa) ->
            if (index > revealed + 0.001f) return@forEachIndexed
            val center = Offset(centers[index], y(gpa))
            drawCircle(color = colors.canvas, radius = 5.dp.toPx(), center = center)
            drawCircle(color = colors.signal, radius = 4.dp.toPx(), center = center)
            val value = textMeasurer.measure(
                "%.2f".format(gpa),
                tickStyle.copy(color = colors.text)
            )
            val valueX = (center.x - value.size.width / 2f)
                .coerceIn(0f, size.width - value.size.width)
            drawText(value, topLeft = Offset(valueX, center.y - value.size.height - 6.dp.toPx()))
            val label = textMeasurer.measure(
                shortLabel(term),
                tickStyle.copy(color = colors.textSecondary)
            )
            val labelX = (center.x - label.size.width / 2f)
                .coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(labelX, bottom + 6.dp.toPx()))
        }
    }
}

/** 2025-2026-1 -> 25-1; unexpected labels are shown unchanged. */
internal fun shortTermLabel(term: String): String {
    val match = Regex("^\\d{2}(\\d{2})-\\d{4}-(\\d)$").find(term.trim())
    return if (match != null) {
        match.groupValues[1] + "-" + match.groupValues[2]
    } else {
        term
    }
}

@Composable
private fun NoticesList(lines: List<String>) {
    val colors = LocalSuiSignColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        lines.take(3).forEach { line ->
            Text(
                text = line,
                color = colors.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 5.dp)
            )
            Hairline()
        }
        Text(
            text = "来自教务消息通知",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun GradeRow(
    grade: JwxtGrade,
    expanded: Boolean,
    detail: top.sykesp42.rin.data.JwxtGradeDetail?,
    onToggle: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = grade.recordId.isNotBlank(), onClick = onToggle)
                .padding(vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = grade.courseName,
                        color = colors.text,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${grade.term} · ${grade.category} · ${grade.studyMode}",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = grade.score,
                    color = if (grade.active) colors.text else colors.textSecondary,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1
                )
                Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                    Text(
                        text = grade.gradePoint?.let { "绩点 $it" } ?: "",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    val diff = top.sykesp42.rin.data.StatsCalculator.difficultyLabel(
                        grade.score.toDoubleOrNull()
                    )
                    if (diff.isNotBlank()) {
                        Text(
                            text = diff,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (diff) {
                                "给分好" -> colors.signal
                                "高危" -> colors.warning
                                else -> colors.textSecondary
                            }
                        )
                    }
                }
            }
            if (expanded) {
                if (detail == null) {
                    Text(
                        text = "正在读取构成…",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    detail.items.forEach { item ->
                        Row(modifier = Modifier.padding(top = 5.dp)) {
                            Text(
                                text = item.name,
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${item.score} 分 × ${item.weight}%",
                                color = colors.text,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
        Hairline()
    }
}

private fun hasScheduleData(overview: ApiScheduleOverview): Boolean =
    overview.days.isNotEmpty() ||
        overview.today.isNotEmpty() ||
        overview.current != null ||
        overview.next != null

internal fun cachedLabel(fromCache: Boolean, cachedAt: String, needsLogin: Boolean = false): String {
    if (!fromCache) return ""
    val at = cachedAt.takeIf { it.isNotBlank() }
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?.let { formatClock(it, top.sykesp42.rin.ui.schedule.schoolZone) }
    val base = if (at != null) "缓存 · $at" else "缓存数据"
    return if (needsLogin) "教务会话已过期 · $base" else base
}
