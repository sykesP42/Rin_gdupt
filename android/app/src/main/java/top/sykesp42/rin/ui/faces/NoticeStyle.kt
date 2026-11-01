package top.sykesp42.rin.ui.faces

/**
 * A face atlas under `assets/notice/rin_faces_<key>.png`.
 *
 * [lightPlateOnly] is what AUTO resolves to for this outfit. The gothic bust wears
 * a black top that merges into any dark plate, so it is the one set that always
 * needs a light disc; the others carry a white jacket that already separates them
 * from a dark notification card.
 */
enum class NoticeOutfit(val key: String, val label: String, val lightPlateOnly: Boolean) {
    ACADEMIC("academic", "样式 1", false),
    GOTHIC("gothic", "样式 2", true),
    COAT("coat", "样式 3", false),
    HACKER("hacker", "样式 4", false);

    val asset: String get() = "notice/rin_faces_$key.png"

    companion object {
        fun from(key: String?): NoticeOutfit = entries.firstOrNull { it.key == key } ?: ACADEMIC
    }
}

/** Circular ground behind the bust. `AUTO` follows the outfit and the system shade. */
enum class NoticePlate(val key: String, val label: String) {
    AUTO("auto", "自动"),
    INK("ink", "墨蓝"),
    SLATE("slate", "石板"),
    FROST("frost", "铂白"),
    LINEN("linen", "亚麻"),
    NONE("none", "无底板");

    companion object {
        fun from(key: String?): NoticePlate = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** A place a face can be shown in reminders and in-app badges. */
enum class NoticeSlot(val key: String, val label: String, val default: NoticeIcon) {
    REMINDER("reminder", "上课提醒", NoticeIcon.ALERT),
    SCHEDULE("schedule", "课表变动", NoticeIcon.CHANGE),
    WEATHER("weather", "天气变化", NoticeIcon.DELIGHT),
    EVENING("evening", "晚间提醒", NoticeIcon.SLEEPY),
    TODO("todo", "待办提醒", NoticeIcon.THINK),
    ERROR("error", "提醒异常", NoticeIcon.PANIC);

    companion object {
        fun from(key: String?): NoticeSlot? = entries.firstOrNull { it.key == key }
    }
}
