package top.sykesp42.rin.ui.today

import top.sykesp42.rin.data.AppSettings

/**
 * The modular Today page. The sign-in chat stays fixed; every other card can be
 * switched off and reordered from settings.
 */
enum class TodayCardId(
    val key: String,
    val label: String,
    val description: String,
    val defaultOrder: Int,
    val defaultEnabled: Boolean
) {
    COMPANION("companion", "Rin", "结合课表、考试、新闻和天气说一句话", 10, true),
    MINI_DAY("mini_day", "Mini 课表 · 日", "只留今天的几行课", 20, true),
    MINI_TABLE("mini_table", "Mini 课表 · 表", "压缩的网格课表", 30, true),
    WEATHER("weather", "逐小时天气", "未来 6 小时图标与温度曲线", 40, true),
    MEMO("memo", "今日速记", "随手记下今天要做的事", 50, true),
    CAMPUS_NEWS("news", "校园要闻", "学校最新发布", 60, true),
    WEATHER_DENSE("weather_dense", "24 小时天气", "从现在起 24 小时的密集网格", 70, false),
    WEATHER_DAY("weather_day", "天气日卡", "今明两天的简略天气", 80, false),
    TOMORROW("tomorrow", "明日预览", "22:00 后显示明天的课与提醒", 90, false),
    TIMELINE("timeline", "今日课程", "今天的时间线", 100, false),
    EXAM_COUNTDOWN("exam", "考试倒计时", "最近的考试还有几天", 110, false),
    QUICK_LINKS("links", "快捷跳转", "教务、要闻和常用页面", 120, false),
    FILE_HUB("files", "文件归纳", "分享图与缓存一目了然", 130, false),
    MINI_WEEK("mini_week", "Mini 课表 · 周", "七天的课量一眼看完", 140, false),
    RIN_SHOWCASE("showcase", "Rin 展示架", "放一只 Rin 在首页", 150, false),
    PHOTO_FRAME("frame", "相框", "放一张自己的照片", 160, false)
}

object TodayCards {
    /** Bumped when the shipped defaults change so existing installs migrate once. */
    const val PRESET_VERSION = 1

    val all: List<TodayCardId> get() = TodayCardId.entries.sortedBy { it.defaultOrder }

    /** Current order, with unknown/legacy keys ignored and new cards appended. */
    fun ordered(settings: AppSettings?): List<TodayCardId> {
        val saved = settings?.todayCardsOrder.orEmpty()
            .split(',')
            .mapNotNull { key -> TodayCardId.entries.firstOrNull { it.key == key } }
        return (saved + all).distinct()
    }

    private const val NONE = "none"

    fun enabled(settings: AppSettings?): List<TodayCardId> {
        val raw = settings?.todayCardsDisabled.orEmpty()
        if (raw.isBlank()) return ordered(settings).filter { it.defaultEnabled }
        if (raw.trim() == NONE) return ordered(settings)
        val disabled = raw.split(',').filter { it.isNotBlank() }.toSet()
        return ordered(settings).filter { it.key !in disabled }
    }

    fun isEnabled(settings: AppSettings?, id: TodayCardId): Boolean {
        val raw = settings?.todayCardsDisabled.orEmpty()
        if (raw.isBlank()) return id.defaultEnabled
        if (raw.trim() == NONE) return true
        return id.key !in raw.split(',').filter { it.isNotBlank() }
    }

    fun defaultCsv(): String = all.joinToString(",") { it.key }

    fun defaultOrderCsv(): String = all.joinToString(",") { it.key }

    fun defaultDisabledCsv(): String =
        all.filterNot { it.defaultEnabled }.joinToString(",") { it.key }.ifBlank { NONE }
}
