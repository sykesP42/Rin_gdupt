package top.sky2333.rin.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.sky2333.rin.data.ApiScheduleEvent
import top.sky2333.rin.data.AppSettings
import top.sky2333.rin.data.ChecklistItem
import top.sky2333.rin.data.CourseReminder
import top.sky2333.rin.data.ManualExam
import top.sky2333.rin.data.PetWeather
import top.sky2333.rin.data.ReminderPrefs
import top.sky2333.rin.ui.AppViewModel
import top.sky2333.rin.ui.components.Hairline
import top.sky2333.rin.ui.components.PaperPage
import top.sky2333.rin.ui.components.RinMasthead
import top.sky2333.rin.ui.components.SuiSecondaryButton
import top.sky2333.rin.ui.components.SuiSwitch
import top.sky2333.rin.ui.formatDateLine
import top.sky2333.rin.ui.pet.PetEvent
import top.sky2333.rin.ui.pet.PetPerch
import top.sky2333.rin.ui.schedule.ExamItem
import top.sky2333.rin.ui.schedule.ClassSlotState
import top.sky2333.rin.ui.schedule.classSlotState
import top.sky2333.rin.ui.schedule.examItems
import top.sky2333.rin.ui.schedule.nextExamItem
import top.sky2333.rin.ui.schedule.nowMinutesAt
import top.sky2333.rin.ui.schedule.todayAtCampusClock
import top.sky2333.rin.ui.theme.LocalAnimationsEnabled
import top.sky2333.rin.ui.theme.LocalRinColors
import top.sky2333.rin.ui.theme.ThemeMode
import top.sky2333.rin.ui.today.CompanionAction
import top.sky2333.rin.ui.today.RinCompanionCard
import top.sky2333.rin.ui.today.RinCompanionProfile
import top.sky2333.rin.ui.today.TodayCardId
import top.sky2333.rin.ui.today.TodayCards
import top.sky2333.rin.ui.today.companionLine

@Composable
fun TodayScreen(
    vm: AppViewModel,
    themeMode: ThemeMode,
    avatarText: String,
    onToggleTheme: () -> Unit,
    onOpenSchedule: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val manual by vm.manualSchedule.payload.collectAsStateWithLifecycle()
    val reminders by vm.courseReminders.payload.collectAsStateWithLifecycle()
    val weather by vm.petWeather.collectAsStateWithLifecycle()
    val frame by vm.todayFrame.collectAsStateWithLifecycle()
    val appSettings = settings ?: AppSettings()
    val colors = LocalRinColors.current
    val animations = LocalAnimationsEnabled.current
    val themeSpec = themeMode.spec
    val today = todayAtCampusClock()
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
    val nowMinutes = nowMinutesAt(java.time.Instant.ofEpochMilli(tick))
    val overview = schedule.overview
    val todayEvents = overview?.today.orEmpty()
    val next = overview?.next
    val current = overview?.current
    val week = overview?.week ?: schedule.currentWeek
    val dateLine = formatDateLine(today) + if (week > 0) " · 第 $week 周" else ""
    val exams = remember(manual.exams) { examItems(manual.exams, today) }
    val nextExam = remember(exams, today) { nextExamItem(exams, today) }
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickTodayFrame(uri)
    }

    val profile = remember(
        todayEvents,
        current,
        next,
        manual,
        weather,
        nextExam,
        nowMinutes,
        week,
        today
    ) {
        val tomorrow = today.plusDays(1)
        val tomorrowEvents = overview?.days?.firstOrNull { it.date == tomorrow.toString() }?.events.orEmpty()
        RinCompanionProfile(
            todayCourses = todayEvents.size,
            firstCourse = todayEvents.firstOrNull()?.courseName.orEmpty(),
            firstStart = todayEvents.firstOrNull()?.startTime.orEmpty(),
            nextCourse = next?.courseName.orEmpty(),
            nextStart = next?.startTime.orEmpty(),
            nextRoom = next?.location.orEmpty(),
            nextDayCourse = tomorrowEvents.firstOrNull()?.courseName.orEmpty(),
            nextDayStart = tomorrowEvents.firstOrNull()?.startTime.orEmpty(),
            currentCourse = current?.courseName.orEmpty(),
            afterLast = todayEvents.isNotEmpty() && current == null && next == null,
            isWeekend = today.dayOfWeek.value >= 6,
            minutesNow = nowMinutes,
            week = week,
            termWeeks = 20,
            examName = nextExam?.exam?.name.orEmpty(),
            examDays = nextExam?.time?.date?.let { ChronoUnit.DAYS.between(today, it).toInt() } ?: -1,
            todoCount = manual.todos.count { !it.done },
            busiestWeekday = busiestWeekday(overview?.days.orEmpty()),
            freeDays = (1..5).count { weekday ->
                overview?.days?.firstOrNull { it.weekday == weekday }?.events.isNullOrEmpty()
            },
            temp = weather?.temperatureC?.let { "$it°" }.orEmpty(),
            humidity = weather?.humidity?.let { "$it%" }.orEmpty(),
            weather = weather?.weather ?: PetWeather.UNKNOWN
        )
    }
    val companion = remember(profile, today) {
        companionLine(profile, seed = (today.toEpochDay() + week * 7).toInt())
    }
    val enabledCards = remember(appSettings) { TodayCards.enabled(appSettings) }

    PaperPage(ambientSpec = themeSpec) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = pageInset,
                end = pageInset,
                top = 10.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "masthead") {
                RinMasthead(
                    dateLine = dateLine,
                    title = "Rin",
                    avatarText = avatarText,
                    onRefresh = {
                        vm.refreshSchedule()
                        vm.refreshPetWeather()
                    },
                    onTheme = onToggleTheme
                )
            }
            item(key = "cover") {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TodayMoodCover(
                        spec = themeSpec,
                        dateLine = dateLine,
                        week = week,
                        courseCount = if (overview == null) -1 else todayEvents.size,
                        weather = weather
                    )
                    if (appSettings.petEnabled) {
                        PetPerch(
                            enabled = true,
                            contextAware = appSettings.petContextAware,
                            weather = weather,
                            event = PetEvent.NONE,
                            motion = animations,
                            hero = themeSpec.hero,
                            artFocus = themeSpec.artFocus,
                            petSize = 58.dp,
                            offsetX = appSettings.petOffsetX,
                            offsetY = appSettings.petOffsetY,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }
            item(key = "next") {
                NextClassLine(
                    current = current,
                    next = next,
                    todayCount = todayEvents.size,
                    onOpen = onOpenSchedule
                )
            }
            item(key = "sign-reminder") {
                SignReminderCard(
                    settings = appSettings,
                    next = next,
                    todayEvents = todayEvents,
                    onToggle = vm::setSignReminderEnabled,
                    onRemind = {
                        val target = next
                        if (target != null) {
                            val date = today
                            val start = target.startTime.takeIf { it.length == 5 } ?: "08:00"
                            val lead = 15
                            val reminder = CourseReminder(
                                id = "sign-${date}-${start}-${target.courseName}".hashCode().toString(),
                                courseName = target.courseName,
                                date = date.toString(),
                                startTime = start,
                                endTime = target.endTime,
                                location = target.location,
                                leadMinutes = lead,
                                appReminder = true,
                                calendarEvent = false,
                                alarm = true,
                                signReminder = true,
                                createdAt = java.time.Instant.now().toString()
                            )
                            vm.saveReminder(
                                reminder,
                                ReminderPrefs(leadMinutes = lead, appReminder = true, alarm = true)
                            )
                        } else {
                            vm.notify("今天没有可提醒的课")
                        }
                    }
                )
            }
            items(enabledCards, key = { it.key }) { card ->
                when (card) {
                    TodayCardId.COMPANION -> RinCompanionCard(
                        line = companion,
                        onAction = { action ->
                            if (action == CompanionAction.SCHEDULE) onOpenSchedule()
                        }
                    )
                    TodayCardId.MINI_DAY -> MiniTimetableDayCard(overview, today, onOpenSchedule)
                    TodayCardId.MINI_TABLE -> MiniTimetableTableCard(overview, today, nowMinutes, onOpenSchedule)
                    TodayCardId.MINI_WEEK -> MiniTimetableWeekCard(overview, onOpenSchedule)
                    TodayCardId.WEATHER -> HourlyWeatherCard(weather, vm::refreshPetWeather)
                    TodayCardId.WEATHER_DENSE -> WeatherDenseCard(weather)
                    TodayCardId.WEATHER_DAY -> WeatherDayCard(weather)
                    TodayCardId.MEMO -> MemoCard(vm)
                    TodayCardId.TOMORROW -> TomorrowCard(overview, today, onOpenSchedule)
                    TodayCardId.TIMELINE -> TodayTimelineCard(todayEvents, nowMinutes, onOpenSchedule)
                    TodayCardId.EXAM_COUNTDOWN -> ExamCountdownCard(manual.exams, today)
                    TodayCardId.RIN_SHOWCASE -> RinShowcaseCard()
                    TodayCardId.PHOTO_FRAME -> PhotoFrameCard(
                        bitmap = frame,
                        onPick = {
                            runCatching {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }.onFailure { vm.notify("这台设备没有可用的图片选择器") }
                        },
                        onClear = vm::clearTodayFrame
                    )
                    TodayCardId.FILE_HUB -> FileHubCard(onMessage = vm::notify)
                }
            }
            item(key = "privacy") {
                Text(
                    text = "课表与提醒只保存在本机。",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun NextClassLine(
    current: ApiScheduleEvent?,
    next: ApiScheduleEvent?,
    todayCount: Int,
    onOpen: () -> Unit
) {
    val colors = LocalRinColors.current
    val title: String
    val detail: String
    when {
        current != null -> {
            title = "正在上 ${current.courseName}"
            detail = listOfNotNull(
                current.location.takeIf { it.isNotBlank() },
                current.endTime.takeIf { it.isNotBlank() }?.let { "$it 下课" }
            ).joinToString(" · ")
        }
        next != null -> {
            title = "下一节 ${next.courseName}"
            detail = listOfNotNull(
                next.startTime.takeIf { it.isNotBlank() },
                next.location.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
        }
        todayCount > 0 -> {
            title = "今天的课上完了"
            detail = "剩下的时间归你。"
        }
        else -> {
            title = "今天没有课"
            detail = "慢慢来，给自己留一点空白。"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        TextButton(onClick = onOpen) {
            Text("看课表", color = colors.signal, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SignReminderCard(
    settings: AppSettings,
    next: ApiScheduleEvent?,
    todayEvents: List<ApiScheduleEvent>,
    onToggle: (Boolean) -> Unit,
    onRemind: () -> Unit
) {
    val colors = LocalRinColors.current
    val target = next
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "签到提醒",
                    color = colors.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (target != null) {
                        "${target.courseName} · ${target.startTime} 前提醒"
                    } else {
                        "今天没有需要提醒的课"
                    },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            SuiSwitch(
                checked = settings.signReminderEnabled,
                onCheckedChange = onToggle,
                contentDescription = "签到提醒"
            )
        }
        if (settings.signReminderEnabled) {
            Hairline()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "提前 15 分钟，只在本机提醒你留意签到。",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                SuiSecondaryButton(
                    text = "设提醒",
                    onClick = onRemind,
                    enabled = target != null
                )
            }
        }
    }
}

@Composable
private fun MemoCard(vm: AppViewModel) {
    val colors = LocalRinColors.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ChecklistItem>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(vm) { items = vm.checklists.items("today") }
    fun save(next: List<ChecklistItem>) {
        items = next
        scope.launch { vm.checklists.save("today", next) }
    }
    WidgetCard("今日速记", "${items.count { !it.done }} 条") {
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = item.done,
                    onCheckedChange = {
                        save(items.map { if (it == item) it.copy(done = !it.done) else it })
                    }
                )
                Text(
                    text = item.text,
                    color = if (item.done) colors.textSecondary else colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { save(items - item) }) {
                    Text("删", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(60) },
                placeholder = { Text("记一件今天要做的事") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    save(items + ChecklistItem(draft.trim()))
                    draft = ""
                }
            ) { Text("添加", color = colors.signal) }
        }
    }
}

@Composable
private fun TomorrowCard(
    overview: top.sky2333.rin.data.ApiScheduleOverview?,
    today: LocalDate,
    onOpen: () -> Unit
) {
    val colors = LocalRinColors.current
    val tomorrow = today.plusDays(1)
    val events = overview?.days?.firstOrNull { it.date == tomorrow.toString() }?.events.orEmpty()
    WidgetCard("明日预览", formatDateLine(tomorrow), "打开", onOpen) {
        if (events.isEmpty()) {
            Text("明天没有课", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            events.forEach { event ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.startTime,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(46.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = event.courseName,
                            color = colors.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = event.location,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTimelineCard(
    events: List<ApiScheduleEvent>,
    nowMinutes: Int,
    onOpen: () -> Unit
) {
    val colors = LocalRinColors.current
    WidgetCard("今日课程", "${events.size} 节", "打开", onOpen) {
        if (events.isEmpty()) {
            Text("今天没有课", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            events.forEach { event ->
                val state = classSlotState(event.startTime, event.endTime, nowMinutes)
                val active = state == ClassSlotState.CURRENT
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(30.dp)
                            .background(if (active) colors.signal else colors.border)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = event.courseName,
                            color = colors.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = listOfNotNull(
                                event.startTime.takeIf { it.isNotBlank() },
                                event.endTime.takeIf { it.isNotBlank() },
                                event.location.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (active) {
                        Text("正在上", color = colors.signal, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun busiestWeekday(days: List<top.sky2333.rin.data.ApiScheduleDay>): String {
    val busiest = days.maxByOrNull { it.events.size } ?: return ""
    if (busiest.events.isEmpty()) return ""
    return listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(busiest.weekday - 1) { "" }
}
