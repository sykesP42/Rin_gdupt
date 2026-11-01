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
import top.sykesp42.rin.ui.CalibrationUiState
import top.sykesp42.rin.ui.SettingsUiState
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
    var threshold by mutableIntStateOf(10)
    var rosterName by mutableStateOf("")
    var jwxtAccount by mutableStateOf("")
    var jwxtPassword by mutableStateOf("")
    var cloudUrl by mutableStateOf("")
    var cloudUser by mutableStateOf("")
    var cloudPass by mutableStateOf("")
}

@Composable
fun rememberSettingsAdvancedDrafts(): SettingsAdvancedDrafts = remember { SettingsAdvancedDrafts() }

/**
 * Spec 4.5: 自动签到、安全阈值、本地/云端、教务连接 and 诊断 sit behind one
 * default-collapsed row. Nothing was deleted — expanding reveals exactly the controls
 * the old card wall showed, with the same ViewModel callbacks, launchers and dialogs
 * (the dialogs themselves stay owned by `SettingsScreen`).
 */
@Composable
fun SettingsAdvanced(
    vm: AppViewModel,
    settings: AppSettings,
    settingsPage: SettingsUiState,
    drafts: SettingsAdvancedDrafts,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    autoServiceConnected: Boolean,
    onAutoSignChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onSaveThreshold: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val calibration by vm.calibration.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth()) {
        AdvancedToggleRow(
            expanded = expanded,
            summary = advancedSummary(settings, settingsPage),
            onToggle = onToggleExpanded
        )
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DataSourceGroup(vm = vm, settings = settings, drafts = drafts)
                AutoSignGroup(
                    settings = settings,
                    settingsPage = settingsPage,
                    drafts = drafts,
                    autoServiceConnected = autoServiceConnected,
                    onAutoSignChange = onAutoSignChange,
                    onOpenAccessibility = onOpenAccessibility,
                    onSaveThreshold = onSaveThreshold,
                    onLoadThreshold = vm::loadThreshold,
                    onSaveLocalThreshold = vm::setLocalThreshold
                )
                ReliabilityGroup()
                DiagnosticsGroup(
                    vm = vm,
                    autoServiceConnected = autoServiceConnected,
                    calibration = calibration
                )
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
internal fun advancedSummary(settings: AppSettings, settingsPage: SettingsUiState): String =
    listOf(
        if (settings.autoSignEnabled) "自动签到 已开" else "自动签到 未开",
        if (settings.localMode) "本地模式" else "云端模式",
        when {
            settings.jwxtNeedsLogin -> "教务 需重连"
            settings.jwxtConnectedAt.isNotBlank() -> "教务 已连接"
            else -> "教务 未连接"
        },
        if (settings.localMode) {
            if (settings.localThreshold > 0) "安全线 ${settings.localThreshold} 人（本机）" else "安全线已关（本机）"
        } else {
            thresholdSummary(settingsPage)
        }
    ).joinToString(" · ")

/** Threshold segment of [advancedSummary]: loading, failed and confirmed read. */
internal fun thresholdSummary(page: SettingsUiState): String = when {
    page.thresholdLoading -> "安全线读取中"
    page.thresholdError.isNotBlank() -> "安全线读取失败"
    else -> "安全线 ${page.threshold} 人"
}

/** The single expandable row: title, 48dp target, summary line and hairline. */
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

/** 自动签到: experimental switch, accessibility launcher, threshold block and safety copy. */
@Composable
internal fun AutoSignGroup(
    settings: AppSettings,
    settingsPage: SettingsUiState,
    drafts: SettingsAdvancedDrafts,
    autoServiceConnected: Boolean,
    onAutoSignChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onSaveThreshold: (Int) -> Unit,
    onLoadThreshold: () -> Unit,
    onSaveLocalThreshold: (Int) -> Unit
) {
    val colors = LocalSuiSignColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "自动签到",
            meta = if (settings.autoSignEnabled) "已开启" else "未开启"
        )
        PaperSwitchRow(
            title = "自动签到（实验）",
            description = "到安全线后用本机企微扫码",
            checked = settings.autoSignEnabled,
            onCheckedChange = onAutoSignChange,
            contentDescription = "自动签到"
        )
        PermissionRow(
            title = "无障碍服务",
            description = "只处理本 App 的扫码流程",
            granted = autoServiceConnected,
            actionLabel = "去开启",
            onAction = onOpenAccessibility
        )
        Hairline()
        ThresholdBlock(
            settings = settings,
            settingsPage = settingsPage,
            draft = drafts.threshold,
            onDraftChange = { drafts.threshold = it },
            onSaveThreshold = onSaveThreshold,
            onLoadThreshold = onLoadThreshold,
            onSaveLocalThreshold = onSaveLocalThreshold
        )
        Text(
            text = "亮屏解锁时自动执行；锁屏时先提醒，解锁后继续。失败退回手动签到。" +
                "仅限本人账号；自动化有风控风险，需自行评估。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Same draft/save behaviour as the old card: slider edits a draft, save is gated on a change. */
@Composable
private fun ThresholdBlock(
    settings: AppSettings,
    settingsPage: SettingsUiState,
    draft: Int,
    onDraftChange: (Int) -> Unit,
    onSaveThreshold: (Int) -> Unit,
    onLoadThreshold: () -> Unit,
    onSaveLocalThreshold: (Int) -> Unit
) {
    val colors = LocalSuiSignColors.current
    val localMode = settings.localMode
    // The threshold that actually gates local monitoring is localThreshold;
    // the cloud value behind settingsPage never touches the local pipeline.
    val effective = if (localMode) settings.localThreshold else settingsPage.threshold
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(
            title = "安全线人数",
            meta = if (localMode) "本机" else "云端"
        )
        if (!localMode && settingsPage.thresholdLoading) {
            Row(
                modifier = Modifier.heightIn(min = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThresholdDot(color = colors.signal)
                Text(
                    text = "正在读取设置…",
                    color = colors.text,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            val min = if (localMode) 0 else settingsPage.minThreshold.coerceAtLeast(0)
            val max = if (localMode) 30 else settingsPage.maxThreshold.coerceAtLeast(min + 1)
            val shownDraft = if (localMode) effective else draft
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$shownDraft",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.text
                )
                Text(
                    text = "人",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            StatusNote(
                text = if (shownDraft == 0) "0 人：关闭自动跳转" else "没到阈值只提醒",
                dotColor = if (shownDraft == 0) colors.warning else colors.textSecondary
            )
            Slider(
                value = shownDraft.toFloat(),
                onValueChange = { value ->
                    if (localMode) onSaveLocalThreshold(value.roundToInt()) else onDraftChange(value.roundToInt())
                },
                valueRange = min.toFloat()..max.toFloat(),
                steps = 0,
                colors = SliderDefaults.colors(
                    thumbColor = colors.signal,
                    activeTrackColor = colors.signal,
                    inactiveTrackColor = colors.text.copy(alpha = 0.1f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
            Text(
                text = if (localMode) {
                    "本地监控用它判定阈值"
                } else {
                    "已保存 ${settingsPage.threshold} 人，同步服务端"
                },
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            if (!localMode && settingsPage.thresholdError.isNotBlank()) {
                AdvancedWarning(settingsPage.thresholdError)
            }
            if (!localMode) {
                // Cloud value needs an explicit save + re-read; the local one
                // applies on release with nothing to sync.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuiPrimaryButton(
                        text = "保存安全线",
                        onClick = { onSaveThreshold(draft) },
                        loading = settingsPage.savingThreshold,
                        enabled = draft != settingsPage.threshold || settingsPage.thresholdError.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                    SuiSecondaryButton(
                        text = "重新读取",
                        onClick = onLoadThreshold,
                        enabled = !settingsPage.savingThreshold
                    )
                }
            }
        }
    }
}

/** 数据来源: cloud/local switch, roster matching name and the local-mode 教务连接 block. */
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
            meta = if (settings.localMode) "本地直连" else "云端服务"
        )
        PaperSwitchRow(
            title = "本地模式",
            description = if (settings.localMode) {
                "直连学校接口，不依赖服务端" +
                    if (top.sykesp42.rin.data.InternalGate.isOn && settings.rosterName.isBlank()) "；要先填名单姓名" else ""
            } else {
                "开启后手机直接连学校，不经服务端"
            },
            checked = settings.localMode,
            onCheckedChange = { next -> vm.setLocalMode(next) },
            contentDescription = "本地模式"
        )
        if (settings.localMode && top.sykesp42.rin.data.InternalGate.isOn) {
            OutlinedTextField(
                value = drafts.rosterName,
                onValueChange = { drafts.rosterName = it },
                label = { Text("名单姓名") },
                supportingText = { Text("需与名单完全一致") },
                singleLine = true,
                colors = suiTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuiPrimaryButton(
                    text = "保存姓名",
                    onClick = { vm.setRosterName(drafts.rosterName) },
                    enabled = drafts.rosterName != settings.rosterName,
                    modifier = Modifier.weight(1f)
                )
            }
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
                    "登录过期了，点「重新登录教务」恢复"
                jwxtConnected && !hasCredential ->
                    "已连接 · 填了账号密码可以自动续期"
                jwxtConnected -> "已连接"
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
            text = "账号密码只保存在这台手机上，Rin 不会上传或发送。",
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
        if (top.sykesp42.rin.data.InternalGate.isOn) {
            SuiSecondaryButton(
                text = "用此姓名填名单匹配",
                onClick = { vm.fillRosterNameFromJwxt() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = "校外时需要先登录 WebVPN。",
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

/** 诊断: the WeChat chain rehearsal, its copy and the result line. */
@Composable
internal fun DiagnosticsGroup(
    vm: AppViewModel,
    autoServiceConnected: Boolean,
    calibration: CalibrationUiState
) {
    val colors = LocalSuiSignColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "诊断",
            meta = if (autoServiceConnected) "服务已就绪" else "需无障碍"
        )
        SuiSecondaryButton(
            text = if (calibration is CalibrationUiState.Running) "演练中…" else "演练校准",
            onClick = { vm.runCalibration() },
            enabled = autoServiceConnected && calibration !is CalibrationUiState.Running,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "走一遍企微链路，验证当前版本是否兼容。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        when (val result = calibration) {
            is CalibrationUiState.Done -> StatusNote(
                text = result.text,
                dotColor = if (result.ok) colors.signal else colors.warning
            )
            else -> Unit
        }
    }
}

/** Threshold saving is a network write; the error stays one hairline-terminated stripe. */
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
