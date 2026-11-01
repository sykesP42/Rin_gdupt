package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.RinSheet
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.schedule.CourseLoad
import top.sykesp42.rin.ui.schedule.ExportPalette
import top.sykesp42.rin.ui.schedule.PeriodLoad
import top.sykesp42.rin.ui.schedule.ReportRow
import top.sykesp42.rin.ui.schedule.ReportStat
import top.sykesp42.rin.ui.schedule.SemesterReport
import top.sykesp42.rin.ui.schedule.ShareStyle
import top.sykesp42.rin.ui.schedule.WeekExport
import top.sykesp42.rin.ui.schedule.WeekLoad
import top.sykesp42.rin.ui.schedule.WeekdayLoad
import top.sykesp42.rin.ui.theme.LocalSuiSignColors

/** 学期报告: term-wide numbers, charts and fun stats, shareable as one card. */
@Composable
internal fun SemesterReportSheet(
    report: SemesterReport,
    palette: ExportPalette,
    shareStyle: ShareStyle,
    shareOwner: String,
    appVersion: String,
    onDismiss: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun share() {
        scope.launch {
            val bitmap = runCatching {
                withContext(Dispatchers.Default) {
                    WeekExport.renderReport(report, palette, shareStyle, shareOwner, appVersion)
                }
            }.getOrNull() ?: return@launch
            WeekExport.share(context, bitmap, report.title)
        }
    }

    RinSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = report.title,
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("关闭", color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            text = report.subtitle,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )
        Text(
            text = report.note,
            color = colors.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "统计口径",
                color = colors.signal,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "1 次课 = 一个上课时段；1-2 节、1-4 节连堂都只算 1 次课，课时按实际节数累计。",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        StatGrid(report.stats)

        ReportSection(
            title = "各周课次",
            meta = report.peakWeek?.let { "峰值 第 ${it.week} 周 · ${it.sessions} 次课" }
        ) {
            WeekBars(report.weeks, report.currentWeek)
        }

        ReportSection(title = "星期分布", meta = "学期累计") {
            WeekdayBars(report.weekdays)
        }

        ReportSection(title = "时段分布", meta = "学期累计") {
            PeriodBars(report.periods)
        }

        if (report.topCourses.isNotEmpty()) {
            ReportSection(title = "课程课次", meta = "按学期课次排序") {
                CourseBars(report.topCourses)
            }
        }

        if (report.rows.isNotEmpty()) {
            ReportSection(title = "趣味统计") {
                StatRows(report.rows)
            }
        }

        SuiPrimaryButton(
            text = "分享学期报告",
            onClick = { share() },
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
        )
    }
}

@Composable
private fun StatGrid(stats: List<ReportStat>) {
    val colors = LocalSuiSignColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { stat ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stat.label,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = stat.value,
                                color = colors.text,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stat.unit,
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReportSection(
    title: String,
    meta: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalSuiSignColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Hairline()
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun WeekBars(weeks: List<WeekLoad>, currentWeek: Int) {
    val colors = LocalSuiSignColors.current
    val max = weeks.maxOfOrNull { it.sessions }?.coerceAtLeast(1) ?: 1
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            weeks.forEach { week ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fraction = (week.sessions.toFloat() / max).coerceAtLeast(0.035f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                if (week.week == currentWeek) colors.signal
                                else colors.accent.copy(alpha = 0.34f)
                            )
                    )
                }
            }
        }
        Hairline()
        Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
            weeks.forEach { week ->
                Text(
                    text = if (week.week == 1 || week.week % 5 == 0 || week.week == weeks.last().week) {
                        week.week.toString()
                    } else "",
                    color = colors.textSecondary,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WeekdayBars(days: List<WeekdayLoad>) {
    val colors = LocalSuiSignColors.current
    val max = days.maxOfOrNull { it.sessions }?.coerceAtLeast(1) ?: 1
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        days.forEach { day ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = weekdayName(day.weekday),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(30.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(colors.border.copy(alpha = 0.45f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((day.sessions.toFloat() / max).coerceAtLeast(0f))
                            .fillMaxHeight()
                            .background(
                                if (day.weekday >= 6) colors.signal
                                else colors.accent.copy(alpha = 0.72f)
                            )
                    )
                }
                Text(
                    text = "${day.sessions} 次课",
                    color = if (day.sessions == 0) colors.textSecondary else colors.text,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(42.dp)
                )
            }
        }
    }
}

@Composable
private fun PeriodBars(periods: List<PeriodLoad>) {
    val colors = LocalSuiSignColors.current
    val max = periods.maxOfOrNull { it.sessions }?.coerceAtLeast(1) ?: 1
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        periods.forEach { period ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = period.label,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(30.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(colors.border.copy(alpha = 0.45f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((period.sessions.toFloat() / max).coerceAtLeast(0f))
                            .fillMaxHeight()
                            .background(colors.signal.copy(alpha = 0.75f))
                    )
                }
                Text(
                    text = "${period.sessions} 次课",
                    color = if (period.sessions == 0) colors.textSecondary else colors.text,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp)
                )
            }
        }
    }
}

@Composable
private fun CourseBars(courses: List<CourseLoad>) {
    val colors = LocalSuiSignColors.current
    val max = courses.maxOfOrNull { it.sessions }?.coerceAtLeast(1) ?: 1
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        courses.forEach { course ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = course.name,
                        color = colors.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${course.sessions} 次课",
                        color = colors.text,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(colors.border.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((course.sessions.toFloat() / max).coerceAtLeast(0f))
                            .fillMaxHeight()
                            .background(colors.accent.copy(alpha = 0.7f))
                    )
                }
                Text(
                    text = "第 ${course.firstWeek}-${course.lastWeek} 周 · 本周 ${course.meetingsPerWeek} 次课" +
                        " · 共 ${course.sections} 课时",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StatRows(rows: List<ReportRow>) {
    val colors = LocalSuiSignColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { row ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.label,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = row.value,
                            color = colors.text,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun weekdayName(weekday: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(weekday - 1) { "—" }
