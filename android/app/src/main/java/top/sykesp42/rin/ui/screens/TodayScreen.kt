package top.sykesp42.rin.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.TodayFrameStore
import top.sykesp42.rin.data.withManualEntries
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.components.LocalCourseOverrides
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.RinMasthead
import top.sykesp42.rin.ui.pet.PetEvent
import top.sykesp42.rin.ui.pet.PetPerch
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.formatDateLine
import top.sykesp42.rin.ui.schedule.clockToMinutes
import top.sykesp42.rin.ui.schedule.coursesOnDate
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.schedule.sectionStart
import top.sykesp42.rin.ui.schedule.parseExamTime
import top.sykesp42.rin.ui.schedule.schoolZone
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

/** One line for the weekday labels. */
private fun weekdayLabel(value: Int): String =
    arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(value - 1) { "周一" }

/**
 * 今日 page: masthead, the mood cover with Rin perched on it, then the
 * user-ordered card wall (companion line, weather, timetable minis, memo,
 * exams, campus news, quick links, photo frame) and a campus clock footer.
 * The sign-only surface of the full client does not exist in this build.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: AppViewModel,
    themeMode: ThemeMode,
    avatarText: String,
    onToggleTheme: () -> Unit,
    onOpenSchedule: () -> Unit,
    minimal: Boolean = false,
    onOpenProfile: () -> Unit = {},
) {
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val grades by vm.grades.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val petWeather by vm.petWeather.collectAsStateWithLifecycle()
    val reminders by vm.courseReminders.payload.collectAsStateWithLifecycle()
    val manual by vm.manualSchedule.payload.collectAsStateWithLifecycle()
    val campusNews by vm.campusNews.collectAsStateWithLifecycle()
    val appSettings = settings ?: AppSettings()
    val petEnabled = settings?.petEnabled == true
    val petContextAware = settings?.petContextAware == true
    val petWeatherEnabled = settings?.petWeatherEnabled == true
    val narrow = LocalConfiguration.current.screenWidthDp < 380
    val themeSpec = LocalVisualStyle.current.spec
    val basePetSize = when {
        narrow -> 52.dp
        themeSpec.hasArt -> 58.dp
        else -> 64.dp
    }
    val petSize = (basePetSize * (settings?.petScale ?: 1f))
        .coerceIn(30.dp, LocalConfiguration.current.screenWidthDp.dp * 0.42f)
    val context = LocalContext.current
    // Spec 2.2: 24dp page inset, tightened to 20dp on narrow phones.
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp

    // A one-second tick keeps the campus clock and the timeline's "now" line
    // honest without recomputing the whole composition.
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000)
        }
    }
    val serverNow = remember(tick) { Instant.ofEpochMilli(tick) }

    LaunchedEffect(Unit) {
        vm.ensureScheduleLoaded()
        vm.ensureCampusNewsLoaded()
    }

    // Spec 4.2: the school clock anchors "today", so this page and the week
    // grid can never disagree on which day it is.
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
    LaunchedEffect(todayDate, nowHour) {
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

    var openedCourseEvent by remember { mutableStateOf<ApiScheduleEvent?>(null) }

    val sessionExpired = appSettings.jwxtNeedsLogin
    val cacheAt = remember(schedule.cachedAt) {
        schedule.cachedAt.takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }
    val petNowMinutes = nowMinutesAt(serverNow)
    val petEvent = if (
        todayClasses.any { event ->
            val start = clockToMinutes(event.startTime) ?: return@any false
            start > petNowMinutes && start - petNowMinutes <= 15
        }
    ) {
        PetEvent.RUSH_TO_CLASS
    } else {
        PetEvent.NONE
    }

    PaperPage(
        skyWash = true,
        ambientSpec = if (minimal) null else themeSpec
    ) {
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { vm.retryJwxtNow() },
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
                        dateLine = dateLine,
                        title = "Rin",
                        avatarText = avatarText,
                        onRefresh = {},
                        onTheme = onToggleTheme,
                        compact = minimal
                    )
                }
                if (sessionExpired || schedule.fromCache) {
                    item(key = "source-status") {
                        TodaySourceStrip(
                            sessionExpired = sessionExpired,
                            fromCache = schedule.fromCache,
                            cachedAt = cacheAt
                        )
                    }
                }
                if (!minimal) {
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
                if (!minimal) {
                    for (card in TodayCards.enabled(appSettings)) {
                        when (card) {
                            TodayCardId.COMPANION -> item(key = "card-companion") {
                                RinCompanionCard(
                                    line = companion,
                                    onAction = { action ->
                                        when (action) {
                                            CompanionAction.SCHEDULE -> onOpenSchedule()
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
                                    onSchedule = onOpenSchedule
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
                if (schedule.error.isNotBlank()) {
                    item(key = "error") {
                        TodayErrorRow(
                            schedule.error,
                            onRetry = { vm.retryJwxtNow() },
                            showAction = false
                        )
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
                } else {
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
            onDeleteReminder = vm::removeCourseReminder
        )
    }
}
