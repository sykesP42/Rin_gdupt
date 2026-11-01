package top.sky2333.rin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class RinColors(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val textSecondary: Color,
    val border: Color,
    val signal: Color,
    val signalInk: Color,
    val warning: Color,
    val actionBg: Color,
    val actionText: Color,
    val focusRing: Color,
    val accent: Color,
    val warm: Color
)

val DarkRinColors = RinColors(
    canvas = DarkTokens.canvas,
    surface = DarkTokens.surface,
    surfaceRaised = DarkTokens.surfaceRaised,
    text = DarkTokens.text,
    textSecondary = DarkTokens.textSecondary,
    border = DarkTokens.border,
    signal = DarkTokens.signal,
    signalInk = DarkTokens.signalInk,
    warning = DarkTokens.warning,
    actionBg = DarkTokens.actionBg,
    actionText = DarkTokens.actionText,
    focusRing = DarkTokens.focusRing,
    accent = DarkTokens.accent,
    warm = DarkTokens.warm
)

val RinRinColors = RinColors(
    canvas = RinTokens.canvas,
    surface = RinTokens.surface,
    surfaceRaised = RinTokens.surfaceRaised,
    text = RinTokens.text,
    textSecondary = RinTokens.textSecondary,
    border = RinTokens.border,
    signal = RinTokens.signal,
    signalInk = RinTokens.signalInk,
    warning = RinTokens.warning,
    actionBg = RinTokens.actionBg,
    actionText = RinTokens.actionText,
    focusRing = RinTokens.focusRing,
    accent = RinTokens.accent,
    warm = RinTokens.warm
)

val LightRinColors = RinColors(
    canvas = LightTokens.canvas,
    surface = LightTokens.surface,
    surfaceRaised = LightTokens.surfaceRaised,
    text = LightTokens.text,
    textSecondary = LightTokens.textSecondary,
    border = LightTokens.border,
    signal = LightTokens.signal,
    signalInk = LightTokens.signalInk,
    warning = LightTokens.warning,
    actionBg = LightTokens.actionBg,
    actionText = LightTokens.actionText,
    focusRing = LightTokens.focusRing,
    accent = LightTokens.accent,
    warm = LightTokens.warm
)

val SakuraRinColors = RinColors(
    canvas = SakuraTokens.canvas,
    surface = SakuraTokens.surface,
    surfaceRaised = SakuraTokens.surfaceRaised,
    text = SakuraTokens.text,
    textSecondary = SakuraTokens.textSecondary,
    border = SakuraTokens.border,
    signal = SakuraTokens.signal,
    signalInk = SakuraTokens.signalInk,
    warning = SakuraTokens.warning,
    actionBg = SakuraTokens.actionBg,
    actionText = SakuraTokens.actionText,
    focusRing = SakuraTokens.focusRing,
    accent = SakuraTokens.accent,
    warm = SakuraTokens.warm
)

val MangaRinColors = RinColors(
    canvas = MangaTokens.canvas,
    surface = MangaTokens.surface,
    surfaceRaised = MangaTokens.surfaceRaised,
    text = MangaTokens.text,
    textSecondary = MangaTokens.textSecondary,
    border = MangaTokens.border,
    signal = MangaTokens.signal,
    signalInk = MangaTokens.signalInk,
    warning = MangaTokens.warning,
    actionBg = MangaTokens.actionBg,
    actionText = MangaTokens.actionText,
    focusRing = MangaTokens.focusRing,
    accent = MangaTokens.accent,
    warm = MangaTokens.warm
)

val NeonRinColors = RinColors(
    canvas = NeonTokens.canvas,
    surface = NeonTokens.surface,
    surfaceRaised = NeonTokens.surfaceRaised,
    text = NeonTokens.text,
    textSecondary = NeonTokens.textSecondary,
    border = NeonTokens.border,
    signal = NeonTokens.signal,
    signalInk = NeonTokens.signalInk,
    warning = NeonTokens.warning,
    actionBg = NeonTokens.actionBg,
    actionText = NeonTokens.actionText,
    focusRing = NeonTokens.focusRing,
    accent = NeonTokens.accent,
    warm = NeonTokens.warm
)

val LocalRinColors = staticCompositionLocalOf { RinRinColors }
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

private val uiFont = FontFamily.SansSerif
private val displayFont = FontFamily.Serif

val RinTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.0).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = uiFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = uiFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = uiFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = uiFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = uiFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = uiFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = uiFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = uiFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = uiFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = displayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.8.sp
    )
)

val RinShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

object Spacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 24.dp
    val xxl = 32.dp
}


/**
 * Material You dynamic palette mapped onto the paper tokens (Android 12+).
 * The wallpaper seed drives the ink/signal roles; the paper structure stays.
 */
fun monetRinColors(scheme: androidx.compose.material3.ColorScheme): RinColors = RinColors(
    canvas = scheme.background,
    surface = scheme.surfaceContainerLow,
    surfaceRaised = scheme.surfaceContainer,
    text = scheme.onSurface,
    textSecondary = scheme.onSurfaceVariant,
    border = scheme.outlineVariant,
    signal = scheme.primary,
    signalInk = scheme.onPrimary,
    warning = scheme.error,
    actionBg = scheme.primary,
    actionText = scheme.onPrimary,
    focusRing = scheme.primary,
    accent = scheme.primary,
    warm = scheme.tertiaryContainer
)