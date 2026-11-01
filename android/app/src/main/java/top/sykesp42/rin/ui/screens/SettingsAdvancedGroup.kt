package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.sykesp42.rin.R
import top.sykesp42.rin.reminder.SystemPermissions
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.theme.LocalRinColors

/**
 * Spec 4.5's folded 高级设置 group, minus everything sign-related: the school
 * login entry and the keep-alive switches (exact alarm / battery / vendor
 * autostart) that keep the local course reminders honest. Collapsed by
 * default; the summary line says what is inside.
 */
@Composable
internal fun SettingsAdvancedGroup(
    vm: AppViewModel,
    simpleMode: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "高级设置",
            meta = advancedSummary(vm = vm, simpleMode = simpleMode)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "收起" else "展开",
                color = LocalRinColors.current.signal,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            val arrow = if (expanded) {
                Icons.Filled.KeyboardArrowUp
            } else {
                Icons.Filled.KeyboardArrowDown
            }
            Icon(
                imageVector = arrow,
                contentDescription = null,
                tint = LocalRinColors.current.textSecondary
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (!simpleMode) {
                    JwxtEntryRow(vm = vm)
                }
                KeepAliveCard()
            }
        }
    }
}

@Composable
private fun advancedSummary(vm: AppViewModel, simpleMode: Boolean): String =
    listOfNotNull(
        if (simpleMode) "简洁模式" else "标准模式",
        when {
            !simpleMode && vm.jwxtConnected.value -> "教务 已连接"
            !simpleMode -> "教务 未连接"
            else -> null
        },
        "本地优先"
    ).joinToString(" · ")

/** Mirrors the school login state inside the folded group. */
@Composable
private fun JwxtEntryRow(vm: AppViewModel) {
    val connected by vm.jwxtConnected.collectAsStateWithLifecycle()
    // The real entry stays the navigation card above; this only mirrors it.
    Text(
        text = if (connected) {
            "教务 已连接 · 只读课表"
        } else {
            "教务 未连接 · 在「教务连接」里登录"
        },
        color = LocalRinColors.current.textSecondary,
        style = MaterialTheme.typography.bodySmall
    )
}

/**
 * 后台保活, ported from the full client's ReliabilityGroup: exact alarms and
 * battery optimization decide whether a course reminder fires on time.
 */
@Composable
private fun KeepAliveCard() {
    val context = LocalContext.current
    val colors = LocalRinColors.current
    var exact by remember { mutableStateOf(SystemPermissions.canScheduleExactAlarms(context)) }
    var ignoringBattery by remember { mutableStateOf(SystemPermissions.isIgnoringBatteryOptimizations(context)) }
    // Coming back from a system page: re-read both switches.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exact = SystemPermissions.canScheduleExactAlarms(context)
                ignoringBattery = SystemPermissions.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "后台保活",
            meta = if (exact && ignoringBattery) "已就绪" else "会被系统打断"
        )
        StatusNote(
            text = if (exact) "已开启，提醒准点响" else "未开启，提醒可能延迟十几分钟",
            dotColor = if (exact) colors.signal else colors.warning
        )
        if (!exact) {
            SuiSecondaryButton(
                text = "去开精确闹钟",
                onClick = {
                    runCatching {
                        SystemPermissions.exactAlarmSettingsIntent(context)?.let {
                            context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        StatusNote(
            text = if (ignoringBattery) "已忽略，后台不会被省电打断" else "未忽略，锁屏后可能被杀",
            dotColor = if (ignoringBattery) colors.signal else colors.warning
        )
        if (!ignoringBattery) {
            SuiSecondaryButton(
                text = "关闭电池优化",
                onClick = {
                    runCatching {
                        context.startActivity(
                            SystemPermissions.batterySettingsIntent()
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        SuiSecondaryButton(
            text = "厂商自启动设置",
            onClick = {
                runCatching {
                    context.startActivity(
                        SystemPermissions.autoStartSettingsIntent(context)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "国产系统需允许自启动与后台运行。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
