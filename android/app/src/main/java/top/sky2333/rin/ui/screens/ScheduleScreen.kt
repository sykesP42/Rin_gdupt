package top.sky2333.rin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import top.sky2333.rin.data.AppSettings
import top.sky2333.rin.data.ManualPayload
import top.sky2333.rin.data.buildWeekOverview
import top.sky2333.rin.ui.AppViewModel
import top.sky2333.rin.ui.Identity
import top.sky2333.rin.ui.components.PaperPage
import top.sky2333.rin.ui.components.RinMasthead
import top.sky2333.rin.ui.components.SuiSecondaryButton
import top.sky2333.rin.ui.formatDateLine
import top.sky2333.rin.ui.schedule.ExportPalette
import top.sky2333.rin.ui.schedule.ShareFormat
import top.sky2333.rin.ui.schedule.ShareStyle
import top.sky2333.rin.ui.schedule.buildSemesterReport
import top.sky2333.rin.ui.schedule.nowMinutesAt
import top.sky2333.rin.ui.schedule.semesterSlots
import top.sky2333.rin.ui.schedule.todayAtCampusClock
import top.sky2333.rin.ui.theme.LocalRinColors
import top.sky2333.rin.ui.theme.LocalVisualStyle
import top.sky2333.rin.ui.theme.ThemeMode

@Composable
fun ScheduleScreen(
    vm: AppViewModel,
    themeMode: ThemeMode,
    avatarText: String,
    onToggleTheme: () -> Unit,
    onGoToday: () -> Unit
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
        append(manual.courses.size)
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ScheduleWeekView(
                    overview = overview,
                    onCourseTap = { event -> vm.openCourseDetail(event) },
                    onClose = onGoToday,
                    onSwipeWeek = { delta -> vm.switchWeek(delta) },
                    emptyContent = if (manual.courses.isEmpty()) {
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
