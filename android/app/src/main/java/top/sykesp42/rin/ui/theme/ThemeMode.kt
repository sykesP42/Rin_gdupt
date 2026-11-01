package top.sykesp42.rin.ui.theme

/**
 * Stable theme ids. Classic moods keep their old keys so persisted settings
 * survive; the twelve illustrated themes use a `rin-` prefix.
 */
enum class ThemeMode(val key: String) {
    SKY("sky"),
    BOOK("book"),
    CRYSTAL("crystal"),
    RADIO("radio"),
    CINEMA("rin-cinema"),
    ABYSS("rin-abyss"),
    STICKER("rin-sticker"),
    NEON("rin-neon"),
    MORNING("rin-morning"),
    INK("rin-ink"),
    SHOJO("rin-shojo"),
    STORYBOOK("rin-storybook"),
    PIXEL("rin-pixel"),
    SNOW("rin-snow"),
    PAPERDOLL("rin-paperdoll"),
    CLAY("rin-clay");

    val spec: ThemeSpec get() = ThemeCatalog.specOf(this)
    val label: String get() = spec.name
    val title: String get() = "${spec.name} · ${spec.en}"
    val description: String get() = spec.blurb
    val decoration: DecorationStyle get() = spec.decoration

    fun resolveDark(systemDark: Boolean): Boolean = spec.dark

    companion object {
        val default: ThemeMode = SKY

        fun from(key: String): ThemeMode {
            val raw = key.trim()
            return when (raw) {
                "", "rin", "light", "system", "monet" -> SKY
                "manga" -> BOOK
                "sakura" -> CRYSTAL
                "dark", "neon" -> RADIO
                else -> entries.firstOrNull { it.key == raw } ?: when (raw) {
                    "cinema" -> CINEMA
                    "abyss" -> ABYSS
                    "sticker" -> STICKER
                    "morning" -> MORNING
                    "ink" -> INK
                    "shojo" -> SHOJO
                    "storybook" -> STORYBOOK
                    "pixel" -> PIXEL
                    "snow" -> SNOW
                    "paperdoll" -> PAPERDOLL
                    "clay" -> CLAY
                    else -> default
                }
            }
        }

        /** Four representative choices for first-run, not the full gallery. */
        fun starters(): List<ThemeMode> = listOf(SKY, MORNING, ABYSS, CINEMA)
    }
}
