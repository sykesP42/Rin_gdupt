package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import top.sykesp42.rin.data.ManualCourse
import top.sykesp42.rin.data.ReminderPrefs
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.CourseDetailUiState
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.RinSheet
import top.sykesp42.rin.ui.components.SectionTitle
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.schedule.parseSections
import top.sykesp42.rin.ui.schedule.sectionBounds
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.theme.LocalRinColors

@Composable
internal fun CourseDetailSheet(
    vm: AppViewModel,
    detail: CourseDetailUiState,
    onDismiss: () -> Unit
) {
    val colors = LocalRinColors.current
    val manual by vm.manualSchedule.payload.collectAsState()
    val reminders by vm.courseReminders.payload.collectAsState()
    var alias by remember(detail.courseName, detail.alias) { mutableStateOf(detail.alias) }
    val course = manual.courses.firstOrNull { it.name == detail.courseName }
    val dateHint = remember(detail.courseName, detail.weekday, manual.moves) {
        nextOccurrence(detail.weekday, course)
    }
    val startHint = remember(detail.sectionText) {
        parseSections(detail.sectionText).firstOrNull()
            ?.let { sectionBounds[it]?.first }
            ?.let { "%02d:%02d".format(it / 60, it % 60) }
            .orEmpty()
    }
    val endHint = remember(detail.sectionText) {
        parseSections(detail.sectionText).lastOrNull()
            ?.let { sectionBounds[it]?.second }
            ?.let { "%02d:%02d".format(it / 60, it % 60) }
            .orEmpty()
    }

    RinSheet(onDismiss = onDismiss) {
        Text(
            text = detail.courseName,
            color = colors.text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = listOfNotNull(
                detail.teacher.takeIf { it.isNotBlank() },
                detail.location.takeIf { it.isNotBlank() },
                detail.sectionText.takeIf { it.isNotBlank() }?.let { "第 $it 节" },
                "第 ${detail.firstWeek}-${detail.lastWeek} 周"
            ).joinToString(" · "),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )

        Hairline()
        SectionTitle("上课时间")
        detail.meetings.forEach { meeting ->
            Text(
                text = "${weekdayLabel(meeting.weekday)} · ${meeting.sectionText} 节" +
                    if (meeting.location.isNotBlank()) " · ${meeting.location}" else "",
                color = colors.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Hairline()
        SectionTitle("显示名")
        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it.take(20) },
            placeholder = { Text("留空则使用课程原名") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuiPrimaryButton(
                text = "保存",
                onClick = { vm.setCourseOverride(detail.courseName, alias, detail.colorIndex) },
                enabled = alias.trim() != detail.alias,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { vm.setCourseOverride(detail.courseName, "", detail.colorIndex) },
                enabled = detail.alias.isNotBlank()
            ) {
                Text("恢复原名", color = colors.textSecondary)
            }
        }

        Hairline()
        if (course != null) {
            CourseReminderSection(
                courseName = detail.courseName,
                location = detail.location,
                dateHint = dateHint,
                startHint = startHint,
                endHint = endHint,
                prefs = reminders.prefs,
                reminders = reminders.reminders,
                onSave = { reminder, prefs -> vm.saveReminder(reminder, prefs) },
                onDelete = vm::removeCourseReminderById
            )
        } else {
            Text(
                text = "手动添加的课程可以设置本机提醒。",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Text(
            text = "提醒只保存在本机。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )
    }
}

private fun nextOccurrence(weekday: Int, course: ManualCourse?): String {
    val today = todayAtCampusClock()
    if (weekday !in 1..7) return today.toString()
    val target = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday)))
    val week = if (course == null) 0 else {
        ((target.toEpochDay() - today.toEpochDay()) / 7).toInt()
    }
    val adjusted = if (course != null && week == 0 && target.isBefore(today)) target.plusWeeks(1) else target
    return adjusted.toString()
}

private fun weekdayLabel(value: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(value - 1) { "待定" }
