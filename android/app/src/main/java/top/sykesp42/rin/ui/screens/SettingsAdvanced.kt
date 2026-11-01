package top.sykesp42.rin.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.JwxtStudentInfo
import top.sykesp42.rin.jwxt.JwxtLoginActivity
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.PermissionRow
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.components.suiTextFieldColors
import top.sykesp42.rin.ui.theme.LocalSuiSignColors

/**
 * Drafts for the collapsed advanced controls. They live above the collapse so hiding
 * the group never throws away a typed account/name or a dragged threshold, and they are
 * plain `remember` state: the drafts themselves never reach disk. Saving 教务 writes the
 * pair to the AndroidKeyStore-encrypted store described in [JwxtGroup].
 */
@Stable
class SettingsAdvancedDrafts {
    var rosterName by mutableStateOf("")
    var jwxtAccount by mutableStateOf("")
    var jwxtPassword by mutableStateOf("")
}

@Composable
fun rememberSettingsAdvancedDrafts(): SettingsAdvancedDrafts = remember { SettingsAdvancedDrafts() }

/**
 * Spec 4.5: 数据来源、教务连接 and the keep-alive group sit behind one
 * default-collapsed row. The sign-side groups (自动签到/阈值/诊断) do not exist
 * in this build.
 */
@Composable
fun SettingsAdvanced(
    vm: AppViewModel,
    settings: AppSettings,
    drafts: SettingsAdvancedDrafts,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        AdvancedToggleRow(
            expanded = expanded,
            summary = advancedSummary(settings),
            onToggle = onToggleExpanded
        )
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DataSourceGroup(vm = vm, settings = settings, drafts = drafts)
                ReliabilityGroup()
            }
        }
    }
}

/**
 * Collapsed-row state summary. The threshold segment is deliberately read from the page
 * state's *load* status: `SettingsUiState.threshold` defaults to 10 and keeps its previous
 * value when a read fails, so printing it unconditionally would advertise a number the
 * server never confirmed. Internal (not private) so the branches stay unit-testable.
 */
internal fun advancedSummary(settings: AppSettings): String =
    listOf(
        settings.localMode.takeIf { it }?.let { "本地模式" } ?: "云端预留",
        "教务 " + if (settings.jwxtConnectedAt.isNotBlank()) "已连接" else "未连接",
        "本地优先"
    ).filterNotNull().joinToString(" · ")

@Composable
private fun AdvancedToggleRow(
    expanded: Boolean,
    summary: String,
    onToggle: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(
                    onClickLabel = if (expanded) "收起高级设置" else "展开高级设置",
                    onClick = onToggle
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "高级设置",
                color = colors.text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "收起" else "展开",
                color = colors.text,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 2.dp)
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = summary,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Hairline()
    }
}

@Composable
internal fun DataSourceGroup(
    vm: AppViewModel,
    settings: AppSettings,
    drafts: SettingsAdvancedDrafts,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    val context = LocalContext.current
    val container = remember { AppContainer.get(context) }
    val scope = rememberCoroutineScope()
    val grades by vm.grades.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "数据来源",
            meta = "本地直连学校"
        )
        Hairline()
        JwxtGroup(
            vm = vm,
            container = container,
            scope = scope,
            context = context,
            settings = settings,
            drafts = drafts,
            student = grades.student
        )
    }
}

/**
 * 教务连接: credentials entry (masked; the pair is kept only in AndroidKeyStore-encrypted
 * on-device storage for automatic re-login and dropped by 断开), login launcher,
 * disconnect and student echo.
 */
@Composable
private fun JwxtGroup(
    vm: AppViewModel,
    container: AppContainer,
    scope: CoroutineScope,
    context: Context,
    settings: AppSettings,
    drafts: SettingsAdvancedDrafts,
    student: JwxtStudentInfo?
) {
    val colors = LocalSuiSignColors.current
    val jwxtConnected = settings.jwxtConnectedAt.isNotBlank()
    // WebView login stores only the cookie; without the account/password pair
    // an expired cookie cannot be renewed silently. Say so instead of letting
    // the user discover it at the worst moment.
    val hasCredential = remember(settings.jwxtConnectedAt) {
        container.jwxtCredentialStore.load() != null
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "教务连接",
            meta = when {
                settings.jwxtNeedsLogin -> "需重连"
                jwxtConnected -> "已连接"
                else -> "未连接"
            }
        )
        StatusNote(
            text = when {
                settings.jwxtNeedsLogin ->
                    "会话已过期，需重新过滑块；课表成绩仍可看缓存"
                jwxtConnected && !hasCredential ->
                    "已连接，仅存网页会话；填账号可自动续期"
                jwxtConnected -> "已连接，按课表自动调度"
                else -> "未连接"
            },
            dotColor = if (jwxtConnected && hasCredential && !settings.jwxtNeedsLogin) colors.signal else colors.warning
        )
        OutlinedTextField(
            value = drafts.jwxtAccount,
            onValueChange = { drafts.jwxtAccount = it },
            label = { Text("教务账号（学号）") },
            singleLine = true,
            colors = suiTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = drafts.jwxtPassword,
            onValueChange = { drafts.jwxtPassword = it },
            label = { Text("教务密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = suiTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        SuiPrimaryButton(
            text = "保存并连接",
            onClick = { vm.saveJwxtCredentials(drafts.jwxtAccount, drafts.jwxtPassword) },
            enabled = drafts.jwxtAccount.isNotBlank() && drafts.jwxtPassword.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuiPrimaryButton(
                text = if (jwxtConnected) "重新登录教务" else "登录教务",
                onClick = {
                    runCatching {
                        context.startActivity(JwxtLoginActivity.intent(context))
                    }.onFailure { vm.notify("打不开教务登录") }
                },
                modifier = Modifier.weight(1f)
            )
            if (jwxtConnected) {
                SuiSecondaryButton(
                    text = "断开",
                    onClick = {
                        scope.launch {
                            container.localJwxtStore.clear()
                            // The disclosure promises that the encrypted account/password
                            // kept for auto re-login disappears too, so clear both stores.
                            container.jwxtCredentialStore.clear()
                            container.settings.setJwxtConnectedAt("")
                            container.settings.setJwxtNeedsLogin(false)
                            vm.notify("教务连接已断开")
                        }
                    }
                )
            }
        }
        if (jwxtConnected) {
            SuiSecondaryButton(
                text = "刷新登录状态",
                onClick = vm::renewJwxtSession,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = "手机直连教务，不绕过签到权限。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        student?.let { info ->
            Hairline()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = info.name,
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(info.className, info.major, info.grade, info.status)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text(
            text = "校外先登录 webvpn2.gdupt.edu.cn，滑块验证需人工完成。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * 后台保活：the two system switches that silently break monitoring on real
 * phones — exact alarms (inexact ones can drift past a 10-minute sign window
 * in Doze) and battery optimisation (OEM killers stop the foreground service).
 * Each row reports live state and jumps to the matching system page.
 */
@Composable
private fun ReliabilityGroup() {
    val context = LocalContext.current
    val colors = LocalSuiSignColors.current
    val container = remember { AppContainer.get(context) }
    val power = remember { context.getSystemService(android.os.PowerManager::class.java) }
    var exact by remember { mutableStateOf(container.alarms.canScheduleExact()) }
    var ignoringBattery by remember {
        mutableStateOf(power?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    // The user leaves for a system page and comes back: re-read both switches.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                exact = container.alarms.canScheduleExact()
                ignoringBattery = power?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "后台保活",
            meta = if (exact && ignoringBattery) "已就绪" else "会被系统打断"
        )
        StatusNote(
            text = if (exact) {
                "已开启，课堂窗口准点唤醒"
            } else {
                "未开启，提醒可能延迟十几分钟"
            },
            dotColor = if (exact) colors.signal else colors.warning
        )
        if (!exact) {
            SuiSecondaryButton(
                text = "去开精确闹钟",
                onClick = { openExactAlarmSettings(context) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        StatusNote(
            text = if (ignoringBattery) {
                "已忽略，后台不会被省电打断"
            } else {
                "未忽略，锁屏后可能被杀"
            },
            dotColor = if (ignoringBattery) colors.signal else colors.warning
        )
        if (!ignoringBattery) {
            SuiSecondaryButton(
                text = "关闭电池优化",
                onClick = { openBatterySettings(context) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        SuiSecondaryButton(
            text = "厂商自启动设置",
            onClick = { openAutoStartSettings(context) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "国产系统需允许自启动与后台运行。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (android.os.Build.VERSION.SDK_INT < 31) return
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun openBatterySettings(context: Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun openAutoStartSettings(context: Context) {
    val vendors = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity"
    )
    val opened = vendors.any { (pkg, cls) ->
        runCatching {
            context.startActivity(
                android.content.Intent().setComponent(android.content.ComponentName(pkg, cls))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }
    if (!opened) {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun AdvancedWarning(message: String) {
    val colors = LocalSuiSignColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.06f))
    ) {
        Hairline()
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = message,
                color = colors.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
        Hairline()
    }
}

@Composable
private fun ThresholdDot(color: Color) {
    Box(
        Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color)
    )
}
