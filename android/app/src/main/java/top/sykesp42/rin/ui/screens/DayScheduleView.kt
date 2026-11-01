package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import top.sykesp42.rin.data.ApiScheduleDay
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.ui.formatDateLine
import top.sykesp42.rin.ui.schedule.ClassSlotState
import top.sykesp42.rin.ui.schedule.classSlotState
import top.sykesp42.rin.ui.schedule.courseCountLabel
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.courseAccent
import top.sykesp42.rin.ui.theme.courseTint

@Composable
internal fun DayScheduleView(
    days: List<ApiScheduleDay>,
    selectedDate: LocalDate,
    today: LocalDate,
    mark: String,
    onSelectDate: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onToday: () -> Unit,
    onCourseTap: (ApiScheduleEvent) -> Unit
) {
    val colors = LocalRinColors.current
    val dark = colors.canvas.luminance() < 0.4f
    val day = days.firstOrNull { it.date == selectedDate.toString() }
    val nowMinutes = nowMinutesAt()
    val isToday = selectedDate == today

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ 周", color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                text = formatDateLine(selectedDate),
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (!isToday) {
                TextButton(onClick = onToday) {
                    Text("今天", color = colors.signal, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (mark.isNotBlank()) {
            Text(
                text = mark,
                color = colors.signal,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            days.forEach { item ->
                val date = runCatching { LocalDate.parse(item.date) }.getOrNull() ?: return@forEach
                val selected = date == selectedDate
                val itemToday = date == today
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) colors.signal.copy(alpha = 0.12f) else colors.surface)
                        .border(
                            1.dp,
                            if (selected) colors.signal else colors.border.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectDate(date) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = weekdayShort(date.dayOfWeek.value),
                        color = if (selected) colors.signal else colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = if (selected) colors.signal else colors.text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (itemToday || selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .height(2.dp)
                            .width(if (item.events.isEmpty()) 0.dp else 14.dp)
                            .background(
                                if (itemToday) colors.signal else colors.accent,
                                CircleShape
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (day == null || day.events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("这一天没有课", color = colors.text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "可以切到周表添加课程。",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(day.events, key = { "${it.courseName}-${it.startTime}-${it.sectionText}" }) { event ->
                val state = if (isToday) {
                    classSlotState(event.startTime, event.endTime, nowMinutes)
                } else {
                    ClassSlotState.UPCOMING
                }
                val active = state == ClassSlotState.CURRENT
                val past = state == ClassSlotState.PAST
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) courseTint(event.courseName, dark) else colors.surface)
                        .border(
                            1.dp,
                            if (active) courseAccent(event.courseName, dark) else colors.border.copy(alpha = 0.6f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onCourseTap(event) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.width(54.dp)) {
                        Text(
                            text = event.startTime.ifBlank { "—" },
                            color = courseAccent(event.courseName, dark),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = event.endTime,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(34.dp)
                            .background(courseAccent(event.courseName, dark), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = event.courseName,
                            color = if (past) colors.textSecondary else colors.text,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOfNotNull(
                                event.sectionText.takeIf { it.isNotBlank() }?.let { "$it 节" },
                                event.location.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (active) {
                        Text("正在上", color = colors.signal, style = MaterialTheme.typography.labelSmall)
                    } else if (past) {
                        Text("已结束", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Text(
                    text = courseCountLabel(day.events.size),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
            }
        }
    }
}

private fun weekdayShort(value: Int): String =
    listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(value - 1) { "" }
