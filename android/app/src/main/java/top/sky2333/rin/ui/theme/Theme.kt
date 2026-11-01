package top.sky2333.rin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun RinTheme(
    mode: ThemeMode = ThemeMode.default,
    resolvedDark: Boolean = isSystemInDarkTheme(),
    animationsEnabled: Boolean = true,
    accentOverride: String = "",
    corners: ThemeCorners = ThemeCorners.NATIVE,
    decorationsEnabled: Boolean = true,
    customTheme: CustomThemeState? = null,
    courseOverrides: Map<String, top.sky2333.rin.data.CourseOverride> = emptyMap(),
    content: @Composable () -> Unit
) {
    val appearance = resolveAppearance(
        mode = mode,
        resolvedDark = resolvedDark,
        accentOverride = accentOverride.ifBlank { null },
        corners = corners,
        decorations = decorationsEnabled,
        customTheme = customTheme
    )
    val colors = appearance.colors
    val scheme = if (appearance.dark) {
        darkColorScheme(
            // `primary` paints text; filled buttons use actionBg, calibrated with actionText.
            primary = colors.signal,
            onPrimary = colors.signalInk,
            primaryContainer = colors.surfaceRaised,
            onPrimaryContainer = colors.text,
            secondary = colors.signal,
            onSecondary = colors.signalInk,
            secondaryContainer = colors.signal.copy(alpha = 0.14f),
            onSecondaryContainer = colors.signal,
            tertiary = colors.warning,
            onTertiary = colors.canvas,
            background = colors.canvas,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            surfaceTint = colors.signal,
            outline = colors.textSecondary.copy(alpha = 0.45f),
            outlineVariant = colors.border,
            error = colors.warning,
            onError = colors.canvas,
            errorContainer = colors.warning.copy(alpha = 0.15f),
            onErrorContainer = colors.warning
        )
    } else {
        lightColorScheme(
            primary = colors.signal,
            onPrimary = colors.signalInk,
            primaryContainer = colors.surfaceRaised,
            onPrimaryContainer = colors.text,
            secondary = colors.signal,
            onSecondary = colors.signalInk,
            secondaryContainer = colors.signal.copy(alpha = 0.12f),
            onSecondaryContainer = colors.signal,
            tertiary = colors.warning,
            onTertiary = colors.canvas,
            background = colors.canvas,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            surfaceTint = colors.signal,
            outline = colors.textSecondary.copy(alpha = 0.45f),
            outlineVariant = colors.border,
            error = colors.warning,
            onError = colors.canvas,
            errorContainer = colors.warning.copy(alpha = 0.12f),
            onErrorContainer = colors.warning
        )
    }
    CompositionLocalProvider(
        LocalRinColors provides colors,
        LocalVisualStyle provides appearance,
        LocalAnimationsEnabled provides animationsEnabled,
        top.sky2333.rin.ui.components.LocalCourseOverrides provides courseOverrides
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = appearance.typography,
            shapes = appearance.shapes,
            content = content
        )
    }
}

@Composable
fun systemDark(): Boolean = isSystemInDarkTheme()
