package top.sky2333.rin.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import top.sky2333.rin.data.HourlyWeather
import top.sky2333.rin.data.PetWeather
import top.sky2333.rin.data.PetWeatherSnapshot
import top.sky2333.rin.ui.theme.LocalRinColors

@Composable
internal fun HourlyWeatherCard(snapshot: PetWeatherSnapshot?, onRefresh: () -> Unit) {
    val colors = LocalRinColors.current
    WidgetCard(
        title = "逐小时天气",
        meta = listOfNotNull(
            snapshot?.temperatureC?.let { "$it°" },
            snapshot?.humidity?.let { "$it%" }
        ).joinToString(" · ").ifBlank { "等待更新" },
        actionLabel = "刷新",
        onAction = onRefresh
    ) {
        val hours = nextHours(snapshot, 6)
        if (hours.isEmpty()) {
            Text("天气还没更新", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            return@WidgetCard
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            hours.forEach { hour ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = hour.time,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(5.dp))
                    ConditionDot(hour.weather, size = 8.dp)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "${hour.temperatureC}°",
                        color = colors.text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TemperatureLine(
            values = hours.map { it.temperatureC },
            modifier = Modifier.fillMaxWidth().height(34.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeatherDenseCard(snapshot: PetWeatherSnapshot?) {
    val colors = LocalRinColors.current
    WidgetCard(
        title = "24 小时天气",
        meta = snapshot?.let { weatherLabel(it.weather) }.orEmpty()
    ) {
        val hours = nextHours(snapshot, 24)
        if (hours.isEmpty()) {
            Text("天气还没更新", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            return@WidgetCard
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 8,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            hours.forEach { hour ->
                Column(
                    modifier = Modifier.width(34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = hour.time.take(2),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(3.dp))
                    ConditionDot(hour.weather, size = 5.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${hour.temperatureC}°",
                        color = colors.text,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
internal fun WeatherDayCard(snapshot: PetWeatherSnapshot?) {
    val colors = LocalRinColors.current
    WidgetCard(
        title = "天气日卡",
        meta = snapshot?.let { weatherLabel(it.weather) }.orEmpty()
    ) {
        if (snapshot == null) {
            Text("天气还没更新", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            return@WidgetCard
        }
        val hours = nextHours(snapshot, 24)
        val low = hours.minOfOrNull { it.temperatureC } ?: snapshot.temperatureC
        val high = hours.maxOfOrNull { it.temperatureC } ?: snapshot.temperatureC
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConditionDot(snapshot.weather, size = 12.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = weatherLabel(snapshot.weather),
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = listOfNotNull(
                        snapshot.temperatureC?.let { "现在 $it°" },
                        low?.let { "最低 $it°" },
                        high?.let { "最高 $it°" }
                    ).joinToString(" · "),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = snapshot.temperatureC?.let { "$it°" } ?: "—",
                color = colors.signal,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = listOfNotNull(
                snapshot.humidity?.let { "湿度 $it%" },
                "未来 24 小时"
            ).joinToString(" · "),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ConditionDot(weather: PetWeather, size: androidx.compose.ui.unit.Dp) {
    val colors = LocalRinColors.current
    val color = when (weather) {
        PetWeather.CLEAR -> colors.signal
        PetWeather.CLOUDY -> colors.textSecondary
        PetWeather.RAIN -> colors.accent
        PetWeather.SNOW -> colors.textSecondary
        PetWeather.HOT -> colors.warning
        PetWeather.COLD -> colors.accent
        else -> colors.border
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun TemperatureLine(values: List<Int>, modifier: Modifier = Modifier) {
    val colors = LocalRinColors.current
    if (values.size < 2) return
    Canvas(modifier) {
        val low = values.min().toFloat()
        val high = values.max().toFloat()
        val span = max(1f, high - low)
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(
                x = index * stepX,
                y = size.height - ((value - low) / span) * (size.height * 0.72f) - size.height * 0.14f
            )
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(colors.signal.copy(alpha = 0.20f), Color.Transparent)
            )
        )
        drawPath(path = line, color = colors.signal, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        points.forEach { point ->
            drawCircle(color = colors.signal, radius = 2.6f, center = point)
        }
    }
}

private fun nextHours(snapshot: PetWeatherSnapshot?, limit: Int): List<HourlyWeather> {
    val all = snapshot?.hourly.orEmpty()
    if (all.isEmpty()) return emptyList()
    val now = java.time.LocalDateTime.now()
    val today = now.toLocalDate().toString()
    val currentHour = now.hour
    val upcoming = all.filter { hour ->
        val time = hour.time
        if (!time.startsWith(today)) return@filter true
        val hourOfDay = time.substringAfter('T').take(2).toIntOrNull() ?: 0
        hourOfDay >= currentHour
    }
    return (upcoming.ifEmpty { all }).take(limit)
}

private fun weatherLabel(weather: PetWeather): String = when (weather) {
    PetWeather.CLEAR -> "晴"
    PetWeather.CLOUDY -> "多云"
    PetWeather.RAIN -> "有雨"
    PetWeather.SNOW -> "有雪"
    PetWeather.HOT -> "炎热"
    PetWeather.COLD -> "寒冷"
    PetWeather.UNKNOWN -> "天气"
}
