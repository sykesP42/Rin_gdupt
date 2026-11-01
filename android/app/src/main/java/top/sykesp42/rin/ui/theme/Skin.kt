package top.sykesp42.rin.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DecorationStyle {
    PLAIN,
    SKY_GRID,
    DIARY,
    MANGA_INK,
    CIRCUIT,
    FILM,
    DEPTH,
    STICKER,
    HUD,
    PAPER,
    SEAL,
    LACE,
    STORY,
    PIXEL,
    SNOW,
    COLLAGE,
    CLAY
}

data class VisualAppearance(
    val dark: Boolean,
    val colors: SuiSignColors,
    val mode: ThemeMode,
    val spec: ThemeSpec,
    val label: String,
    val title: String,
    val description: String,
    val decoration: DecorationStyle,
    val typography: Typography,
    val shapes: Shapes,
    val ruleWidth: Dp,
    val surface: SurfaceStyle,
    val decorationsEnabled: Boolean
)

val LocalVisualStyle = staticCompositionLocalOf { resolveAppearance() }

fun resolveAppearance(
    mode: ThemeMode = ThemeMode.default,
    resolvedDark: Boolean = false,
    dynamicColors: SuiSignColors? = null,
    accentOverride: String? = null,
    corners: ThemeCorners = ThemeCorners.NATIVE,
    decorations: Boolean = true,
    customTheme: CustomThemeState? = null
): VisualAppearance {
    val baseSpec = mode.spec
    val baseColors = dynamicColors ?: ThemeCatalog.colorsFor(baseSpec)
    val custom = customTheme?.takeIf { it.hasCover }

    val spec: ThemeSpec
    val colors: SuiSignColors
    val dark: Boolean
    if (custom == null) {
        spec = baseSpec
        colors = baseColors
        dark = baseSpec.dark
    } else {
        val customSeed = custom.seed
        val paletteSeed = when (custom.colorMode) {
            CustomThemeColorMode.BASE -> null
            CustomThemeColorMode.IMAGE -> customSeed
            CustomThemeColorMode.ACCENT -> null
            CustomThemeColorMode.MONO -> customSeed?.let(CustomThemePalette::mono)
                ?: baseSpec.seed?.let(CustomThemePalette::mono)
        }
        colors = when (custom.colorMode) {
            CustomThemeColorMode.BASE -> baseColors
            CustomThemeColorMode.IMAGE -> paletteSeed?.let(ThemeCatalog::deriveColors) ?: baseColors
            CustomThemeColorMode.ACCENT -> customSeed?.accent?.let {
                ThemeCatalog.withAccent(baseColors, CustomThemePalette.hex(it))
            } ?: baseColors
            CustomThemeColorMode.MONO -> paletteSeed?.let(ThemeCatalog::deriveColors) ?: baseColors
        }
        dark = if (custom.colorMode == CustomThemeColorMode.BASE) {
            baseSpec.dark
        } else {
            colors.canvas.luminance() < 0.42f
        }
        spec = baseSpec.copy(
            name = "自定义封面",
            en = "CUSTOM",
            blurb = "本机封面 · 基于${baseSpec.name}",
            stamp = "自定义",
            dark = dark,
            seed = paletteSeed ?: customSeed ?: baseSpec.seed,
            fixedColors = if (custom.colorMode == CustomThemeColorMode.BASE) {
                baseSpec.fixedColors
            } else {
                null
            },
            artFocus = custom.focus,
            artOffsetX = custom.offsetX,
            artOffsetY = custom.offsetY,
            customArtPath = custom.imageFileName,
            artScale = custom.scale,
            artBlur = custom.blur
        )
    }

    val finalColors = ThemeCatalog.withAccent(colors, accentOverride)
    val finalSpec = spec.copy(dark = dark)
    val ruleWidth = when (finalSpec.type) {
        TypeStyle.SERIF, TypeStyle.EDITORIAL -> 2.dp
        else -> 1.dp
    }
    return VisualAppearance(
        dark = dark,
        colors = finalColors,
        mode = mode,
        spec = finalSpec,
        label = finalSpec.name,
        title = "${finalSpec.name} · ${finalSpec.en}",
        description = finalSpec.blurb,
        decoration = if (decorations) finalSpec.decoration else DecorationStyle.PLAIN,
        typography = ThemeCatalog.typographyFor(finalSpec.type),
        shapes = ThemeCatalog.shapesFor(finalSpec.shape, corners),
        ruleWidth = ruleWidth,
        surface = finalSpec.surface,
        decorationsEnabled = decorations
    )
}
