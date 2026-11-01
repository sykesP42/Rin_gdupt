package top.sykesp42.rin.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.CampusNewsItem
import top.sykesp42.rin.data.JwxtExam
import top.sykesp42.rin.monitor.NoticeFaces
import top.sykesp42.rin.monitor.NoticeIcons
import top.sykesp42.rin.monitor.NoticeSlot
import top.sykesp42.rin.ui.schedule.firstSection
import top.sykesp42.rin.ui.schedule.parseExamTime
import top.sykesp42.rin.ui.schedule.sectionSpan
import top.sykesp42.rin.ui.schedule.todayEvents
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.courseAccent

@Composable
private fun WidgetCard(
    title: String,
    meta: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalSuiSignColors.current
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
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(actionLabel, color = colors.signal, style = MaterialTheme.typography.labelSmall)
                }
            } else if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
internal fun MiniTimetableDayCard(
    overview: ApiScheduleOverview?,
    today: LocalDate,
    onOpen: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    val dark = LocalVisualStyle.current.spec.dark
    val events = overview?.let { todayEvents(it, today) }.orEmpty()
    WidgetCard("Mini 课表 · 日", "${events.size} 节", "打开", onOpen) {
        if (overview == null) {
            Text("课表还没同步", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else if (events.isEmpty()) {
            Text("今天没有课", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            events.take(4).forEach { event ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.startTime.ifBlank { sectionStart(event.sectionText) },
                        color = courseAccent(event.courseName, dark),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(54.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = event.courseName,
                            color = colors.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (event.location.isNotBlank()) {
                            Text(
                                text = event.location,
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            if (events.size > 4) {
                Text(
                    text = "还有 ${events.size - 4} 节",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
internal fun MiniTimetableWeekCard(overview: ApiScheduleOverview?, onOpen: () -> Unit) {
    val colors = LocalSuiSignColors.current
    val counts = (1..7).map { weekday ->
        overview?.days?.firstOrNull { it.weekday == weekday }?.events?.size ?: 0
    }
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    WidgetCard("Mini 课表 · 周", "${counts.sum()} 节", "打开", onOpen) {
        if (overview == null) {
            Text("课表还没同步", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            return@WidgetCard
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            counts.forEachIndexed { index, count ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = count.toString(),
                        color = if (count == 0) colors.textSecondary else colors.text,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((10 + 36f * count / max).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (index == 5 || index == 6) colors.signal.copy(alpha = 0.55f)
                                else colors.accent.copy(alpha = 0.45f)
                            )
                    )
                    Text(
                        text = "一二三四五六日"[index].toString(),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/** Current period, or the next one during a break; null after the last period. */
internal fun miniTableSection(nowMinutes: Int): Int? =
    sectionBounds.entries.firstOrNull { nowMinutes in it.value.first..it.value.second }?.key
        ?: sectionBounds.entries.firstOrNull { nowMinutes < it.value.first }?.key

@Composable
internal fun MiniTimetableTableCard(
    overview: ApiScheduleOverview?,
    today: LocalDate,
    nowMinutes: Int,
    onOpen: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    val dark = LocalVisualStyle.current.spec.dark
    WidgetCard("Mini 课表 · 表", "", "打开", onOpen) {
        if (overview == null) {
            Text("课表还没同步", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            return@WidgetCard
        }
        val cells = overview.days.flatMap { day ->
            day.events.mapNotNull { event ->
                val start = firstSection(event.sectionText)
                val span = sectionSpan(event.sectionText)
                Triple(day.weekday, event, start to span)
            }
        }
        val colorsByCourse = cells.associate { (_, event, _) ->
            event.courseName to courseAccent(event.courseName, dark)
        }
        val todayColumn = today.dayOfWeek.value
            .takeIf { overview.days.any { day -> day.date == today.toString() } }
        val highlightRow = miniTableSection(nowMinutes)
        Column {
            Row(Modifier.fillMaxWidth()) {
                "一二三四五六日".forEachIndexed { index, label ->
                    val isToday = index + 1 == todayColumn
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isToday) colors.signal.copy(alpha = 0.13f)
                                else Color.Transparent
                            )
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label.toString(),
                            color = if (isToday) colors.signal else colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .width(14.dp)
                                .height(2.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isToday) colors.signal else Color.Transparent
                                )
                        )
                    }
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .padding(top = 4.dp)
            ) {
                val colW = size.width / 7f
                val rowH = size.height / 10f
                // X/Y cross highlight: today's weekday column plus the current
                // period row, drawn under the grid so course blocks stay legible.
                todayColumn?.let { column ->
                    val left = (column - 1) * colW
                    drawRect(
                        colors.signal.copy(alpha = 0.07f),
                        Offset(left, 0f),
                        Size(colW, size.height)
                    )
                    drawLine(
                        colors.signal.copy(alpha = 0.35f),
                        Offset(left, 0f),
                        Offset(left, size.height),
                        1.dp.toPx()
                    )
                    drawLine(
                        colors.signal.copy(alpha = 0.35f),
                        Offset(left + colW, 0f),
                        Offset(left + colW, size.height),
                        1.dp.toPx()
                    )
                }
                highlightRow?.let { section ->
                    val top = (section - 1).coerceIn(0, 9) * rowH
                    drawRect(
                        colors.signal.copy(alpha = 0.06f),
                        Offset(0f, top),
                        Size(size.width, rowH)
                    )
                    drawLine(
                        colors.signal.copy(alpha = 0.38f),
                        Offset(0f, top),
                        Offset(size.width, top),
                        1.dp.toPx()
                    )
                    drawLine(
                        colors.signal.copy(alpha = 0.38f),
                        Offset(0f, top + rowH),
                        Offset(size.width, top + rowH),
                        1.dp.toPx()
                    )
                }
                for (line in 1..9) {
                    drawLine(
                        colors.border.copy(alpha = 0.4f),
                        Offset(0f, line * rowH),
                        Offset(size.width, line * rowH),
                        0.8.dp.toPx()
                    )
                }
                cells.forEach { (weekday, event, slot) ->
                    val (start, span) = slot
                    val left = (weekday - 1) * colW + 1.dp.toPx()
                    val top = (start - 1).coerceIn(0, 9) * rowH + 1.dp.toPx()
                    val bottom = (top + span * rowH - 2.dp.toPx()).coerceAtMost(size.height)
                    drawRoundRect(
                        colorsByCourse[event.courseName] ?: colors.accent,
                        topLeft = Offset(left, top),
                        size = Size(colW - 2.dp.toPx(), (bottom - top).coerceAtLeast(2.dp.toPx())),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuickLinksCard(
    settings: AppSettings,
    newsUrl: String,
    onSchedule: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalSuiSignColors.current
    WidgetCard("快捷跳转", "", null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickLink("教务系统", Modifier.weight(1f)) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(settings.jwxtBaseUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                QuickLink("校园要闻", Modifier.weight(1f)) {
                    if (newsUrl.isNotBlank()) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(newsUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            }
            QuickLink("打开课表", Modifier.weight(1f), onSchedule)
        }
    }
}

@Composable
private fun QuickLink(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalSuiSignColors.current
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.canvas.copy(alpha = 0.65f))
            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = colors.text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun FileHubCard(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val colors = LocalSuiSignColors.current
    var nonce by remember { mutableIntStateOf(0) }
    val stats = remember(nonce) {
        val dir = File(context.cacheDir, "share")
        val files = dir.listFiles().orEmpty()
        files.size to files.sumOf { it.length() }
    }
    WidgetCard("文件归纳", "", null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "分享缓存 ${stats.first} 个",
                    color = colors.text,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatBytes(stats.second),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            TextButton(
                onClick = {
                    File(context.cacheDir, "share").listFiles()?.forEach { it.delete() }
                    nonce++
                    onMessage("已清理分享缓存")
                },
                modifier = Modifier.heightIn(min = 40.dp)
            ) { Text("清理", color = colors.warning, style = MaterialTheme.typography.labelSmall) }
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure { onMessage("打不开系统文件设置") }
                },
                modifier = Modifier.heightIn(min = 40.dp)
            ) { Text("系统文件", color = colors.signal, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(Locale.US, bytes / 1024f / 1024f)
    bytes >= 1024 -> "%.0f KB".format(Locale.US, bytes / 1024f)
    else -> "$bytes B"
}

@Composable
internal fun RinShowcaseCard() {
    val colors = LocalSuiSignColors.current
    val context = LocalContext.current
    val face = remember {
        NoticeIcons.largeIcon(context, NoticeFaces.face(NoticeSlot.CONFIRMED))
    }
    WidgetCard("Rin 展示架", "", null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                bitmap = face.asImageBitmap(),
                contentDescription = "Rin",
                modifier = Modifier.size(74.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "今天也在。",
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "忙的时候看一眼，闲的时候也看一眼。",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
internal fun PhotoFrameCard(
    bitmap: Bitmap?,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    WidgetCard("相框", "", null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.7f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.canvas)
                .border(1.dp, colors.border.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "今日相框",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "添加一张照片",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onPick) {
                Text("更换", color = colors.signal, style = MaterialTheme.typography.labelSmall)
            }
            if (bitmap != null) {
                TextButton(onClick = onClear) {
                    Text("移除", color = colors.warning, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun ExamCountdownCard(exams: List<JwxtExam>, today: LocalDate) {
    val colors = LocalSuiSignColors.current
    val next = exams.mapNotNull { exam ->
        val date = parseExamTime(exam.time).date ?: return@mapNotNull null
        if (date.isBefore(today)) null else Triple(exam, date, ChronoUnit.DAYS.between(today, date))
    }.minByOrNull { it.third }
    WidgetCard("考试倒计时", "", null) {
        if (next == null) {
            Text("暂无待考", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            return@WidgetCard
        }
        val (exam, date, days) = next
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (days == 0L) "今天" else "$days",
                color = colors.signal,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (days > 0) {
                Text(
                    text = " 天后",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = date.toString().takeLast(5),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            text = exam.courseName,
            color = colors.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        listOf(
            exam.time.takeIf { it.isNotBlank() },
            exam.location.takeIf { it.isNotBlank() }
        ).filterNotNull().joinToString(" · ").takeIf { it.isNotBlank() }?.let {
            Text(it, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun CampusNewsCard(items: List<CampusNewsItem>, onOpen: (String) -> Unit) {
    val colors = LocalSuiSignColors.current
    var expanded by remember { mutableStateOf(false) }
    val canExpand = items.size > 2
    WidgetCard(
        title = "校园要闻",
        actionLabel = if (expanded) "收起" else if (canExpand) "展开" else null,
        onAction = if (expanded || canExpand) {
            { expanded = !expanded }
        } else {
            null
        }
    ) {
        if (items.isEmpty()) {
            Text("暂无更新", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            return@WidgetCard
        }
        Text(
            text = "共 ${items.size} 条" + when {
                expanded -> " · 卡片内上下滑动"
                canExpand -> " · 显示最新 2 条"
                else -> ""
            },
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expanded) {
                        Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                )
        ) {
            val visible = if (expanded) items else items.take(2)
            visible.forEachIndexed { index, item ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.border.copy(alpha = 0.35f))
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable { onOpen(item.url) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = item.title,
                        color = colors.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.date,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

private fun sectionStart(text: String): String {
    val raw = Regex("\\d+").find(text)?.value.orEmpty()
    val first = when {
        raw.length >= 4 && raw.length % 2 == 0 -> raw.take(2).toIntOrNull() ?: 1
        else -> raw.toIntOrNull() ?: 1
    }
    val minutes = sectionBounds[first]?.first ?: 8 * 60
    return "%02d:%02d".format(minutes / 60, minutes % 60)
}
