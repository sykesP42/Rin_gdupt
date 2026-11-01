package top.sykesp42.rin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.RinApp
import top.sykesp42.rin.ui.RinSplash
import top.sykesp42.rin.ui.theme.CustomThemeColorMode
import top.sykesp42.rin.ui.theme.CustomThemeFocus
import top.sykesp42.rin.ui.theme.CustomThemePalette
import top.sykesp42.rin.ui.theme.CustomThemeState
import top.sykesp42.rin.ui.theme.RinTheme
import top.sykesp42.rin.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(
            if (LaunchHint.isDark(this)) R.style.Theme_Rin_Launch_Dark
            else R.style.Theme_Rin_Launch_Light
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = AppContainer.get(this)
        setContent {
            val vm: AppViewModel = viewModel(factory = AppViewModel.factory(container))
            val liveSettings by vm.settings.collectAsStateWithLifecycle()
            val settings = liveSettings ?: AppSettings()
            val mode = ThemeMode.from(settings.theme)
            val customTheme = remember(settings) {
                CustomThemeState(
                    enabled = settings.customThemeEnabled,
                    imageFileName = settings.customThemeImage,
                    seed = CustomThemePalette.decode(settings.customThemeSeed),
                    focus = CustomThemeFocus.from(settings.customThemeFocus).value,
                    colorMode = CustomThemeColorMode.from(settings.customThemePalette),
                    scale = settings.customThemeScale,
                    offsetX = settings.customThemeOffsetX,
                    offsetY = settings.customThemeOffsetY,
                    blur = settings.customThemeBlur
                )
            }
            val appearance = remember(
                mode,
                customTheme,
                settings.themeAccent,
                settings.themeCorners,
                settings.themeDecorations
            ) {
                top.sykesp42.rin.ui.theme.resolveAppearance(
                    mode = mode,
                    accentOverride = settings.themeAccent.ifBlank { null },
                    corners = top.sykesp42.rin.ui.theme.ThemeCorners.from(settings.themeCorners),
                    decorations = settings.themeDecorations,
                    customTheme = customTheme
                )
            }
            val dark = appearance.dark
            val appContext = LocalContext.current.applicationContext
            LaunchedEffect(dark) { LaunchHint.remember(appContext, dark) }
            val animationsEnabled = settings.animationsEnabled
            val courseOverrides by vm.courseOverrides.collectAsStateWithLifecycle()
            val view = LocalView.current
            var splashVisible by remember { mutableStateOf(settings.splashEnabled) }
            var splashSettled by remember { mutableStateOf(!splashVisible) }
            val appScale by animateFloatAsState(
                targetValue = if (splashVisible) 0.985f else 1f,
                animationSpec = if (animationsEnabled) tween(620, easing = FastOutSlowInEasing) else tween(0),
                label = "app_scale"
            )

            LaunchedEffect(settings.splashEnabled) {
                if (!settings.splashEnabled) splashVisible = false
            }
            LaunchedEffect(splashVisible, animationsEnabled) {
                if (splashVisible) {
                    splashSettled = false
                } else {
                    delay(if (animationsEnabled) 560 else 0)
                    splashSettled = true
                }
            }

            if (splashSettled) {
                SideEffect {
                    view.context.findActivity()?.window?.let { window ->
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = !dark
                            isAppearanceLightNavigationBars = !dark
                        }
                    }
                }
            }

            RinTheme(
                mode = mode,
                animationsEnabled = animationsEnabled,
                accentOverride = settings.themeAccent,
                corners = top.sykesp42.rin.ui.theme.ThemeCorners.from(settings.themeCorners),
                decorationsEnabled = settings.themeDecorations,
                customTheme = customTheme,
                courseOverrides = courseOverrides
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = appScale
                                    scaleY = appScale
                                }
                        ) {
                            RinApp(vm = vm, settings = settings)
                        }
                        AnimatedVisibility(
                            visible = splashVisible,
                            exit = if (animationsEnabled) {
                                slideOutVertically(
                                    animationSpec = tween(560, easing = FastOutSlowInEasing),
                                    targetOffsetY = { -it }
                                )
                            } else {
                                fadeOut(tween(0))
                            }
                        ) {
                            RinSplash(dark = dark, onFinished = { splashVisible = false })
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
