package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import top.sykesp42.rin.data.ChecklistItem
import top.sykesp42.rin.data.CourseReminder
import top.sykesp42.rin.data.ReminderPrefs
import top.sykesp42.rin.ui.CourseDetailUiState
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.RinSheet
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.components.suiTextFieldColors
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.courseAccent

/**
 * Course detail as a transient paper sheet. It replaces the old inline card and
 * keeps every existing field: assessment, teacher/location, slots, room code,
 * monitor action, free-room link and the teaching calendar.
 */
@Composable
fun CourseDetailSheet(
    detail: CourseDetailUiState,
    currentWeek: Int,
    fallbackLocation: String = "",
    fallbackSectionText: String = "",
    override: top.sykesp42.rin.data.CourseOverride = top.sykesp42.rin.data.CourseOverride(),
    onOverride: (String, Int) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onOpenFreeRooms: (Int, String) -> Unit,
    checklist: List<ChecklistItem> = emptyList(),
    onSaveChecklist: (String, List<ChecklistItem>) -> Unit = { _, _ -> },
    reminderPrefs: ReminderPrefs = ReminderPrefs(),
    reminders: List<CourseReminder> = emptyList(),
    onSaveReminder: (CourseReminder, ReminderPrefs) -> Boolean = { _, _ -> false },
    onDeleteReminder: (String) -> Unit = {},
) {
    if (detail.courseName.isBlank()) return
    val colors = LocalSuiSignColors.current
    val location = detail.location.ifBlank { fallbackLocation }
    // Only a verified code may be shown or monitored. `一教 0101` is a room
    // label, not the four-digit attendance code; extracting it here silently
    // armed the wrong target.
    val roomCode = detail.roomCode
    RinSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = detail.courseName,
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
        if (detail.assessMode.isNotBlank()) {
            Text(
                text = "考核方式：${detail.assessMode}",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Hairline()
        if (detail.teacher.isNotBlank() || location.isNotBlank()) {
            Text(
                text = listOf(detail.teacher, location)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        detail.slots.forEach { slot ->
            val weeks = if (slot.firstWeek == slot.lastWeek) {
                "第 ${slot.firstWeek} 周"
            } else {
                "第 ${slot.firstWeek}-${slot.lastWeek} 周"
            }
            Text(
                text = "周${"一二三四五六日"[(slot.weekday - 1).coerceIn(0, 6)]} " +
                    "第 ${slot.sectionText} 节 · $weeks",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (detail.slots.isEmpty() && fallbackSectionText.isNotBlank()) {
            Text(
                text = "节次 $fallbackSectionText",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        CourseReminderSection(
            courseName = detail.courseName,
            location = location,
            dateHint = detail.reminderDate,
            startHint = detail.reminderStart,
            endHint = detail.reminderEnd,
            slots = detail.slots,
            prefs = reminderPrefs,
            reminders = reminders,
            onSave = onSaveReminder,
            onDelete = onDeleteReminder
        )
        Hairline()
        Text(
            text = "教学周历",
            color = colors.text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp)
        )
        when {
            detail.loading -> Text(
                text = "正在读取教学周历…",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
            detail.entries.isEmpty() -> Text(
                text = "还没有周历数据",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
            else -> {
                // Collapsed by default: a 15-week calendar buried the checklist
                // and monitor actions under dozens of rows. Default view is the
                // current week; everything stays one tap away.
                var showAllEntries by remember(detail.courseName) { mutableStateOf(false) }
                val shownEntries = if (showAllEntries) {
                    detail.entries
                } else {
                    detail.entries.filter { it.week == currentWeek }
                        .ifEmpty { detail.entries.take(3) }
                }
                shownEntries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (entry.week == currentWeek) {
                            Box(
                                Modifier
                                    .padding(top = 5.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(colors.signal)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = "第 ${entry.week} 周 · ${entry.mode.ifBlank { "教学" }}" +
                                (if (entry.content.isNotBlank()) " · ${entry.content}" else "") +
                                (if (entry.location.isNotBlank()) " · ${entry.location}" else ""),
                            color = if (entry.week == currentWeek) colors.text else colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                TextButton(
                    onClick = { showAllEntries = !showAllEntries },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = if (showAllEntries) "收起周历"
                        else "展开全部 ${detail.entries.size} 条",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        Hairline()
        Text(
            text = "课前准备",
            color = colors.text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
        )
        if (detail.checklistLoading) {
            Text(
                text = "正在读取课前准备…",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        } else if (checklist.isEmpty()) {
            Text(
                text = "还没有准备项",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        checklist.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = { done ->
                        val next = checklist.toMutableList()
                        next[index] = item.copy(done = done)
                        onSaveChecklist(detail.courseName, next)
                    }
                )
                Text(
                    text = item.text,
                    color = if (item.done) colors.textSecondary else colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        onSaveChecklist(
                            detail.courseName,
                            checklist.filterIndexed { itemIndex, _ -> itemIndex != index }
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("删除", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        var newItem by remember(detail.courseName) { mutableStateOf("") }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = newItem,
                onValueChange = { newItem = it },
                placeholder = { Text("带什么/做什么…") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    if (newItem.isNotBlank()) {
                        onSaveChecklist(
                            detail.courseName,
                            checklist + ChecklistItem(text = newItem.trim())
                        )
                        newItem = ""
                    }
                },
                enabled = newItem.isNotBlank()
            ) { Text("添加") }
        }
        var moreOpen by remember(detail.courseName) { mutableStateOf(false) }
        TextButton(
            onClick = { moreOpen = !moreOpen },
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Text(
                text = if (moreOpen) "收起更多操作" else "更多操作",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (moreOpen) {
        if (detail.freeRoomDay in 1..7 && detail.freeRoomSections.isNotBlank()) {
            SuiSecondaryButton(
                text = "查该课时段空教室",
                onClick = { onOpenFreeRooms(detail.freeRoomDay, detail.freeRoomSections) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }
        Hairline()
        var aliasDraft by remember(detail.courseName) { mutableStateOf(override.alias) }
        var colorDraft by remember(detail.courseName) { mutableIntStateOf(override.colorIndex) }
        val overrideDirty = aliasDraft.trim() != override.alias || colorDraft != override.colorIndex
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "个性化",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (overrideDirty) {
                TextButton(
                    onClick = { onOverride(aliasDraft.trim(), colorDraft) },
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text("保存", color = colors.signal, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        OutlinedTextField(
            value = aliasDraft,
            onValueChange = { aliasDraft = it },
            label = { Text("显示别名") },
            placeholder = { Text(detail.courseName) },
            singleLine = true,
            colors = suiTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        val draftName = aliasDraft.trim().ifBlank { detail.courseName } +
            "\u200B".repeat(colorDraft)
        val draftInk = courseAccent(draftName, colors.canvas.luminance() < 0.4f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(draftInk)
            )
            Text(
                text = "颜色",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            if (colorDraft != 0) {
                TextButton(
                    onClick = { colorDraft = 0 },
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text("默认", color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(
                onClick = { colorDraft = (colorDraft + 1) % 8 },
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text(
                    text = "换一色",
                    color = colors.text,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        }
        Spacer(Modifier.height(8.dp))
    }
}

