package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import top.sykesp42.rin.data.ApiScheduleDay
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.data.JwxtTermCourse
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.LocalCourseOverrides
import top.sykesp42.rin.ui.components.courseDisplayName
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.schedule.ClassSlotState
import top.sykesp42.rin.ui.schedule.classSlotState
import top.sykesp42.rin.ui.schedule.courseCountLabel
import top.sykesp42.rin.ui.schedule.displayedToday
import top.sykesp42.rin.ui.schedule.nextUpcomingIndex
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.schedule.restHint
import top.sykesp42.rin.ui.schedule.todayAnchor
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.courseAccent

private val weekdayShort = listOf("一", "二", "三", "四", "五", "六", "日")
private val weekdayLong = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** The weekday the day view opens on: the shown week's today, else the clock's. */
internal fun initialDayFocus(overview: ApiScheduleOverview): Int {
    val days = weekDays(overview)
    val dates = days.map { it.date }
    val today = displayedToday(dates)
    return today?.dayOfWeek?.value ?: todayAtCampusClock().dayOfWeek.value
}

/**
 * One step through the week, spilling into the neighbouring week when the edge
 * is crossed, so a swipe always moves exactly one day.
 */
internal fun stepDay(from: Int, delta: Int): Pair<Int, Int> {
    val next = from + delta
    return when {
        next in 1..7 -> next to 0
        next > 7 -> 1 to 1
        else -> 7 to -1
    }
}

/**
 * [stepDay] with the user's 跨周 preference applied: when crossing is off the week
 * edge is a wall and the step is refused, so a swipe there does nothing instead of
 * quietly flipping the week.
 */
internal fun stepDayWithinWeek(from: Int, delta: Int, crossWeek: Boolean): Pair<Int, Int>? {
    val step = stepDay(from, delta)
    return if (!crossWeek && step.second != 0) null else step
}

/**
 * A2 day-first schedule: a seven-day paper strip, one large date and a quiet
 * timeline. No outer card; only hairlines and a pale sky wash for the current
 * row. Selection and swipe handling live in [ScheduleScreen] so this view stays
 * presentational.
 */
@Composable
fun ScheduleDayView(
    overview: ApiScheduleOverview,
    pickedWeekday: Int,
    onWeekdayPick: (Int) -> Unit,
    onCourseTap: (ApiScheduleEvent) -> Unit
) {
    val colors = LocalRinColors.current
    // Live school-clock minute: recomposed on a 30s tick so "现在" follows the
    // real class window instead of the server snapshot taken at fetch time.
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
    val nowMinutes = nowMinutesAt(java.time.Instant.ofEpochMilli(tick))
    // Spec 4.2: mark today only when it belongs to the displayed week. A stale
    // overview.date from another week must not win over the school clock.
    val days = remember(overview) {
        val d = weekDays(overview)
        if (d.isNotEmpty()) d
        else {
            // Stale cache without days: synthesize a single-day view from today.
            val todayDate = todayAtCampusClock().toString()
            val weekday = todayAtCampusClock().dayOfWeek.value
            listOf(
                top.sykesp42.rin.data.ApiScheduleDay(
                    date = todayDate, weekday = weekday, events = overview.today
                )
            )
        }
    }
    val today = displayedToday(days.map { it.date })
    val todayString = today?.toString()
        ?: todayAtCampusClock().toString()
    // Today positioning: the displayed week's today first; when the shown week
    // is stale (cached from another week), fall back to the school clock's
    // weekday instead of silently landing on Monday.
    val weekday = pickedWeekday.takeIf { it in 1..7 } ?: initialDayFocus(overview)
    val day = days.firstOrNull { it.weekday == weekday } ?: days.first()
    // Weekday match ONLY for the synthesized single-day view (a stale cache
    // without dates). A full week whose dates don't contain today is another
    // week — marking its same-weekday day as "today" would strike through
    // last week's Wednesday with this week's clock.
    val isToday = day.date == todayString ||
        (today == null && days.size == 1 && day.weekday == todayAtCampusClock().dayOfWeek.value)
    // Live slot states only for today; other days stay neutral (no "现在",
    // no strike-through) because comparing their clock to today's minute is
    // meaningless.
    val slotStates = if (isToday) {
        day.events.map { classSlotState(it.startTime, it.endTime, nowMinutes) }
    } else {
        List(day.events.size) { null }
    }
    val nextIndex = nextUpcomingIndex(slotStates.filterNotNull())
    val hint = if (isToday) {
        restHint(day.events.map { it.startTime to it.endTime }, nowMinutes)
    } else {
        null
    }
    val restAfter = when (hint) {
        null -> -1
        "还没开课" -> -1
        "今天课上完了" -> day.events.lastIndex
        else -> slotStates.indexOfLast { it == ClassSlotState.PAST }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        WeekStrip(
            days = days,
            todayDate = todayString,
            selectedWeekday = weekday,
            onSelect = onWeekdayPick
        )
        Spacer(Modifier.height(14.dp))
        DayHeading(day = day, weekday = weekday, isToday = isToday, classCount = day.events.size)
        Hairline()
        if (day.events.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp)
            ) {
                Text(
                    text = if (isToday) "今天没有课" else "这天没有课",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text
                )
                Text(
                    text = "可以切到周表添加课程，",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        } else {
            if (restAfter < 0 && hint != null) RestBanner(hint)
            day.events.forEachIndexed { index, event ->
                DayTimelineRow(
                    event = event,
                    slot = slotStates[index],
                    isNext = index == nextIndex,
                    onCourseTap = { onCourseTap(event) }
                )
                if (index == restAfter && hint != null) RestBanner(hint)
            }
        }
    }
}

/** Seven cells: today gets a sky dot, the selected day a 2dp sky underline. */
@Composable
private fun WeekStrip(
    days: List<ApiScheduleDay>,
    todayDate: String,
    selectedWeekday: Int,
    onSelect: (Int) -> Unit
) {
    val colors = LocalRinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
    ) {
        days.forEach { day ->
            val selected = day.weekday == selectedWeekday
            val isToday = day.date == todayDate ||
                (todayDate.isBlank() && day.weekday == todayAtCampusClock().dayOfWeek.value)
            val date = parseScheduleDate(day.date)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(day.weekday) },
                        role = Role.Tab
                    )
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(4.dp)
                        .background(if (isToday) colors.signal else Color.Transparent, CircleShape)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = weekdayShort[(day.weekday - 1).coerceIn(0, 6)],
                    color = if (selected || isToday) colors.text else colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = date?.dayOfMonth?.toString()
                        ?: day.date.takeLast(2).trimStart('0').ifBlank { "–" },
                    color = if (selected) colors.text else colors.textSecondary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(18.dp)
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
private fun DayHeading(day: ApiScheduleDay, weekday: Int, isToday: Boolean, classCount: Int) {
    val colors = LocalRinColors.current
    val date = parseScheduleDate(day.date)
    val dayNumber = date?.dayOfMonth?.toString()
        ?: day.date.takeLast(2).trimStart('0').ifBlank { "–" }
    val meta = listOfNotNull(
        date?.let { "${it.monthValue} 月" },
        courseCountLabel(classCount)
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = dayNumber,
            color = colors.text,
            style = MaterialTheme.typography.displayMedium,
            maxLines = 1
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.padding(bottom = 5.dp)) {
            Text(
                text = weekdayLong[(weekday - 1).coerceIn(0, 6)] + if (isToday) " · 今天" else "",
                color = colors.text,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = meta,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * One paper timeline row. `slot` is null for non-today days (neutral), or the
 * live UPCOMING/CURRENT/PAST state computed on the school clock. The whole row
 * is tappable and opens the course detail sheet.
 */
@Composable
private fun DayTimelineRow(
    event: ApiScheduleEvent,
    slot: ClassSlotState?,
    isNext: Boolean,
    onCourseTap: () -> Unit
) {
    val colors = LocalRinColors.current
    val dark = colors.canvas.luminance() < 0.4f
    val displayName = courseDisplayName(event.courseName, LocalCourseOverrides.current)
    val ink = courseAccent(displayName, dark)
    val isCurrent = slot == ClassSlotState.CURRENT
    val isPast = slot == ClassSlotState.PAST
    val wash = colors.signal.copy(alpha = 0.07f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(if (isCurrent) wash else Color.Transparent)
                .drawBehind {
                    if (isCurrent) {
                        drawRect(
                            color = colors.signal,
                            topLeft = Offset.Zero,
                            size = Size(2.dp.toPx(), size.height)
                        )
                    }
                }
                .clickable(onClick = onCourseTap)
                .padding(
                    start = if (isCurrent) 12.dp else 0.dp,
                    end = 0.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 58dp keeps "08:00" unclipped at font_scale 1.3 with monospace 13sp.
            Column(modifier = Modifier.widthIn(min = 58.dp)) {
                Text(
                    text = event.startTime.ifBlank { "--:--" },
                    color = if (isPast) colors.textSecondary else colors.text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    maxLines = 1
                )
                if (event.endTime.isNotBlank()) {
                    Text(
                        text = event.endTime,
                        color = if (isCurrent) colors.text else colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        maxLines = 1
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = if (isPast) colors.textSecondary else ink,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (isPast) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = listOfNotNull(
                    if (isCurrent) "现在" else if (isPast) "已上完" else if (isNext) "下一节" else null,
                    event.sectionText.takeIf { it.isNotBlank() },
                    event.location.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        color = if (isCurrent) colors.text else colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when {
                isCurrent -> Text(
                    text = "现在",
                    color = colors.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                isPast -> Text(
                    text = "已上完",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
                isNext -> Text(
                    text = "下一节",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Hairline()
    }
}

/** Whole-term summary kept as a paper list; tapping a row opens the course sheet. */
@Composable
internal fun TermOverview(
    courses: List<JwxtTermCourse>,
    loading: Boolean,
    currentWeek: Int,
    onOpen: (String) -> Unit
) {
    val colors = LocalRinColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "学期总览", meta = if (courses.isEmpty()) null else "${courses.size} 门")
        when {
            loading -> Text(
                text = "正在读取全学期课程…",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            courses.isEmpty() -> Text(
                text = "还没有课程数据",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            else -> courses.forEach { course ->
                TermCourseRow(
                    course = course,
                    currentWeek = currentWeek,
                    onOpen = { onOpen(course.courseName) }
                )
            }
        }
    }
}

@Composable
private fun TermCourseRow(course: JwxtTermCourse, currentWeek: Int, onOpen: () -> Unit) {
    val colors = LocalRinColors.current
    val inProgress = currentWeek in course.firstWeek..course.lastWeek
    val span = (course.lastWeek - course.firstWeek + 1).coerceAtLeast(1)
    val progress = if (currentWeek <= 0) {
        0f
    } else {
        ((currentWeek - course.firstWeek + 1).coerceIn(0, span).toFloat() / span)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inProgress) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(colors.signal)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = course.courseName,
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (inProgress) {
                Text(
                    text = "进行中",
                    color = colors.text,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Text(
            text = listOf(course.teacher, course.location)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "教室待定" },
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "第${course.firstWeek}-${course.lastWeek}周 · 共${course.weekCount}次课",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(4.dp)
                .background(colors.border, MaterialTheme.shapes.extraSmall)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(colors.signal, MaterialTheme.shapes.extraSmall)
            )
        }
        course.slots.take(3).forEach { slot ->
            Text(
                text = "周${weekdayShort[(slot.weekday - 1).coerceIn(0, 6)]} 第${slot.sectionText}节" +
                    (if (slot.location.isNotBlank()) " · ${slot.location}" else ""),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
    Hairline()
}

private fun parseScheduleDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()

/**
 * The week strip needs seven dated cells. Server data normally has them; when
 * only today's events exist (mock/legacy payload), derive the Monday-Sunday
 * dates around the overview date and keep the known events under today.
 */
private fun weekDays(overview: ApiScheduleOverview): List<ApiScheduleDay> {
    val provided = overview.days.associateBy { it.weekday }
    val reference = todayAnchor(overview.date) ?: todayAtCampusClock()
    val monday = reference.with(DayOfWeek.MONDAY)
    return (1..7).map { weekday ->
        provided[weekday] ?: ApiScheduleDay(
            date = monday.plusDays((weekday - 1).toLong()).toString(),
            weekday = weekday,
            events = if (weekday == reference.dayOfWeek.value) overview.today else emptyList()
        )
    }
}
