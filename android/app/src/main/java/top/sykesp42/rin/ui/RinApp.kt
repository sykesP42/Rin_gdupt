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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.sykesp42.rin.R
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.ui.components.BrandMark
import top.sykesp42.rin.ui.components.RinBackdrop
import top.sykesp42.rin.ui.components.RinBottomBar
import top.sykesp42.rin.ui.components.ThemePickerSheet
import top.sykesp42.rin.ui.screens.AcademicScreen
import top.sykesp42.rin.ui.screens.OnboardingScreen
import top.sykesp42.rin.ui.screens.ScheduleScreen
import top.sykesp42.rin.ui.screens.SettingsScreen
import top.sykesp42.rin.ui.screens.TodayScreen
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.ThemeMode

enum class MainTab(val label: String, @DrawableRes val icon: Int) {
    TODAY("今日", R.drawable.ic_tab_today),
    SCHEDULE("课表", R.drawable.ic_tab_schedule),
    ACADEMIC("校务", R.drawable.ic_tab_academic),
    ME("我的", R.drawable.ic_tab_me)
}

@Composable
fun RinApp(vm: AppViewModel, settings: AppSettings?) {
    val colors = LocalRinColors.current
    if (settings == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            RinBackdrop(Modifier.fillMaxSize())
            CircularProgressIndicator(
                modifier = Modifier.padding(6.dp),
                strokeWidth = 2.dp,
                color = colors.signal
            )
        }
        return
    }
    if (!settings.onboarded) {
        OnboardingScreen(vm = vm, settings = settings)
        return
    }
    MainScaffold(vm = vm, settings = settings)
}

@Composable
private fun MainScaffold(vm: AppViewModel, settings: AppSettings) {
    var tab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    var themePickerOpen by rememberSaveable { mutableStateOf(false) }
    val colors = LocalRinColors.current
    val snackbar = remember { SnackbarHostState() }
    val themeMode = ThemeMode.from(settings.theme)
    val animations = LocalAnimationsEnabled.current
    val density = LocalDensity.current
    val risePx = with(density) { 8.dp.roundToPx() }
    val displayName = Identity.displayName(settings, "R")
    // 简洁模式 keeps only 今日 / 课表 / 我的: 校务 is a school-side page and has
    // nothing to show without a JWXT connection.
    val tabs = remember(settings.simpleMode) {
        if (settings.simpleMode) {
            listOf(MainTab.TODAY, MainTab.SCHEDULE, MainTab.ME)
        } else {
            MainTab.entries.toList()
        }
    }

    fun selectTab(item: MainTab) {
        tab = item
        when (item) {
            MainTab.TODAY -> vm.refreshSchedule()
            MainTab.SCHEDULE -> vm.refreshSchedule()
            MainTab.ACADEMIC -> vm.openAcademic()
            MainTab.ME -> Unit
        }
    }

    // Switching to 简洁模式 while standing on 校务 would leave an invisible tab
    // selected; fall back to 今日.
    LaunchedEffect(settings.simpleMode) {
        if (settings.simpleMode && tab == MainTab.ACADEMIC) tab = MainTab.TODAY
    }

    BackHandler(enabled = tab != MainTab.TODAY) { selectTab(MainTab.TODAY) }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) { vm.refreshSchedule() }

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
                RinBottomBar(tabs = tabs, selected = tab, onSelect = ::selectTab)
            }
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (!animations) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (
                            fadeIn(tween(240)) + slideInVertically(tween(240)) { risePx }
                            ) togetherWith fadeOut(tween(160))
                    }
                },
                label = "tab_content",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
            ) { activeTab ->
                when (activeTab) {
                    MainTab.TODAY -> TodayScreen(
                        vm = vm,
                        themeMode = themeMode,
                        avatarText = displayName,
                        onToggleTheme = { themePickerOpen = true },
                        onOpenSchedule = { selectTab(MainTab.SCHEDULE) }
                    )
                    MainTab.SCHEDULE -> ScheduleScreen(
                        vm = vm,
                        themeMode = themeMode,
                        avatarText = displayName,
                        onToggleTheme = { themePickerOpen = true },
                        onGoToday = { selectTab(MainTab.TODAY) },
                        onOpenSettings = { selectTab(MainTab.ME) }
                    )
                    MainTab.ACADEMIC -> AcademicScreen(
                        vm = vm,
                        themeMode = themeMode,
                        avatarText = displayName,
                        onToggleTheme = { themePickerOpen = true },
                        onOpenSettings = { selectTab(MainTab.ME) }
                    )
                    MainTab.ME -> SettingsScreen(
                        vm = vm,
                        settings = settings,
                        themeMode = themeMode,
                        onToggleTheme = { themePickerOpen = true }
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
