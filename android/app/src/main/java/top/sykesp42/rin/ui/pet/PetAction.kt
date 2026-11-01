package top.sykesp42.rin.ui.pet

import top.sykesp42.rin.data.PetWeather

/** A sprite sheet in assets/pet/<asset>.png, laid out on a fixed 128px grid. */
enum class PetAction(
    val asset: String,
    val label: String,
    val outfit: String,
    val frameCount: Int,
    val columns: Int,
    val rows: Int,
    val frameMillis: Long,
) {
    NOD("nod", "点头问好", "学术制服", 8, 4, 2, 360),
    WATCH("watch", "看时间", "学术制服", 8, 4, 2, 200),
    EYES("eyes", "揉揉眼睛", "学术制服", 8, 4, 2, 380),
    STUDY("study", "低头记笔记", "学术制服", 8, 4, 2, 300),
    NAP("nap", "坐垫打盹", "学术制服", 8, 4, 2, 640),
    STRETCH("stretch", "伸个懒腰", "学术制服", 8, 4, 2, 380),
    PEN("pen", "转笔", "学术制服", 8, 4, 2, 200),
    BECKON("beckon", "招手过来", "学术制服", 8, 4, 2, 260),
    SCHEDULE("schedule", "看一眼课表", "学术制服", 8, 4, 2, 340),
    EAT("eat", "吃便当", "日常开衫", 8, 4, 2, 280),
    READ("read", "看书", "日常开衫", 8, 4, 2, 340),
    JACKET("jacket", "扶正外套", "机能", 8, 4, 2, 320),
    FAN("fan", "小风扇", "机能", 8, 4, 2, 260),
    SWEAT("sweat", "擦擦汗", "机能", 8, 4, 2, 320),
    BOOTSHAFT("bootshaft", "拉好靴筒", "机能", 8, 4, 2, 340),
    ZIP("zip", "拉上拉链", "机能", 8, 4, 2, 320),
    COLLAR("collar", "拉衣领挡雨", "日常大衣", 8, 4, 2, 340),
    WARM("warm", "哈气暖手", "日常大衣", 8, 4, 2, 360),
    PACK("pack", "整理书包", "日常大衣", 8, 4, 2, 340),
    COINS("coins", "数零钱", "日常大衣", 8, 4, 2, 360),
    HOTDRINK("hotdrink", "捧热饮", "日常大衣", 8, 4, 2, 360),
    DUST("dust", "拍掉肩灰", "日常大衣", 8, 4, 2, 300),
    BUTTON("button", "扣好大衣", "日常大衣", 8, 4, 2, 340),
    HEART("heart", "比心", "哥特", 8, 4, 2, 380),
    SKIRT("skirt", "整理裙摆", "哥特", 8, 4, 2, 360),
    PHOTO("photo", "举手机拍照", "科技社", 8, 4, 2, 300),
    LEAF("leaf", "拂落叶", "科技社", 8, 4, 2, 400),
    PARCEL("parcel", "收快递", "科技社", 8, 4, 2, 320);

    init {
        require(frameCount in 1..columns * rows) { "$asset: $frameCount frames exceed ${columns}x$rows" }
    }
}

enum class PetEvent { NONE, REMINDER, CHEER, RUSH_TO_CLASS }

data class PetContext(
    val hour: Int,
    val weather: PetWeather = PetWeather.UNKNOWN,
    val event: PetEvent = PetEvent.NONE,
)

/**
 * Pure scene picker. Primary action answers the clock/weather/event; the rest of
 * the list gives the pet something new to do if it stays on today for a while.
 */
object PetDirector {
    fun candidates(context: PetContext): List<PetAction> {
        when (context.event) {
            PetEvent.REMINDER -> return listOf(PetAction.NOD)
            PetEvent.CHEER -> return listOf(PetAction.HEART)
            PetEvent.RUSH_TO_CLASS -> return listOf(PetAction.WATCH)
            PetEvent.NONE -> Unit
        }
        val night = context.hour >= 23 || context.hour < 6
        if (!night) {
            when (context.weather) {
                PetWeather.RAIN, PetWeather.SNOW ->
                    return listOf(PetAction.COLLAR, PetAction.BUTTON, PetAction.READ, PetAction.COINS)
                PetWeather.HOT ->
                    return listOf(PetAction.FAN, PetAction.SWEAT, PetAction.JACKET, PetAction.READ)
                PetWeather.COLD ->
                    return listOf(PetAction.WARM, PetAction.HOTDRINK, PetAction.READ, PetAction.COINS)
                else -> Unit
            }
        }
        return byHour(context.hour)
    }

    fun primary(context: PetContext): PetAction = candidates(context).first()

    private fun byHour(hour: Int): List<PetAction> = when (hour.coerceIn(0, 23)) {
        in 6..8 -> listOf(
            PetAction.NOD,
            PetAction.WATCH,
            PetAction.STRETCH,
            PetAction.JACKET,
            PetAction.BECKON,
        )
        in 9..10 -> listOf(
            PetAction.STUDY,
            PetAction.READ,
            PetAction.PEN,
            PetAction.JACKET,
            PetAction.ZIP,
            PetAction.EYES,
        )
        in 11..12 -> listOf(
            PetAction.EAT,
            PetAction.COINS,
            PetAction.READ,
            PetAction.JACKET,
            PetAction.SCHEDULE,
        )
        in 13..13 -> listOf(PetAction.NAP, PetAction.READ, PetAction.EYES)
        in 14..16 -> listOf(
            PetAction.STUDY,
            PetAction.READ,
            PetAction.PEN,
            PetAction.PHOTO,
            PetAction.FAN,
            PetAction.SWEAT,
            PetAction.BOOTSHAFT,
            PetAction.COINS,
        )
        in 17..18 -> listOf(
            PetAction.NOD,
            PetAction.BECKON,
            PetAction.PHOTO,
            PetAction.PACK,
            PetAction.EAT,
            PetAction.LEAF,
            PetAction.PARCEL,
            PetAction.DUST,
        )
        in 19..21 -> listOf(
            PetAction.PACK,
            PetAction.READ,
            PetAction.COINS,
            PetAction.BUTTON,
            PetAction.SKIRT,
            PetAction.PHOTO,
            PetAction.EYES,
        )
        in 22..23 -> listOf(PetAction.EYES, PetAction.NAP, PetAction.READ)
        else -> listOf(PetAction.NAP, PetAction.EYES, PetAction.READ)
    }
}
