package top.sykesp42.rin.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.theme.LocalRinColors

/** App-level reminder defaults followed by the system gates they depend on. */
@Composable
internal fun SettingsRemindersContent(
    settings: AppSettings,
    onBack: () -> Unit,
    onSound: (String) -> Unit,
    onVolume: (Int) -> Unit,
    onVibrate: (Boolean) -> Unit,
    onVibrationMode: (String) -> Unit,
    onHeadsUp: (Boolean) -> Unit
) {
    SettingsSubpage(
        title = "提醒",
        subtitle = "铃声、音量、震动与权限",
        onBack = onBack
    ) {
        item(key = "app-level") {
            AppReminderSettings(
                settings = settings,
                onSound = onSound,
                onVolume = onVolume,
                onVibrate = onVibrate,
                onVibrationMode = onVibrationMode,
                onHeadsUp = onHeadsUp
            )
        }
        item(key = "system-level") {
            SystemReminderSettings()
        }
    }
}

@Composable
private fun AppReminderSettings(
    settings: AppSettings,
    onSound: (String) -> Unit,
    onVolume: (Int) -> Unit,
    onVibrate: (Boolean) -> Unit,
    onVibrationMode: (String) -> Unit,
    onHeadsUp: (Boolean) -> Unit
) {
    val colors = LocalRinColors.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "应用级提醒",
            color = colors.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "课程提醒和一次性闹钟共用这套默认值",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        ReminderLabel("铃声")
        OptionRow(
            options = listOf("default" to "系统默认", "alarm" to "系统闹钟", "silent" to "静音"),
            selected = settings.reminderSound,
            onSelect = onSound
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReminderLabel("音量", Modifier.weight(1f))
            Text(
                text = "${settings.reminderVolume}%",
                color = colors.text,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Slider(
            value = settings.reminderVolume.toFloat(),
            onValueChange = { onVolume(it.toInt()) },
            valueRange = 0f..100f,
            steps = 19
        )
        Text(
            text = "闹钟提醒时临时作用于闹钟声道，响铃后自动恢复",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(6.dp))
        Hairline()
        Spacer(Modifier.height(6.dp))
        PaperSwitchRow(
            title = "震动",
            description = "课程提醒和闹钟是否震动",
            checked = settings.reminderVibrate,
            onCheckedChange = onVibrate,
            contentDescription = "提醒震动"
        )
        if (settings.reminderVibrate) {
            ReminderLabel("震动模式")
            OptionRow(
                options = listOf(
                    "default" to "默认",
                    "short" to "短",
                    "long" to "长",
                    "pulse" to "脉冲"
                ),
                selected = settings.reminderVibrationMode,
                onSelect = onVibrationMode
            )
        }
        Spacer(Modifier.height(6.dp))
        Hairline()
        PaperSwitchRow(
            title = "横幅提醒",
            description = "提醒到达时在屏幕顶部弹出",
            checked = settings.reminderHeadsUp,
            onCheckedChange = onHeadsUp,
            contentDescription = "提醒横幅"
        )
    }
}

@Composable
private fun SystemReminderSettings() {
    val colors = LocalRinColors.current
    val context = LocalContext.current
    val notificationManager = remember {
        context.getSystemService(NotificationManager::class.java)
    }
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var exactGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 31 ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        )
    }
    var dndGranted by remember {
        mutableStateOf(notificationManager.isNotificationPolicyAccessGranted)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                exactGranted = Build.VERSION.SDK_INT < 31 ||
                    context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
                dndGranted = notificationManager.isNotificationPolicyAccessGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun open(intent: Intent, label: String) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "系统级提醒设置",
            color = colors.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "这些开关由系统管理，会影响所有应用",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        SystemSettingRow(
            title = "通知权限",
            detail = if (notificationsGranted) "已允许 Rin 发通知" else "未允许，提醒不会出现",
            granted = notificationsGranted,
            onClick = {
                open(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    "通知权限"
                )
            }
        )
        SystemSettingRow(
            title = "精确闹钟",
            detail = if (exactGranted) "可以准点响铃" else "未授权，可能延迟响铃",
            granted = exactGranted,
            onClick = {
                val intent = if (Build.VERSION.SDK_INT >= 31) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:${context.packageName}"))
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                }
                open(intent, "精确闹钟")
            }
        )
        SystemSettingRow(
            title = "勿扰访问",
            detail = if (dndGranted) "可以按需临时解除静音" else "未授权，强解静音不会生效",
            granted = dndGranted,
            onClick = {
                open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "勿扰访问")
            }
        )
        SystemSettingRow(
            title = "系统音量与铃声",
            detail = "打开系统的声音设置",
            granted = true,
            action = "打开",
            onClick = { open(Intent(Settings.ACTION_SOUND_SETTINGS), "系统声音") }
        )
    }
}

@Composable
private fun ReminderLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LocalRinColors.current.text,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun OptionRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val colors = LocalRinColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            val active = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(if (active) colors.signal.copy(alpha = 0.16f) else colors.surface)
                    .border(
                        1.dp,
                        if (active) colors.signal else colors.border,
                        CircleShape
                    )
                    .clickable { onSelect(key) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) colors.text else colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SystemSettingRow(
    title: String,
    detail: String,
    granted: Boolean,
    action: String = if (granted) "已授权" else "去设置",
    onClick: () -> Unit
) {
    val colors = LocalRinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, color = colors.text, style = MaterialTheme.typography.bodyMedium)
            Text(text = detail, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = action,
            color = if (granted) colors.textSecondary else colors.signal,
            style = MaterialTheme.typography.labelMedium
        )
    }
    Hairline()
}
