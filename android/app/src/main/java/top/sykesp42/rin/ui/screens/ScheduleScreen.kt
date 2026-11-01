package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.ManualPayload
import top.sykesp42.rin.data.buildWeekOverview
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.Identity
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.RinMasthead
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.formatDateLine
import top.sykesp42.rin.ui.schedule.ExportPalette
import top.sykesp42.rin.ui.schedule.ShareFormat
import top.sykesp42.rin.ui.schedule.ShareStyle
import top.sykesp42.rin.ui.schedule.buildSemesterReport
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.schedule.semesterSlots
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.ThemeMode

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
    var tableMode by remember { mutableStateOf(appSettings.weekDefaultTable) }
    var fullscreen by remember { mutableStateOf(appSettings.weekDefaultFullscreen) }
    var viewMode by remember { mutableStateOf(ScheduleViewMode.WEEK) }
    var selectedDate by remember { mutableStateOf(today) }
    val report = remember(manual, overview.week) {
        buildSemesterReport(
            termLabel = "本学期",
            currentWeek = overview.week.coerceAtLeast(1),
            slots = semesterSlots(manual.courses),
            examCount = manual.exams.size,
            todoCount = manual.todos.count { !it.done }
        )
    }
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

    PaperPage(ambientSpec = themeMode.spec) {
        Column(Modifier.fillMaxSize()) {
            RinMasthead(
                dateLine = dateLine,
                title = "课表",
                avatarText = avatarText,
                onRefresh = vm::refreshSchedule,
                onTheme = onToggleTheme,
                compact = true
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ViewModeTab("周", viewMode == ScheduleViewMode.WEEK) {
                    viewMode = ScheduleViewMode.WEEK
                }
                ViewModeTab("日", viewMode == ScheduleViewMode.DAY) {
                    selectedDate = today
                    viewMode = ScheduleViewMode.DAY
                }
                if (!appSettings.simpleMode) {
                    ViewModeTab("月", viewMode == ScheduleViewMode.MONTH) {
                        viewMode = ScheduleViewMode.MONTH
                    }
                }
            }
            if (viewMode == ScheduleViewMode.WEEK) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuiSecondaryButton(
                    text = if (manual.isEmpty) "添加课程" else "手动数据",
                    onClick = { manualOpen = true }
                )
                SuiSecondaryButton(
                    text = "学期报告",
                    onClick = { reportOpen = true },
                    enabled = manual.courses.isNotEmpty()
                )
                if (overview.week != schedule.currentWeek && schedule.currentWeek > 0) {
                    TextButton(onClick = vm::backToCurrentWeek) {
                        Text("回到本周", color = colors.signal, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            }
            if (!appSettings.simpleMode) {
                schedule.warning?.let { warning ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = warning,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onOpenSettings) {
                            Text(
                                "去连接",
                                color = colors.signal,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
            if (viewMode == ScheduleViewMode.DAY) {
                DayScheduleView(
                    days = overview.days,
                    selectedDate = selectedDate,
                    today = today,
                    mark = "",
                    onSelectDate = { selectedDate = it },
                    onBack = { viewMode = ScheduleViewMode.WEEK },
                    onToday = { selectedDate = today },
                    onCourseTap = { event -> vm.openCourseDetail(event) }
                )
            } else if (viewMode == ScheduleViewMode.MONTH) {
                MonthScheduleView(
                    payload = manual,
                    settings = appSettings,
                    today = today,
                    onBack = { viewMode = ScheduleViewMode.WEEK },
                    onPickDate = { date ->
                        selectedDate = date
                        viewMode = ScheduleViewMode.DAY
                    },
                    jwxt = schedule.overview
                )
            } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ScheduleWeekView(
                    overview = overview,
                    onCourseTap = { event -> vm.openCourseDetail(event) },
                    onClose = onGoToday,
                    onSwipeWeek = { delta -> vm.switchWeek(delta) },
                    emptyContent = if (overview.days.all { it.events.isEmpty() }) {
                        {
                            EmptySchedule(onAdd = { manualOpen = true })
                        }
                    } else {
                        null
                    },
                    fullscreen = fullscreen,
                    onFullscreen = { fullscreen = !fullscreen },
                    tableMode = tableMode,
                    onToggleTableMode = { tableMode = !tableMode },
                    currentWeek = schedule.currentWeek,
                    onBackToCurrentWeek = vm::backToCurrentWeek,
                    shareOwner = owner,
                    shareStyle = shareStyle,
                    appVersion = vm.appVersion,
                    onShareStyleChange = { vm.setShareStyle(it.name.lowercase()) },
                    shareFormat = shareFormat,
                    onShareFormatChange = { vm.setShareFormat(it.name.lowercase()) }
                )
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

/** Distinct course names in the visible week, from whichever source filled it. */
private fun weekCourseCount(overview: top.sykesp42.rin.data.ApiScheduleOverview): Int =
    overview.days.flatMap { it.events }.map { it.courseName }.filter { it.isNotBlank() }.distinct().size

/** Week / day / month switch shared by the three timetable surfaces. */
private enum class ScheduleViewMode { WEEK, DAY, MONTH }

@Composable
private fun ViewModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalRinColors.current
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 40.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            color = if (selected) colors.text else colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun EmptySchedule(onAdd: () -> Unit) {
    val colors = LocalRinColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "课表还是空的",
            color = colors.text,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "手动添加课程后，今日、提醒和学期报告都会跟着更新。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        SuiSecondaryButton(text = "添加第一门课", onClick = onAdd)
    }
}
