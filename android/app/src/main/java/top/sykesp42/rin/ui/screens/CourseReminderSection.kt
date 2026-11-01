package top.sykesp42.rin.ui.screens

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.sykesp42.rin.data.CourseReminder
import top.sykesp42.rin.data.ReminderPrefs
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.theme.LocalRinColors

/**
 * The important-course reminder editor. Force-unmute and its children always
 * start off; other form values come from the last saved reminder.
 */
@Composable
internal fun CourseReminderSection(
    courseName: String,
    location: String,
    dateHint: String,
    startHint: String,
    endHint: String,
    prefs: ReminderPrefs,
    reminders: List<CourseReminder>,
    onSave: (CourseReminder, ReminderPrefs) -> Boolean,
    onDelete: (String) -> Unit
) {
    val colors = LocalRinColors.current
    val context = LocalContext.current
    val notificationManager = remember {
        context.getSystemService(NotificationManager::class.java)
    }
    var open by remember(courseName) { mutableStateOf(false) }
    var appReminder by remember(courseName, prefs) { mutableStateOf(prefs.appReminder) }
    var calendarEvent by remember(courseName, prefs) { mutableStateOf(prefs.calendarEvent) }
    var alarm by remember(courseName, prefs) { mutableStateOf(prefs.alarm) }
    var lead by remember(courseName, prefs) { mutableIntStateOf(prefs.leadMinutes) }
    var volume by remember(courseName, prefs) { mutableIntStateOf(prefs.volume) }
    var forceUnmute by remember(courseName) { mutableStateOf(false) }
    var volumeEnabled by remember(courseName) { mutableStateOf(false) }
    var vibrateEnabled by remember(courseName) { mutableStateOf(false) }
    var savedFlash by remember(courseName) { mutableStateOf(false) }

    var policyGranted by remember {
        mutableStateOf(notificationManager.isNotificationPolicyAccessGranted)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, forceUnmute) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                policyGranted = notificationManager.isNotificationPolicyAccessGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val occurrence = remember(courseName, dateHint, startHint, endHint) {
        occurrenceOf(dateHint, startHint, endHint)
    }
    val trigger = runCatching {
        LocalDateTime.of(occurrence.first, LocalTime.parse(occurrence.second))
            .minusMinutes(lead.toLong())
    }.getOrNull()
    val past = trigger == null || !trigger.isAfter(LocalDateTime.now())
    val result = when {
        trigger == null -> "课程时间不完整"
        past -> "提醒时间已过，请确认课程时间"
        else -> "将在 ${trigger.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))} 响铃"
    }
    val courseReminders = reminders.filter { it.courseName == courseName }
        .sortedBy { it.date + it.startTime }

    Hairline()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { open = !open }
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "重要课程提醒",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (courseReminders.isEmpty()) {
                    "${formatOccurrence(occurrence)} · 一次性提醒"
                } else {
                    "已设 ${courseReminders.size} 个 · ${courseReminders.first().resultLine()}"
                },
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            text = if (open) "收起" else "设置",
            color = colors.signal,
            style = MaterialTheme.typography.labelLarge
        )
    }
    if (open) {
        Text(
            text = "${courseName} · ${formatOccurrence(occurrence)}" +
                location.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
        ReminderToggle(
            title = "App 提醒",
            detail = "到点后发一条通知",
            checked = appReminder,
            onCheckedChange = { appReminder = it; savedFlash = false }
        )
        ReminderToggle(
            title = "加入系统待办日历",
            detail = "打开系统日历并填入这节课",
            checked = calendarEvent,
            onCheckedChange = { calendarEvent = it; savedFlash = false }
        )
        ReminderToggle(
            title = "一次性闹钟",
            detail = "可提前提醒，并按闹钟设置响铃",
            checked = alarm,
            onCheckedChange = { alarm = it; savedFlash = false }
        )
        if (alarm) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "提前 $lead 分钟",
                    color = colors.text,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "0–120",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Slider(
                value = lead.toFloat(),
                onValueChange = {
                    lead = (it / 5f).toInt() * 5
                    savedFlash = false
                },
                valueRange = 0f..120f,
                steps = 23
            )
        }
        Text(
            text = result,
            color = if (past) colors.warning else colors.signal,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        if (alarm) {
            ReminderToggle(
                title = "响铃前强制解除系统静音",
                detail = "需要勿扰访问权限，默认关闭",
                checked = forceUnmute,
                onCheckedChange = { forceUnmute = it; savedFlash = false }
            )
        }
        if (alarm && forceUnmute) {
            if (!policyGranted) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "尚未授权，通知可能仍是静音",
                        color = colors.warning,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    ) { Text("去授权", color = colors.signal) }
                }
            }
            ReminderToggle(
                title = "调整闹铃音量",
                detail = "仅在本次提醒时临时调整",
                checked = volumeEnabled,
                onCheckedChange = { volumeEnabled = it; savedFlash = false }
            )
            if (volumeEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "音量 $volume%",
                        color = colors.text,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { volume = it.toInt(); savedFlash = false },
                    valueRange = 0f..100f,
                    steps = 19
                )
            }
            ReminderToggle(
                title = "震动",
                detail = "按应用级的震动模式执行",
                checked = vibrateEnabled,
                onCheckedChange = { vibrateEnabled = it; savedFlash = false }
            )
            Text(
                text = "强解静音及其音量/震动子选项每次默认关闭，需手动开启。",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }

        SuiPrimaryButton(
            text = "保存提醒",
            enabled = !past && (appReminder || alarm || calendarEvent),
            onClick = {
                val reminder = CourseReminder(
                    id = "$courseName|${occurrence.first}|${occurrence.second}".hashCode().toString(),
                    courseName = courseName,
                    date = occurrence.first.toString(),
                    startTime = occurrence.second,
                    endTime = occurrence.third,
                    location = location,
                    leadMinutes = lead,
                    appReminder = appReminder,
                    calendarEvent = calendarEvent,
                    alarm = alarm,
                    forceUnmute = alarm && forceUnmute,
                    volume = volume,
                    volumeEnabled = alarm && volumeEnabled,
                    vibrate = alarm && vibrateEnabled,
                    createdAt = Instant.now().toString()
                )
                val saved = onSave(
                    reminder,
                    ReminderPrefs(
                        leadMinutes = lead,
                        appReminder = appReminder,
                        calendarEvent = calendarEvent,
                        alarm = alarm,
                        volume = volume,
                        vibrate = vibrateEnabled
                    )
                )
                if (saved) {
                    savedFlash = true
                    forceUnmute = false
                    volumeEnabled = false
                    vibrateEnabled = false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (savedFlash) {
            Text(
                text = "已设置，到点会提醒你。",
                color = colors.signal,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (courseReminders.isNotEmpty()) {
            Text(
                text = "本课程的提醒",
                color = colors.text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
            )
            courseReminders.forEach { reminder ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${reminder.date.takeLast(5)} ${reminder.startTime} · " +
                            "提前 ${reminder.leadMinutes} 分钟",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onDelete(reminder.id) }) {
                        Text("取消", color = colors.warning, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ReminderToggle(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalRinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, color = colors.text, style = MaterialTheme.typography.bodyMedium)
            Text(text = detail, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun occurrenceOf(
    dateHint: String,
    startHint: String,
    endHint: String
): Triple<LocalDate, String, String> {
    val today = todayAtCampusClock()
    val parsedDate = runCatching { LocalDate.parse(dateHint) }.getOrNull()
    var date = parsedDate ?: today
    val start = startHint.takeIf { it.length == 5 } ?: "08:00"
    val end = endHint.takeIf { it.length == 5 } ?: plusMinutes(start, 95)
    if (parsedDate == null) {
        val at = runCatching {
            LocalDateTime.of(date, LocalTime.parse(start))
        }.getOrNull()
        if (at != null && at.isBefore(LocalDateTime.now())) date = date.plusDays(7)
    }
    return Triple(date, start, end)
}


private fun plusMinutes(start: String, minutes: Int): String {
    val time = runCatching { LocalTime.parse(start) }.getOrNull() ?: LocalTime.of(8, 0)
    return time.plusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun formatOccurrence(occurrence: Triple<LocalDate, String, String>): String =
    occurrence.first.format(DateTimeFormatter.ofPattern("M月d日 · E", Locale.CHINA)) +
        " " + occurrence.second
