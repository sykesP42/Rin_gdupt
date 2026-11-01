package top.sykesp42.rin.ui.faces

/**
 * A Rin face. Which face answers which situation is [NoticeSlot]'s business.
 *
 * The ordinal doubles as the cell index into every `assets/notice/rin_faces_*.png`
 * atlas (8 columns x 2 rows), which `build_atlas.py` writes in this exact order —
 * adding a face means appending here and rebuilding, never inserting in the middle.
 */
enum class NoticeIcon(val label: String) {
    FOCUS("专注"),
    BECKON("招手"),
    HAPPY("开心"),
    CONFUSED("疑惑"),
    PANIC("慌张"),
    SLEEPY("犯困"),
    ALERT("提醒"),
    DELIGHT("惊喜"),
    OFFLINE("断联"),
    CHANGE("察觉"),
    CALM("安心"),
    HEART("比心"),
    BLOCKED("拒绝"),
    THINK("等待"),
    AWKWARD("尴尬"),
    WAVE("告别");
}
