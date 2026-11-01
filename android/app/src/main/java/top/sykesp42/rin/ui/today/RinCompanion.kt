package top.sykesp42.rin.ui.today

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.sykesp42.rin.data.PetWeather
import top.sykesp42.rin.monitor.NoticeFaces
import top.sykesp42.rin.monitor.NoticeIcons
import top.sykesp42.rin.monitor.NoticeSlot
import top.sykesp42.rin.ui.theme.LocalSuiSignColors

data class RinCompanionProfile(
    val todayCourses: Int = 0,
    val firstCourse: String = "",
    val firstStart: String = "",
    val nextCourse: String = "",
    val nextStart: String = "",
    val nextRoom: String = "",
    val nextDayCourse: String = "",
    val nextDayStart: String = "",
    val currentCourse: String = "",
    val afterLast: Boolean = false,
    val isWeekend: Boolean = false,
    val minutesNow: Int = 0,
    val week: Int = 0,
    val termWeeks: Int = 0,
    val examName: String = "",
    val examDays: Int = -1,
    val newsTitle: String = "",
    val todoCount: Int = 0,
    val busiestWeekday: String = "",
    val freeDays: Int = 0,
    val temp: String = "",
    val humidity: String = "",
    val weather: PetWeather = PetWeather.UNKNOWN
)

enum class CompanionAction(val label: String) {
    NONE(""),
    SCHEDULE("看看课表"),
    NEWS("看校园要闻")
}

data class CompanionLine(
    val text: String,
    val slot: NoticeSlot,
    val action: CompanionAction
)

private data class CompanionSet(
    val slot: NoticeSlot,
    val action: CompanionAction,
    val lines: List<String>
)

/** 100+ handwritten daily lines, grouped so the card reacts to real school data. */
private object Presets {
    val morning = CompanionSet(
        NoticeSlot.CONFIRMED, CompanionAction.NONE,
        listOf(
            "早，第{week}周，今天有{courseCount}节课。",
            "窗外{weatherText}，今天{temp}。出门前看一眼有没有带伞。",
            "第一节课在{firstStart}，{first}。不着急，时间够。",
            "今天最早的一节是{first}，{firstStart}开始。",
            "早上好。本周最忙的是{busiest}，先留一点空给自己。",
            "速记里还有{todo}条，今天挑一件先做掉。",
            "今天的课从{firstStart}开始，路上慢一点。",
            "距离{examName}还有{days}天，今天可以先翻两页。",
            "今天共有{courseCount}节课，课间记得站起来走走。",
            "第{week}周，第{todayCourses}次进教室。慢慢来。"
        )
    )

    val noon = CompanionSet(
        NoticeSlot.SESSION, CompanionAction.NONE,
        listOf(
            "到饭点了。第{week}周，先去吃饭，回来再看消息。",
            "下午的课在等着，午休二十分钟也很有用。",
            "今天已经上了不少课，记得喝水。",
            "食堂人多的话，晚十分钟再去。",
            "本周{busiest}最忙，今天能完成一件小事就够了。",
            "中午别睡太久，不然下午会更困。",
            "今天还有{todo}条速记没做，先留一件到晚上。",
            "吃完饭走两圈再坐下。",
            "下午还有{nextCourse}，{nextStart}开始。",
            "如果下午没课，距离{examName}还有{days}天，可以慢慢开始。"
        )
    )

    val evening = CompanionSet(
        NoticeSlot.SESSION, CompanionAction.NONE,
        listOf(
            "今天的课结束了。第{week}周，剩下的时间归你。",
            "晚上适合整理一下今天的内容，哪怕只写三行。",
            "明天{nextDayCourse}在{nextDayStart}，今晚早点收拾。",
            "速记里还有{todo}条，挑最小的一件先做掉。",
            "本周最忙的是{busiest}，今晚不用再排太满。",
            "今天走了多少步？记得活动一下肩颈。",
            "把明天的闹钟确认一遍再放松。",
            "距离{examName}还有{days}天，今晚看十分钟也算开始。",
            "今天已经完成得不错了，剩下的明天再说。",
            "明天第一节在{nextDayStart}，睡前把东西收好。"
        )
    )

    val beforeClass = CompanionSet(
        NoticeSlot.READY, CompanionAction.SCHEDULE,
        listOf(
            "{nextCourse}在{nextStart}开始，教室{nextRoom}。",
            "下一节是{nextCourse}，{nextStart}。现在出发刚好。",
            "还有一点时间，检查一下{nextCourse}要用的东西。",
            "{nextCourse} · {nextRoom}，别走错楼。",
            "接下来是{nextCourse}，路上留出五分钟。",
            "如果现在去教室，可以挑个靠前的位置。",
            "下一节{nextCourse}，{nextStart}开始，水杯带上。",
            "{nextCourse}快开始了，先把手机静音。",
            "还有几十分钟，{nextCourse}在等你。",
            "下一节课在{nextRoom}，提前看一下楼层。",
            "{nextCourse}的课件可以先翻一页。",
            "从这节{nextCourse}开始，今天还剩{courseCount}节。"
        )
    )

    val inClass = CompanionSet(
        NoticeSlot.MONITOR, CompanionAction.NONE,
        listOf(
            "现在在上{currentCourse}，先专心听课。",
            "{currentCourse}进行中。课后再看手机也来得及。",
            "正在上课。把一闪而过的想法记在纸上。",
            "现在是{currentCourse}，听不懂的地方先做个记号。",
            "上课中。下课前把问题想清楚一个。",
            "{currentCourse}的这段时间，留给课堂。",
            "如果在走神，先回到老师正在讲的那一句。",
            "课堂进行中。坐直一点，呼吸慢一点。"
        )
    )

    val afterClass = CompanionSet(
        NoticeSlot.CONFIRMED, CompanionAction.NONE,
        listOf(
            "今天的课都上完了。先回宿舍还是去食堂？",
            "课结束了，最后整理一下今天的笔记。",
            "今天最后一节是{currentCourse}？辛苦了。",
            "从教室出来先看看天气，{weatherText}，{temp}。",
            "不想立刻学习的话，先走十分钟。",
            "下课后把今天没听懂的地方标出来。",
            "今天结束了，下一节课{nextDayCourse}在{nextDayStart}。",
            "速记里还有{todo}条，回宿舍路上挑一件想想。",
            "今天第{week}周的第{courseCount}节课收尾了，给自己留半小时空白。",
            "把书包里不需要的东西拿出来，明天会轻一点。"
        )
    )

    val freeDay = CompanionSet(
        NoticeSlot.GRADES, CompanionAction.SCHEDULE,
        listOf(
            "今天没课。第{week}周，最忙的是{busiest}，其他日子按自己的节奏来。",
            "空白的一天。下一节课是{nextDayCourse}，在{nextDayStart}。",
            "今天没有课，适合把落下的东西补一补。",
            "没课的日子，速记里还有{todo}条，先挑一件。",
            "今天全空。{weatherText}，{temp}。出门走走也不错。",
            "本周还有{freeDays}个工作日没课，今天先留一点给自己。",
            "今天不用赶教室，慢慢吃个饭。",
            "空闲的一天，距离{examName}还有{days}天，可以先开个头。",
            "今天没课，下次见到{nextDayCourse}是在{nextDayStart}。",
            "空出来的一天，看看想看的书吧。",
            "今天没有课，{newsTitle}，有空可以看看。",
            "一周里难得的一天，按自己的速度来。"
        )
    )

    val weekend = CompanionSet(
        NoticeSlot.CONFIRMED, CompanionAction.NONE,
        listOf(
            "周末。第{week}周，今天不赶课，先好好吃个早饭。",
            "周末的校园会安静一些。",
            "下周第一节是{nextDayCourse}，在{nextDayStart}，先记一下。",
            "周末适合把一周的衣服和桌面一起收拾了。",
            "今天可以晚一点睡，但别太晚。",
            "速记里还有{todo}条，周末结束前清掉一件吧。",
            "要不要去图书馆换个环境？距离{examName}还有{days}天。",
            "周末也别忘了看一眼下周的课表。",
            "今天没有必须完成的事，先照顾好自己。",
            "周末的下午适合散步，不适合焦虑。"
        )
    )

    val exam = CompanionSet(
        NoticeSlot.CLASS_REMINDER, CompanionAction.SCHEDULE,
        listOf(
            "{examName}还有{days}天。今天先复习一小节。",
            "距离{examName}还有{days}天，别把复习都留到最后。",
            "{examName}快到了。先整理一份重点清单。",
            "{days}天后考{examName}。今天可以刷一组题。",
            "{examName}在前方。把最容易忘的公式写一遍。",
            "还有{days}天考{examName}。今天重点是回顾错题。",
            "考试周临近，{examName}的笔记该翻出来了。",
            "{examName}还有{days}天。复习不用一次做完，先开始。",
            "离{examName}不远了。今晚留二十分钟给它。",
            "{examName}快到了，确认一下考试时间和教室。",
            "{days}天后的{examName}。今天先把不懂的列出来。",
            "把{examName}的复习拆成三块，先做最小的一块。"
        )
    )

    val weather = CompanionSet(
        NoticeSlot.WAITING, CompanionAction.NONE,
        listOf(
            "今天{weatherText}，{temp}。出门记得看路。",
            "{weatherText}的一天，{temp}。伞放在包里了吗？",
            "现在{temp}，湿度{humidity}。体感可能不太一样。",
            "看起来要下雨，去上课的路上慢一点。",
            "今天很热，水杯别忘在宿舍。",
            "今天有点冷，{temp}。多穿一件再出门。",
            "天气不太好，课间就别跑太远了。",
            "雨天的教室会更闷，带一瓶水。",
            "今天{weatherText}，适合把伞放在门口。",
            "湿度{humidity}，衣服可能不太好干。",
            "天气变化大，晚上回来记得看一眼温度。",
            "如果下雨，今晚就少安排一点路上奔波的事。"
        )
    )

    val news = CompanionSet(
        NoticeSlot.GRADES, CompanionAction.NEWS,
        listOf(
            "校园要闻更新了：{news}",
            "学校刚发了新消息：{news}",
            "有空可以看看：{news}",
            "最新的校园通知是「{news}」。",
            "教务和学校都可能有新动静：{news}",
            "今天的新消息：{news}。看看和你有没有关系。",
            "要闻栏更新了一条：{news}",
            "如果和你的安排有关，记得点开看看：{news}",
            "学校发布了「{news}」，课间可以扫一眼。",
            "刚更新的校园要闻：{news}"
        )
    )

    val todos = CompanionSet(
        NoticeSlot.WAITING, CompanionAction.NONE,
        listOf(
            "今天还有{todo}条速记，挑最小的先做。",
            "速记里还有{todo}条。别让它们一直躺着。",
            "有{todo}件事记在今天，做完一件就划掉一件。",
            "今天的小清单还有{todo}条，先处理一件。",
            "如果不知道先做什么，就选{todo}条里最短的。",
            "速记里还有{todo}条，睡前清掉一条也好。",
            "今天的待办还有{todo}条，慢慢来。",
            "有{todo}条记下来的事，先把最容易的那件做掉。",
            "别急着全部完成，先动起来一条。",
            "今天记了{todo}件事，做完记得回来勾掉。"
        )
    )

    val busy = CompanionSet(
        NoticeSlot.MONITOR, CompanionAction.SCHEDULE,
        listOf(
            "今天{courseCount}节课，节奏有点满。课间尽量别安排别的事。",
            "今天课多，吃饭和喝水要按时。",
            "{busiest}最忙，今天先照顾好体力。",
            "课表很满的一天，晚上给自己留一点空白。",
            "今天{courseCount}节课，书包里备点吃的。",
            "满课日，先把最重要的事放在课间完成。",
            "今天几乎没有整块时间，碎片时间就别刷太久了。",
            "忙的时候更要慢一点，别把东西落在教室。",
            "今天课多，晚上回去先休息十分钟再学习。",
            "把今天的任务减到三件，其他的明天再说。"
        )
    )

    val all: List<CompanionSet> = listOf(
        morning, noon, evening, beforeClass, inClass, afterClass, freeDay,
        weekend, exam, weather, news, todos, busy
    )
}

/** Exposed for the unit test that keeps the preset bank above 100 lines. */
internal fun companionPresetCount(): Int = Presets.all.sumOf { it.lines.size }

fun companionLine(profile: RinCompanionProfile, seed: Int): CompanionLine {
    val soon = profile.nextCourse.isNotBlank() &&
        profile.minutesNow in (minutes(profile.nextStart) - 60) until minutes(profile.nextStart)
    val timeSet = when {
        profile.minutesNow < 11 * 60 -> Presets.morning
        profile.minutesNow < 17 * 60 -> Presets.noon
        else -> Presets.evening
    }
    // Immediate contexts must match reality; everything else rotates so the
    // card draws on different slices of the user's data on each page load.
    val immediate = when {
        profile.currentCourse.isNotBlank() -> Presets.inClass
        soon -> Presets.beforeClass
        profile.afterLast && profile.todayCourses > 0 -> Presets.afterClass
        else -> null
    }
    val set = immediate ?: run {
        val candidates = mutableListOf<CompanionSet>()
        if (profile.examDays in 0..14 && profile.examName.isNotBlank()) {
            candidates += Presets.exam
            candidates += Presets.exam
        }
        if (profile.weather == PetWeather.RAIN || profile.weather == PetWeather.SNOW ||
            profile.weather == PetWeather.HOT || profile.weather == PetWeather.COLD
        ) {
            candidates += Presets.weather
        }
        if (profile.todoCount > 0) candidates += Presets.todos
        if (profile.newsTitle.isNotBlank()) candidates += Presets.news
        if (profile.todayCourses == 0) {
            candidates += if (profile.isWeekend) Presets.weekend else Presets.freeDay
        } else {
            if (profile.todayCourses >= 5) candidates += Presets.busy
            candidates += timeSet
        }
        candidates[Math.floorMod(seed, candidates.size)]
    }
    val index = Math.floorMod(seed, set.lines.size)
    val slots = slotsFor(set)
    return CompanionLine(
        text = render(set.lines[index], profile),
        slot = slots[index % slots.size],
        action = set.action
    )
}

/** Different lines in one scenario get different Rin expressions. */
private fun slotsFor(set: CompanionSet): List<NoticeSlot> = when (set) {
    Presets.morning -> listOf(NoticeSlot.CONFIRMED, NoticeSlot.READY, NoticeSlot.GRADES)
    Presets.noon -> listOf(NoticeSlot.SESSION, NoticeSlot.WAITING)
    Presets.evening -> listOf(NoticeSlot.SESSION, NoticeSlot.EXPIRED)
    Presets.beforeClass -> listOf(NoticeSlot.READY, NoticeSlot.CLASS_REMINDER)
    Presets.inClass -> listOf(NoticeSlot.MONITOR)
    Presets.afterClass -> listOf(NoticeSlot.CONFIRMED, NoticeSlot.EXPIRED)
    Presets.freeDay -> listOf(NoticeSlot.GRADES, NoticeSlot.CONFIRMED)
    Presets.weekend -> listOf(NoticeSlot.GRADES, NoticeSlot.READY)
    Presets.exam -> listOf(NoticeSlot.CLASS_REMINDER, NoticeSlot.WAITING)
    Presets.weather -> listOf(NoticeSlot.WAITING, NoticeSlot.SESSION)
    Presets.news -> listOf(NoticeSlot.GRADES, NoticeSlot.READY)
    Presets.todos -> listOf(NoticeSlot.WAITING, NoticeSlot.MONITOR)
    Presets.busy -> listOf(NoticeSlot.MONITOR, NoticeSlot.CLASS_REMINDER)
    else -> listOf(set.slot)
}

private fun minutes(clock: String): Int {
    val parts = clock.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
        (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun render(template: String, p: RinCompanionProfile): String {
    val weatherText = when (p.weather) {
        PetWeather.RAIN -> "有雨"
        PetWeather.SNOW -> "有雪"
        PetWeather.CLEAR -> "晴"
        PetWeather.CLOUDY -> "多云"
        PetWeather.HOT -> "很热"
        PetWeather.COLD -> "很冷"
        else -> "天气一般"
    }
    return template
        .replace("{courseCount}", p.todayCourses.toString())
        .replace("{todayCourses}", p.todayCourses.toString())
        .replace("{course}", p.currentCourse.ifBlank { p.nextCourse }.ifBlank { "这节课" })
        .replace(
            "{currentCourse}",
            p.currentCourse.ifBlank { p.nextCourse }.ifBlank { "这节课" }
        )
        .replace("{first}", p.firstCourse.ifBlank { "第一节课" })
        .replace("{firstStart}", p.firstStart.ifBlank { "早上" })
        .replace("{nextCourse}", p.nextCourse.ifBlank { "下一节课" })
        .replace("{nextStart}", p.nextStart.ifBlank { "待定" })
        .replace("{nextRoom}", p.nextRoom.ifBlank { "教室待定" })
        .replace(
            "{nextDayCourse}",
            p.nextDayCourse.ifBlank { p.nextCourse.ifBlank { "下一节课" } }
        )
        .replace(
            "{nextDayStart}",
            p.nextDayStart.ifBlank { p.nextStart.ifBlank { "待定" } }
        )
        .replace("{examName}", p.examName.ifBlank { "考试" })
        .replace("{exam}", p.examName.ifBlank { "考试" })
        .replace("{days}", p.examDays.toString())
        .replace("{news}", p.newsTitle.ifBlank { "新消息" })
        .replace("{newsTitle}", p.newsTitle.ifBlank { "校园要闻" })
        .replace("{freeDays}", p.freeDays.toString())
        .replace("{todo}", p.todoCount.toString())
        .replace("{week}", p.week.toString())
        .replace("{temp}", p.temp.ifBlank { "今天" })
        .replace("{humidity}", p.humidity.ifBlank { "不明" })
        .replace("{weatherText}", weatherText)
        .replace("{busiest}", p.busiestWeekday.ifBlank { "今天" })
}

/** A chat-like card with a matching pixel Rin expression. */
@Composable
fun RinCompanionCard(
    line: CompanionLine,
    onAction: (CompanionAction) -> Unit
) {
    val colors = LocalSuiSignColors.current
    val context = LocalContext.current
    val face = remember(line.slot) {
        NoticeIcons.largeIcon(context, NoticeFaces.face(line.slot))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Image(
            bitmap = face.asImageBitmap(),
            contentDescription = "Rin 表情",
            modifier = Modifier.size(44.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Rin",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = line.text,
                color = colors.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (line.action != CompanionAction.NONE) {
                TextButton(
                    onClick = { onAction(line.action) },
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text(
                        text = line.action.label,
                        color = colors.signal,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
