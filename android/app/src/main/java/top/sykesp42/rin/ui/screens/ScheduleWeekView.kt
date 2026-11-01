package top.sykesp42.rin.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import java.time.DayOfWeek
import top.sykesp42.rin.data.ApiScheduleDay
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.ui.schedule.displayedToday
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.schedule.todayAnchor
import top.sykesp42.rin.ui.schedule.todayAtSchoolClock
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.schedule.ShareFormat
import top.sykesp42.rin.ui.schedule.ShareStyle
import top.sykesp42.rin.ui.schedule.ExportPalette
import top.sykesp42.rin.ui.schedule.WeekExport
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import top.sykesp42.rin.ui.components.SuiAlertDialog

/**
 * Secondary full-week view inside the 课表 tab. It fills the tab content
 * edge-to-edge: only the top action row is inset, the seven-column canvas runs
 * to both screen edges and has no outer card. Horizontal swipes still change
 * the viewed week; pull-to-refresh is owned by [ScheduleScreen].
 *
 * When [emptyContent] is supplied (the viewed week has no data), it replaces
 * the grid while the action row, week label and week swiping stay usable.
 */
@Composable
fun ScheduleWeekView(
    overview: ApiScheduleOverview,
    onCourseTap: (ApiScheduleEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeWeek: (Int) -> Unit = {},
    emptyContent: (@Composable () -> Unit)? = null,
    fullscreen: Boolean = false,
    onFullscreen: (() -> Unit)? = null,
    tableMode: Boolean = false,
    onToggleTableMode: (() -> Unit)? = null,
    currentWeek: Int = 0,
    onBackToCurrentWeek: (() -> Unit)? = null,
    shareOwner: String = "",
    shareStyle: ShareStyle = ShareStyle.PLAIN,
    appVersion: String = "",
    onShareStyleChange: (ShareStyle) -> Unit = {},
    shareFormat: ShareFormat = ShareFormat.COLUMNS,
    onShareFormatChange: (ShareFormat) -> Unit = {}
) {
    val colors = LocalSuiSignColors.current
    val animations = LocalAnimationsEnabled.current
    val shareContext = LocalContext.current
    val exportPalette = ExportPalette(
        canvas = colors.canvas.toArgb(),
        surface = colors.surface.toArgb(),
        text = colors.text.toArgb(),
        textSecondary = colors.textSecondary.toArgb(),
        signal = colors.signal.toArgb(),
        border = colors.border.toArgb(),
        dark = LocalVisualStyle.current.spec.dark
    )
    var stylePickerOpen by remember { mutableStateOf(false) }

    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp
    val days = remember(overview) { gridDays(overview) }
    // Live "now" minute on a 30s tick, so the grid's current-class highlight
    // moves with the school clock instead of freezing at composition time.
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
    val nowMinutes = nowMinutesAt(java.time.Instant.ofEpochMilli(tick))
    val today = displayedToday(days.map { it.date })
    val weekLabel = if (overview.week > 0) "第 ${overview.week} 周" else "本周"
    // How far the viewed week sits from the real one, so a scrolled-away week
    // never reads as "this week".
    val weekDelta = if (currentWeek > 0 && overview.week > 0) overview.week - currentWeek else 0
    val currentSwipe by rememberUpdatedState(onSwipeWeek)
    val scope = rememberCoroutineScope()

    /** 周 mode shares the column-card card; 表 mode has its own corner chip. */
    fun shareAgenda() {
        scope.launch {
            val bitmap = runCatching {
                withContext(Dispatchers.Default) {
                    WeekExport.renderAgenda(
                        weekLabel = weekLabel,
                        days = days,
                        palette = exportPalette,
                        style = shareStyle,
                        owner = shareOwner,
                        version = appVersion,
                        format = shareFormat
                    )
                }
            }.getOrNull()
            if (bitmap != null) WeekExport.share(shareContext, bitmap, weekLabel)
        }
    }

    // A single, finite reveal that respects the app's motion switch.
    var revealed by remember { mutableStateOf(!animations) }
    LaunchedEffect(Unit) { revealed = true }
    val reveal by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = if (animations) 220 else 0),
        label = "week_view_reveal"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .alpha(reveal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = pageInset, end = pageInset, top = 8.dp, bottom = 2.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (fullscreen) {
                HeaderAction(text = "退出全屏", onClick = onClose)
                Spacer(Modifier.weight(1f))
                if (onToggleTableMode != null) {
                    ModeTab("周", selected = !tableMode) {
                        if (tableMode) onToggleTableMode()
                    }
                    ModeTab("表", selected = tableMode) {
                        if (!tableMode) onToggleTableMode()
                    }
                }
            } else {
                // 日 / 周 / 表 share one switcher: 日 returns to the dated
                // timeline, 周 is this agenda, 表 is the seven-column canvas.
                ModeTab("日", selected = false, onClick = onClose)
                if (onToggleTableMode != null) {
                    ModeTab("周", selected = !tableMode) {
                        if (tableMode) onToggleTableMode()
                    }
                    ModeTab("表", selected = tableMode) {
                        if (!tableMode) onToggleTableMode()
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onFullscreen != null) {
                    HeaderAction(text = "全屏", onClick = onFullscreen)
                }
            }
            // Arrows keep week stepping reachable while the canvas is zoomed
            // (pan owns the single-finger drag there). Built on plain clickable
            // boxes: Material TextButton's 58dp MinWidth pushed this row past
            // the viewport at large font scales.
            HeaderAction(text = "‹", onClick = { currentSwipe(-1) })
            Text(
                text = weekLabel,
                color = colors.text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
            HeaderAction(text = "›", onClick = { currentSwipe(1) })
            if (weekDelta != 0) {
                OffWeekChip(delta = weekDelta, onBack = onBackToCurrentWeek)
            }
        }
        if (stylePickerOpen) {
            ShareStyleDialog(
                current = shareStyle,
                format = shareFormat,
                onFormat = onShareFormatChange,
                onPick = { style ->
                    onShareStyleChange(style)
                    stylePickerOpen = false
                },
                onDismiss = { stylePickerOpen = false }
            )
        }
        if (emptyContent != null) {
            // Keep `返回日视图`, the week label and week swiping usable when
            // the viewed week has no data; the screen supplies the paper
            // EmptyState. WeekAgenda owns the gesture in the data branch.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var dragAccum = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragAccum = 0f },
                            onHorizontalDrag = { change, amount ->
                                dragAccum += amount
                                change.consume()
                            },
                            onDragEnd = {
                                if (dragAccum < -140f) currentSwipe(1)
                                else if (dragAccum > 140f) currentSwipe(-1)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                emptyContent()
            }
        } else if (tableMode) {
            WeekTableCanvas(
                days = days,
                today = today,
                nowMinutes = nowMinutes,
                onCourseTap = onCourseTap,
                onSwipeWeek = onSwipeWeek,
                weekLabel = weekLabel,
                shareStyle = shareStyle,
                shareOwner = shareOwner,
                appVersion = appVersion,
                // The dialog's 版式 choice applies here too: 竖排 renders the
                // full-information row card, 横排 renders the seven-column grid.
                shareFormat = shareFormat,
                onShareOptions = { stylePickerOpen = true },
                // Fallback when the share card cannot be written.
                onShare = { text ->
                    runCatching {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "我的课表")
                        }
                        shareContext.startActivity(
                            android.content.Intent.createChooser(send, "分享课表")
                        )
                    }
                },
                rowHeight = if (fullscreen) 72.dp else 64.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var dragAccum = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragAccum = 0f },
                            onHorizontalDrag = { change, amount ->
                                dragAccum += amount
                                change.consume()
                            },
                            onDragEnd = {
                                if (dragAccum < -140f) currentSwipe(1)
                                else if (dragAccum > 140f) currentSwipe(-1)
                            }
                        )
                    }
            ) {
                WeekAgenda(
                    days = days,
                    today = today,
                    nowMinutes = nowMinutes,
                    fullscreen = fullscreen,
                    onCourseTap = onCourseTap,
                    modifier = Modifier.fillMaxSize()
                )
                ShareChip(
                    onClick = { shareAgenda() },
                    onLongClick = { stylePickerOpen = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}

/**
 * The canvas always needs seven dated columns. Server data normally provides
 * them; when only today's events exist, derive the Monday-Sunday dates around
 * the overview date and keep the known events under today (same rule as the
 * day view strip).
 */
private fun gridDays(overview: ApiScheduleOverview): List<ApiScheduleDay> {
    val provided = overview.days.associateBy { it.weekday }
    val reference = todayAnchor(overview.date) ?: todayAtSchoolClock()
    val monday = reference.with(DayOfWeek.MONDAY)
    return (1..7).map { weekday ->
        provided[weekday] ?: ApiScheduleDay(
            date = monday.plusDays((weekday - 1).toLong()).toString(),
            weekday = weekday,
            events = if (weekday == reference.dayOfWeek.value) overview.today else emptyList()
        )
    }
}


@Composable
internal fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalSuiSignColors.current
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.text else colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Compact header action on a plain clickable box. Material TextButton carries
 * a 58dp default MinWidth; six of them plus the week label overflowed the
 * inline header at large font scales.
 */
@Composable
private fun HeaderAction(text: String, onClick: () -> Unit) {
    val colors = LocalSuiSignColors.current
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.text,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * Sits next to the week label when the viewed week is not the current one, and
 * doubles as the way back. Warning tint because a stale week reads as "no class".
 */
@Composable
private fun OffWeekChip(delta: Int, onBack: (() -> Unit)?) {
    val colors = LocalSuiSignColors.current
    val label = when (delta) {
        -1 -> "上周"
        1 -> "下周"
        else -> if (delta < 0) "${-delta} 周前" else "$delta 周后"
    }
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .heightIn(min = 28.dp)
            .clip(CircleShape)
            .background(colors.warning.copy(alpha = 0.14f))
            .border(1.dp, colors.warning.copy(alpha = 0.4f), CircleShape)
            .then(if (onBack != null) Modifier.clickable(onClick = onBack) else Modifier)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (onBack != null) "$label · 回本周" else label,
            color = colors.warning,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * One quiet corner control: tap shares, long-press picks the signature style.
 * A single 30dp disc so it never competes with the timetable itself.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ShareChip(onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSuiSignColors.current
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(colors.surface.copy(alpha = 0.92f))
            .border(1.dp, colors.border, CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = "分享课表（长按选择水印）",
            tint = colors.textSecondary,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun ShareStyleDialog(
    current: ShareStyle,
    format: ShareFormat,
    onFormat: (ShareFormat) -> Unit,
    onPick: (ShareStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    SuiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享方式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("版式", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    ShareFormat.entries.forEach { option ->
                        Text(
                            text = option.label,
                            color = if (option == format) colors.signal else colors.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onFormat(option) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                Text("水印", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                ShareStyle.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onPick(option) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.label,
                            color = if (option == current) colors.signal else colors.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (option == current) {
                            Text("当前", color = colors.signal, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = colors.textSecondary) }
        }
    )
}
