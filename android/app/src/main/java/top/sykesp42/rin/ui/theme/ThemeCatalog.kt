package top.sykesp42.rin.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.sykesp42.rin.R
import kotlin.math.max
import kotlin.math.min

enum class ThemeCollection(val label: String) {
    RIN("RIN"),
    CLASSIC("经典")
}

enum class TypeStyle { SANS, SERIF, EDITORIAL, MONO, PIXEL, ROUND, DELICATE }

enum class ShapeStyle { SOFT, SHARP, CRYSTAL, PILL, PIXEL, EDITORIAL, FILM, CUT }

enum class SurfaceStyle { PAPER, GLASS, OUTLINE, STICKER, PIXEL, CLAY }

enum class HeroStyle { COVER, SPLIT, TICKET, BOOK, CRYSTAL }

enum class ThemeCorners(val key: String, val label: String) {
    NATIVE("native", "默认"),
    SOFT("soft", "柔和"),
    SHARP("sharp", "利落");

    companion object {
        fun from(key: String): ThemeCorners = entries.firstOrNull { it.key == key } ?: NATIVE
    }
}

@Immutable
data class ThemeSeed(
    val bg: Color,
    val card: Color,
    val ink: Color,
    val accent: Color,
    val accent2: Color
)

@Immutable
data class ThemeSpec(
    val mode: ThemeMode,
    val name: String,
    val en: String,
    val blurb: String,
    val tags: List<String>,
    val collection: ThemeCollection,
    val dark: Boolean,
    val stamp: String,
    val type: TypeStyle,
    val shape: ShapeStyle,
    val decoration: DecorationStyle,
    val surface: SurfaceStyle,
    val hero: HeroStyle,
    val seed: ThemeSeed? = null,
    val fixedColors: RinColors? = null,
    @DrawableRes val art: Int? = null,
    @DrawableRes val artThumb: Int? = null,
    val artFocus: Float = 0.28f,
    val artOffsetX: Float = 0f,
    val artOffsetY: Float = 0f,
    val customArtPath: String? = null,
    val artScale: Float = 1f,
    val artBlur: Float = 0f
) {
    val hasArt: Boolean get() = customArtPath != null || art != null
}

data class AccentPreset(val key: String, val label: String, val hex: String)

object ThemeCatalog {
    const val DEFAULT_ID = "sky"

    private val specs: List<ThemeSpec> = listOf(
        ThemeSpec(
            mode = ThemeMode.SKY,
            name = "晴空映像",
            en = "SUMMER SKY",
            blurb = "晴空蓝与奶白；宋体标题，课程色时间线。",
            tags = listOf("经典", "天光", "纸感"),
            collection = ThemeCollection.CLASSIC,
            dark = false,
            stamp = "晴空",
            type = TypeStyle.SANS,
            shape = ShapeStyle.SOFT,
            decoration = DecorationStyle.SKY_GRID,
            surface = SurfaceStyle.PAPER,
            hero = HeroStyle.COVER,
            fixedColors = SkyRinColors,
            art = R.drawable.rin_manual_sky,
            artThumb = R.drawable.theme_classic_sky_thumb,
            artFocus = 0.46f
        ),
        ThemeSpec(
            mode = ThemeMode.BOOK,
            name = "绀蓝书院",
            en = "JOURNAL",
            blurb = "奶油纸与绀蓝墨；双线版心，目录式列表。",
            tags = listOf("经典", "书卷", "衬线"),
            collection = ThemeCollection.CLASSIC,
            dark = false,
            stamp = "书院",
            type = TypeStyle.SERIF,
            shape = ShapeStyle.SHARP,
            decoration = DecorationStyle.DIARY,
            surface = SurfaceStyle.PAPER,
            hero = HeroStyle.BOOK,
            fixedColors = BookRinColors
        ),
        ThemeSpec(
            mode = ThemeMode.CRYSTAL,
            name = "琉璃标本",
            en = "PRISM",
            blurb = "晶片折角与少量紫；正文实底，长课名可换行。",
            tags = listOf("经典", "琉璃", "清透"),
            collection = ThemeCollection.CLASSIC,
            dark = false,
            stamp = "琉璃",
            type = TypeStyle.SANS,
            shape = ShapeStyle.CRYSTAL,
            decoration = DecorationStyle.SKY_GRID,
            surface = SurfaceStyle.GLASS,
            hero = HeroStyle.CRYSTAL,
            fixedColors = CrystalRinColors
        ),
        ThemeSpec(
            mode = ThemeMode.RADIO,
            name = "月白电台",
            en = "RADIO",
            blurb = "墨色夜幕与月白文字；课程沿细轨道排列。",
            tags = listOf("经典", "夜色", "电台"),
            collection = ThemeCollection.CLASSIC,
            dark = true,
            stamp = "电台",
            type = TypeStyle.MONO,
            shape = ShapeStyle.SOFT,
            decoration = DecorationStyle.CIRCUIT,
            surface = SurfaceStyle.OUTLINE,
            hero = HeroStyle.COVER,
            fixedColors = RadioRinColors,
            art = R.drawable.rin_manual_night,
            artThumb = R.drawable.theme_classic_radio_thumb,
            artFocus = 0.46f
        ),
        ThemeSpec(
            mode = ThemeMode.CINEMA,
            name = "午夜场",
            en = "MIDNIGHT CINEMA",
            blurb = "胶片边框 · 衬线大标题 · 红黑票根",
            tags = listOf("电影", "夜景", "暗色"),
            collection = ThemeCollection.RIN,
            dark = true,
            stamp = "午夜",
            type = TypeStyle.SERIF,
            shape = ShapeStyle.FILM,
            decoration = DecorationStyle.FILM,
            surface = SurfaceStyle.OUTLINE,
            hero = HeroStyle.COVER,
            seed = seed(0xFF0B0E18, 0xFF15192A, 0xFFF6F0E7, 0xFFE2544F, 0xFFE9B872),
            art = R.drawable.theme_cinema,
            artThumb = R.drawable.theme_cinema_thumb,
            artFocus = 0.22f
        ),
        ThemeSpec(
            mode = ThemeMode.ABYSS,
            name = "深海",
            en = "ABYSS",
            blurb = "深度表 · 暗玻璃 · 夜光青",
            tags = listOf("深海", "夜航", "夜光"),
            collection = ThemeCollection.RIN,
            dark = true,
            stamp = "深海",
            type = TypeStyle.SANS,
            shape = ShapeStyle.CRYSTAL,
            decoration = DecorationStyle.DEPTH,
            surface = SurfaceStyle.GLASS,
            hero = HeroStyle.SPLIT,
            seed = seed(0xFF051620, 0xFF0C2A39, 0xFFE8F7F4, 0xFF66E0D3, 0xFFA99BE0),
            art = R.drawable.theme_abyss,
            artThumb = R.drawable.theme_abyss_thumb,
            artFocus = 0.30f
        ),
        ThemeSpec(
            mode = ThemeMode.STICKER,
            name = "贴纸社",
            en = "STICKER CLUB",
            blurb = "贴纸叠层 · 粗描边 · Q 版",
            tags = listOf("Q版", "贴纸", "夏日"),
            collection = ThemeCollection.RIN,
            dark = false,
            stamp = "贴纸",
            type = TypeStyle.ROUND,
            shape = ShapeStyle.PILL,
            decoration = DecorationStyle.STICKER,
            surface = SurfaceStyle.STICKER,
            hero = HeroStyle.SPLIT,
            seed = seed(0xFFEEFAF4, 0xFFFFFDF7, 0xFF1F211E, 0xFF12A8E8, 0xFFFF7FA5),
            art = R.drawable.theme_sticker,
            artThumb = R.drawable.theme_sticker_thumb,
            artFocus = 0.32f
        ),
        ThemeSpec(
            mode = ThemeMode.NEON,
            name = "电子夜",
            en = "NEON OS",
            blurb = "HUD 网格 · 等宽字 · 荧光绿",
            tags = listOf("赛博", "HUD", "重度"),
            collection = ThemeCollection.RIN,
            dark = true,
            stamp = "电子",
            type = TypeStyle.MONO,
            shape = ShapeStyle.SHARP,
            decoration = DecorationStyle.HUD,
            surface = SurfaceStyle.OUTLINE,
            hero = HeroStyle.COVER,
            seed = seed(0xFF050A08, 0xFF0C1712, 0xFFE9FFF4, 0xFFB6FF3B, 0xFF00E5FF),
            art = R.drawable.theme_neon,
            artThumb = R.drawable.theme_neon_thumb,
            artFocus = 0.24f
        ),
        ThemeSpec(
            mode = ThemeMode.MORNING,
            name = "晨刊",
            en = "MORNING EDITION",
            blurb = "方格纸 · 晨报分栏 · 水彩",
            tags = listOf("水彩", "晨光", "纸刊"),
            collection = ThemeCollection.RIN,
            dark = false,
            stamp = "晨刊",
            type = TypeStyle.EDITORIAL,
            shape = ShapeStyle.EDITORIAL,
            decoration = DecorationStyle.PAPER,
            surface = SurfaceStyle.PAPER,
            hero = HeroStyle.COVER,
            seed = seed(0xFFF7F1E4, 0xFFFFFDF7, 0xFF2B2A26, 0xFFB4543A, 0xFF6E8B74),
            art = R.drawable.theme_morning,
            artThumb = R.drawable.theme_morning_thumb,
            artFocus = 0.28f
        ),
        ThemeSpec(
            mode = ThemeMode.INK,
            name = "墨判",
            en = "INK & SEAL",
            blurb = "木版留白 · 朱印 · 宋体",
            tags = listOf("和风", "木版", "留白"),
            collection = ThemeCollection.RIN,
            dark = false,
            stamp = "墨判",
            type = TypeStyle.SERIF,
            shape = ShapeStyle.CUT,
            decoration = DecorationStyle.SEAL,
            surface = SurfaceStyle.PAPER,
            hero = HeroStyle.TICKET,
            seed = seed(0xFFF2EAD9, 0xFFFFF9EC, 0xFF191714, 0xFFA63228, 0xFF2F3B33),
            art = R.drawable.theme_ink,
            artThumb = R.drawable.theme_ink_thumb,
            artFocus = 0.25f
        ),
        ThemeSpec(
            mode = ThemeMode.SHOJO,
            name = "少女信",
            en = "SHOJO LETTER",
            blurb = "花边 · 少女漫 · 粉色信笺",
            tags = listOf("少女漫", "花边", "柔"),
            collection = ThemeCollection.RIN,
            dark = false,
            stamp = "少女",
            type = TypeStyle.DELICATE,
            shape = ShapeStyle.PILL,
            decoration = DecorationStyle.LACE,
            surface = SurfaceStyle.PAPER,
            hero = HeroStyle.COVER,
            seed = seed(0xFFFFF0F4, 0xFFFFFFFF, 0xFF4A2C3A, 0xFFD96A8A, 0xFF9B7BB5),
            art = R.drawable.theme_shojo,
            artThumb = R.drawable.theme_shojo_thumb,
            artFocus = 0.24f
        ),
        ThemeSpec(
            mode = ThemeMode.STORYBOOK,
            name = "绘本",
            en = "STORYBOOK",
            blurb = "画框 · 绘本 · 鼠尾草绿",
            tags = listOf("绘本", "森林", "柔"),
            collection = ThemeCollection.RIN,
            dark = false,
            stamp = "绘本",
            type = TypeStyle.EDITORIAL,
            shape = ShapeStyle.EDITORIAL,
            decoration = DecorationStyle.STORY,
            surface = SurfaceStyle.PAPER,
            hero = HeroStyle.TICKET,
            seed = seed(0xFFEEF3E7, 0xFFFFFDF7, 0xFF2E3A2F, 0xFF7B9E6B, 0xFFD98F8F),
            art = R.drawable.theme_storybook,
            artThumb = R.drawable.theme_storybook_thumb,
            artFocus = 0.28f
        ),
        ThemeSpec(
            mode = ThemeMode.PIXEL,
            name = "像素夏",
            en = "PIXEL SUMMER",
            blurb = "像素窗口 · 硬阴影 · 8-bit",
            tags = listOf("像素", "复古", "游戏"),
            collection = ThemeCollection.RIN,
            dark = true,
            stamp = "像素",
            type = TypeStyle.PIXEL,
            shape = ShapeStyle.PIXEL,
            decoration = DecorationStyle.PIXEL,
            surface = SurfaceStyle.PIXEL,
            hero = HeroStyle.TICKET,
            seed = seed(0xFF0E1B33, 0xFF16294A, 0xFFF4F7FF, 0xFFFFCB47, 0xFFFF6B6B),
            art = R.drawable.theme_pixel,
            artThumb = R.drawable.theme_pixel_thumb,
            artFocus = 0.30f
        ),
        ThemeSpec(
            mode = ThemeMode.SNOW,
            name = "雪原",
            en = "SNOWFIELD",
            blurb = "透明卡 · 大量留白 · 极简",
            tags = listOf("极简", "冷白", "安静"),
            collection = ThemeCollection.RIN,
            dark = false,
            stamp = "雪原",
            type = TypeStyle.SANS,
            shape = ShapeStyle.CRYSTAL,
            decoration = DecorationStyle.SNOW,
            surface = SurfaceStyle.GLASS,
            hero = HeroStyle.COVER,
            seed = seed(0xFFF3F8FB, 0xFFFFFFFF, 0xFF243A4A, 0xFF74A8C8, 0xFFC9A76A),
            art = R.drawable.theme_snow,
            artThumb = R.drawable.theme_snow_thumb,
            artFocus = 0.30f
        ),
        ThemeSpec(
            mode = ThemeMode.PAPERDOLL,
            name = "纸娃娃",
            en = "PAPER DOLL",
            blurb = "剪贴 · 纸片 · 时尚编辑",
            tags = listOf("时装", "拼贴", "编辑"),
            collection = ThemeCollection.RIN,
            dark = false,
            stamp = "纸娃",
            type = TypeStyle.EDITORIAL,
            shape = ShapeStyle.EDITORIAL,
            decoration = DecorationStyle.COLLAGE,
            surface = SurfaceStyle.PAPER,
            hero = HeroStyle.SPLIT,
            seed = seed(0xFFF4EEE4, 0xFFFFFDF8, 0xFF232323, 0xFFD95545, 0xFF2F6B5E),
            art = R.drawable.theme_paperdoll,
            artThumb = R.drawable.theme_paperdoll_thumb,
            artFocus = 0.22f
        ),
        ThemeSpec(
            mode = ThemeMode.CLAY,
            name = "软冰淇淋",
            en = "SOFT SERVE",
            blurb = "软胶 · 大圆角 · 糖果色",
            tags = listOf("软胶", "甜", "Q弹"),
            collection = ThemeCollection.RIN,
            dark = false,
            stamp = "软冰",
            type = TypeStyle.ROUND,
            shape = ShapeStyle.PILL,
            decoration = DecorationStyle.CLAY,
            surface = SurfaceStyle.CLAY,
            hero = HeroStyle.SPLIT,
            seed = seed(0xFFFFF3E9, 0xFFFFFFFF, 0xFF4A3340, 0xFFF28C8C, 0xFF8FD3C7),
            art = R.drawable.theme_clay,
            artThumb = R.drawable.theme_clay_thumb,
            artFocus = 0.30f
        )
    )

    private val byMode: Map<ThemeMode, ThemeSpec> = specs.associateBy { it.mode }

    val all: List<ThemeSpec> get() = specs
    val rin: List<ThemeSpec> get() = specs.filter { it.collection == ThemeCollection.RIN }
    val classic: List<ThemeSpec> get() = specs.filter { it.collection == ThemeCollection.CLASSIC }

    fun specOf(mode: ThemeMode): ThemeSpec = byMode.getValue(mode)

    fun spec(key: String): ThemeSpec = specOf(ThemeMode.from(key))

    val accentPresets: List<AccentPreset> = listOf(
        AccentPreset("default", "原生", ""),
        AccentPreset("sky", "夏空", "#007BAF"),
        AccentPreset("sakura", "樱色", "#C25B7C"),
        AccentPreset("mint", "薄荷", "#1E8F72"),
        AccentPreset("amber", "琥珀", "#B5732A"),
        AccentPreset("violet", "夜紫", "#7462A8"),
        AccentPreset("ink", "朱墨", "#A63228")
    )

    fun colorsFor(spec: ThemeSpec): RinColors =
        spec.fixedColors ?: deriveColors(requireNotNull(spec.seed))

    fun deriveColors(seed: ThemeSeed): RinColors {
        val dark = seed.bg.luminance() < 0.42f
        var text = seed.ink
        repeat(3) { text = ensureContrast(ensureContrast(text, seed.card, 7f), seed.bg, 7f) }
        val raised = if (dark) mix(seed.card, Color.White, 0.07f) else mix(seed.card, seed.bg, 0.48f)
        val warm = mix(seed.card, seed.accent2, if (dark) 0.16f else 0.10f)
        var secondary = mix(text, seed.bg, 0.70f)
        repeat(2) {
            secondary = ensureContrast(ensureContrast(secondary, seed.card, 4.65f), seed.bg, 4.65f)
            secondary = ensureContrast(ensureContrast(secondary, raised, 4.65f), warm, 4.65f)
        }
        var signal = seed.accent
        repeat(2) {
            signal = ensureContrast(ensureContrast(signal, seed.bg, 4.6f), seed.card, 4.6f)
            signal = ensureContrast(ensureContrast(signal, raised, 4.6f), warm, 4.6f)
        }
        val signalInk = contrastInk(signal)
        var warning = if (dark) Color(0xFFF0A35E) else Color(0xFFB9542D)
        repeat(2) {
            warning = ensureContrast(ensureContrast(warning, seed.card, 4.5f), seed.bg, 4.5f)
            warning = ensureContrast(ensureContrast(warning, raised, 4.5f), warm, 4.5f)
        }
        return RinColors(
            canvas = seed.bg,
            surface = seed.card,
            surfaceRaised = raised,
            text = text,
            textSecondary = secondary,
            border = mix(seed.card, text, if (dark) 0.24f else 0.16f),
            signal = signal,
            signalInk = signalInk,
            warning = warning,
            actionBg = signal,
            actionText = signalInk,
            focusRing = signal,
            accent = signal,
            warm = warm
        )
    }

    fun withAccent(colors: RinColors, hex: String?): RinColors {
        val raw = parseHex(hex) ?: return colors
        var signal = raw
        repeat(2) {
            signal = ensureContrast(ensureContrast(signal, colors.canvas, 4.6f), colors.surface, 4.6f)
            signal = ensureContrast(signal, colors.surfaceRaised, 4.6f)
        }
        val ink = contrastInk(signal)
        return colors.copy(
            signal = signal,
            signalInk = ink,
            actionBg = signal,
            actionText = ink,
            focusRing = signal,
            accent = signal
        )
    }

    fun typographyFor(style: TypeStyle): Typography = when (style) {
        TypeStyle.SANS -> RinTypography
        TypeStyle.SERIF -> serifTypography
        TypeStyle.EDITORIAL -> editorialTypography
        TypeStyle.MONO -> monoTypography
        TypeStyle.PIXEL -> pixelTypography
        TypeStyle.ROUND -> roundTypography
        TypeStyle.DELICATE -> delicateTypography
    }

    fun shapesFor(style: ShapeStyle): Shapes = when (style) {
        ShapeStyle.SOFT -> RinShapes
        ShapeStyle.SHARP -> sharpShapes
        ShapeStyle.CRYSTAL -> crystalShapes
        ShapeStyle.PILL -> pillShapes
        ShapeStyle.PIXEL -> pixelShapes
        ShapeStyle.EDITORIAL -> editorialShapes
        ShapeStyle.FILM -> filmShapes
        ShapeStyle.CUT -> cutShapes
    }

    fun shapesFor(style: ShapeStyle, corners: ThemeCorners): Shapes = when (corners) {
        ThemeCorners.NATIVE -> shapesFor(style)
        ThemeCorners.SOFT -> softOverrideShapes
        ThemeCorners.SHARP -> sharpOverrideShapes
    }

    fun parseHex(value: String?): Color? {
        val raw = value?.trim()?.removePrefix("#") ?: return null
        if (raw.length != 6 || raw.any { it.digitToIntOrNull(16) == null }) return null
        return Color((0xFF000000L or raw.toLong(16)).toInt())
    }

    private fun seed(bg: Long, card: Long, ink: Long, accent: Long, accent2: Long) = ThemeSeed(
        bg = Color(bg),
        card = Color(card),
        ink = Color(ink),
        accent = Color(accent),
        accent2 = Color(accent2)
    )

    private fun mix(from: Color, to: Color, amount: Float): Color {
        val t = amount.coerceIn(0f, 1f)
        return Color(
            red = from.red + (to.red - from.red) * t,
            green = from.green + (to.green - from.green) * t,
            blue = from.blue + (to.blue - from.blue) * t,
            alpha = 1f
        )
    }

    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun contrastInk(background: Color): Color {
        val onDark = (background.luminance() + 0.05f) / 0.05f
        val onLight = 1.05f / (background.luminance() + 0.05f)
        return if (onDark >= onLight) Color(0xFF0A1118) else Color.White
    }

    private fun ensureContrast(foreground: Color, background: Color, minRatio: Float): Color {
        if (contrastRatio(foreground, background) >= minRatio) return foreground
        val target = if (background.luminance() > 0.5f) Color(0xFF0A1118) else Color.White
        var candidate = foreground
        for (step in 1..12) {
            candidate = mix(foreground, target, step / 12f)
            if (contrastRatio(candidate, background) >= minRatio) return candidate
        }
        return target
    }

    private val serifTypography = RinTypography.copy(
        titleLarge = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 27.sp,
            letterSpacing = 0.2.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.3.sp
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 21.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 1.sp
        )
    )

    private val editorialTypography = serifTypography.copy(
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 25.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.1).sp
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 27.sp,
            letterSpacing = 0.1.sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.3.sp
        )
    )

    private val monoTypography = RinTypography.copy(
        titleLarge = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 27.sp,
            letterSpacing = 0.2.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.3.sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.6.sp
        )
    )

    private val pixelTypography = monoTypography.copy(
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 23.sp,
            lineHeight = 29.sp,
            letterSpacing = 0.4.sp
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            lineHeight = 25.sp,
            letterSpacing = 0.3.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.2.sp
        )
    )

    private val roundTypography = RinTypography.copy(
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.2.sp
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 27.sp
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 21.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.8.sp
        )
    )

    private val delicateTypography = serifTypography.copy(
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.6.sp
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            letterSpacing = 1.4.sp
        )
    )

    private val sharpShapes = Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp)
    )
    private val crystalShapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )
    private val pillShapes = Shapes(
        extraSmall = RoundedCornerShape(16.dp),
        small = RoundedCornerShape(22.dp),
        medium = RoundedCornerShape(28.dp),
        large = RoundedCornerShape(34.dp),
        extraLarge = RoundedCornerShape(40.dp)
    )
    private val pixelShapes = Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp)
    )
    private val editorialShapes = Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(20.dp)
    )
    private val filmShapes = Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(18.dp)
    )
    private val cutShapes = Shapes(
        extraSmall = CutCornerShape(4.dp),
        small = CutCornerShape(8.dp),
        medium = CutCornerShape(12.dp),
        large = CutCornerShape(16.dp),
        extraLarge = CutCornerShape(20.dp)
    )
    private val softOverrideShapes = Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(22.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(34.dp)
    )
    private val sharpOverrideShapes = Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(14.dp)
    )
}
