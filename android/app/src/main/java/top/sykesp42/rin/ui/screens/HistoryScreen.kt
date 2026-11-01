package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.sykesp42.rin.data.HistoryEvent
import top.sykesp42.rin.data.HistoryKind
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.components.EmptyState
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.SuiAlertDialog
import top.sykesp42.rin.ui.components.RinMasthead
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.formatDateLine
import top.sykesp42.rin.ui.history.groupHistoryByDay
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.ThemeMode

/**
 * Spec 4.4 记录: one A2 paper timeline grouped by local calendar day.
 * Newest day first, newest event first inside each day; day headers are
 * `SectionHeader` hairlines and events are flat rows — no card wall, and the
 * stored events are already redacted (no link, QR or name).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    vm: AppViewModel,
    themeMode: ThemeMode,
    avatarText: String,
    onToggleTheme: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val history by vm.history.collectAsStateWithLifecycle()
    val attendance by vm.attendance.collectAsStateWithLifecycle()
    val colors = LocalSuiSignColors.current
    // Spec 2.2: 24dp page inset, tightened to 20dp on narrow phones.
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp
    var confirmClear by remember { mutableStateOf(false) }
    var insightsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadHistory()
        vm.loadAttendance()
    }

    val days = remember(history.events) { groupHistoryByDay(history.events) }

    PaperPage {
        PullToRefreshBox(
            isRefreshing = history.loading,
            onRefresh = vm::loadHistory,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = pageInset,
                    end = pageInset,
                    top = 8.dp,
                    bottom = 92.dp
                )
            ) {
                if (onBack != null) {
                    item(key = "back") {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            Text(
                                text = "‹ 返回签到",
                                color = colors.text,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                item(key = "masthead") {
                    RinMasthead(
                        dateLine = if (history.loading) "读取中" else "${history.events.size} 条记录",
                        title = "记录",
                        avatarText = avatarText,
                        onRefresh = vm::loadHistory,
                        onTheme = onToggleTheme
                    )
                }
                item(key = "attendance") {
                    AttendanceSection(state = attendance)
                }
                if (history.events.isNotEmpty()) {
                    item(key = "insights-toggle") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { insightsOpen = !insightsOpen }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "近 30 天",
                                color = colors.text,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (insightsOpen) "收起" else "展开",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    if (insightsOpen) {
                        item(key = "insights") {
                            HistoryInsights(events = history.events)
                        }
                    }
                }
                if (history.loading && history.events.isEmpty()) {
                    item(key = "loading") {
                        HistoryLoadingLine(modifier = Modifier.padding(top = 24.dp))
                    }
                }
                if (history.events.isEmpty() && !history.loading) {
                    item(key = "empty") {
                        EmptyState(
                            title = "还没有记录",
                            body = "签到与监控事件"
                        )
                    }
                }
                days.forEach { day ->
                    item(key = "day-${day.date}") {
                        SectionHeader(
                            title = formatDateLine(day.date),
                            meta = "${day.events.size} 条"
                        )
                    }
                    items(
                        items = day.events,
                        key = { event -> "${day.date}#${event.at}#${event.kind}" }
                    ) { event ->
                        HistoryRow(event)
                    }
                }
                item(key = "privacy-note") {
                    Text(
                        text = "仅本机 · 500 条 / 90 天",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
                if (history.events.isNotEmpty()) {
                    item(key = "clear-action") {
                        TextButton(
                            onClick = { confirmClear = true },
                            modifier = Modifier.heightIn(min = 48.dp).padding(top = 4.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "清空本机记录",
                                color = colors.warning,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        SuiAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空本地记录？") },
            text = { Text("只删本机记录") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearHistory()
                        confirmClear = false
                    }
                ) {
                    Text("清空", color = colors.warning)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("取消", color = colors.textSecondary)
                }
            }
        )
    }
}

/** One flat timeline row: monospace clock, status dot, title, body and course/room meta. */
@Composable
private fun HistoryRow(event: HistoryEvent) {
    val colors = LocalSuiSignColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 11.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = formatClock(event.at),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                modifier = Modifier.widthIn(min = 40.dp)
            )
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor(event.kind))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = historyTitle(event.kind),
                    color = colors.text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val body = historyBody(event.kind)
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                val meta = listOfNotNull(
                    event.courseName.takeIf { it.isNotBlank() },
                    event.roomCode.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = colors.text.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Hairline()
    }
}

/** No spinner and no shimmer: one static sky dot keeps the page free of endless animation. */
@Composable
private fun HistoryLoadingLine(modifier: Modifier = Modifier) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = modifier.heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.signal)
        )
        Text(
            text = "正在读取记录…",
            color = colors.text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Signal and warning are dot-only; copy never leans on them for contrast. */
@Composable
internal fun dotColor(kind: String): Color {
    val colors = LocalSuiSignColors.current
    return when (kind) {
        HistoryKind.READY, HistoryKind.CONFIRMED, HistoryKind.HANDOFF_OPENED,
        HistoryKind.HANDOFF_SHARED, HistoryKind.AUTO_STARTED -> colors.signal
        HistoryKind.HANDOFF_FAILED, HistoryKind.HANDOFF_UNCONFIRMED,
        HistoryKind.AUTO_FAILED, HistoryKind.SESSION_EXPIRED,
        HistoryKind.SCHEDULE_CHANGE -> colors.warning
        else -> colors.textSecondary
    }
}

internal fun historyTitle(kind: String): String = when (kind) {
    HistoryKind.READY -> "可以签到"
    HistoryKind.CONFIRMED -> "已确认签到"
    HistoryKind.CLASS_REMINDER -> "上课提醒"
    HistoryKind.SCHEDULE_CHANGE -> "课表变动"
    HistoryKind.LOGIN -> "登录"
    HistoryKind.LOGOUT -> "退出登录"
    HistoryKind.MONITOR_ENABLED -> "开启后台监控"
    HistoryKind.MONITOR_DISABLED -> "关闭后台监控"
    HistoryKind.SESSION_EXPIRED -> "登录失效"
    HistoryKind.HANDOFF_OPENED -> "已发起签到"
    HistoryKind.HANDOFF_SHARED -> "已分享到微信"
    HistoryKind.HANDOFF_FAILED -> "发起失败"
    HistoryKind.HANDOFF_UNCONFIRMED -> "已发起，未确认"
    HistoryKind.AUTO_ENABLED -> "开启自动签到"
    HistoryKind.AUTO_DISABLED -> "关闭自动签到"
    HistoryKind.AUTO_STARTED -> "自动发起签到"
    HistoryKind.AUTO_DEFERRED -> "待解锁后自动继续"
    HistoryKind.AUTO_FAILED -> "自动签到失败"
    HistoryKind.AUTO_CALIBRATION -> "自动模式演练"
    else -> kind
}

internal fun historyBody(kind: String): String = when (kind) {
    HistoryKind.READY -> "人数够了，等你处理"
    HistoryKind.CONFIRMED -> "名单已确认"
    HistoryKind.CLASS_REMINDER -> "课堂窗口开始"
    HistoryKind.SCHEDULE_CHANGE -> "教室或时间有更新"
    HistoryKind.LOGIN -> "已连上服务"
    HistoryKind.LOGOUT -> "已清除本机会话"
    HistoryKind.MONITOR_ENABLED -> "课堂窗口内自动检查"
    HistoryKind.MONITOR_DISABLED -> "不再检查课堂窗口"
    HistoryKind.SESSION_EXPIRED -> "要重新登录"
    HistoryKind.HANDOFF_OPENED -> "已打开微信，等名单确认"
    HistoryKind.HANDOFF_SHARED -> "已发企微并点开"
    HistoryKind.HANDOFF_FAILED -> "微信未打开或链接无效"
    HistoryKind.HANDOFF_UNCONFIRMED -> "60 秒内名单没确认，可重试"
    HistoryKind.AUTO_ENABLED -> "达阈值自动识码"
    HistoryKind.AUTO_DISABLED -> "回到通知和手动交接"
    HistoryKind.AUTO_STARTED -> "已交企微识码，待名单确认"
    HistoryKind.AUTO_DEFERRED -> "锁屏中，解锁后继续"
    HistoryKind.AUTO_FAILED -> "自动失败，已回到通知手动签到"
    HistoryKind.AUTO_CALIBRATION -> "企微识码链演练"
    else -> ""
}


/**
 * 本地统计洞察：最近 30 天的关键动作计数 + 近 7 天提醒小柱图。
 * 纯内存计算，不碰网络。
 */
@Composable
private fun HistoryInsights(events: List<HistoryEvent>) {
    val colors = LocalSuiSignColors.current
    val zone = java.time.ZoneId.of("Asia/Shanghai")
    val today = java.time.LocalDate.now(zone)
    val windowStart = today.minusDays(29).atStartOfDay(zone).toInstant()
    val recent = events.filter { it.at.isAfter(windowStart) }
    if (recent.isEmpty()) return
    val reminders = recent.count { it.kind == HistoryKind.CLASS_REMINDER }
    val ready = recent.count { it.kind == HistoryKind.READY }
    val confirmed = recent.count { it.kind == HistoryKind.CONFIRMED }
    val perDay = remember(events) {
        (0..6).map { offset ->
            val day = today.minusDays((6 - offset).toLong())
            day to events.count {
                it.kind == HistoryKind.CLASS_REMINDER &&
                    it.at.atZone(zone).toLocalDate() == day
            }
        }
    }
    val maxDay = perDay.maxOf { it.second }.coerceAtLeast(1)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            InsightStat("近30天提醒", "$reminders")
            InsightStat("签到就绪", "$ready")
            InsightStat("已确认", "$confirmed")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            perDay.forEach { (day, count) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((6.dp.value + 34.dp.value * (count.toFloat() / maxDay)).dp)
                            .background(
                                if (count > 0) colors.signal else colors.border,
                                MaterialTheme.shapes.extraSmall
                            )
                    )
                    Text(
                        text = "周${"一二三四五六日"[(day.dayOfWeek.value - 1).coerceIn(0, 6)]}",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        Text(
            text = "近 7 天课堂提醒",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Hairline()
    }
}

@Composable
private fun InsightStat(label: String, value: String) {
    val colors = LocalSuiSignColors.current
    Column {
        Text(
            text = value,
            color = colors.text,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = label,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
