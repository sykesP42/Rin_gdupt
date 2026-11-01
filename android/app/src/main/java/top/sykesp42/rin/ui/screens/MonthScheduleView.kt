package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.ManualDayMark
import top.sykesp42.rin.data.ManualPayload
import top.sykesp42.rin.data.buildWeekOverview
import top.sykesp42.rin.data.termStartDate
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.theme.LocalRinColors

@Composable
internal fun MonthScheduleView(
    payload: ManualPayload,
    settings: AppSettings,
    today: LocalDate,
    onBack: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    jwxt: top.sykesp42.rin.data.ApiScheduleOverview? = null
) {
    val colors = LocalRinColors.current
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    // Dates covered by the loaded JWXT week come from the school; the rest of
    // the month still renders from the local manual timetable.
    val jwxtByDate = remember(jwxt) {
        jwxt?.days?.associateBy { it.date }.orEmpty()
    }
    val eventsByDate = remember(month, payload, settings, jwxtByDate) {
        val result = linkedMapOf<LocalDate, List<ApiScheduleEvent>>()
        var date = month.atDay(1)
        while (date.month == month.month) {
            val fromJwxt = jwxtByDate[date.toString()]?.events
            if (fromJwxt != null) {
                result[date] = fromJwxt
            } else {
                val week = weekFor(date, settings)
                val overview = buildWeekOverview(payload, settings, week, date, nowMinutesAt())
                result[date] = overview.days.firstOrNull { it.date == date.toString() }?.events.orEmpty()
            }
            date = date.plusDays(1)
        }
        result
    }
    val marks = remember(payload.dayMarks) { payload.dayMarks.associateBy { it.date } }
    val cells = remember(month) {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        val values = MutableList<LocalDate?>(leading) { null }
        (1..days).forEach { values += month.atDay(it) }
        while (values.size % 7 != 0) values += null
        values
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ 周", color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                text = "${month.year}年${month.monthValue}月",
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { month = month.minusMonths(1) }) {
                Text("‹", color = colors.textSecondary, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { month = month.plusMonths(1) }) {
                Text("›", color = colors.textSecondary, style = MaterialTheme.typography.titleMedium)
            }
            if (month != YearMonth.from(today)) {
                TextButton(onClick = { month = YearMonth.from(today) }) {
                    Text("本月", color = colors.signal, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val events = eventsByDate[date].orEmpty()
                        val mark = marks[date.toString()]
                        val isToday = date == today
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isToday) colors.signal.copy(alpha = 0.12f)
                                    else colors.surface.copy(alpha = 0.55f)
                                )
                                .border(
                                    1.dp,
                                    if (isToday) colors.signal else colors.border.copy(alpha = 0.45f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onPickDate(date) }
                                .padding(5.dp)
                        ) {
                            Column {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    color = if (isToday) colors.signal else colors.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
                                )
                                if (events.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.padding(top = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        repeat(events.size.coerceAtMost(3)) {
                                            Box(
                                                Modifier
                                                    .width(4.dp)
                                                    .height(4.dp)
                                                    .background(colors.accent, CircleShape)
                                            )
                                        }
                                        if (events.size > 3) {
                                            Text(
                                                text = "+",
                                                color = colors.textSecondary,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                                if (mark != null) {
                                    Box(
                                        Modifier
                                            .padding(top = 3.dp)
                                            .width(14.dp)
                                            .height(2.dp)
                                            .background(markColor(mark.kind), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(4.dp).height(4.dp).background(colors.accent, CircleShape))
            Text(
                " 有课",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.width(14.dp))
            Box(Modifier.width(14.dp).height(2.dp).background(colors.warning, CircleShape))
            Text(
                " 标记日",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            text = "点一天即可查看当天的课。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )
    }
}

private fun weekFor(date: LocalDate, settings: AppSettings): Int {
    val start = termStartDate(settings, date)
    if (date.isBefore(start)) return 1
    return ((ChronoUnit.DAYS.between(start, date) / 7).toInt() + 1).coerceIn(1, 30)
}

@Composable
private fun markColor(kind: String): Color {
    val colors = LocalRinColors.current
    return when (kind) {
        ManualDayMark.EXAM -> colors.signal
        ManualDayMark.MAKEUP -> colors.accent
        ManualDayMark.OFF -> colors.warning
        else -> colors.textSecondary
    }
}
