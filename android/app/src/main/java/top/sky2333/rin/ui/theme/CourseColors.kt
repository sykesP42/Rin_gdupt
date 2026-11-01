package top.sky2333.rin.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Eight distinct, paper-friendly course inks. Every light ink is >=5.3:1 on
 * the Rin canvas and every dark ink is >=8.7:1 on the night canvas, so the
 * rainbow stays readable instead of turning into decorative noise.
 */
fun courseAccent(courseName: String, dark: Boolean): Color {
    val light = listOf(
        Color(0xFF1F6B6B), // teal
        Color(0xFF1E5F8A), // ocean
        Color(0xFF4B4E8C), // indigo
        Color(0xFF7A3E6E), // plum
        Color(0xFF8A3B52), // rose
        Color(0xFF9A4A2E), // terracotta
        Color(0xFF5E6B2E), // olive
        Color(0xFF8A5A16)  // amber
    )
    val darkAccent = listOf(
        Color(0xFF8FD6C9),
        Color(0xFF8FC9EF),
        Color(0xFFB3B7F0),
        Color(0xFFE0A6D4),
        Color(0xFFF2A7B8),
        Color(0xFFF2B08F),
        Color(0xFFC9D98A),
        Color(0xFFE8C57A)
    )
    return (if (dark) darkAccent else light)[abs(courseName.hashCode()) % 8]
}

fun courseTint(courseName: String, dark: Boolean): Color =
    courseAccent(courseName, dark).copy(alpha = if (dark) 0.13f else 0.055f)
