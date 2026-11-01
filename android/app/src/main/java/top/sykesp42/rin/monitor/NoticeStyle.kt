package top.sykesp42.rin.monitor

import top.sykesp42.rin.domain.State

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

/**
 * A place a face can be shown. Slots, rather than raw states, so a user can remap
 * "which face the sign-in-ready notification wears" without knowing enum internals.
 */
enum class NoticeSlot(val key: String, val label: String, val default: NoticeIcon) {
    MONITOR("monitor", "后台监控中", NoticeIcon.FOCUS),
    SESSION("session", "教务会话保活", NoticeIcon.SLEEPY),
    READY("ready", "可以签到", NoticeIcon.BECKON),
    CONFIRMED("confirmed", "已确认签到", NoticeIcon.HAPPY),
    UNCONFIRMED("unconfirmed", "未确认签到", NoticeIcon.CONFUSED),
    UNREADABLE("unreadable", "名单读取失败", NoticeIcon.OFFLINE),
    ERROR("error", "监控异常", NoticeIcon.PANIC),
    CLASS_REMINDER("class", "上课提醒", NoticeIcon.ALERT),
    GRADES("grades", "新成绩发布", NoticeIcon.DELIGHT),
    SCHEDULE_CHANGE("schedule", "课表变动", NoticeIcon.CHANGE),
    WAITING("waiting", "等待人数达标", NoticeIcon.THINK),
    NOT_IN_ROSTER("not_in_roster", "本人不在名单", NoticeIcon.BLOCKED),
    AMBIGUOUS("ambiguous", "名单有同名", NoticeIcon.AWKWARD),
    EXPIRED("expired", "签到已结束", NoticeIcon.WAVE);

    companion object {
        fun from(key: String?): NoticeSlot? = entries.firstOrNull { it.key == key }

        /** The monitor notification reports a state, so the slot follows it. */
        fun forState(state: State): NoticeSlot = when (state) {
            State.READY -> READY
            State.CONFIRMED -> CONFIRMED
            State.WAITING_THRESHOLD -> WAITING
            State.EXPIRED -> EXPIRED
            State.BLOCKED_NOT_IN_ROSTER -> NOT_IN_ROSTER
            State.BLOCKED_AMBIGUOUS_NAME -> AMBIGUOUS
            State.BLOCKED -> ERROR
            State.NOT_STARTED, State.IDLE -> MONITOR
        }
    }
}
