package top.sky2333.rin.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A deliberately small palette picker for user photos. It never trusts one raw
 * pixel: the extracted seed is handed back to the normal contrast-safe
 * [ThemeCatalog.deriveColors] path, so a muddy photo cannot silently break text.
 */
object CustomThemePalette {
    private const val SAMPLE_SIZE = 64

    fun extract(pixels: IntArray, width: Int, height: Int): ThemeSeed {
        val samples = ArrayList<Int>(min(pixels.size, 4096))
        for (pixel in pixels) {
            if ((pixel ushr 24 and 0xFF) > 128) samples.add(pixel and 0xFFFFFF)
        }
        if (samples.isEmpty()) return fallback()

        val hsls = samples.map { argbToHsl(it) }
        val averageLightness = hsls.map { it.l }.average().toFloat()
        val dark = averageLightness < 0.42f
        val ordered = hsls.mapIndexed { index, hsl -> index to hsl }
            .sortedBy { it.second.l }

        fun slice(from: Float, to: Float): List<Int> {
            val start = (ordered.size * from).toInt().coerceIn(0, ordered.lastIndex)
            val end = (ordered.size * to).toInt().coerceIn(start + 1, ordered.size)
            return ordered.subList(start, end).map { samples[it.first] }
        }

        val background = averageArgb(if (dark) slice(0f, 0.14f) else slice(0.84f, 1f))
        val card = averageArgb(if (dark) slice(0.14f, 0.30f) else slice(0.64f, 0.84f))
        val ink = averageArgb(if (dark) slice(0.84f, 1f) else slice(0f, 0.16f))
        val saturated = hsls.mapIndexed { index, hsl -> index to hsl }
            .filter { it.second.s > 0.32f && it.second.l in 0.24f..0.78f }
            .sortedByDescending { it.second.s }
            .take(24)
            .map { samples[it.first] }
        val rawAccent = saturated.ifEmpty { slice(0.34f, 0.66f) }
        val accentHsl = argbToHsl(averageArgb(rawAccent))
        val accent = hslToArgb(
            h = accentHsl.h,
            s = if (saturated.isEmpty()) 0.18f else accentHsl.s.coerceIn(0.42f, 0.80f),
            l = 0.52f
        )
        val accent2 = hslToArgb(
            h = (accentHsl.h + 32f) % 360f,
            s = if (saturated.isEmpty()) 0.16f else (accentHsl.s * 0.78f).coerceIn(0.25f, 0.72f),
            l = if (dark) 0.60f else 0.48f
        )

        return ThemeSeed(
            bg = Color(
                adjustArgb(
                    background,
                    saturation = if (dark) 0.18f else 0.12f,
                    lightness = if (dark) 0.10f else 0.92f
                )
            ),
            card = Color(
                adjustArgb(
                    card,
                    saturation = if (dark) 0.16f else 0.08f,
                    lightness = if (dark) 0.16f else 0.98f
                )
            ),
            ink = Color(
                adjustArgb(
                    ink,
                    saturation = if (dark) 0.08f else 0.12f,
                    lightness = if (dark) 0.95f else 0.10f
                )
            ),
            accent = Color(accent),
            accent2 = Color(accent2)
        )
    }

    fun encode(seed: ThemeSeed): String = listOf(
        seed.bg, seed.card, seed.ink, seed.accent, seed.accent2
    ).joinToString(",") { hex(it) }

    fun decode(value: String?): ThemeSeed? {
        val parts = value?.split(',')?.map(String::trim)?.filter(String::isNotBlank) ?: return null
        if (parts.size != 5) return null
        val colors = parts.mapNotNull(::parseHex)
        if (colors.size != 5) return null
        return ThemeSeed(colors[0], colors[1], colors[2], colors[3], colors[4])
    }

    fun mono(seed: ThemeSeed): ThemeSeed = ThemeSeed(
        bg = grayscale(seed.bg),
        card = grayscale(seed.card),
        ink = grayscale(seed.ink),
        accent = grayscale(seed.accent),
        accent2 = grayscale(seed.accent2)
    )

    fun hex(color: Color): String {
        val rgb = color.toArgb() and 0xFFFFFF
        return "#" + rgb.toString(16).padStart(6, '0')
    }

    private fun fallback(): ThemeSeed = ThemeSeed(
        bg = Color(0xFFF7F6F1),
        card = Color(0xFFFFFFFF),
        ink = Color(0xFF17202A),
        accent = Color(0xFF007BAF),
        accent2 = Color(0xFFE8B87C)
    )

    private fun parseHex(raw: String): Color? {
        val value = raw.removePrefix("#")
        if (value.length != 6 || value.any { it.digitToIntOrNull(16) == null }) return null
        return Color(0xFF000000L or value.toLong(16))
    }

    private fun grayscale(color: Color): Color {
        val target = color.luminance().coerceIn(0f, 1f)
        val channel = if (target <= 0.0031308f) {
            target * 12.92f
        } else {
            1.055f * target.pow(1f / 2.4f) - 0.055f
        }.coerceIn(0f, 1f)
        return Color(channel, channel, channel, 1f)
    }

    private fun averageArgb(colors: List<Int>): Int {
        if (colors.isEmpty()) return 0xFF000000.toInt()
        var red = 0L
        var green = 0L
        var blue = 0L
        for (color in colors) {
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        val count = colors.size.toLong()
        return 0xFF000000.toInt() or
            ((red / count).toInt() shl 16) or
            ((green / count).toInt() shl 8) or
            (blue / count).toInt()
    }

    private fun adjustArgb(color: Int, saturation: Float, lightness: Float): Int {
        val hsl = argbToHsl(color)
        return hslToArgb(hsl.h, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
    }

    private fun argbToHsl(color: Int): Hsl {
        val red = ((color shr 16 and 0xFF) / 255f)
        val green = ((color shr 8 and 0xFF) / 255f)
        val blue = ((color and 0xFF) / 255f)
        val max = max(red, max(green, blue))
        val min = min(red, min(green, blue))
        val lightness = (max + min) / 2f
        if (max == min) return Hsl(0f, 0f, lightness)
        val delta = max - min
        val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
        val hue = when (max) {
            red -> ((green - blue) / delta + if (green < blue) 6f else 0f) * 60f
            green -> ((blue - red) / delta + 2f) * 60f
            else -> ((red - green) / delta + 4f) * 60f
        }
        return Hsl(hue % 360f, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
    }

    private fun hslToArgb(h: Float, s: Float, l: Float): Int {
        if (s <= 0f) {
            val gray = (l * 255f).roundToInt()
            return 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
        }
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        val red = hueToRgb(p, q, h + 120f)
        val green = hueToRgb(p, q, h)
        val blue = hueToRgb(p, q, h - 120f)
        return 0xFF000000.toInt() or
            ((red * 255f).roundToInt() shl 16) or
            ((green * 255f).roundToInt() shl 8) or
            (blue * 255f).roundToInt()
    }

    private fun hueToRgb(p: Float, q: Float, hueInput: Float): Float {
        var hue = hueInput
        if (hue < 0f) hue += 360f
        if (hue >= 360f) hue -= 360f
        return when {
            hue < 60f -> p + (q - p) * hue / 60f
            hue < 180f -> q
            hue < 240f -> p + (q - p) * (240f - hue) / 60f
            else -> p
        }
    }

    private data class Hsl(val h: Float, val s: Float, val l: Float)
}
