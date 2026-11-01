package top.sykesp42.rin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.buildWeekOverview
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.Identity
import top.sykesp42.rin.ui.components.EmptyState
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.RinMasthead
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.formatDateLine
import top.sykesp42.rin.ui.schedule.ExportPalette
import top.sykesp42.rin.ui.schedule.ShareFormat
import top.sykesp42.rin.ui.schedule.buildSemesterReport
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.schedule.semesterSlots
import top.sykesp42.rin.ui.schedule.shouldShowReturnToCurrent
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.ThemeMode

/**
 * 课表, laid out the way the full client does it: day-first, one LazyColumn, a
 * week navigation row with 日 / 周 / 表 / 学期 entries, and a whole-page week
 * layer for 周 / 表. The full client keeps the timetable free of permanent
 * action buttons — manual data and the semester report hang off the same row —
 * and this build follows that.
 */
@Composable
fun ScheduleScreen(
    vm: AppViewModel,
    themeMode: ThemeMode,
    avatarText: String,
    onToggleTheme: () -> Unit,
    onGoToday: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val academic by vm.academic.collectAsStateWithLifecycle()
    val manual by vm.manualSchedule.payload.collectAsStateWithLifecycle()
    val detail by vm.courseDetail.collectAsStateWithLifecycle()
    val appSettings = settings ?: AppSettings()
    val colors = LocalRinColors.current
    val today = todayAtCampusClock()
    val nowMinutes = nowMinutesAt()
    val overview = schedule.overview
        ?: buildWeekOverview(manual, appSettings, 0, today, nowMinutes)
    var manualOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var termView by rememberSaveable { mutableStateOf(false) }
    // The whole-page 周 / 表 layer. It renders without the page header, the
    // way the full client does — squeezing the 72dp-row grid into the strip
    // under the header forced fit-scale to ~0.7 and crushed the cells.
    var weekOpen by rememberSaveable { mutableStateOf(false) }
    var tableMode by rememberSaveable { mutableStateOf(appSettings.weekDefaultTable) }
    // Day focus inside the loaded week; a step past the edge also switches the
    // loaded week (the full client does the same one-week spill).
    var dayFocus by rememberSaveable { mutableIntStateOf(initialDayFocus(overview)) }

    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp
    val shareStyle = Identity.shareStyle(appSettings)
    val shareFormat = ShareFormat.entries.firstOrNull {
        it.name.equals(appSettings.shareFormat, ignoreCase = true)
    } ?: ShareFormat.COLUMNS
    val owner = Identity.shareOwner(appSettings, avatarText)
    val palette = ExportPalette(
        canvas = colors.canvas.toArgb(),
        surface = colors.surface.toArgb(),
        text = colors.text.toArgb(),
        textSecondary = colors.textSecondary.toArgb(),
        signal = colors.signal.toArgb(),
        border = colors.border.toArgb(),
        dark = LocalVisualStyle.current.spec.dark
    )
    val dateLine = buildString {
        append(formatDateLine(today))
        append(" · 第 ")
        append(overview.week.coerceAtLeast(1))
        append(" 周 · ")
        append(weekCourseCount(overview))
        append(" 门课")
    }

    // Day paging: a horizontal drag anywhere over the day view steps one day.
    val step = rememberUpdatedState { weekday: Int, delta: Int ->
        val (nextWeekday, weekOffset) = stepDay(weekday, delta)
        dayFocus = nextWeekday
        if (weekOffset != 0) vm.switchWeek(weekOffset)
    }

    BackHandler(enabled = weekOpen) {
        weekOpen = false
        tableMode = false
    }

    PaperPage(ambientSpec = themeMode.spec) {
        if (weekOpen && hasScheduleData(overview)) {
            ScheduleWeekView(
                overview = overview,
                onCourseTap = { event -> vm.openCourseDetail(event) },
                onClose = {
                    weekOpen = false
                    tableMode = false
                },
                onSwipeWeek = { delta -> vm.switchWeek(delta) },
                fullscreen = true,
                onFullscreen = null,
                tableMode = tableMode,
                onToggleTableMode = { tableMode = !tableMode },
                currentWeek = schedule.currentWeek,
                onBackToCurrentWeek = vm::backToCurrentWeek,
                shareOwner = owner,
                shareStyle = shareStyle,
                appVersion = vm.appVersion,
                onShareStyleChange = { vm.setShareStyle(it.name.lowercase()) },
                shareFormat = shareFormat,
                onShareFormatChange = { vm.setShareFormat(it.name.lowercase()) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                RinMasthead(
                    dateLine = dateLine,
                    title = "课表",
                    avatarText = avatarText,
                    onRefresh = vm::refreshSchedule,
                    onTheme = onToggleTheme,
                    compact = true
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            var accum = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { accum = 0f },
                                onHorizontalDrag = { change, amount ->
                                    accum += amount
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (accum < -140f) step.value(dayFocus, 1)
                                    else if (accum > 140f) step.value(dayFocus, -1)
                                }
                            )
                        },
                    contentPadding = PaddingValues(
                        start = pageInset,
                        end = pageInset,
                        top = 4.dp,
                        bottom = 92.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 简洁模式 promises no school plumbing on the page: an
                    // expired session belongs to the settings entry there.
                    if (schedule.warning != null && !appSettings.simpleMode) {
                        item(key = "schedule-warning") {
                            ScheduleErrorRow(
                                message = schedule.warning.orEmpty(),
                                onRetry = vm::refreshSchedule,
                                actionLabel = "重试"
                            )
                        }
                    }
                    if (!hasScheduleData(overview)) {
                        item(key = "schedule-empty") {
                            if (schedule.loading) {
                                ScheduleLoadingSurface()
                            } else {
                                EmptyState(
                                    title = "课表还是空的",
                                    body = "连上教务后自动读取；之前看过的课表会留一份在本机。"
                                )
                            }
                        }
                    } else {
                        item(key = "week-nav") {
                            WeekNavigation(
                                week = overview.week,
                                termView = termView,
                                showReturn = shouldShowReturnToCurrent(
                                    overview.week,
                                    schedule.currentWeek
                                ),
                                showWeekAction = hasScheduleData(overview),
                                onPrev = { step.value(dayFocus, -1) },
                                onNext = { step.value(dayFocus, 1) },
                                onReturn = vm::backToCurrentWeek,
                                onToggleTerm = {
                                    termView = !termView
                                    if (termView) vm.ensureTermCourses()
                                },
                                onOpenWeek = {
                                    tableMode = false
                                    weekOpen = true
                                },
                                onOpenTable = {
                                    tableMode = true
                                    weekOpen = true
                                }
                            )
                        }
                        if (termView) {
                            item(key = "term-actions") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SuiSecondaryButton(
                                        text = "手动数据",
                                        onClick = { manualOpen = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item(key = "term-overview") {
                                TermOverview(
                                    courses = academic.termCourses,
                                    loading = academic.termCoursesLoading,
                                    currentWeek = overview.week,
                                    onOpen = { name ->
                                        val slot = academic.termCourses
                                            .firstOrNull { it.courseName == name }
                                            ?.slots?.firstOrNull()
                                            ?: return@TermOverview
                                        vm.openCourseDetail(
                                            ApiScheduleEvent(
                                                courseName = slot.courseName,
                                                location = slot.location,
                                                sectionText = slot.sectionText
                                            )
                                        )
                                    }
                                )
                            }
                        } else {
                            item(key = "day-view") {
                                ScheduleDayView(
                                    overview = overview,
                                    pickedWeekday = dayFocus,
                                    onWeekdayPick = { dayFocus = it },
                                    onCourseTap = { event -> vm.openCourseDetail(event) }
                                )
                            }
                        }
                        item(key = "schedule-foot") {
                            ScheduleFoot(
                                fromCache = schedule.fromCache,
                                cachedAt = schedule.cachedAt,
                                needsLogin = schedule.warning != null
                            )
                        }
                    }
                }
            }
        }
        if (manualOpen) {
            ManualDataSheet(
                payload = manual,
                existingNames = emptySet(),
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
        if (reportOpen) {
            val report = remember(manual, overview.week) {
                buildSemesterReport(
                    termLabel = "本学期",
                    currentWeek = overview.week.coerceAtLeast(1),
                    slots = semesterSlots(manual.courses),
                    examCount = manual.exams.size,
                    todoCount = manual.todos.count { !it.done }
                )
            }
            SemesterReportSheet(
                report = report,
                palette = palette,
                shareStyle = shareStyle,
                shareOwner = owner,
                appVersion = vm.appVersion,
                onDismiss = { reportOpen = false }
            )
        }
        if (detail.open) {
            CourseDetailSheet(
                vm = vm,
                detail = detail,
                onDismiss = vm::dismissCourseDetail
            )
        }
    }
}

/** Distinct course names in the visible week, from whichever source filled it. */
private fun weekCourseCount(overview: top.sykesp42.rin.data.ApiScheduleOverview): Int =
    overview.days.flatMap { it.events }.map { it.courseName }.filter { it.isNotBlank() }.distinct().size
