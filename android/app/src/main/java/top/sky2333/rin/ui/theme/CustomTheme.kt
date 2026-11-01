package top.sky2333.rin.ui.theme

import androidx.compose.runtime.Immutable

enum class CustomThemeFocus(val key: String, val label: String, val value: Float) {
    TOP("top", "上", 0.18f),
    MIDDLE("middle", "中", 0.48f),
    BOTTOM("bottom", "下", 0.80f);

    companion object {
        fun from(key: String): CustomThemeFocus = entries.firstOrNull { it.key == key } ?: MIDDLE
    }
}

enum class CustomThemeColorMode(val key: String, val label: String) {
    IMAGE("image", "跟随封面"),
    BASE("base", "保留基底"),
    ACCENT("accent", "只换强调色"),
    MONO("mono", "黑白纸墨");

    companion object {
        fun from(key: String): CustomThemeColorMode = entries.firstOrNull { it.key == key } ?: IMAGE
    }
}

@Immutable
data class CustomThemeState(
    val enabled: Boolean = false,
    val imageFileName: String = "",
    val seed: ThemeSeed? = null,
    val focus: Float = CustomThemeFocus.MIDDLE.value,
    val colorMode: CustomThemeColorMode = CustomThemeColorMode.IMAGE,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val blur: Float = 0f
) {
    val hasCover: Boolean get() = enabled && imageFileName.isNotBlank()
}
