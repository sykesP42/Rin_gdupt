package top.sykesp42.rin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.JwxtExam
import top.sykesp42.rin.data.JwxtFreeRoom
import top.sykesp42.rin.data.JwxtGrade
import top.sykesp42.rin.ui.AcademicUiState
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.RoomDetailUiState
import top.sykesp42.rin.ui.RoomSlotState
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.PaperSelectorRow
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.RinMasthead
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.formatDateLine
import top.sykesp42.rin.ui.schedule.ROOM_SLOTS
import top.sykesp42.rin.ui.schedule.calendarDaysByDate
import top.sykesp42.rin.ui.schedule.calendarMonthDays
import top.sykesp42.rin.ui.schedule.calendarMonthsOf
import top.sykesp42.rin.ui.schedule.dayCourses
import top.sykesp42.rin.ui.schedule.parseExamTime
import top.sykesp42.rin.ui.schedule.weekNumberFromLabel
import top.sykesp42.rin.ui.schedule.RoomSlot
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.schedule.roomSlotAt
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.ThemeMode

/**
 * 校务: the read-only school side. Three segments share one page —
 * the room helper (which classrooms are free right now), exam arrangements and
 * grades. Nothing here writes to the school: it only reads.
 */
@Composable
fun AcademicScreen(
    vm: AppViewModel,
    themeMode: ThemeMode,
    avatarText: String,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val academic by vm.academic.collectAsStateWithLifecycle()
    val calendar by vm.calendar.collectAsStateWithLifecycle()
    val manual by vm.manualSchedule.payload.collectAsStateWithLifecycle()
    val appSettings = settings ?: AppSettings()
    val colors = LocalRinColors.current
    val detail = academic.detail
    val context = androidx.compose.ui.platform.LocalContext.current
    val login = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val cookie = result.data
            ?.getStringExtra(top.sykesp42.rin.jwxt.JwxtLoginActivity.EXTRA_COOKIE)
            .orEmpty()
        if (cookie.isBlank()) return@rememberLauncherForActivityResult
        vm.saveJwxtSession(cookie)
        vm.refreshAcademic()
    }
    val relogin: () -> Unit = {
        runCatching {
            login.launch(
                top.sykesp42.rin.jwxt.JwxtLoginActivity.intent(context, appSettings.jwxtBaseUrl)
            )
        }.onFailure { vm.notify("打不开教务页面") }
    }

    LaunchedEffect(Unit) { vm.openAcademic() }

    if (detail != null) {
        RoomDetailPage(
            detail = detail,
            nowMinutes = nowMinutesAt(),
            onBack = vm::closeRoomDetail,
            onOpenRoom = { name -> vm.openRoomDetail(name) },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    var segment by rememberSaveable { mutableIntStateOf(0) }
    var openedCalDay by remember { mutableStateOf<top.sykesp42.rin.data.JwxtCalendarDay?>(null) }
    LaunchedEffect(segment) {
        if (segment == 1) vm.ensureCalendar()
    }
    val calDays = remember(calendar.days) { calendarDaysByDate(calendar.days) }
    val calMonths = remember(calDays) { calendarMonthsOf(calDays) }
    // One reconnect row for the whole page: an expired session breaks every
    // segment at once, so it should not need four taps to fix.
    val expired = listOf(
        academic.error,
        calendar.error,
        academic.examsError,
        academic.gradesError
    ).any { it.contains("过期") || it.contains("验证码") }
    val headerItems = 1
    val listState = rememberLazyListState()
    // The calendar covers the whole term (August → January). Opening it on
    // August would make the reader scroll every time, so land on the current
    // month the first time it is shown — the same thing the full client does.
    LaunchedEffect(segment, calMonths.size, headerItems) {
        if (segment == 1 && calMonths.isNotEmpty()) {
            val target = calMonths.indexOfFirst { it == YearMonth.from(todayAtCampusClock()) }
            if (target >= 0) listState.scrollToItem(headerItems + target)
        }
    }
    PaperPage(ambientSpec = themeMode.spec) {
        Column(modifier = Modifier.fillMaxSize()) {
            RinMasthead(
                dateLine = buildString {
                    append(formatDateLine(todayAtCampusClock()))
                    if (academic.week > 0) {
                        append(" · 第 ")
                        append(academic.week)
                        append(" 周")
                    }
                },
                title = "校务",
                avatarText = avatarText,
                onRefresh = vm::refreshAcademic,
                onTheme = onToggleTheme,
                compact = true
            )
            // Fixed above the scroll area: the calendar's month list is long, and
            // the reader must be able to jump to 空教室 / 考试 / 成绩 from anywhere
            // in it.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SegmentTab("空教室", segment == 0) { segment = 0 }
                SegmentTab("校历", segment == 1) { segment = 1 }
                SegmentTab("考试", segment == 2) { segment = 2 }
                SegmentTab("成绩", segment == 3) { segment = 3 }
            }
            // One reconnect row for the whole page: an expired session breaks
            // every segment at once, so it should not need four taps to fix.
            if (expired) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "教务会话已过期",
                        color = colors.warning,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = relogin) {
                        Text(
                            text = "重新登录",
                            color = colors.signal,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 96.dp)
            ) {
                when (segment) {
                    0 -> roomSection(vm = vm, academic = academic, connected = appSettings.jwxtConnectedAt.isNotBlank(), onOpenSettings = onOpenSettings)
                    1 -> {
                        item(key = "cal-head") {
                            CalendarHead(
                                state = calendar,
                                markedCount = manual.dayMarks.size,
                                onRetry = vm::refreshAcademic,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        val examsByDate = academic.exams.groupBy { exam ->
                            parseExamTime(exam.time).date
                        }.filterKeys { it != null }.mapKeys { it.key!! }
                        val marks = manual.dayMarks.associateBy { it.date }
                        items(calMonths, key = { "cal-$it" }) { month ->
                            MonthGrid(
                                month = month,
                                days = calendarMonthDays(calDays, month),
                                today = todayAtCampusClock(),
                                examsByDate = examsByDate,
                                marks = marks,
                                onDayClick = { openedCalDay = it }
                            )
                        }
                    }
                    2 -> examSection(academic)
                    else -> gradeSection(academic)
                }
            }
        }
        val sheetDay = openedCalDay
        if (sheetDay != null) {
            val date = runCatching { LocalDate.parse(sheetDay.date) }.getOrNull()
            val week = weekNumberFromLabel(sheetDay.week)
            val courses = if (date != null && week != null) {
                dayCourses(
                    termCourses = academic.termCourses,
                    manualCourses = manual.courses,
                    weekday = date.dayOfWeek.value,
                    week = week
                )
            } else {
                emptyList()
            }
            CalendarDaySheet(
                day = sheetDay,
                date = date,
                courses = courses,
                exams = date?.let { d -> academic.exams.filter { parseExamTime(it.time).date == d } }.orEmpty(),
                today = todayAtCampusClock(),
                mark = manual.dayMarks.firstOrNull { it.date == sheetDay.date },
                onMark = { kind -> vm.setManualDayMark(sheetDay.date, kind) },
                onDismiss = { openedCalDay = null }
            )
        }
    }
}

// ---- 空教室 ---------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.roomSection(
    vm: AppViewModel,
    academic: AcademicUiState,
    connected: Boolean,
    onOpenSettings: () -> Unit
) {
    val campuses = academic.rooms.map { it.campus }.filter { it.isNotBlank() }.distinct()
    item(key = "filters") {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            if (campuses.size > 1) {
                PaperSelectorRow(
                    options = listOf("全部" to "") + campuses.map { it to it },
                    selectedValue = academic.campus,
                    onSelect = vm::selectAcademicCampus
                )
            }
            PaperSelectorRow(
                options = (1..7).map { day ->
                    "一二三四五六日"[day - 1].toString() to day.toString()
                },
                selectedValue = academic.day.dayOfWeek.value.toString(),
                onSelect = { value -> vm.selectAcademicWeekday(value.toIntOrNull() ?: 1) }
            )
            PaperSelectorRow(
                options = ROOM_SLOTS.map { it.short to it.key },
                selectedValue = academic.slot.key,
                onSelect = { key ->
                    ROOM_SLOTS.firstOrNull { it.key == key }?.let(vm::selectAcademicSlot)
                }
            )
        }
    }
    item(key = "rooms-header") {
        val visible = academic.rooms.filter {
            academic.campus.isBlank() || it.campus == academic.campus
        }
        Column(modifier = Modifier.padding(top = 18.dp)) {
            SectionHeader(
                title = "空教室",
                meta = when {
                    academic.loading -> "查询中"
                    visible.isNotEmpty() -> "${visible.size} 间"
                    else -> null
                }
            )
            Text(
                text = buildString {
                    append(formatDateLine(academic.day))
                    append(" · ")
                    append(academic.slot.label)
                    append(" ")
                    append(academic.slot.start)
                    append("-")
                    append(academic.slot.end)
                },
                color = colorsSecondary(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
    if (academic.loading && academic.rooms.isEmpty()) {
        item(key = "rooms-loading") {
            LoadingLine(modifier = Modifier.padding(vertical = 20.dp))
        }
    }
    if (academic.error.isNotBlank()) {
        item(key = "rooms-error") {
            Column(modifier = Modifier.padding(vertical = 14.dp)) {
                Text(
                    text = academic.error,
                    color = LocalRinColors.current.warning,
                    style = MaterialTheme.typography.bodySmall
                )
                if (!connected) {
                    SuiSecondaryButton(
                        text = "去连接教务",
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    TextButton(onClick = vm::refreshAcademic) {
                        Text("重试", color = LocalRinColors.current.signal)
                    }
                }
            }
        }
    }
    if (!academic.loading && academic.error.isBlank() && academic.rooms.isEmpty()) {
        item(key = "rooms-empty") {
            Text(
                text = "这个时段没有查到空教室。换个节次或日期看看。",
                color = colorsSecondary(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
    val visible = academic.rooms.filter { academic.campus.isBlank() || it.campus == academic.campus }
    items(visible, key = { "${it.roomName}-${it.building}" }) { room ->
        FreeRoomRow(room = room, onClick = { vm.openRoomDetail(room) })
    }
    if (visible.isNotEmpty()) {
        item(key = "rooms-note") {
            Text(
                text = "空闲数据来自教务的空闲教室查询，只覆盖学校开放借用的教室；" +
                    "这里没有列出的教室，不代表一定在上课。",
                color = colorsSecondary(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

/**
 * One free classroom. It prints the room, where it is, how many seats it has
 * and the one fact this list is about — 空闲. No internal code, no guessed
 * course: this is a "where can I sit now" list, not a school admin screen.
 */
@Composable
private fun FreeRoomRow(room: JwxtFreeRoom, onClick: () -> Unit) {
    val colors = LocalRinColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
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
                    text = listOf(room.campus, room.building, room.area)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ") +
                        if (room.capacity > 0) " · 容纳 ${room.capacity} 人" else "",
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
                    .clip(RoundedCornerShape(50))
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

// ---- 教室详情 -------------------------------------------------------------

/**
 * The room helper's answer for one classroom: whether each of today's five
 * blocks is free, plus whatever the phone can honestly add — your own class in
 * this room. "在上什么课" for other classes is not something a student account
 * can read, so the page never invents it.
 */
@Composable
private fun RoomDetailPage(
    detail: RoomDetailUiState,
    nowMinutes: Int,
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    BackHandler(onBack = onBack)
    val currentSlot = roomSlotAt(nowMinutes)
    val currentState = detail.slots.firstOrNull { it.slot.key == currentSlot?.key }
    val today = todayAtCampusClock()
    PaperPage(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("返回", color = colors.text, style = MaterialTheme.typography.labelLarge)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)
            ) {
                item(key = "head") {
                    Column {
                        Text(
                            text = detail.roomName,
                            color = colors.text,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val place = listOfNotNull(
                            detail.campus.takeIf { it.isNotBlank() },
                            detail.building.takeIf { it.isNotBlank() },
                            detail.area.takeIf { it.isNotBlank() },
                            detail.capacity.takeIf { it > 0 }?.let { "$it 座" }
                        ).joinToString(" · ")
                        if (place.isNotBlank()) {
                            Text(
                                text = place,
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        RoomStatusLine(
                            detail = detail,
                            currentState = currentState,
                            currentSlot = currentSlot,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
                if (detail.loading) {
                    item(key = "loading") {
                        LoadingLine(modifier = Modifier.padding(vertical = 18.dp))
                    }
                }
                if (detail.error.isNotBlank()) {
                    item(key = "error") {
                        Text(
                            text = detail.error,
                            color = colors.warning,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 14.dp)
                        )
                    }
                }
                if (detail.myCourses.isNotEmpty()) {
                    item(key = "my-courses") {
                        Column(modifier = Modifier.padding(top = 20.dp)) {
                            SectionHeader(title = "你的课", meta = formatDateLine(today))
                            detail.myCourses.forEach { line ->
                                Text(
                                    text = line,
                                    color = colors.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                item(key = "today-header") {
                    Column(modifier = Modifier.padding(top = 20.dp)) {
                        SectionHeader(title = "今天", meta = formatDateLine(today))
                    }
                }
                items(detail.slots, key = { it.slot.key }) { state ->
                    RoomSlotRow(
                        state = state,
                        isNow = currentSlot?.key == state.slot.key
                    )
                }
                if (detail.alternatives.isNotEmpty()) {
                    item(key = "alternatives") {
                        Column(modifier = Modifier.padding(top = 20.dp)) {
                            SectionHeader(title = "同一时段还空着", meta = "${detail.alternatives.size} 间")
                            detail.alternatives.forEach { name ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenRoom(name) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = name,
                                        color = colors.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "查看",
                                        color = colors.textSecondary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Hairline()
                            }
                        }
                    }
                }
                item(key = "note") {
                    Text(
                        text = "空闲与占用来自教务的空闲教室查询，只覆盖学校开放借用的教室。" +
                            "其他班级在上什么课不向学生账号公开，所以这里不会编造课程名。",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomStatusLine(
    detail: RoomDetailUiState,
    currentState: RoomSlotState?,
    currentSlot: RoomSlot?,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    val (label, tint) = when {
        detail.loading -> "查询中" to colors.textSecondary
        currentSlot == null -> "今天的课已结束" to colors.textSecondary
        currentState == null -> "状态未知" to colors.textSecondary
        currentState.free == true -> "现在空闲" to colors.signal
        currentState.free == false -> "现在有课" to colors.warning
        else -> "教务未开放这间教室的占用信息" to colors.textSecondary
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tint)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                color = tint,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (currentSlot != null) {
            Text(
                text = "${currentSlot.label} · ${currentSlot.start}-${currentSlot.end}",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun RoomSlotRow(state: RoomSlotState, isNow: Boolean) {
    val colors = LocalRinColors.current
    val (text, tint) = when (state.free) {
        true -> "空闲" to colors.signal
        false -> "有课" to colors.warning
        null -> "—" to colors.textSecondary
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isNow) colors.signal.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent
                )
                .padding(vertical = 11.dp, horizontal = if (isNow) 10.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.slot.label,
                color = colors.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(84.dp)
            )
            Text(
                text = "${state.slot.start}-${state.slot.end}",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            if (isNow) {
                Text(
                    text = "现在",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                text = text,
                color = tint,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Hairline()
    }
}

// ---- 考试 / 成绩 ----------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.examSection(academic: AcademicUiState) {
    item(key = "exam-header") {
        Column(modifier = Modifier.padding(top = 18.dp)) {
            SectionHeader(
                title = "考试安排",
                meta = when {
                    academic.examsLoading -> "查询中"
                    academic.exams.isNotEmpty() -> "${academic.exams.size} 场"
                    else -> null
                }
            )
        }
    }
    if (academic.examsLoading && academic.exams.isEmpty()) {
        item(key = "exam-loading") { LoadingLine(modifier = Modifier.padding(vertical = 18.dp)) }
    }
    if (academic.examsError.isNotBlank()) {
        item(key = "exam-error") {
            Text(
                text = academic.examsError,
                color = LocalRinColors.current.warning,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
    if (!academic.examsLoading && academic.examsError.isBlank() && academic.exams.isEmpty()) {
        item(key = "exam-empty") {
            Text(
                text = "教务还没有公布本学期的考试安排。",
                color = colorsSecondary(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        }
    }
    items(academic.exams, key = { "${it.courseName}-${it.time}-${it.location}" }) { exam ->
        ExamRow(exam)
    }
}

@Composable
private fun ExamRow(exam: JwxtExam) {
    val colors = LocalRinColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        Text(
            text = exam.courseName,
            color = colors.text,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val detail = listOfNotNull(
            exam.time.takeIf { it.isNotBlank() },
            exam.location.takeIf { it.isNotBlank() },
            exam.seat.takeIf { it.isNotBlank() }?.let { "座位 $it" }
        ).joinToString(" · ")
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (exam.note.isNotBlank()) {
            Text(
                text = exam.note,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
    Hairline()
}

private fun androidx.compose.foundation.lazy.LazyListScope.gradeSection(academic: AcademicUiState) {
    val graded = academic.grades.filter { it.score.isNotBlank() }
    val credits = graded.sumOf { it.credits ?: 0.0 }
    val weighted = graded.mapNotNull { grade ->
        val point = grade.gradePoint ?: return@mapNotNull null
        val credit = grade.credits ?: return@mapNotNull null
        point * credit
    }.sum()
    val gpa = if (credits > 0) weighted / credits else null
    item(key = "grade-header") {
        Column(modifier = Modifier.padding(top = 18.dp)) {
            SectionHeader(
                title = "成绩",
                meta = when {
                    academic.gradesLoading -> "查询中"
                    graded.isNotEmpty() -> "${graded.size} 门"
                    else -> null
                }
            )
            if (graded.isNotEmpty()) {
                Text(
                    text = buildString {
                        append("已修 ")
                        append(String.format(java.util.Locale.US, "%.1f", credits))
                        append(" 学分")
                        if (gpa != null) {
                            append(" · 绩点 ")
                            append(String.format(java.util.Locale.US, "%.2f", gpa))
                        }
                    },
                    color = colorsSecondary(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
    if (academic.gradesLoading && academic.grades.isEmpty()) {
        item(key = "grade-loading") { LoadingLine(modifier = Modifier.padding(vertical = 18.dp)) }
    }
    if (academic.gradesError.isNotBlank()) {
        item(key = "grade-error") {
            Text(
                text = academic.gradesError,
                color = LocalRinColors.current.warning,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
    if (!academic.gradesLoading && academic.gradesError.isBlank() && graded.isEmpty()) {
        item(key = "grade-empty") {
            Text(
                text = "还没有已公布的成绩。",
                color = colorsSecondary(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        }
    }
    items(graded, key = { "${it.term}-${it.courseName}-${it.score}" }) { grade ->
        GradeRow(grade)
    }
}

@Composable
private fun GradeRow(grade: JwxtGrade) {
    val colors = LocalRinColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = grade.courseName,
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = grade.score,
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        val detail = listOfNotNull(
            grade.credits?.let { String.format(java.util.Locale.US, "%.1f 学分", it) },
            grade.gradePoint?.let { String.format(java.util.Locale.US, "绩点 %.1f", it) },
            grade.category.takeIf { it.isNotBlank() },
            grade.term.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
    Hairline()
}

// ---- 校历 ---------------------------------------------------------------

// ---- 小组件 ---------------------------------------------------------------

@Composable
private fun SegmentTab(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun LoadingLine(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 1.6.dp,
            color = LocalRinColors.current.signal
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "正在问教务…",
            color = LocalRinColors.current.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** Reads the theme's secondary ink without threading colors through every helper. */
@Composable
private fun colorsSecondary(): androidx.compose.ui.graphics.Color = LocalRinColors.current.textSecondary
