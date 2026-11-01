package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID
import top.sykesp42.rin.data.ManualCourse
import top.sykesp42.rin.data.ManualExam
import top.sykesp42.rin.data.ManualMove
import top.sykesp42.rin.data.ManualPayload
import top.sykesp42.rin.data.ManualTodo
import top.sykesp42.rin.data.findCourseConflict
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.RinSheet
import top.sykesp42.rin.ui.components.SuiAlertDialog
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.components.suiTextFieldColors
import top.sykesp42.rin.ui.theme.LocalRinColors

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 手动数据: an extra course, an exam, a todo, or a one-off move. Manual rows
 * always win over generated ones, and the store lives outside the schedule
 * cache so rebuilding the week never erases them.
 */
@Composable
internal fun ManualDataSheet(
    payload: ManualPayload,
    existingNames: Set<String>,
    onAddCourse: (ManualCourse) -> Unit,
    onRemoveCourse: (String) -> Unit,
    onAddExam: (ManualExam) -> Unit,
    onRemoveExam: (String) -> Unit,
    onAddTodo: (ManualTodo) -> Unit,
    onToggleTodo: (String) -> Unit,
    onRemoveTodo: (String) -> Unit,
    onAddMove: (ManualMove) -> Unit,
    onRemoveMove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRinColors.current
    var tab by remember { mutableIntStateOf(0) }
    var pendingConflict by remember { mutableStateOf<ManualCourse?>(null) }

    fun submitCourse(course: ManualCourse) {
        val clashManual = findCourseConflict(payload.courses, course) != null
        val clashExisting = existingNames.any { it.trim() == course.name.trim() }
        if (clashManual || clashExisting) pendingConflict = course else onAddCourse(course)
    }

    RinSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "手动数据",
                    color = colors.text,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("关闭", color = colors.textSecondary) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf("课程", "考试", "待办", "调课").forEachIndexed { index, label ->
                    Text(
                        text = label,
                        color = if (tab == index) colors.signal else colors.textSecondary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clickable { tab = index }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Hairline()
            when (tab) {
                0 -> CourseForm(payload.courses, onRemoveCourse, ::submitCourse)
                1 -> ExamForm(payload.exams, onRemoveExam, onAddExam)
                2 -> TodoForm(payload.todos, onToggleTodo, onRemoveTodo, onAddTodo)
                else -> MoveForm(payload.moves, onRemoveMove, onAddMove)
            }
        }
    }

    pendingConflict?.let { candidate ->
        SuiAlertDialog(
            onDismissRequest = { pendingConflict = null },
            title = { Text("已经有同名安排") },
            text = { Text("「${candidate.name}」已存在。手动添加的为准，要用手动这条吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onAddCourse(candidate)
                    pendingConflict = null
                }) { Text("用手动", color = colors.signal) }
            },
            dismissButton = {
                TextButton(onClick = { pendingConflict = null }) {
                    Text("保留原数据", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onChange(if (numeric) raw.filter { it.isDigit() || it == '-' || it == ',' } else raw)
        },
        label = { Text(label) },
        singleLine = true,
        colors = suiTextFieldColors(),
        modifier = modifier
    )
}

@Composable
private fun WeekdayRow(selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalRinColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        WEEKDAY_LABELS.forEachIndexed { index, label ->
            Text(
                text = label,
                color = if (selected == index + 1) colors.signal else colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clickable { onSelect(index + 1) }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DeleteRow(text: String, onDelete: () -> Unit) {
    val colors = LocalRinColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = colors.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDelete) { Text("删除", color = colors.textSecondary) }
    }
    Hairline()
}

@Composable
private fun CourseForm(courses: List<ManualCourse>, onRemove: (String) -> Unit, onSubmit: (ManualCourse) -> Unit) {
    var name by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var sections by remember { mutableStateOf("") }
    var weekday by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxWidth()) {
        Field(name, { name = it }, "课程名", Modifier.fillMaxWidth().padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Field(sections, { sections = it }, "节次 如 3-4", Modifier.weight(1f), numeric = true)
            Field(room, { room = it }, "教室", Modifier.weight(1f))
        }
        WeekdayRow(weekday) { weekday = it }
        SuiPrimaryButton(
            text = "加入课表",
            onClick = {
                onSubmit(
                    ManualCourse(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(), location = room.trim(),
                        weekday = weekday, sectionText = sections.trim()
                    )
                )
                name = ""; room = ""; sections = ""
            },
            enabled = name.isNotBlank() && sections.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (courses.isNotEmpty()) Hairline()
        courses.forEach { course ->
            DeleteRow("周${WEEKDAY_LABELS.getOrElse(course.weekday - 1) { "-" }} ${course.sectionText} · ${course.name}") {
                onRemove(course.id)
            }
        }
    }
}

@Composable
private fun ExamForm(exams: List<ManualExam>, onRemove: (String) -> Unit, onAdd: (ManualExam) -> Unit) {
    var name by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Field(name, { name = it }, "考试名", Modifier.fillMaxWidth().padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Field(time, { time = it }, "时间", Modifier.weight(1f))
            Field(room, { room = it }, "地点", Modifier.weight(1f))
        }
        SuiPrimaryButton(
            text = "加入考试",
            onClick = {
                onAdd(ManualExam(UUID.randomUUID().toString(), name.trim(), time.trim(), room.trim()))
                name = ""; time = ""; room = ""
            },
            enabled = name.isNotBlank() && time.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (exams.isNotEmpty()) Hairline()
        exams.forEach { exam ->
            DeleteRow(listOf(exam.name, exam.time, exam.location).filter(String::isNotBlank).joinToString(" · ")) {
                onRemove(exam.id)
            }
        }
    }
}

@Composable
private fun TodoForm(
    todos: List<ManualTodo>,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (ManualTodo) -> Unit
) {
    val colors = LocalRinColors.current
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Field(title, { title = it }, "待办", Modifier.weight(1f))
            Field(due, { due = it }, "截止", Modifier.weight(0.6f))
        }
        SuiPrimaryButton(
            text = "加入待办",
            onClick = {
                onAdd(ManualTodo(UUID.randomUUID().toString(), title.trim(), due.trim()))
                title = ""; due = ""
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (todos.isNotEmpty()) Hairline()
        todos.forEach { todo ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (if (todo.done) "✓ " else "☐ ") + todo.title +
                        if (todo.due.isNotBlank()) " · ${todo.due}" else "",
                    color = if (todo.done) colors.textSecondary else colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).clickable { onToggle(todo.id) }
                )
                TextButton(onClick = { onRemove(todo.id) }) { Text("删除", color = colors.textSecondary) }
            }
            Hairline()
        }
    }
}

@Composable
private fun MoveForm(moves: List<ManualMove>, onRemove: (String) -> Unit, onAdd: (ManualMove) -> Unit) {
    var name by remember { mutableStateOf("") }
    var sections by remember { mutableStateOf("") }
    var week by remember { mutableStateOf("") }
    var weekday by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxWidth()) {
        Field(name, { name = it }, "课程名", Modifier.fillMaxWidth().padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Field(week, { week = it }, "周次", Modifier.weight(0.6f), numeric = true)
            Field(sections, { sections = it }, "新节次", Modifier.weight(1f), numeric = true)
        }
        WeekdayRow(weekday) { weekday = it }
        SuiPrimaryButton(
            text = "保存调课",
            onClick = {
                onAdd(
                    ManualMove(
                        id = UUID.randomUUID().toString(),
                        courseName = name.trim(), week = week.toIntOrNull() ?: 0,
                        weekday = weekday, sectionText = sections.trim()
                    )
                )
                name = ""; week = ""; sections = ""
            },
            enabled = name.isNotBlank() && sections.isNotBlank() && (week.toIntOrNull() ?: 0) > 0,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (moves.isNotEmpty()) Hairline()
        moves.forEach { move ->
            DeleteRow(
                "第${move.week}周 周${WEEKDAY_LABELS.getOrElse(move.weekday - 1) { "-" }} " +
                    "${move.sectionText} · ${move.courseName}"
            ) { onRemove(move.id) }
        }
    }
}
