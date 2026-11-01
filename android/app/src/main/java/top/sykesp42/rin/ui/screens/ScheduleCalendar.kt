package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import top.sykesp42.rin.data.JwxtCalendarDay
import top.sykesp42.rin.data.JwxtExam
import top.sykesp42.rin.data.ManualDayMark
import top.sykesp42.rin.ui.CalendarUiState
import top.sykesp42.rin.ui.components.EmptyState
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.RinSheet
import top.sykesp42.rin.ui.components.ScheduleErrorRow
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.formatDateLine
import top.sykesp42.rin.ui.schedule.DayCourse
import top.sykesp42.rin.ui.schedule.calendarMonthRows
import top.sykesp42.rin.ui.schedule.examCountdownLabel
import top.sykesp42.rin.ui.schedule.parseExamTime
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.courseAccent

/**
 * Calendar header block: term, progress/error, day counts and the legend. The
 * month grids themselves are emitted as separate list items by the caller so
 * the schedule list can pin the tabs and scroll to the current month.
 */
@Composable
internal fun CalendarHead(
    state: CalendarUiState,
    markedCount: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "重试"
) {
    val colors = LocalRinColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "校历",
            meta = state.termLabel.ifBlank { "本学期" }
        )
        if (state.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.signal,
                trackColor = colors.border
            )
        }
        if (state.error.isNotBlank()) {
            ScheduleErrorRow(state.error, onRetry = onRetry, actionLabel = actionLabel)
        }
        if (state.days.isEmpty()) {
            when {
                state.loading -> Text(
                    text = "正在读取校历…",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                state.error.isBlank() -> EmptyState(
                    title = "校历还没拉到",
                    body = "支持离线缓存"
                )
            }
            return@Column
        }
        Text(
            text = if (markedCount > 0) {
                "共 ${state.days.size} 天 · 已标记 $markedCount 天"
            } else {
                "共 ${state.days.size} 天 · 点击日期可自行标记"
            },
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
        )
        TermProgress(state.days)
        CalendarLegend()
    }
}

@Composable
private fun CalendarLegend() {
    val colors = LocalRinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegendItem(color = colors.accent, label = "今天", ring = true)
        LegendItem(color = colors.text, label = "考试")
        LegendItem(color = colors.warning, label = "放假")
        LegendItem(color = colors.signal, label = "调休")
        LegendItem(color = colors.accent, label = "自习")
    }
}

@Composable
private fun dayMarkColor(kind: String): Color {
    val colors = LocalRinColors.current
    return when (kind) {
        ManualDayMark.OFF -> colors.warning
        ManualDayMark.MAKEUP -> colors.signal
        ManualDayMark.EXAM -> colors.text
        ManualDayMark.STUDY -> colors.accent
        else -> colors.textSecondary
    }
}

@Composable
private fun LegendItem(color: Color, label: String, ring: Boolean = false) {
    val colors = LocalRinColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .then(if (ring) Modifier.border(1.dp, color, CircleShape) else Modifier.clip(CircleShape).background(color))
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
internal fun MonthGrid(
    month: YearMonth,
    days: List<JwxtCalendarDay>,
    today: LocalDate,
    examsByDate: Map<LocalDate, List<JwxtExam>>,
    marks: Map<String, ManualDayMark>,
    onDayClick: (JwxtCalendarDay) -> Unit
) {
    val colors = LocalRinColors.current
    val weeks = days.mapNotNull { day ->
        Regex("\\d+").find(day.week)?.value?.toIntOrNull()
    }.distinct().sorted()
    val weekMeta = when {
        weeks.isEmpty() -> ""
        weeks.first() == weeks.last() -> "第 ${weeks.first()} 周"
        else -> "第 ${weeks.first()}-${weeks.last()} 周"
    }
    val rows = calendarMonthRows(
        month = month,
        days = days.associateBy { LocalDate.parse(it.date) }
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "${month.year} 年 ${month.monthValue} 月",
            meta = weekMeta
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp)
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        rows.forEach { row ->
            val firstDay = row.indexOfFirst { it != null }
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { index, day ->
                    if (day == null) {
                        Box(Modifier.weight(1f).height(52.dp))
                    } else {
                        CalendarDayCell(
                            day = day,
                            date = LocalDate.parse(day.date),
                            today = today,
                            exams = examsByDate[LocalDate.parse(day.date)].orEmpty(),
                            mark = marks[day.date],
                            showWeek = index == firstDay,
                            onClick = { onDayClick(day) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun CalendarDayCell(
    day: JwxtCalendarDay,
    date: LocalDate,
    today: LocalDate,
    exams: List<JwxtExam>,
    mark: ManualDayMark?,
    showWeek: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    val isToday = date == today
    val description = buildString {
        append("${date.monthValue} 月 ${date.dayOfMonth} 日")
        if (mark != null) append(" · ").append(ManualDayMark.label(mark.kind))
        if (day.week.isNotBlank()) append(" · ").append(day.week)
        if (isToday) append(" · 今天")
        if (exams.isNotEmpty()) append(" · ${exams.size} 场考试")
        if (day.note.isNotBlank()) append(" · ").append(day.note)
    }

    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .then(
                        if (isToday) Modifier.border(1.dp, colors.accent, CircleShape) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = colors.text,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
            Text(
                text = mark?.let { ManualDayMark.label(it.kind) } ?: " ",
                color = mark?.let { dayMarkColor(it.kind) } ?: Color.Transparent,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 1.dp)
            )
            if (showWeek && day.week.isNotBlank()) {
                Text(
                    text = day.week,
                    color = colors.textSecondary,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    maxLines = 1
                )
            }
        }
        if (exams.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.text)
            )
        }
    }
}

@Composable
internal fun CalendarDaySheet(
    day: JwxtCalendarDay,
    date: LocalDate?,
    courses: List<DayCourse>,
    exams: List<JwxtExam>,
    today: LocalDate,
    mark: ManualDayMark?,
    onMark: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRinColors.current
    val dark = colors.canvas.luminance() < 0.4f
    val isToday = date == today

    RinSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = date?.let(::formatDateLine) ?: day.date,
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("关闭", color = colors.text, style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isToday) DayChip("今天", colors.accent, filled = false)
            if (exams.isNotEmpty()) DayChip("考试日", colors.signal, filled = true)
            if (day.week.isNotBlank()) DayChip(day.week, colors.textSecondary, filled = false)
            if (mark != null) DayChip(ManualDayMark.label(mark.kind), dayMarkColor(mark.kind), filled = false)
        }

        DaySectionLabel("我的标记", mark?.let { ManualDayMark.label(it.kind) } ?: "未标记")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ManualDayMark.kinds.forEach { kind ->
                MarkChip(
                    label = ManualDayMark.label(kind),
                    color = dayMarkColor(kind),
                    selected = mark?.kind == kind,
                    onClick = { onMark(if (mark?.kind == kind) "" else kind) }
                )
            }
            if (mark != null) {
                MarkChip(
                    label = "清除",
                    color = colors.textSecondary,
                    selected = false,
                    onClick = { onMark("") }
                )
            }
        }

        if (exams.isNotEmpty()) {
            DaySectionLabel("考试", "${exams.size} 场")
            exams.forEach { exam ->
                DayExamCard(exam = exam, today = today, dark = dark)
                Spacer(Modifier.height(8.dp))
            }
        }

        if (courses.isNotEmpty()) {
            DaySectionLabel("课程", "${courses.size} 门")
            Hairline()
            courses.forEach { course ->
                DayCourseRow(course = course, dark = dark)
            }
        }

        if (day.note.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.warm.copy(alpha = if (dark) 0.18f else 0.55f))
                    .padding(horizontal = 13.dp, vertical = 11.dp)
            ) {
                Text(
                    text = day.note,
                    color = if (dark) colors.text else colors.warning,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MarkChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalRinColors.current
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = if (selected) 0.9f else 0.4f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.text else color,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun DayChip(text: String, color: Color, filled: Boolean) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (filled) color else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.dp, color.copy(alpha = 0.4f), CircleShape))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = if (filled) Color.White else color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun DaySectionLabel(title: String, count: String) {
    val colors = LocalRinColors.current
    Row(
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = colors.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        Text(text = count, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DayExamCard(exam: JwxtExam, today: LocalDate, dark: Boolean) {
    val colors = LocalRinColors.current
    val parsed = parseExamTime(exam.time)
    val clock = when {
        parsed.start.isNotBlank() && parsed.end.isNotBlank() -> "${parsed.start}-${parsed.end}"
        parsed.start.isNotBlank() -> parsed.start
        else -> exam.time
    }
    val detail = listOfNotNull(
        clock.takeIf { it.isNotBlank() },
        exam.location.takeIf { it.isNotBlank() },
        exam.seat.takeIf { it.isNotBlank() }?.let { "座号 $it" }
    ).joinToString(" · ")
    val countdown = examCountdownLabel(parsed, today)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.signal.copy(alpha = if (dark) 0.14f else 0.06f))
            .border(1.dp, colors.signal.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.signal)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("考", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(countdown, color = colors.signal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            text = exam.courseName,
            color = courseAccent(exam.courseName, dark),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
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
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DayCourseRow(course: DayCourse, dark: Boolean) {
    val colors = LocalRinColors.current
    val ink = courseAccent(course.courseName, dark)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .width(3.dp)
                .heightIn(min = 34.dp)
                .clip(CircleShape)
                .background(ink)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = course.courseName,
                color = ink,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                course.sectionText.takeIf { it.isNotBlank() }?.let { "第 $it 节" },
                course.location.takeIf { it.isNotBlank() },
                course.teacher.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    Hairline()
}

/**
 * 学期进度：只按校历的起止日期计算，不推断哪一天上课、哪一天放假。
 */
@Composable
private fun TermProgress(days: List<JwxtCalendarDay>) {
    val colors = LocalRinColors.current
    val today = todayAtCampusClock()
    val dates = days
        .mapNotNull { runCatching { java.time.LocalDate.parse(it.date) }.getOrNull() }
        .sorted()
    if (dates.isEmpty()) return
    val first = dates.first()
    val last = dates.last()
    val total = java.time.temporal.ChronoUnit.DAYS.between(first, last).coerceAtLeast(1)
    val elapsed = java.time.temporal.ChronoUnit.DAYS.between(first, today).toFloat()
    val fraction = (elapsed / total).coerceIn(0f, 1f)
    val remaining = java.time.temporal.ChronoUnit.DAYS.between(today, last)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(colors.border, MaterialTheme.shapes.extraSmall)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(colors.signal, MaterialTheme.shapes.extraSmall)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "学期进度 %.0f%%".format(fraction * 100),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.weight(1f))
            val tail = when {
                today.isAfter(last) -> "本学期已结束"
                today.isBefore(first) -> "距开学 ${java.time.temporal.ChronoUnit.DAYS.between(today, first)} 天"
                else -> "距学期结束 $remaining 天"
            }
            Text(
                text = tail,
                color = colors.text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}