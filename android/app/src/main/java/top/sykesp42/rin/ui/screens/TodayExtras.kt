package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.sykesp42.rin.data.CourseReminder
import top.sykesp42.rin.data.HourlyWeather
import top.sykesp42.rin.data.ManualTodo
import top.sykesp42.rin.data.PetWeather
import top.sykesp42.rin.data.PetWeatherSnapshot
import top.sykesp42.rin.domain.SignAccessState
import top.sykesp42.rin.domain.State
import top.sykesp42.rin.monitor.MonitorView
import top.sykesp42.rin.monitor.MySignState
import top.sykesp42.rin.monitor.NoticeFaces
import top.sykesp42.rin.monitor.NoticeIcons
import top.sykesp42.rin.monitor.NoticeSlot
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.suiTextFieldColors
import top.sykesp42.rin.ui.theme.LocalSuiSignColors

internal data class SignChatInfo(val text: String, val slot: NoticeSlot)

/**
 * Truthful one-line sign status. A poll that found no course is IDLE, not an
 * active sign-in, so it must never fall through to "in progress".
 */
internal fun signChatInfo(
    view: MonitorView?,
    loadFailed: Boolean,
    sessionExpired: Boolean,
    ready: Boolean,
    hasCoursesToday: Boolean?,
    viewIsToday: Boolean
): SignChatInfo {
    fun info(text: String, slot: NoticeSlot) = SignChatInfo(text, slot)
    if (loadFailed) return info("教务没连上，去签到页重试", NoticeSlot.ERROR)
    if (sessionExpired) return info("教务会话过期，去签到页重连", NoticeSlot.ERROR)
    if (view == null) {
        return if (hasCoursesToday == false) {
            info("今天没课，签到这边不用管。", NoticeSlot.GRADES)
        } else {
            info("还没有新的签到消息，Rin 会留意。", NoticeSlot.SESSION)
        }
    }
    if (!viewIsToday) return info("签到消息还停在之前，去签到页刷新一下。", NoticeSlot.WAITING)
    if (view.state == State.CONFIRMED) {
        return info("已确认：${view.courseName}", NoticeSlot.CONFIRMED)
    }
    if (ready) {
        return info("可以签到：${view.courseName} · ${view.roomCode}", NoticeSlot.READY)
    }
    val roster = view.roster
    val me = when (roster.me) {
        MySignState.SIGNED -> "你已签"
        MySignState.UNSIGNED -> "你还未签"
        MySignState.NOT_IN_ROSTER -> "名单里没有你"
        MySignState.AMBIGUOUS -> "匹配到多个姓名"
        else -> ""
    }
    val counts = if (roster.known) {
        "${roster.signed}/${roster.total} 人已签" + me.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    } else {
        ""
    }
    return when (view.state) {
        State.IDLE -> when {
            view.recommendationStatus == "no_course_today" || hasCoursesToday == false ->
                info("今天没课，签到先休息。", NoticeSlot.GRADES)
            view.recommendationStatus == "day_done" ->
                info("今天的课结束了，签到也收工。", NoticeSlot.GRADES)
            view.recommendationStatus == "unknown_room" ->
                info("今天有课，教室码还没确认。", NoticeSlot.WAITING)
            view.recommendationStatus == "unavailable" ->
                info("课表暂时读不到，晚点再看看。", NoticeSlot.WAITING)
            else -> info("现在没有待签到的课。", NoticeSlot.WAITING)
        }
        State.EXPIRED -> info(
            if (counts.isBlank()) "这次签到已经结束了。" else "签到已结束 · $counts",
            NoticeSlot.EXPIRED
        )
        State.NOT_STARTED -> info("签到还没开始，开始后 Rin 会提醒你。", NoticeSlot.CLASS_REMINDER)
        State.WAITING_THRESHOLD -> info(
            if (counts.isBlank()) "签到进行中，等人数到安全线。" else counts,
            NoticeSlot.CLASS_REMINDER
        )
        State.IDLE -> info("名单里没有你，先检查名单姓名。", NoticeSlot.ERROR)
        State.IDLE -> info("姓名匹配到多人，去签到页确认。", NoticeSlot.ERROR)
        State.IDLE -> info("这节课暂时不能签到，去签到页看看原因。", NoticeSlot.ERROR)
        State.READY -> info(
            if (counts.isBlank()) "可以签到了，去签到页看看。" else counts,
            NoticeSlot.READY
        )
        else -> info("现在没有待签到的课。", NoticeSlot.WAITING)
    }
}

/** A quiet mode line on the sign page; it never becomes a second call to action. */
@Composable
internal fun TodayAccessModeStrip(state: SignAccessState) {
    val text = state.bannerText ?: return
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.textSecondary)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** One quiet chat-style line from Rin; the detailed sign-in flow lives on its own page. */
@Composable
internal fun TodaySignChatCard(
    view: MonitorView?,
    loadFailed: Boolean,
    sessionExpired: Boolean,
    ready: Boolean,
    hasCoursesToday: Boolean?,
    viewIsToday: Boolean,
    signPro: Boolean,
    onOpenSign: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    val context = LocalContext.current
    val status = remember(
        view,
        loadFailed,
        sessionExpired,
        ready,
        hasCoursesToday,
        viewIsToday
    ) {
        signChatInfo(view, loadFailed, sessionExpired, ready, hasCoursesToday, viewIsToday)
    }
    val slot = status.slot
    val facePrefs = NoticeFaces.prefs.value
    val face = remember(slot, facePrefs) {
        NoticeIcons.largeIcon(context, NoticeFaces.face(slot))
    }
    val message = if (!signPro && ready) {
        "签到已就绪 · 可查看当前名单"
    } else {
        status.text
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = face.asImageBitmap(),
            contentDescription = "Rin 签到表情",
            modifier = Modifier.size(38.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Rin · 签到",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = message,
                color = colors.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        TextButton(onClick = onOpenSign) {
            Text(
                text = if (signPro) "去签到" else "查看名单",
                color = colors.signal,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** Next hours from the same campus forecast the cover uses, never a location permission. */
@Composable
internal fun TodayHourlyWeatherCard(
    snapshot: PetWeatherSnapshot?,
    today: LocalDate,
    nowHour: Int
) {
    val colors = LocalSuiSignColors.current
    // Keep the forecast strictly chronological: after 23:00 the strip must
    // continue into tomorrow instead of wrapping back to tonight's 20:00.
    val hours = upcomingWeatherHours(snapshot?.hourly.orEmpty(), today, nowHour, 6)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "逐小时天气",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            val temp = snapshot?.temperatureC
            val humidity = snapshot?.humidity
            Text(
                text = listOfNotNull(
                    temp?.let { "$it°" },
                    humidity?.let { "湿度 $it%" }
                ).joinToString(" · "),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (hours.isEmpty()) {
            Text(
                text = "逐小时预报暂不可用",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            hours.forEachIndexed { index, hour ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            index == 0 && hourOf(hour.time) == nowHour -> "现在"
                            !hour.time.startsWith(today.toString()) -> "明${hourOf(hour.time)}时"
                            else -> "${hourOf(hour.time)}时"
                        },
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    WeatherIcon(
                        weather = hour.weather,
                        tint = weatherColor(hour.weather),
                        modifier = Modifier.padding(vertical = 4.dp).size(26.dp)
                    )
                    Text(
                        text = weatherGlyph(hour.weather),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "${hour.temperatureC}°",
                        color = colors.text,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        if (hours.size >= 2) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "温度曲线",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                val min = hours.minOf { it.temperatureC }
                val max = hours.maxOf { it.temperatureC }
                Text(
                    text = "低 $min° · 高 $max°",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            TemperatureLine(hours.map { it.temperatureC })
        }
    }
}

/** Dense 8×3 matrix for the full next day, one cell per hour. */
@Composable
internal fun TodayDenseWeatherCard(
    snapshot: PetWeatherSnapshot?,
    today: LocalDate,
    nowHour: Int
) {
    val colors = LocalSuiSignColors.current
    val hours = upcomingWeatherHours(snapshot?.hourly.orEmpty(), today, nowHour, 24)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "24 小时天气",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = listOfNotNull(
                    snapshot?.temperatureC?.let { "$it°" },
                    snapshot?.humidity?.let { "湿度 $it%" }
                ).joinToString(" · "),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (hours.isEmpty()) {
            Text(
                text = "24 小时预报暂不可用",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        hours.chunked(8).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(8) { column ->
                    val hour = row.getOrNull(column)
                    if (hour == null) {
                        Spacer(Modifier.weight(1f))
                        return@repeat
                    }
                    val isNow = rowIndex == 0 && column == 0 &&
                        hour.time.startsWith(today.toString()) &&
                        hourOf(hour.time) == nowHour
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isNow) colors.signal.copy(alpha = 0.13f)
                                else colors.canvas.copy(alpha = 0.5f)
                            )
                            .border(
                                1.dp,
                                if (isNow) colors.signal.copy(alpha = 0.45f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = when {
                                isNow -> "现在"
                                !hour.time.startsWith(today.toString()) -> "明${hourOf(hour.time)}"
                                else -> "${hourOf(hour.time)}时"
                            },
                            color = if (isNow) colors.signal else colors.textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                        WeatherIcon(
                            weather = hour.weather,
                            tint = weatherColor(hour.weather),
                            modifier = Modifier.padding(vertical = 2.dp).size(16.dp)
                        )
                        Text(
                            text = "${hour.temperatureC}°",
                            color = colors.text,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

internal data class WeatherDaySummary(
    val low: Int,
    val high: Int,
    val weather: PetWeather,
    val rainHours: Int
)

/** Aggregate the hourly campus forecast without adding a second weather API. */
internal fun weatherDaySummary(hours: List<HourlyWeather>, date: LocalDate): WeatherDaySummary? {
    val day = hours.filter { it.time.startsWith(date.toString()) }
    if (day.isEmpty()) return null
    val rainHours = day.count { it.weather == PetWeather.RAIN || it.weather == PetWeather.SNOW }
    val dominant = day.groupingBy { it.weather }.eachCount()
        .maxByOrNull { it.value }?.key ?: day.first().weather
    return WeatherDaySummary(
        low = day.minOf { it.temperatureC },
        high = day.maxOf { it.temperatureC },
        weather = if (rainHours >= 3) {
            if (day.any { it.weather == PetWeather.SNOW }) PetWeather.SNOW else PetWeather.RAIN
        } else {
            dominant
        },
        rainHours = rainHours
    )
}

/** Two quiet daily rows: today and tomorrow, with a high/low pair. */
@Composable
internal fun TodayWeatherDayCard(snapshot: PetWeatherSnapshot?, today: LocalDate) {
    val colors = LocalSuiSignColors.current
    val hourly = snapshot?.hourly.orEmpty()
    val days = listOf(today, today.plusDays(1)).mapNotNull { date ->
        weatherDaySummary(hourly, date)?.let { date to it }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "天气日卡",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "今明",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (days.isEmpty()) {
            Text(
                text = "日天气暂不可用",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            return@Column
        }
        days.forEachIndexed { index, (date, summary) ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .height(1.dp)
                        .background(colors.border.copy(alpha = 0.45f))
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(48.dp)) {
                    Text(
                        text = if (index == 0) "今天" else "明天",
                        color = colors.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = weekdayName(date.dayOfWeek.value),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                WeatherIcon(
                    weather = summary.weather,
                    tint = weatherColor(summary.weather),
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = weatherName(summary.weather),
                        color = colors.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (summary.rainHours > 0) {
                            "${summary.rainHours} 小时有雨"
                        } else {
                            "无明显降雨"
                        },
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    text = "${summary.high}°",
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = " / ${summary.low}°",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/** A small vector weather icon; avoids emoji and scales cleanly in every theme. */
@Composable
internal fun WeatherIcon(
    weather: PetWeather,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        val c = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
        fun rays(radius: Float, inner: Float, color: Color, width: Float) {
            repeat(8) { index ->
                val angle = Math.PI * index / 4.0
                drawLine(
                    color,
                    androidx.compose.ui.geometry.Offset(
                        c.x + kotlin.math.cos(angle).toFloat() * inner,
                        c.y + kotlin.math.sin(angle).toFloat() * inner
                    ),
                    androidx.compose.ui.geometry.Offset(
                        c.x + kotlin.math.cos(angle).toFloat() * radius,
                        c.y + kotlin.math.sin(angle).toFloat() * radius
                    ),
                    width
                )
            }
        }
        fun cloud(color: Color) {
            drawCircle(color, w * 0.24f, androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.48f))
            drawCircle(color, w * 0.30f, androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.42f))
            drawRoundRect(
                color,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.50f),
                size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f)
            )
        }
        when (weather) {
            PetWeather.CLEAR, PetWeather.HOT -> {
                drawCircle(tint, w * 0.24f, c)
                rays(w * 0.46f, w * 0.30f, tint, sw)
            }
            PetWeather.CLOUDY -> cloud(tint.copy(alpha = 0.85f))
            PetWeather.RAIN -> {
                cloud(tint.copy(alpha = 0.75f))
                repeat(3) { index ->
                    val x = w * (0.30f + index * 0.18f)
                    drawLine(
                        tint,
                        androidx.compose.ui.geometry.Offset(x, h * 0.74f),
                        androidx.compose.ui.geometry.Offset(x - w * 0.05f, h * 0.90f),
                        sw
                    )
                }
            }
            PetWeather.SNOW, PetWeather.COLD -> {
                cloud(tint.copy(alpha = 0.75f))
                repeat(3) { index ->
                    drawCircle(
                        tint,
                        sw,
                        androidx.compose.ui.geometry.Offset(w * (0.30f + index * 0.18f), h * 0.82f)
                    )
                }
            }
            else -> drawCircle(tint.copy(alpha = 0.7f), w * 0.18f, c)
        }
    }
}

@Composable
private fun TemperatureLine(temperatures: List<Int>) {
    val colors = LocalSuiSignColors.current
    val min = temperatures.minOrNull() ?: return
    val max = temperatures.maxOrNull() ?: return
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
    ) {
        val pad = 6.dp.toPx()
        val range = (max - min).coerceAtLeast(1)
        val step = (size.width - pad * 2f) / (temperatures.size - 1).coerceAtLeast(1)
        val points = temperatures.mapIndexed { index, value ->
            androidx.compose.ui.geometry.Offset(
                pad + index * step,
                pad + (1f - (value - min).toFloat() / range) * (size.height - pad * 2f)
            )
        }
        val area = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, size.height - pad)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height - pad)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(colors.signal.copy(alpha = 0.26f), Color.Transparent),
                startY = pad,
                endY = size.height - pad
            )
        )
        val line = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            line,
            color = colors.signal,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
        points.forEachIndexed { index, point ->
            drawCircle(
                color = if (index == 0) colors.text else colors.signal,
                radius = 2.8.dp.toPx(),
                center = point
            )
        }
    }
}

/** Quick notes are today's manual todos; the same rows stay manageable in 手动数据. */
@Composable
internal fun TodayMemoCard(
    todos: List<ManualTodo>,
    today: String,
    onAdd: (String) -> Unit,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearDone: () -> Unit = {}
) {
    val colors = LocalSuiSignColors.current
    val mine = todos.filter { it.due == today || it.due == "今天" }
        .sortedWith(compareBy({ it.done }, { it.title }))
    var draft by rememberSaveable(today) { mutableStateOf("") }
    val done = mine.count { it.done }
    fun addDraft() {
        if (draft.isNotBlank()) {
            onAdd(draft.trim())
            draft = ""
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "今日速记",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (mine.isEmpty()) "今天还空着" else "${mine.size - done}/${mine.size} 完成",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            if (done > 0) {
                TextButton(
                    onClick = onClearDone,
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Text("清除已完成", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (mine.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colors.border.copy(alpha = 0.55f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(done.toFloat() / mine.size)
                        .fillMaxHeight()
                        .background(colors.signal)
                )
            }
            Spacer(Modifier.height(4.dp))
            mine.forEach { todo ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = todo.done, onCheckedChange = { onToggle(todo.id) })
                    Text(
                        text = todo.title,
                        color = if (todo.done) colors.textSecondary else colors.text,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onRemove(todo.id) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Text("删", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("记一条今天要做的事…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addDraft() }),
                colors = suiTextFieldColors(),
                modifier = Modifier.weight(1f)
            )
            TextButton(
                enabled = draft.isNotBlank(),
                onClick = { addDraft() }
            ) {
                Text("记下", color = colors.signal, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

data class TomorrowCourse(
    val name: String,
    val start: String,
    val location: String
)

/** The 22:00 night card: tomorrow's weather segments, first class and reminders. */
@Composable
internal fun TomorrowPreviewCard(
    tomorrow: LocalDate,
    courses: List<TomorrowCourse>,
    weather: PetWeatherSnapshot?,
    reminders: List<CourseReminder>,
    todos: List<ManualTodo>
) {
    val colors = LocalSuiSignColors.current
    val segments = listOf(8, 12, 18).mapNotNull { hour ->
        weather?.hourly?.firstOrNull {
            it.time.startsWith(tomorrow.toString()) && hourOf(it.time) == hour
        }
    }
    val first = courses.minByOrNull { it.start }
    val tomorrowTodos = todos.filter { it.due == tomorrow.toString() && !it.done }
    val tomorrowReminders = reminders.filter { it.date == tomorrow.toString() }
        .sortedBy { it.startTime }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(colors.surface, colors.canvas)
                )
            )
            .border(1.dp, colors.signal.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 22.dp)
                    .clip(CircleShape)
                    .background(colors.signal)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "明日预览",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = tomorrow.format(DateTimeFormatter.ofPattern("M月d日 · E", Locale.CHINA)),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = weekdayName(tomorrow.dayOfWeek.value),
                color = colors.text,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (courses.isEmpty()) "明天没有课" else "${courses.size} 节课",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.height(10.dp))
        Hairline()
        if (segments.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                segments.forEach { segment ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.canvas.copy(alpha = 0.55f))
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${hourOf(segment.time)}时",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = weatherGlyph(segment.weather),
                            color = weatherColor(segment.weather),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        Text(
                            text = "${segment.temperatureC}°",
                            color = colors.text,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        } else {
            Text(
                text = weather?.temperatureC?.let { "明天天气参考 · 现在 $it°" } ?: "明天天气暂不可用",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        if (first != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = first.start,
                    color = colors.signal,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = first.name,
                    color = colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                first.location.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            courses.drop(1).take(2).forEach { course ->
                Text(
                    text = "${course.start} · ${course.name}",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp, start = 52.dp)
                )
            }
        }
        val notes = buildList {
            tomorrowReminders.take(2).forEach {
                add("提醒 ${it.startTime} · ${it.courseName}" + if (it.leadMinutes > 0) "（提前 ${it.leadMinutes} 分钟）" else "")
            }
            tomorrowTodos.take(2).forEach { add("待办 ${it.title}") }
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Hairline()
            notes.forEach { note ->
                Text(
                    text = note,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Text(
            text = if (first != null) "明早 ${first.start} 第一节课" else "明天见",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

private fun hourOf(time: String): Int =
    time.substringAfter('T', "").take(2).toIntOrNull() ?: 0

private fun upcomingWeatherHours(
    all: List<HourlyWeather>,
    today: LocalDate,
    nowHour: Int,
    count: Int
): List<HourlyWeather> {
    val start = all.indexOfFirst {
        it.time.startsWith(today.toString()) && hourOf(it.time) >= nowHour
    }
    return if (start >= 0) all.drop(start).take(count) else all.take(count)
}

private fun weatherName(weather: PetWeather): String = when (weather) {
    PetWeather.RAIN -> "有雨"
    PetWeather.SNOW -> "有雪"
    PetWeather.HOT -> "炎热"
    PetWeather.COLD -> "寒冷"
    PetWeather.CLEAR -> "晴"
    PetWeather.CLOUDY -> "多云"
    else -> "天气一般"
}

private fun weekdayName(weekday: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(weekday - 1) { "—" }

private fun weatherGlyph(weather: PetWeather): String = when (weather) {
    PetWeather.RAIN -> "雨"
    PetWeather.SNOW -> "雪"
    PetWeather.HOT -> "热"
    PetWeather.COLD -> "冷"
    PetWeather.CLEAR -> "晴"
    PetWeather.CLOUDY -> "云"
    else -> "—"
}

@Composable
private fun weatherColor(weather: PetWeather): Color {
    val colors = LocalSuiSignColors.current
    return when (weather) {
        PetWeather.RAIN, PetWeather.SNOW -> colors.signal
        PetWeather.HOT -> colors.warning
        PetWeather.COLD -> colors.accent
        else -> colors.textSecondary
    }
}
