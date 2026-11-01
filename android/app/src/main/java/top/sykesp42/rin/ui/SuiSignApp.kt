package top.sykesp42.rin.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.sykesp42.rin.data.InternalGate
import top.sykesp42.rin.monitor.SessionKeeper
import top.sykesp42.rin.R
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.ui.Identity
import top.sykesp42.rin.ui.components.BrandMark
import top.sykesp42.rin.ui.components.RinBackdrop
import top.sykesp42.rin.ui.components.RinBottomBar
import top.sykesp42.rin.ui.components.ThemePickerSheet
import top.sykesp42.rin.ui.screens.HistoryScreen
import top.sykesp42.rin.ui.screens.ScheduleScreen
import top.sykesp42.rin.ui.screens.SearchScreen
import top.sykesp42.rin.ui.screens.SettingsScreen
import top.sykesp42.rin.ui.screens.TodayScreen
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.Spacing
import top.sykesp42.rin.ui.theme.ThemeMode

enum class MainTab(val label: String, @DrawableRes val icon: Int) {
    TODAY("今日", R.drawable.ic_tab_today),
    SCHEDULE("课表", R.drawable.ic_tab_schedule),
    SIGN("签到", R.drawable.ic_tab_sign),
    SEARCH("校务", R.drawable.ic_tab_campus),
    ME("我的", R.drawable.ic_tab_me)
}

object MainNavigation {
    fun visibleTabs(minimal: Boolean, full: Boolean): List<MainTab> = when {
        minimal && full -> listOf(MainTab.SIGN, MainTab.ME)
        minimal -> listOf(MainTab.TODAY, MainTab.ME)
        else -> MainTab.entries.filter { it != MainTab.SIGN || full }
    }

    fun homeTab(minimal: Boolean): MainTab = if (minimal && InternalGate.isOn) MainTab.SIGN else MainTab.TODAY

    /** Restores a saved tab by name; an old/removed name falls back to the home tab. */
    fun restoreTab(name: String, minimal: Boolean, full: Boolean): MainTab =
        MainTab.entries.firstOrNull { it.name == name && it in visibleTabs(minimal, full) }
            ?: homeTab(minimal)
}

@Composable
fun SuiSignApp(vm: AppViewModel, settings: AppSettings?, initialTab: MainTab = MainTab.TODAY) {
    val auth by vm.auth.collectAsStateWithLifecycle()
    val colors = LocalSuiSignColors.current

    if (settings == null || auth is AuthState.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            RinBackdrop(Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(size = 52.dp)
                Text(
                    "Rin",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    "RIN",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 3.dp, bottom = 20.dp)
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.signal
                )
            }
        }
        return
    }

    val context = LocalContext.current
    LaunchedEffect(settings.localMode, settings.monitorEnabled) {
        SessionKeeper.sync(context, settings.localMode && settings.monitorEnabled)
    }

    // No onboarding gate in this build: the local profile is the account, the
    // license comes from the verification service, so first launch lands
    // straight on the main surface.
    when (val currentAuth = auth) {
        is AuthState.LoggedIn ->
            MainScaffold(vm = vm, settings = settings, user = currentAuth.user, initialTab = initialTab)
        // LoggedOut used to render nothing — turning off 本地模式 without a
        // cloud session blanked the whole app. Dead ends get a one-tap way back.
        else -> Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            RinBackdrop(Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(size = 52.dp)
                Text(
                    "登录状态已失效",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    "回到本地模式即可继续使用",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )
                top.sykesp42.rin.ui.components.SuiPrimaryButton(
                    text = "回到本地模式",
                    onClick = { vm.setLocalMode(true) }
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    vm: AppViewModel,
    settings: AppSettings,
    user: top.sykesp42.rin.data.User,
    initialTab: MainTab = MainTab.TODAY
) {
    var tab by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver<MainTab, String>(
            save = { it.name },
            restore = { MainNavigation.restoreTab(it, settings.minimalMode, InternalGate.isOn) }
        )
    ) {
        mutableStateOf(if (settings.minimalMode) MainNavigation.homeTab(true) else initialTab)
    }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    // Mini 卡入口要求课表页落在指定视图（week/table）；带时间戳 nonce 保证
    // 连续点同一张卡也能再次触发。
    var scheduleEntry by remember { mutableStateOf("") }
    var campusSection by rememberSaveable { mutableStateOf<Int?>(null) }
    var themePickerOpen by rememberSaveable { mutableStateOf(false) }
    var profileSignal by rememberSaveable { mutableIntStateOf(0) }
    val colors = LocalSuiSignColors.current
    val snackbar = remember { SnackbarHostState() }
    val themeMode = ThemeMode.from(settings.theme)
    val openThemePicker: () -> Unit = { themePickerOpen = true }
    val animations = LocalAnimationsEnabled.current
    val density = LocalDensity.current
    val risePx = with(density) { 8.dp.roundToPx() }
    // One name for the whole app: privacy mode must not leak the real one.
    val displayName = Identity.displayName(settings, user.displayName.ifBlank { user.username })
    val gateOn by InternalGate.enabled.collectAsStateWithLifecycle()
    val tabs = remember(settings.minimalMode, gateOn) { MainNavigation.visibleTabs(settings.minimalMode, gateOn) }
    val homeTab = MainNavigation.homeTab(settings.minimalMode)

    fun selectTab(item: MainTab) {
        if (item == tab && item == MainTab.SIGN && historyOpen) {
            historyOpen = false
            return
        }
        if (item == MainTab.SEARCH && tab == MainTab.SEARCH) campusSection = null
        if (item != MainTab.SEARCH) campusSection = null
        tab = item
        if (item != MainTab.SIGN) historyOpen = false
        when (item) {
            MainTab.TODAY -> {
                vm.ensureTodayLoaded()
                vm.ensureScheduleLoaded()
            }
            MainTab.SCHEDULE -> vm.ensureScheduleLoaded()
            MainTab.SIGN -> {
                vm.ensureTodayLoaded()
                vm.ensureScheduleLoaded()
            }
            MainTab.ME -> vm.ensureThresholdLoaded()
            MainTab.SEARCH -> Unit
        }
    }

    BackHandler(enabled = historyOpen || tab != homeTab) {
        if (historyOpen) historyOpen = false else selectTab(homeTab)
    }
    LaunchedEffect(settings.minimalMode) {
        historyOpen = false
        if (tabs.none { it == tab }) tab = homeTab
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.ensureTodayLoaded()
        vm.ensureScheduleLoaded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        RinBackdrop(Modifier.fillMaxSize())
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(hostState = snackbar, modifier = Modifier.padding(16.dp)) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = colors.surfaceRaised,
                        contentColor = colors.text,
                        actionColor = colors.signal,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            },
            bottomBar = {
                RinBottomBar(
                    tabs = tabs,
                    selected = tab,
                    onSelect = ::selectTab
                )
            }
        ) { padding ->
            AnimatedContent(
                targetState = tab to historyOpen,
                transitionSpec = {
                    if (!animations) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (
                            fadeIn(tween(240)) +
                                slideInVertically(tween(240)) { risePx }
                            ) togetherWith fadeOut(tween(160))
                    }
                },
                label = "tab_content",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
            ) { (activeTab, showingHistory) ->
                when (activeTab) {
                    MainTab.TODAY -> TodayScreen(
                        vm = vm,
                        themeMode = themeMode,
                        avatarText = displayName,
                        onToggleTheme = openThemePicker,
                        onOpenSchedule = { entry ->
                            scheduleEntry = entry
                            selectTab(MainTab.SCHEDULE)
                        },
                        onOpenSign = { selectTab(MainTab.SIGN) },
                        minimal = settings.minimalMode
                    )
                    MainTab.SCHEDULE -> ScheduleScreen(
                        vm = vm,
                        themeMode = themeMode,
                        avatarText = displayName,
                        onToggleTheme = openThemePicker,
                        onGoToday = { selectTab(MainTab.TODAY) },
                        onOpenProfile = { selectTab(MainTab.ME); profileSignal++ },
                        initialEntry = scheduleEntry
                    )
                    MainTab.SIGN -> if (showingHistory) {
                        HistoryScreen(
                            vm = vm,
                            themeMode = themeMode,
                            avatarText = displayName,
                            onToggleTheme = openThemePicker,
                            onBack = { historyOpen = false }
                        )
                    } else {
                        TodayScreen(
                            vm = vm,
                            themeMode = themeMode,
                            avatarText = displayName,
                            onToggleTheme = openThemePicker,
                            onOpenSchedule = { entry ->
                            scheduleEntry = entry
                            selectTab(MainTab.SCHEDULE)
                        },
                            signOnly = true,
                            onOpenHistory = {
                                vm.loadHistory()
                                vm.loadAttendance()
                                historyOpen = true
                            }
                        )
                    }
                    MainTab.SEARCH -> {
                        val section = campusSection
                        if (section != null) {
                            ScheduleScreen(
                                vm = vm,
                                themeMode = themeMode,
                                avatarText = displayName,
                                onToggleTheme = openThemePicker,
                                onGoToday = { selectTab(MainTab.TODAY) },
                                academicOnly = true,
                                initialSegment = section,
                                onBack = { campusSection = null }
                            )
                        } else {
                            SearchScreen(
                                vm = vm,
                                themeMode = themeMode,
                                avatarText = displayName,
                                onToggleTheme = openThemePicker,
                                onOpenToday = { selectTab(MainTab.TODAY) },
                                onOpenSchedule = {
                                    scheduleEntry = ""
                                    selectTab(MainTab.SCHEDULE)
                                },
                                onOpenAcademic = { campusSection = it }
                            )
                        }
                    }
                    MainTab.ME -> SettingsScreen(
                        vm = vm,
                        settings = settings,
                        user = user,
                        themeMode = themeMode,
                        onToggleTheme = openThemePicker,
                        onLogout = { vm.logout() },
                        profileSignal = profileSignal
                    )
                }
            }
        }
        if (themePickerOpen) {
            ThemePickerSheet(
                selected = themeMode,
                onSelect = {
                    vm.setTheme(it)
                    themePickerOpen = false
                },
                onDismiss = { themePickerOpen = false }
            )
        }
    }
}
