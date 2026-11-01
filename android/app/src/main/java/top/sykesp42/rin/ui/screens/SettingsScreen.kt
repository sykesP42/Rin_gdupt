package top.sykesp42.rin.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.provider.Settings
import java.time.Instant
import top.sykesp42.rin.R
import top.sykesp42.rin.auto.AutoRuntime
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.PET_OFFSET_LIMIT
import top.sykesp42.rin.data.PET_SCALE_MAX
import top.sykesp42.rin.data.PET_SCALE_MIN
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.InternalGate
import top.sykesp42.rin.data.User
import top.sykesp42.rin.monitor.NoticeFaces
import top.sykesp42.rin.monitor.NoticeIcon
import top.sykesp42.rin.monitor.NoticeOutfit
import top.sykesp42.rin.monitor.NoticePlate
import top.sykesp42.rin.monitor.NoticeSlot
import top.sykesp42.rin.monitor.SystemPermissions
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.Identity
import top.sykesp42.rin.ui.SettingsUiState
import top.sykesp42.rin.ui.components.AccentColorRow
import top.sykesp42.rin.ui.components.Avatar
import top.sykesp42.rin.ui.components.CornerStyleRow
import top.sykesp42.rin.ui.components.CustomThemeFocusRow
import top.sykesp42.rin.ui.components.CustomThemePaletteRow
import top.sykesp42.rin.ui.components.DataBand
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.PermissionRow
import top.sykesp42.rin.ui.components.RinMasthead
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.ImageFramingEditor
import top.sykesp42.rin.ui.components.SuiAlertDialog
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.components.SuiSwitch
import top.sykesp42.rin.ui.components.ThemePickerSheet
import top.sykesp42.rin.ui.components.ThemeSummaryCard
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.theme.DarkSuiSignColors
import top.sykesp42.rin.ui.theme.LightSuiSignColors
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.RinSuiSignColors
import top.sykesp42.rin.ui.theme.SuiSignColors
import top.sykesp42.rin.ui.theme.ThemeArtImage
import top.sykesp42.rin.ui.theme.ThemeMode
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt

/**
 * Spec 4.5 我的: one A2 paper page — masthead, flat hairline sections and a single
 * default-collapsed 高级设置 row (`SettingsAdvanced`).
 * Every pre-existing control is kept; only the entry grouping changed, and the only
 * surfaces left on this page are the transient confirm dialogs.
 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    user: User,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    profileSignal: Int = 0
) {
    val context = LocalContext.current
    val container = remember { AppContainer.get(context) }
    val colors = LocalSuiSignColors.current
    // Single source for the service name: the manifest label and this consent
    // dialog must never drift apart.
    val autoServiceLabel = stringResource(R.string.auto_service_label)
    val settingsPage by vm.settingsPage.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val signAccess by vm.signAccess.collectAsStateWithLifecycle()
    val signAccessPro = signAccess.proEnabled
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.Home) }
    var coverEditorOpen by remember { mutableStateOf(false) }
    var coverBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var permissionsTick by remember { mutableIntStateOf(0) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    // A tap on the avatar elsewhere in the app lands here: jump to 个人资料.
    LaunchedEffect(profileSignal) {
        if (profileSignal > 0) destination = SettingsDestination.Profile
    }
    var confirmZero by remember { mutableStateOf(false) }
    var confirmAuto by remember { mutableStateOf(false) }
    var pendingEnable by remember { mutableStateOf(false) }
    var pendingAuto by remember { mutableStateOf(false) }
    val drafts = rememberSettingsAdvancedDrafts()
    // Spec 2.2: 24dp page inset, tightened to 20dp on narrow phones.
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp

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
    val autoServiceConnected = remember(permissionsTick) { AutoRuntime.isConnected() }
    val permissionReady = listOf(notificationsGranted, exactGranted, batteryIgnored).count { it }
    val animationsEnabled = LocalAnimationsEnabled.current

    // Complete a pending auto-mode enable once the user returns from the
    // accessibility settings with the service actually switched on.
    LaunchedEffect(permissionsTick) {
        if (pendingAuto && autoServiceConnected) {
            pendingAuto = false
            vm.setAutoSignEnabled(true)
        } else if (pendingAuto && settings.autoSignEnabled) {
            pendingAuto = false
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionsTick++
        if (granted && pendingEnable) {
            pendingEnable = false
            vm.enableMonitoring()
        } else if (!granted) {
            pendingEnable = false
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            vm.notify("没选图片")
        } else {
            vm.setCustomThemeCover(uri)
            coverEditorOpen = true
        }
    }
    LaunchedEffect(settings.customThemeImage, coverEditorOpen) {
        if (!coverEditorOpen) {
            coverBitmap = null
            return@LaunchedEffect
        }
        repeat(10) {
            coverBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                top.sykesp42.rin.ui.theme.ThemeArtFiles.decode(
                    context,
                    settings.customThemeImage.ifBlank { top.sykesp42.rin.ui.theme.ThemeArtFiles.COVER_FILE }
                )
            }
            if (coverBitmap != null) return@LaunchedEffect
            kotlinx.coroutines.delay(120)
        }
    }

    LaunchedEffect(Unit) { vm.ensureThresholdLoaded() }
    // Same sync the old `remember(savedValue)` blocks did, kept alive across the collapse.
    LaunchedEffect(settingsPage.threshold) { drafts.threshold = settingsPage.threshold }
    LaunchedEffect(settings.rosterName) { drafts.rosterName = settings.rosterName }

    // Returns true only when the intent actually launched; callers gate consent flags on it.
    fun launchIntent(intent: Intent?, failure: String): Boolean {
        if (intent == null) {
            vm.notify(failure)
            return false
        }
        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (!launched) vm.notify(failure)
        return launched
    }

    fun toggleMonitoring(next: Boolean) {
        if (!next) {
            vm.disableMonitoring()
            return
        }
        if (!notificationsGranted && Build.VERSION.SDK_INT >= 33) {
            pendingEnable = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        vm.enableMonitoring()
    }

    fun toggleAutoSign(next: Boolean) {
        if (!next) {
            vm.setAutoSignEnabled(false)
        } else if (autoServiceConnected && settings.monitorEnabled) {
            vm.setAutoSignEnabled(true)
        } else {
            confirmAuto = true
        }
    }

    fun saveThresholdValue(value: Int) {
        if (value == 0) {
            confirmZero = true
        } else {
            vm.saveThreshold(value, "")
        }
    }

    fun openAccessibilitySettings() {
        // Only arm the pending auto-sign enable once the accessibility screen really opened;
        // a failed launch must not silently flip auto-sign on a later resume.
        if (launchIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "打不开无障碍设置")) {
            pendingAuto = true
        }
    }

    fun openWeb() {
        if (settings.serverUrl.isBlank()) {
            vm.notify("还没设置服务地址，先去网页端登录")
            return
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.serverUrl))) }
            .onFailure { vm.notify("打不开浏览器") }
    }

    BackHandler(destination != SettingsDestination.Home) {
        destination = SettingsDestination.Home
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            if (animationsEnabled) {
                slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(180)) togetherWith
                    slideOutHorizontally(tween(180)) { -it / 5 } + fadeOut(tween(120))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "settings_destination"
    ) { target ->
        when (target) {
            SettingsDestination.Home -> SettingsHomeContent(
                user = user,
                settings = settings,
                fullMode = InternalGate.isOn,
                running = today.running,
                permissionReady = permissionReady,
                autoServiceConnected = autoServiceConnected,
                signAccessPro = signAccessPro,
                customThemeActive = settings.customThemeEnabled && settings.customThemeImage.isNotBlank(),
                themeMode = themeMode,
                onOpen = { destination = it },
                onRefresh = { vm.loadThreshold() },
                onToggleTheme = onToggleTheme
            )
            SettingsDestination.Automation -> SettingsAutomationContent(
                vm = vm,
                settings = settings,
                settingsPage = settingsPage,
                drafts = drafts,
                running = today.running,
                stopAt = today.stopAt,
                autoServiceConnected = autoServiceConnected,
                signAccessPro = signAccessPro,
                onBack = { destination = SettingsDestination.Home },
                onMonitorChange = { next -> toggleMonitoring(next) },
                onAutoSignChange = { next -> toggleAutoSign(next) },
                onOpenAccessibility = { openAccessibilitySettings() },
                onSaveThreshold = { value -> saveThresholdValue(value) }
            )
            SettingsDestination.SystemReady -> SettingsSystemReadyContent(
                notificationsGranted = notificationsGranted,
                exactGranted = exactGranted,
                batteryIgnored = batteryIgnored,
                onBack = { destination = SettingsDestination.Home },
                onNotifyAction = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.notify("当前系统不用单独授权通知")
                    }
                },
                onExactAction = {
                    launchIntent(SystemPermissions.exactAlarmSettingsIntent(context), "打不开精确闹钟设置")
                },
                onBatteryAction = {
                    launchIntent(SystemPermissions.batterySettingsIntent(), "打不开电池优化设置")
                },
                onAutoStartAction = {
                    launchIntent(SystemPermissions.autoStartSettingsIntent(context), "打不开自启动设置")
                }
            )
            SettingsDestination.Reminders -> SettingsRemindersContent(
                settings = settings,
                onBack = { destination = SettingsDestination.Home },
                onSound = vm::setReminderSound,
                onVolume = vm::setReminderVolume,
                onVibrate = vm::setReminderVibrate,
                onVibrationMode = vm::setReminderVibrationMode,
                onHeadsUp = vm::setReminderHeadsUp
            )
            SettingsDestination.TodayCards -> SettingsTodayCardsContent(
                settings = settings,
                onBack = { destination = SettingsDestination.Home },
                onToggle = vm::setTodayCardEnabled,
                onMove = vm::moveTodayCard,
                onReset = vm::resetTodayCards
            )
            SettingsDestination.Appearance -> SettingsAppearanceContent(
                vm = vm,
                settings = settings,
                themeMode = themeMode,
                onBack = { destination = SettingsDestination.Home },
                onPickCover = {
                    runCatching {
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }.onFailure {
                        vm.notify("这台设备没有可用的图片选择器")
                    }
                },
                onEditCover = { coverEditorOpen = true }
            )
            SettingsDestination.Profile -> SettingsProfileContent(
                vm = vm,
                settings = settings,
                user = user,
                onBack = { destination = SettingsDestination.Home }
            )
            SettingsDestination.RinMotion -> SettingsRinMotionContent(
                settings = settings,
                onBack = { destination = SettingsDestination.Home },
                onAnimationChange = { next -> vm.setAnimationsEnabled(next) },
                onPetEnabledChange = { next -> vm.setPetEnabled(next) },
                onPetContextChange = { next -> vm.setPetContextAware(next) },
                onPetWeatherChange = { next -> vm.setPetWeatherEnabled(next) },
                onPetScaleChange = vm::setPetScale,
                onPetOffsetXChange = vm::setPetOffsetX,
                onPetOffsetYChange = vm::setPetOffsetY,
            )
            SettingsDestination.Faces -> SettingsFacesContent(
                vm = vm,
                onBack = { destination = SettingsDestination.Home }
            )
            SettingsDestination.Connection -> SettingsConnectionContent(
                vm = vm,
                settings = settings,
                user = user,
                drafts = drafts,
                onBack = { destination = SettingsDestination.Home },
                onLogout = { confirmLogout = true },
                onOpenWeb = { openWeb() },
                onResetManual = { confirmReset = true }
            )
            SettingsDestination.About -> SettingsAboutContent(
                vm = vm,
                onBack = { destination = SettingsDestination.Home }
            )
        }
    }

    if (coverEditorOpen && coverBitmap != null) {
        val focusBias = top.sykesp42.rin.ui.theme.CustomThemeFocus
            .from(settings.customThemeFocus).value * 2f - 1f
        ImageFramingEditor(
            bitmap = coverBitmap!!,
            title = "调整封面",
            aspectRatio = 1.55f,
            circular = false,
            initialScale = settings.customThemeScale,
            initialOffsetX = settings.customThemeOffsetX,
            initialOffsetY = (focusBias + settings.customThemeOffsetY).coerceIn(-1f, 1f),
            onDone = { scale, x, y ->
                vm.setCustomThemeFocus("middle")
                vm.setCustomThemeFrame(scale, x, y)
            },
            onDismiss = { coverEditorOpen = false },
            onPickAgain = {
                runCatching {
                    coverPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }.onFailure { vm.notify("这台设备没有可用的图片选择器") }
            }
        )
    }

    if (confirmLogout) {
        SuiAlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录？") },
            text = { Text("监控会停止，本机记录保留。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) {
                    Text("退出", color = colors.warning)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("取消", color = colors.textSecondary)
                }
            }
        )
    }
    if (confirmReset) {
        SuiAlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("重置手动数据？") },
            text = { Text("手动加入的课程、考试、待办和调课会被删除，教务数据不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetManualData()
                }) { Text("重置", color = colors.warning) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("取消", color = colors.textSecondary)
                }
            }
        )
    }
    if (confirmAuto) {
        SuiAlertDialog(
            onDismissRequest = { confirmAuto = false },
            title = { Text("开启自动签到？") },
            text = {
                Text(
                    "开启后，手机需亮屏解锁，并在本机企业微信里识别当次二维码。" +
                        "普通微信仍需你手动打开；识码完成后临时图片会删除。" +
                        "自动化可能被微信风控，失败会回到通知，让你手动签到。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAuto = false
                    openAccessibilitySettings()
                }) {
                    Text("去开启")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAuto = false }) {
                    Text("取消", color = colors.textSecondary)
                }
            }
        )
    }
    if (confirmZero) {
        SuiAlertDialog(
            onDismissRequest = { confirmZero = false },
            title = { Text("关闭自动跳转？") },
            text = { Text("设为 0 后每次手动确认。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmZero = false
                    vm.saveThreshold(0, "确认修改")
                }) {
                    Text("确认关闭", color = colors.warning)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmZero = false }) {
                    Text("取消", color = colors.textSecondary)
                }
            }
        )
    }
}

/** 账户: avatar, name/handle, monitor status, service line and the two account actions. */
@Composable
private fun AccountSection(
    settings: AppSettings,
    user: User,
    onLogout: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title = "账户")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(user.displayName.ifBlank { user.username }, size = 46.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp)
            ) {
                Text(
                    text = user.displayName.ifBlank { user.username.ifBlank { "同学" } },
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (settings.localMode) "本地模式 · 直连学校"
                    else if (settings.hidePersonal) "@已隐藏" else "@${user.username.ifBlank { "account" }}",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Hairline()
        if (settings.localMode) {
            // Local mode has no cloud account: the server URL and the web
            // portal mean nothing here, and "退出登录" would just disable a
            // mode that never needed a login. The mode is already named above,
            // so only explain why there are no account buttons.
            Text(
                text = "不需要云端账号",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuiSecondaryButton(
                    text = "退出登录",
                    onClick = onLogout,
                    modifier = Modifier.weight(1f)
                )
                SuiSecondaryButton(
                    text = "打开网页端",
                    onClick = onOpenWeb,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 外观: one live theme cover, the shared 16-theme gallery, accent, corners and decoration. */
@Composable
private fun AppearanceSection(
    settings: AppSettings,
    themeMode: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    onAccentChange: (String) -> Unit,
    onCornersChange: (String) -> Unit,
    onDecorationsChange: (Boolean) -> Unit,
    onSplashChange: (Boolean) -> Unit,
    onPickCover: () -> Unit,
    onClearCover: () -> Unit,
    onCustomFocusChange: (String) -> Unit,
    onCustomPaletteChange: (String) -> Unit,
    onCustomScaleChange: (Float) -> Unit,
    onCustomBlurChange: (Float) -> Unit,
    onEditCover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val customActive = settings.customThemeEnabled && settings.customThemeImage.isNotBlank()
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title = "外观", meta = "16 套")
        ThemeSummaryCard(
            onClick = { pickerOpen = true },
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "12 套插画 · 4 套纯色",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = "自定义封面",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        CustomThemeCoverRow(
            active = customActive,
            baseName = themeMode.spec.name,
            onPick = onPickCover,
            onClear = onClearCover
        )
        if (customActive) {
            SuiSecondaryButton(
                text = "调整构图",
                onClick = onEditCover,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Text(
                text = "封面焦点",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
            )
            CustomThemeFocusRow(
                value = settings.customThemeFocus,
                onSelect = onCustomFocusChange
            )
            Text(
                text = "色彩倾向",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
            )
            CustomThemePaletteRow(
                value = settings.customThemePalette,
                onSelect = onCustomPaletteChange
            )
            PetSliderRow(
                title = "封面缩放",
                value = settings.customThemeScale,
                valueRange = 1f..1.8f,
                display = { "${(it * 100).roundToInt()}%" },
                onChange = onCustomScaleChange
            )
            PetSliderRow(
                title = "封面模糊",
                value = settings.customThemeBlur,
                valueRange = 0f..24f,
                display = { "${it.roundToInt()}" },
                onChange = onCustomBlurChange
            )
        }
        Text(
            text = "主题色",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        AccentColorRow(
            value = settings.themeAccent,
            onSelect = onAccentChange
        )
        Text(
            text = "圆角",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
        )
        CornerStyleRow(
            value = settings.themeCorners,
            onSelect = onCornersChange,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Hairline()
        PaperSwitchRow(
            title = "主题装饰",
            description = if (settings.themeDecorations) {
                "纹理、网格、边框"
            } else {
                "只留配色与字体"
            },
            checked = settings.themeDecorations,
            onCheckedChange = onDecorationsChange,
            contentDescription = "主题装饰",
            modifier = Modifier.padding(top = 8.dp)
        )
        PaperSwitchRow(
            title = "启动屏",
            description = if (settings.splashEnabled) {
                "展示今日课程与问候"
            } else {
                "关闭后直接进入主界面"
            },
            checked = settings.splashEnabled,
            onCheckedChange = onSplashChange,
            contentDescription = "启动屏",
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    if (pickerOpen) {
        ThemePickerSheet(
            selected = themeMode,
            onSelect = {
                onSelectTheme(it)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }
}

@Composable
private fun CustomThemeCoverRow(
    active: Boolean,
    baseName: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    val spec = LocalVisualStyle.current.spec
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surface)
            .border(1.dp, colors.border, MaterialTheme.shapes.medium)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 50.dp)
                .clip(MaterialTheme.shapes.small)
                .background(colors.canvas)
        ) {
            ThemeArtImage(
                spec = spec,
                modifier = Modifier.matchParentSize(),
                contentDescription = if (active) "自定义封面" else "当前主题封面"
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (active) "本机封面已应用" else "使用内置封面",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = if (active) "基于 $baseName · 仅存本机" else "选一张本机图片，自动取色",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        SuiSecondaryButton(
            text = if (active) "更换" else "选择",
            onClick = onPick
        )
        if (active) {
            TextButton(onClick = onClear) {
                Text("移除", color = colors.textSecondary)
            }
        }
    }
}

/** 显示: page modes that change what the app shows without changing its palette. */
@Composable
private fun DisplaySection(
    settings: AppSettings,
    onMinimalChange: (Boolean) -> Unit,
    onWeekTableChange: (Boolean) -> Unit,
    onWeekFullscreenChange: (Boolean) -> Unit,
    onDaySwipeCrossWeekChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title = "页面显示")
        PaperSwitchRow(
            title = "极简模式",
            description = "只留「签到」和「我的」",
            checked = settings.minimalMode,
            onCheckedChange = onMinimalChange,
            contentDescription = "极简模式",
            modifier = Modifier.padding(top = 8.dp)
        )
        Hairline()
        PaperSwitchRow(
            title = "周视图默认表格",
            description = "「表」默认打开课程表",
            checked = settings.weekDefaultTable,
            onCheckedChange = onWeekTableChange,
            contentDescription = "周视图默认表格",
            modifier = Modifier.padding(top = 8.dp)
        )
        Hairline()
        PaperSwitchRow(
            title = "周视图默认全屏",
            description = "「周」「表」默认全屏",
            checked = settings.weekDefaultFullscreen,
            onCheckedChange = onWeekFullscreenChange,
            contentDescription = "周视图默认全屏",
            modifier = Modifier.padding(top = 8.dp)
        )
        Hairline()
        PaperSwitchRow(
            title = "跨周滑动",
            description = "日视图滑到周一周日时进入相邻周",
            checked = settings.daySwipeCrossWeek,
            onCheckedChange = onDaySwipeCrossWeekChange,
            contentDescription = "跨周滑动",
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Rin and motion: the companion layer plus the app-wide animation switch. */
@Composable
private fun RinMotionSection(
    settings: AppSettings,
    onAnimationChange: (Boolean) -> Unit,
    onPetEnabledChange: (Boolean) -> Unit,
    onPetContextChange: (Boolean) -> Unit,
    onPetWeatherChange: (Boolean) -> Unit,
    onPetScaleChange: (Float) -> Unit,
    onPetOffsetXChange: (Float) -> Unit,
    onPetOffsetYChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title = "Rin 与动效")
        PaperSwitchRow(
            title = "动效",
            description = "启动页、切页与 Rin 动画",
            checked = settings.animationsEnabled,
            onCheckedChange = onAnimationChange,
            contentDescription = "动效",
            modifier = Modifier.padding(top = 8.dp)
        )
        Hairline()
        PaperSwitchRow(
            title = "Rin",
            description = "待在今日封面边缘，可轻点",
            checked = settings.petEnabled,
            onCheckedChange = onPetEnabledChange,
            contentDescription = "Rin",
            modifier = Modifier.padding(top = 8.dp)
        )
        if (settings.petEnabled) {
            Hairline()
            PaperSwitchRow(
                title = "情境动作",
                description = "按时段与天气换动作",
                checked = settings.petContextAware,
                onCheckedChange = onPetContextChange,
                contentDescription = "情境动作",
                modifier = Modifier.padding(top = 8.dp)
            )
            if (settings.petContextAware) {
                Hairline()
                PaperSwitchRow(
                    title = "天气联动",
                    description = "按校区天气选动作",
                    checked = settings.petWeatherEnabled,
                    onCheckedChange = onPetWeatherChange,
                    contentDescription = "天气联动",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Hairline()
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionHeader(title = "大小与位置")
                PetSliderRow(
                    title = "大小",
                    value = settings.petScale,
                    valueRange = PET_SCALE_MIN..PET_SCALE_MAX,
                    display = { "${(it * 100).roundToInt()}%" },
                    onChange = onPetScaleChange
                )
                PetSliderRow(
                    title = "左右",
                    value = settings.petOffsetX,
                    valueRange = -PET_OFFSET_LIMIT..PET_OFFSET_LIMIT,
                    display = { signedPercent(it) },
                    onChange = onPetOffsetXChange
                )
                PetSliderRow(
                    title = "上下",
                    value = settings.petOffsetY,
                    valueRange = -PET_OFFSET_LIMIT..PET_OFFSET_LIMIT,
                    display = { signedPercent(it) },
                    onChange = onPetOffsetYChange
                )
            }
            if (!settings.animationsEnabled) {
                Text(
                    text = "动效已关闭 · Rin 静帧",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

/**
 * 签到自动化 (visible part): the monitor switch and its runtime line. The auto-sign flow,
 * threshold, data source and diagnostics are inside the collapsed 高级设置 row.
 */
@Composable
private fun AutomationSection(
    settings: AppSettings,
    running: Boolean,
    stopAt: Instant?,
    autoServiceConnected: Boolean,
    signAccessPro: Boolean,
    onMonitorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            title = if (signAccessPro) "签到自动化" else "签到提醒",
            meta = when {
                !signAccessPro -> "仅提醒与名单"
                settings.autoSignEnabled && autoServiceConnected -> "自动模式"
                else -> "通知提醒"
            }
        )
        PaperSwitchRow(
            title = "后台监控",
            description = when {
                running -> "运行中，正在按课表检查。"
                settings.monitorEnabled -> "已开启，会按课表检查。"
                else -> "关闭后，上课时不会检查签到。"
            },
            checked = settings.monitorEnabled,
            onCheckedChange = onMonitorChange,
            contentDescription = "后台监控",
            modifier = Modifier.padding(top = 8.dp)
        )
        if (running) {
            stopAt?.let { at ->
                Text(
                    text = "本次运行至 ${formatClock(at)}",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** 权限与后台: the three existing permission rows, refreshed on every resume. */
@Composable
private fun PermissionSection(
    notificationsGranted: Boolean,
    exactGranted: Boolean,
    batteryIgnored: Boolean,
    onNotifyAction: () -> Unit,
    onExactAction: () -> Unit,
    onBatteryAction: () -> Unit,
    onAutoStartAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    val ready = listOf(notificationsGranted, exactGranted, batteryIgnored).count { it }
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            title = "后台就绪",
            meta = if (ready == 3) "3 / 3 系统权限" else "$ready / 3 系统权限"
        )
        StatusNote(
            text = if (ready == 3) {
                "通知、闹钟、电池都已就绪"
            } else {
                "还差 ${3 - ready} 项权限"
            },
            dotColor = if (ready == 3) colors.signal else colors.warning,
            modifier = Modifier.padding(top = 10.dp)
        )
        Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionRow(
                title = "通知权限",
                description = if (notificationsGranted) {
                    "已开启，会提醒签到"
                } else {
                    "未开启，收不到提醒"
                },
                granted = notificationsGranted,
                actionLabel = "允许通知",
                onAction = onNotifyAction
            )
            Hairline()
            PermissionRow(
                title = "精确闹钟",
                description = if (exactGranted) {
                    "已开启，提醒准点"
                } else {
                    "未开启，提醒可能延迟"
                },
                granted = exactGranted,
                actionLabel = "去授权",
                onAction = onExactAction
            )
            Hairline()
            PermissionRow(
                title = "电池优化",
                description = if (batteryIgnored) {
                    "已忽略电池优化"
                } else {
                    "需设为不受限制"
                },
                granted = batteryIgnored,
                actionLabel = "去设置",
                onAction = onBatteryAction
            )
            Hairline()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("厂商自启动", color = colors.text, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "各家系统开关位置不同",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                TextButton(onClick = onAutoStartAction) {
                    Text("去设置", color = colors.signal)
                }
            }
        }
        StatusNote(
            text = "重启后先解锁一次",
            dotColor = colors.warning,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/** 数据与关于: the record retention line, version line and the responsibility copy. */
@Composable
private fun AboutSection(
    versionName: String,
    onVersionTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title = "数据与关于")
        PaperInfoRow(
            title = "本地记录",
            subtitle = "仅本机 · 最多 500 条 / 90 天",
            modifier = Modifier.padding(top = 10.dp)
        )
        Hairline()
        PaperInfoRow(
            title = "Rin $versionName",
            subtitle = "非官方 · 仅限本人账号",
            modifier = Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onVersionTap
            )
        )
        Text(
            text = "不代签、不共享账号；最终以名单确认为准。",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

internal enum class SettingsDestination {
    Home,
    Automation,
    SystemReady,
    Reminders,
    TodayCards,
    Appearance,
    Profile,
    RinMotion,
    Faces,
    Connection,
    About
}

@Composable
private fun SettingsHomeContent(
    user: User,
    settings: AppSettings,
    fullMode: Boolean,
    running: Boolean,
    permissionReady: Int,
    autoServiceConnected: Boolean,
    signAccessPro: Boolean,
    customThemeActive: Boolean,
    themeMode: ThemeMode,
    onOpen: (SettingsDestination) -> Unit,
    onRefresh: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "0.1.1" }
    }
    val session = sessionSummary(settings)
    val monitor = monitorSummary(running, settings)
    val permission = permissionSummary(permissionReady)

    PaperPage {
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = pageInset,
                end = pageInset,
                top = 8.dp,
                bottom = 92.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "masthead") {
                RinMasthead(
                    onAvatarClick = { onOpen(SettingsDestination.Profile) },
                    dateLine = "账号、外观与后台设置",
                    title = "我的",
                    avatarText = Identity.displayName(settings, user.displayName.ifBlank { user.username }),
                    onRefresh = onRefresh,
                    onTheme = onToggleTheme
                )
            }
            item(key = "status") {
                SettingsStatusCard(
                    session = session,
                    monitor = monitor,
                    permission = permission,
                    onOpenSession = { onOpen(SettingsDestination.Connection) },
                    onOpenMonitor = { onOpen(SettingsDestination.Automation) },
                    onOpenPermissions = { onOpen(SettingsDestination.SystemReady) }
                )
            }
            if (fullMode) item(key = "nav-automation") {
                SettingsNavigationCard(
                    title = if (signAccessPro) "签到自动化" else "签到提醒与名单",
                    summary = if (signAccessPro) {
                        automationSummary(settings, autoServiceConnected)
                    } else if (settings.monitorEnabled) {
                        "后台提醒已开启 · 仅提醒与名单"
                    } else {
                        "仅提醒与名单"
                    },
                    onClick = { onOpen(SettingsDestination.Automation) }
                )
            }
            item(key = "nav-system") {
                SettingsNavigationCard(
                    title = "系统就绪",
                    summary = "$permissionReady / 3 项权限",
                    onClick = { onOpen(SettingsDestination.SystemReady) }
                )
            }
            item(key = "nav-reminders") {
                SettingsNavigationCard(
                    title = "提醒",
                    summary = buildString {
                        append(
                            when (settings.reminderSound) {
                                "alarm" -> "系统闹钟"
                                "silent" -> "静音"
                                else -> "系统默认"
                            }
                        )
                        append(" · 音量 ${settings.reminderVolume}%")
                        append(if (settings.reminderVibrate) " · 震动" else " · 不震动")
                    },
                    onClick = { onOpen(SettingsDestination.Reminders) }
                )
            }
            item(key = "nav-today-cards") {
                SettingsNavigationCard(
                    title = "今日卡片",
                    summary = "已开启 ${top.sykesp42.rin.ui.today.TodayCards.enabled(settings).size} 个 · 可排序",
                    onClick = { onOpen(SettingsDestination.TodayCards) }
                )
            }
            item(key = "nav-appearance") {
                SettingsNavigationCard(
                    title = "外观与显示",
                    summary = appearanceSummary(themeMode, customThemeActive, settings.minimalMode),
                    onClick = { onOpen(SettingsDestination.Appearance) }
                )
            }
            item(key = "nav-profile") {
                SettingsNavigationCard(
                    title = "个人资料",
                    summary = profileSummary(settings, user),
                    onClick = { onOpen(SettingsDestination.Profile) }
                )
            }
            item(key = "nav-rin") {
                SettingsNavigationCard(
                    title = "Rin 与动效",
                    summary = rinSummary(settings),
                    onClick = { onOpen(SettingsDestination.RinMotion) }
                )
            }
            item(key = "nav-faces") {
                SettingsNavigationCard(
                    title = "Rin 表情",
                    summary = noticeFacesSummary(settings),
                    onClick = { onOpen(SettingsDestination.Faces) }
                )
            }
            item(key = "nav-connection") {
                SettingsNavigationCard(
                    title = "教务与数据",
                    summary = connectionSummary(settings),
                    onClick = { onOpen(SettingsDestination.Connection) }
                )
            }
            item(key = "nav-about") {
                SettingsNavigationCard(
                    title = "关于与隐私",
                    summary = "Rin $versionName · 本机记录",
                    onClick = { onOpen(SettingsDestination.About) }
                )
            }
        }
    }
}

@Composable
private fun SettingsAutomationContent(
    vm: AppViewModel,
    settings: AppSettings,
    settingsPage: SettingsUiState,
    drafts: SettingsAdvancedDrafts,
    running: Boolean,
    stopAt: Instant?,
    autoServiceConnected: Boolean,
    signAccessPro: Boolean,
    onBack: () -> Unit,
    onMonitorChange: (Boolean) -> Unit,
    onAutoSignChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onSaveThreshold: (Int) -> Unit
) {
    SettingsSubpage(
        title = if (signAccessPro) "签到自动化" else "签到提醒与名单",
        subtitle = if (signAccessPro) "监控、安全线和自动模式" else "提醒照常，主动签到未启用",
        onBack = onBack
    ) {
        item(key = "monitor") {
            AutomationSection(
                settings = settings,
                running = running,
                stopAt = stopAt,
                autoServiceConnected = autoServiceConnected,
                signAccessPro = signAccessPro,
                onMonitorChange = onMonitorChange
            )
        }
        if (signAccessPro) {
            item(key = "auto-sign") {
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
            }
            item(key = "diagnostics") {
                DiagnosticsGroup(
                    vm = vm,
                    autoServiceConnected = autoServiceConnected,
                    calibration = vm.calibration.collectAsStateWithLifecycle().value
                )
            }
        }
    }
}

@Composable
private fun SettingsSystemReadyContent(
    notificationsGranted: Boolean,
    exactGranted: Boolean,
    batteryIgnored: Boolean,
    onBack: () -> Unit,
    onNotifyAction: () -> Unit,
    onExactAction: () -> Unit,
    onBatteryAction: () -> Unit,
    onAutoStartAction: () -> Unit
) {
    SettingsSubpage(
        title = "系统就绪",
        subtitle = "通知、闹钟、电池和自启动",
        onBack = onBack
    ) {
        item(key = "permissions") {
            PermissionSection(
                notificationsGranted = notificationsGranted,
                exactGranted = exactGranted,
                batteryIgnored = batteryIgnored,
                onNotifyAction = onNotifyAction,
                onExactAction = onExactAction,
                onBatteryAction = onBatteryAction,
                onAutoStartAction = onAutoStartAction
            )
        }
    }
}

@Composable
private fun SettingsAppearanceContent(
    vm: AppViewModel,
    settings: AppSettings,
    themeMode: ThemeMode,
    onBack: () -> Unit,
    onPickCover: () -> Unit,
    onEditCover: () -> Unit
) {
    SettingsSubpage(
        title = "外观与显示",
        subtitle = "主题、封面和页面模式",
        onBack = onBack
    ) {
        item(key = "appearance") {
            AppearanceSection(
                settings = settings,
                themeMode = themeMode,
                onSelectTheme = vm::setTheme,
                onAccentChange = vm::setThemeAccent,
                onCornersChange = vm::setThemeCorners,
                onDecorationsChange = vm::setThemeDecorations,
                onSplashChange = vm::setSplashEnabled,
                onPickCover = onPickCover,
                onClearCover = vm::clearCustomThemeCover,
                onCustomFocusChange = vm::setCustomThemeFocus,
                onCustomPaletteChange = vm::setCustomThemePalette,
                onCustomScaleChange = vm::setCustomThemeScale,
                onCustomBlurChange = vm::setCustomThemeBlur,
                onEditCover = onEditCover
            )
        }
        item(key = "display") {
            DisplaySection(
                settings = settings,
                onMinimalChange = vm::setMinimalMode,
                onWeekTableChange = vm::setWeekDefaultTable,
                onWeekFullscreenChange = vm::setWeekDefaultFullscreen,
                onDaySwipeCrossWeekChange = vm::setDaySwipeCrossWeek,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

@Composable
private fun SettingsRinMotionContent(
    settings: AppSettings,
    onBack: () -> Unit,
    onAnimationChange: (Boolean) -> Unit,
    onPetEnabledChange: (Boolean) -> Unit,
    onPetContextChange: (Boolean) -> Unit,
    onPetWeatherChange: (Boolean) -> Unit,
    onPetScaleChange: (Float) -> Unit,
    onPetOffsetXChange: (Float) -> Unit,
    onPetOffsetYChange: (Float) -> Unit
) {
    SettingsSubpage(
        title = "Rin 与动效",
        subtitle = "角色陪伴和全局动画",
        onBack = onBack
    ) {
        item(key = "rin-motion") {
            RinMotionSection(
                settings = settings,
                onAnimationChange = onAnimationChange,
                onPetEnabledChange = onPetEnabledChange,
                onPetContextChange = onPetContextChange,
                onPetWeatherChange = onPetWeatherChange,
                onPetScaleChange = onPetScaleChange,
                onPetOffsetXChange = onPetOffsetXChange,
                onPetOffsetYChange = onPetOffsetYChange
            )
        }
    }
}

@Composable
private fun SettingsConnectionContent(
    vm: AppViewModel,
    settings: AppSettings,
    user: User,
    drafts: SettingsAdvancedDrafts,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenWeb: () -> Unit,
    onResetManual: () -> Unit
) {
    SettingsSubpage(
        title = "教务与数据",
        subtitle = "账号、数据来源和学校连接",
        onBack = onBack
    ) {
        item(key = "account") {
            AccountSection(
                settings = settings,
                user = user,
                onLogout = onLogout,
                onOpenWeb = onOpenWeb
            )
        }
        item(key = "data-source") {
            DataSourceGroup(
                vm = vm,
                settings = settings,
                drafts = drafts,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
        item(key = "manual-reset") {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                SectionHeader(title = "手动数据")
                Text(
                    text = "重置只清除你手动加入的课程、考试、待办与调课，教务数据保留。",
                    color = LocalSuiSignColors.current.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
                SuiSecondaryButton(
                    text = "重置数据",
                    onClick = onResetManual,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsAboutContent(vm: AppViewModel, onBack: () -> Unit) {
    val colors = LocalSuiSignColors.current
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "0.1.1" }
    }
    var terminalOpen by rememberSaveable { mutableStateOf(false) }
    var versionTaps by rememberSaveable { mutableIntStateOf(0) }
    var versionWindowStart by rememberSaveable { mutableStateOf(0L) }
    fun registerVersionTap() {
        val now = System.currentTimeMillis()
        if (now - versionWindowStart > 3000L) {
            versionTaps = 0
            versionWindowStart = now
        }
        versionTaps++
        if (versionTaps >= 7) {
            versionTaps = 0
            terminalOpen = true
        }
    }
    SettingsSubpage(
        title = "关于与隐私",
        subtitle = "版本、记录和本机数据",
        onBack = onBack
    ) {
        item(key = "about") {
            AboutSection(
                versionName = versionName,
                onVersionTap = ::registerVersionTap
            )
        }
        item(key = "privacy") {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                SectionHeader(title = "隐私边界")
                PaperInfoRow(
                    title = "自定义封面",
                    subtitle = "仅存本机私有目录，不上传",
                    modifier = Modifier.padding(top = 10.dp)
                )
                Hairline()
                PaperInfoRow(
                    title = "校区天气",
                    subtitle = "只取校区天气，不取定位"
                )
                Hairline()
                PaperInfoRow(
                    title = "教务凭据",
                    subtitle = "AndroidKeyStore 加密，断开即清除"
                )
                Text(
                    text = "非学校官方应用",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
    if (terminalOpen) {
        SignAccessTerminal(
            vm = vm,
            versionName = versionName,
            onDismiss = { terminalOpen = false }
        )
    }
}

@Composable
internal fun SettingsSubpage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp
    val colors = LocalSuiSignColors.current
    // Long subpages scroll past a list-item header, which leaves no visible way
    // out, so the header stays outside the scroll.
    PaperPage {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.canvas)
            ) {
                SettingsSubpageHeader(
                    title = title,
                    subtitle = subtitle,
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = pageInset)
                )
                Hairline()
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = pageInset,
                    end = pageInset,
                    top = 18.dp,
                    bottom = 92.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSubpageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回我的",
                tint = colors.text
            )
        }
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.text)
            Text(
                text = subtitle,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Slider with a local draft: dragging stays smooth and only the release writes to
 * the store, so the pet does not restyle on every pixel of movement.
 */
@Composable
private fun PetSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit
) {
    val colors = LocalSuiSignColors.current
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = colors.text, style = MaterialTheme.typography.bodyMedium)
            Text(display(draft), color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onChange(draft) },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = colors.signal,
                activeTrackColor = colors.signal,
                inactiveTrackColor = colors.border
            )
        )
    }
}

private fun signedPercent(value: Float): String {
    val pct = (value * 100).roundToInt()
    return if (pct > 0) "+$pct%" else "$pct%"
}

@Composable
private fun SettingsStatusCard(
    session: Pair<String, Boolean>,
    monitor: Pair<String, Boolean>,
    permission: Pair<String, Boolean>,
    fullMode: Boolean = InternalGate.isOn,
    onOpenSession: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surface)
            .border(1.dp, colors.border, MaterialTheme.shapes.large)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text("今日状态", color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        SettingsStatusRow("教务会话", session, onOpenSession)
        if (fullMode) {
            Hairline()
            SettingsStatusRow("后台监控", monitor, onOpenMonitor)
        }
        Hairline()
        SettingsStatusRow("系统权限", permission, onOpenPermissions)
    }
}

@Composable
private fun SettingsStatusRow(
    label: String,
    value: Pair<String, Boolean>,
    onClick: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (value.second) colors.signal else colors.warning)
        )
        Text(
            text = label,
            color = colors.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp).weight(1f)
        )
        Text(
            text = value.first,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp).size(18.dp)
        )
    }
}

@Composable
private fun SettingsNavigationCard(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surface)
            .border(1.dp, colors.border, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.text, style = MaterialTheme.typography.titleMedium)
            Text(
                text = summary,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun sessionSummary(settings: AppSettings): Pair<String, Boolean> {
    if (!settings.localMode) return "云端服务" to true
    return when {
        settings.jwxtNeedsLogin -> "需要重连" to false
        settings.jwxtConnectedAt.isNotBlank() -> "正常" to true
        else -> "未连接" to false
    }
}

private fun monitorSummary(running: Boolean, settings: AppSettings): Pair<String, Boolean> = when {
    running -> "运行中" to true
    settings.monitorEnabled -> "已开启" to true
    else -> "未开启" to false
}

private fun permissionSummary(ready: Int): Pair<String, Boolean> = "$ready / 3" to (ready == 3)

private fun automationSummary(settings: AppSettings, autoServiceConnected: Boolean): String = when {
    settings.autoSignEnabled && autoServiceConnected -> "自动模式已开启"
    settings.monitorEnabled -> "后台监控已开启"
    else -> "等待开启监控"
}

private fun appearanceSummary(themeMode: ThemeMode, customThemeActive: Boolean, minimalMode: Boolean): String {
    val theme = if (customThemeActive) "自定义封面" else themeMode.spec.name
    return if (minimalMode) "$theme · 极简模式" else theme
}

private fun rinSummary(settings: AppSettings): String = when {
    !settings.petEnabled -> "Rin 已关闭"
    !settings.animationsEnabled -> "Rin 已开启 · 静帧"
    settings.petContextAware -> "Rin 已开启 · 情境动作"
    else -> "Rin 已开启 · 基础动作"
}

private fun connectionSummary(settings: AppSettings): String = when {
    settings.localMode && settings.jwxtNeedsLogin -> "本地模式 · 需要重连"
    settings.localMode && settings.jwxtConnectedAt.isNotBlank() -> "本地模式 · 已连接"
    settings.localMode -> "本地模式 · 未连接"
    else -> "云端服务"
}

/**
 * Title + switch on a 48dp line that is selectable end to end (checked state
 * exposed with `Role.Switch`), with the
 * description underneath on its own line so 1.3x fonts never clip and the next hairline
 * keeps a 7dp breathing room below the glyphs.
 */
@Composable
internal fun PaperSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                // Whole-row target with switch semantics: selectable exposes the
                // checked state to TalkBack, and the nested switch still consumes
                // its own taps, so a tap anywhere on the 48dp line toggles exactly once.
                .selectable(
                    selected = checked,
                    role = Role.Switch
                ) { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            )
            SuiSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                contentDescription = contentDescription
            )
        }
        Text(
            text = description,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 7.dp)
        )
    }
}

/** Read-only info line, replacing the old card-bound `SettingRow` without a maxLines clamp. */
@Composable
internal fun PaperInfoRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            color = colors.text,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = subtitle,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** State is carried by the words; signal/warning survive only as the dot. */
@Composable
internal fun StatusNote(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            color = colors.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}
