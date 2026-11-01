package top.sykesp42.rin.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.monitor.SystemPermissions
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.AuthState
import top.sykesp42.rin.ui.components.BrandMark
import top.sykesp42.rin.ui.components.DataBand
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.InlineError
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.PermissionRow
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.components.ThemeQuickRow
import top.sykesp42.rin.ui.components.suiTextFieldColors
import top.sykesp42.rin.ui.theme.DarkSuiSignColors
import top.sykesp42.rin.ui.theme.LightSuiSignColors
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.RinSuiSignColors
import top.sykesp42.rin.ui.theme.SuiSignColors
import top.sykesp42.rin.ui.theme.ThemeMode

/**
 * Spec 4.6 Onboarding: the same three steps on one A2 paper page — brand masthead,
 * a sky step rule, hairline sections. No container cards for static content; the only
 * tinted surfaces are the warm theme band and the transient notices.
 * Login, mock fill, theme preview and permission entry are unchanged; local mode stays
 * a Settings-only switch and is not offered as a first-run shortcut.
 */
@Composable
fun OnboardingScreen(
    vm: AppViewModel,
    auth: AuthState,
    settings: AppSettings,
    fullFlow: Boolean
) {
    val context = LocalContext.current
    val container = remember { AppContainer.get(context) }
    val colors = LocalSuiSignColors.current
    val login by vm.login.collectAsStateWithLifecycle()
    val currentSettings by vm.settings.collectAsStateWithLifecycle()
    val mode = ThemeMode.from(currentSettings?.theme ?: settings.theme)
    val debugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    // Spec 2.2: 24dp page inset, tightened to 20dp on narrow phones.
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp

    val startStep = if (fullFlow && auth is AuthState.LoggedIn) 1 else 0
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var serverUrl by rememberSaveable {
        mutableStateOf(settings.serverUrl.ifBlank { "https://signup.sky2333.top" })
    }
    var username by rememberSaveable { mutableStateOf(settings.username) }
    var password by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var noteIsFailure by remember { mutableStateOf(true) }
    var permissionsTick by remember { mutableIntStateOf(0) }

    fun setNote(text: String, failure: Boolean = true) {
        note = text
        noteIsFailure = failure
    }

    LaunchedEffect(auth, fullFlow) {
        if (fullFlow && auth is AuthState.LoggedIn && step == 0) step = 1
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionsTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsGranted = remember(permissionsTick) { container.notifications.canPost() }
    val exactGranted = remember(permissionsTick) { SystemPermissions.canScheduleExactAlarms(context) }
    val batteryIgnored = remember(permissionsTick) { SystemPermissions.isIgnoringBatteryOptimizations(context) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionsTick++
    }

    fun launchIntent(intent: Intent?, failure: String) {
        if (intent == null) {
            setNote(failure)
            return
        }
        runCatching { context.startActivity(intent) }.onFailure { setNote(failure) }
    }

    fun openWeb() {
        val url = serverUrl.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            setNote("服务地址格式不正确")
            return
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { setNote("打不开浏览器") }
    }

    PaperPage {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageInset, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OnbMasthead(step = step, showSteps = fullFlow)

            if (fullFlow) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(if (index <= step) colors.signal else colors.border)
                        )
                    }
                }
            }

            if (!fullFlow && auth is AuthState.LoggedOut && auth.reason.isNotBlank()) {
                NoticeBlock(title = "需要重新登录", message = auth.reason)
            }

            when (step) {
                0 -> ConnectStep(
                    fullFlow = fullFlow,
                    serverUrl = serverUrl,
                    onServerUrl = { serverUrl = it },
                    username = username,
                    onUsername = { username = it },
                    password = password,
                    onPassword = { password = it },
                    busy = login.busy,
                    error = login.error,
                    onLogin = { vm.login(serverUrl, username, password) },
                    debugBuild = debugBuild,
                    onFillMock = {
                        serverUrl = "http://127.0.0.1:18080"
                        username = "teststudent"
                        password = "testpassword"
                        setNote("已填入本地 mock，先确认执行过 adb reverse", failure = false)
                    }
                )
                1 -> LookStep(
                    mode = mode,
                    onMode = { vm.setTheme(it) },
                    onContinue = { step = 2 }
                )
                else -> PermissionStep(
                    auth = auth,
                    notificationsGranted = notificationsGranted,
                    exactGranted = exactGranted,
                    batteryIgnored = batteryIgnored,
                    onNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            setNote("当前系统不用单独授权通知", failure = false)
                        }
                    },
                    onExactAlarm = {
                        launchIntent(
                            SystemPermissions.exactAlarmSettingsIntent(context),
                            "打不开精确闹钟设置"
                        )
                    },
                    onBattery = {
                        launchIntent(SystemPermissions.batterySettingsIntent(), "打不开电池优化设置")
                    },
                    onOpenWeb = { openWeb() },
                    onStart = {
                        vm.completeOnboarding()
                    }
                )
            }

            if (note.isNotBlank()) {
                NoticeLine(note, failure = noteIsFailure)
            }
            Text(
                "仅限本人账号 · 默认只提醒",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
        }
    }
}

/** Spec 4.6 brand anchor: logo + serif wordmark + 8sp line + 2dp sky rule + step counter. */
@Composable
private fun OnbMasthead(step: Int, showSteps: Boolean) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Rin",
                color = colors.text,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 27.sp,
                letterSpacing = (-0.4).sp
            )
            Text(
                text = "SUISIGN · 签到助手",
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                letterSpacing = 0.4.sp
            )
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .width(22.dp)
                    .height(2.dp)
                    .background(colors.accent.copy(alpha = 0.75f), RoundedCornerShape(1.dp))
            )
        }
        if (showSteps) {
            Text(
                "0${step + 1} / 03",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * Notice row on paper: the state colour lives in the dot, the wording stays ink.
 * Signal/warning are 4.50:1 / 4.21:1 on the Rin canvas — under AA for 12–14sp text.
 */
@Composable
private fun NoticeLine(text: String, failure: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (failure) colors.warning else colors.signal)
        )
        Text(
            text = text,
            color = colors.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Two-line variant of [NoticeLine] for the expired-session notice; replaces the old card. */
@Composable
private fun NoticeBlock(title: String, message: String) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(colors.warning)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.text, style = MaterialTheme.typography.titleMedium)
            Text(
                text = message,
                color = colors.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ConnectStep(
    fullFlow: Boolean,
    serverUrl: String,
    onServerUrl: (String) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    busy: Boolean,
    error: String,
    onLogin: () -> Unit,
    debugBuild: Boolean,
    onFillMock: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            if (fullFlow) "Rin 帮你盯签到。" else "重新连接账号。",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            if (fullFlow) {
                "课堂窗口内看人数和名单，够了提醒你"
            } else {
                "会话已失效，重新登录后恢复"
            },
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureLine("只在上课时运行，平时不常驻")
            FeatureLine("不到安全线只提醒")
            FeatureLine("名单确认你已签到，才算成功")
        }
        SectionHeader(title = "连接账号", meta = "3 项")
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrl,
            label = { Text("服务地址") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = suiTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = username,
            onValueChange = onUsername,
            label = { Text("用户名") },
            singleLine = true,
            colors = suiTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = suiTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotBlank()) {
            // The page's single error surface: ink copy + warning icon on a warning stripe.
            InlineError(message = error)
        }
        SuiPrimaryButton(
            text = "登录",
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            loading = busy
        )
        Text(
            "只存登录会话，不存密码",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        if (debugBuild) {
            TextButton(
                onClick = onFillMock,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.text)
            ) {
                Text("填入本地 mock", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun LookStep(mode: ThemeMode, onMode: (ThemeMode) -> Unit, onContinue: () -> Unit) {
    val colors = LocalSuiSignColors.current
    val starters = remember(mode) {
        val picks = ThemeMode.starters()
        if (mode in picks) picks else (listOf(mode) + picks.take(3))
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("选一套主题。", style = MaterialTheme.typography.headlineLarge)
        Text(
            "12 套插画 · 4 套纯色",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        SectionHeader(title = "主题", meta = "4 选 1")
        ThemeQuickRow(modes = starters, mode = mode, onSelect = onMode)
        ThemeBand(mode = mode)
        Text(
            "可在「我的」里浏览全部 16 套。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        SuiPrimaryButton(
            text = "继续",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Selected-theme preview on the warm [DataBand] (spec 4.5 外观), matching the 我的 page:
 * a stripe, not a card. Labels are ink because the band is tinted.
 */
@Composable
private fun ThemeBand(mode: ThemeMode, modifier: Modifier = Modifier) {
    val colors = LocalSuiSignColors.current
    val preview = top.sykesp42.rin.ui.theme.resolveAppearance(mode).colors
    val title = mode.title
    val body = mode.description
    DataBand(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(preview.canvas)
                .border(1.dp, preview.border, RoundedCornerShape(8.dp))
                .padding(6.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(preview.text.copy(alpha = 0.8f))
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.35f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(preview.textSecondary)
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(26.dp)
                        .height(9.dp)
                        .clip(CircleShape)
                        .background(preview.actionBg)
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(preview.signal)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.text, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                color = colors.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun PermissionStep(
    auth: AuthState,
    notificationsGranted: Boolean,
    exactGranted: Boolean,
    batteryIgnored: Boolean,
    onNotifications: () -> Unit,
    onExactAlarm: () -> Unit,
    onBattery: () -> Unit,
    onOpenWeb: () -> Unit,
    onStart: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("补齐后台监控的条件。", style = MaterialTheme.typography.headlineLarge)
        if (auth is AuthState.LoggedIn) {
            // Ink copy + sky dot: signal text is 4.50:1 on the Rin canvas, under AA at 12sp.
            NoticeLine(
                text = "已连接：${auth.user.displayName.ifBlank { auth.user.username }}",
                failure = false
            )
        }
        Text(
            "这两项决定后台能否准时",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        SectionHeader(title = "系统权限", meta = "3 项")
        PermissionRow(
            title = "通知权限",
            description = "到阈值时提醒你。Android 13+ 需单独授权。",
            granted = notificationsGranted,
            actionLabel = "允许通知",
            onAction = onNotifications
        )
        Hairline()
        PermissionRow(
            title = "精确闹钟",
            description = "未授权时可能延迟",
            granted = exactGranted,
            actionLabel = "去授权",
            onAction = onExactAlarm
        )
        Hairline()
        PermissionRow(
            title = "电池优化",
            description = "厂商会限制后台网络",
            granted = batteryIgnored,
            actionLabel = "去设置",
            onAction = onBattery
        )
        SectionHeader(title = "教务连接")
        Text(
            "连上后自动识别教室",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        SuiSecondaryButton("打开网页端", onOpenWeb, modifier = Modifier.fillMaxWidth())
        NoticeLine(
            text = "重启后解锁一次才会恢复",
            failure = true
        )
        SuiPrimaryButton(
            text = "开始使用",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            enabled = auth is AuthState.LoggedIn
        )
    }
}

@Composable
private fun FeatureLine(text: String) {
    val colors = LocalSuiSignColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = colors.signal,
            modifier = Modifier.padding(end = 7.dp)
        )
        Text(text, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}
