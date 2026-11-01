package top.sykesp42.rin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import kotlin.math.max
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.domain.State
import top.sykesp42.rin.monitor.MonitorPolicy
import top.sykesp42.rin.monitor.MonitorView
import top.sykesp42.rin.monitor.MySignState
import top.sykesp42.rin.ui.TodayUiState
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.LocalCourseOverrides
import top.sykesp42.rin.ui.components.courseDisplayName
import top.sykesp42.rin.ui.components.RinSheet
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.StatusPill
import top.sykesp42.rin.ui.components.statusAccent
import top.sykesp42.rin.ui.schedule.ClassSlotState
import top.sykesp42.rin.ui.schedule.classSlotState
import top.sykesp42.rin.ui.schedule.courseCountLabel
import top.sykesp42.rin.ui.schedule.nextUpcomingIndex
import top.sykesp42.rin.ui.schedule.restHint
import top.sykesp42.rin.ui.schedule.schoolZone
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.formatCountdown
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.courseAccent

/**
 * Today hero: status line, serif countdown, course, data chips and policy copy.
 * It is an unwrapped paper section, never a card; the 晴空 wash behind it is painted
 * page-level by [PaperPage] so it bleeds to both edges and feathers into the paper.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayHero(
    today: TodayUiState,
    serverNow: Instant,
    monitorEnabled: Boolean,
    minimal: Boolean,
    onShowDetails: () -> Unit,
    onClearManual: () -> Unit,
    signPro: Boolean = true
) {
    val colors = LocalSuiSignColors.current
    val view = today.view
    val failed = view == null && today.errorMessage.isNotBlank()
    val accent = if (view == null) colors.textSecondary else statusAccent(view.state)
    val fontScale = LocalDensity.current.fontScale
    val ready = view?.let {
        it.state == State.READY && it.roomCode.isNotBlank() && it.signId.isNotBlank()
    } == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                view == null && failed -> FailedPill()
                view == null -> LoadingPill()
                else -> StatusPill(view.state)
            }
            Spacer(Modifier.weight(1f))
            if (view != null && !minimal) {
                TextButton(
                    onClick = onShowDetails,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        "详情",
                        color = colors.text,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        when {
            failed -> Unit
            view == null -> Text(
                "正在核对课堂和名单",
                color = colors.text.copy(alpha = 0.80f),
                style = MaterialTheme.typography.bodyMedium
            )
            else -> {
                TodayCountdown(view = view, serverNow = serverNow, accent = accent, fontScale = fontScale)
                Text(
                    text = when {
                        view.state == State.IDLE -> idleHeadline(view.recommendationStatus, view.courseName)
                        view.courseName.isBlank() -> "签到窗口"
                        else -> view.courseName
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    view.sections.takeIf { it.isNotBlank() },
                    view.location.takeIf { it.isNotBlank() },
                    view.teacherNames.takeIf { it.isNotEmpty() }?.joinToString("、")
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        color = colors.text.copy(alpha = 0.80f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TodayDataChips(view = view, minimal = minimal)
                if (!minimal && today.errorMessage.isBlank()) {
                    Text(
                        text = if (!signPro) {
                            if (ready) "可查看当前名单" else "当前仅提醒与名单"
                        } else {
                            heroHint(view, monitorEnabled, ready)
                        },
                        color = colors.text.copy(alpha = 0.80f),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (minimal && ready) {
                    Text(
                        "去微信完成最后一步",
                        color = colors.text.copy(alpha = 0.80f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (today.manualCode.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "手动教室 ${today.manualCode}",
                    color = colors.text,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onClearManual,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        "恢复自动",
                        color = colors.text,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
internal fun TodaySourceStrip(
    sessionExpired: Boolean,
    fromCache: Boolean,
    cachedAt: Instant?,
    rosterPending: Boolean
) {
    if (!sessionExpired && !fromCache && !rosterPending) return
    val colors = LocalSuiSignColors.current
    val warning = sessionExpired
    val dot = if (warning) colors.warning else colors.textSecondary
    val at = cachedAt?.let { formatClock(it, schoolZone) }
    val text = when {
        sessionExpired -> buildString {
            append("教务登录已过期")
            if (at != null) append(" · 显示 $at 的缓存")
        }
        fromCache -> if (at != null) "缓存 · 更新于 $at" else "显示的是缓存"
        else -> "实时数据"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.warm.copy(alpha = 0.9f))
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(5.dp).background(dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = colors.text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

internal data class PreflightItem(
    val label: String,
    val detail: String,
    val ok: Boolean
)

/** Pure four-item gate so the strip and its tests cannot drift apart. */
internal fun oneTapPreflightItems(
    view: MonitorView?,
    wechatInstalled: Boolean,
    sessionExpired: Boolean
): List<PreflightItem> {
    val (signed, total) = view?.displayCounts() ?: (0 to 0)
    val windowReady = !sessionExpired && view?.state == State.READY
    val windowText = when {
        sessionExpired -> "先重连"
        windowReady -> "进行中"
        view?.state == State.WAITING_THRESHOLD -> "人数不足"
        view?.state == State.NOT_STARTED -> "未开始"
        view == null -> "未读取"
        else -> "已结束"
    }
    val thresholdReady = !sessionExpired &&
        view != null &&
        view.signAccessAllowed &&
        view.threshold > 0 &&
        signed >= view.threshold
    val thresholdText = when {
        view == null -> "未读取"
        view.threshold <= 0 -> "已关闭"
        else -> "$signed/$total · 安全线 ${view.threshold}"
    }
    val selfPending = !sessionExpired &&
        view != null &&
        view.roster.me != MySignState.SIGNED &&
        view.state != State.CONFIRMED
    val selfText = when {
        view == null -> "未读取"
        view.roster.me == MySignState.SIGNED -> "已签到"
        view.state == State.CONFIRMED -> "已确认"
        else -> "未签到"
    }
    return listOf(
        PreflightItem("课堂窗口", windowText, windowReady),
        PreflightItem("人数", thresholdText, thresholdReady),
        PreflightItem("本人", selfText, selfPending),
        PreflightItem("微信", if (wechatInstalled) "可用" else "未安装", wechatInstalled)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OneTapPreflight(
    view: MonitorView?,
    wechatInstalled: Boolean,
    sessionExpired: Boolean
) {
    if (view == null && !sessionExpired) return
    val colors = LocalSuiSignColors.current
    val items = oneTapPreflightItems(view, wechatInstalled, sessionExpired)
    val count = items.count { it.ok }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "签到前检查",
                color = colors.text,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (sessionExpired) "等待重连" else "$count / 4",
                color = if (sessionExpired) colors.warning else colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEach { item ->
                PreflightChip(item.label, item.detail, item.ok)
            }
        }
    }
}

@Composable
private fun PreflightChip(label: String, detail: String, ok: Boolean) {
    val colors = LocalSuiSignColors.current
    val tint = if (ok) colors.signal else colors.warning
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(4.dp).background(tint, CircleShape))
        Text(
            "$label · $detail",
            color = colors.text,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun TodayCountdown(
    view: MonitorView,
    serverNow: Instant,
    accent: Color,
    fontScale: Float
) {
    val colors = LocalSuiSignColors.current
    val active = view.state == State.READY ||
        view.state == State.WAITING_THRESHOLD ||
        view.state == State.NOT_STARTED
    val text = when (view.state) {
        State.READY, State.WAITING_THRESHOLD, State.NOT_STARTED ->
            formatCountdown(view.effectiveSignEndAt, serverNow)
        State.CONFIRMED -> "已确认"
        State.EXPIRED -> "已结束"
        State.IDLE -> if (view.courseName.isNotBlank()) "等待签到" else "等待中"
        State.BLOCKED_NOT_IN_ROSTER -> "不在名单"
        State.BLOCKED_AMBIGUOUS_NAME -> "名单同名"
        State.BLOCKED -> "暂不可用"
    }
    val textSize = when {
        !active && text.length > 3 -> 34.sp
        text.length > 5 || fontScale > 1.3f -> 38.sp
        else -> 56.sp
    }
    val annotated = buildAnnotatedString {
        text.forEach { character ->
            if (character == ':') {
                withStyle(SpanStyle(color = accent.copy(alpha = 0.35f))) { append(character) }
            } else {
                append(character)
            }
        }
    }
    Text(
        text = annotated,
        color = if (active || view.state == State.CONFIRMED) accent else colors.textSecondary,
        style = MaterialTheme.typography.displayLarge.copy(fontSize = textSize),
        maxLines = 1,
        overflow = TextOverflow.Clip
    )
    if (active) {
        Text(
            text = when (view.state) {
                State.NOT_STARTED ->
                    "签到还没开始 · 预计 ${formatClock(view.effectiveSignEndAt ?: serverNow)} 结束"
                else -> "距签到结束"
            },
            color = colors.text.copy(alpha = 0.80f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayDataChips(view: MonitorView, minimal: Boolean) {
    val colors = LocalSuiSignColors.current
    val dark = colors.canvas.luminance() < 0.4f
    if (view.rosterCount <= 0 && view.threshold <= 0 && view.signedCount <= 0) return
    // The roster endpoint may return only a visible slice; signAccess carries
    // the counts the threshold decision actually used. Never show a
    // below-threshold chip next to a READY action because the list was partial.
    val (signed, total) = view.displayCounts()
    val reached = view.threshold > 0 && signed >= view.threshold
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DataChip(
                text = if (total > 0) "已签到 $signed / $total" else "已签到 $signed 人",
                background = colors.signal.copy(alpha = 0.10f),
                contentColor = colors.text
            )
            if (view.roster.me != MySignState.UNKNOWN) {
                val mine = view.roster.me == MySignState.SIGNED
                DataChip(
                    text = mySignLabel(view.roster.me),
                    background = (if (mine) colors.signal else colors.warning).copy(alpha = 0.10f),
                    contentColor = colors.text,
                    dotColor = if (mine) colors.signal else colors.warning
                )
            }
            DataChip(
                text = when {
                    view.threshold == 0 -> "自动跳转已关闭"
                    reached -> "已过安全线"
                    else -> "还差 ${max(0, view.threshold - signed)} 人到安全线"
                },
                background = colors.warm.copy(alpha = if (dark) 0.18f else 0.85f),
                contentColor = colors.text,
                dotColor = when {
                    view.threshold == 0 -> colors.warning
                    reached -> colors.signal
                    else -> colors.warning
                }
            )
        }
        if (!minimal && view.threshold > 0 && !reached) {
            Text(
                text = "人数不够时只提醒。",
                color = colors.text.copy(alpha = 0.80f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun DataChip(
    text: String,
    background: Color,
    contentColor: Color,
    dotColor: Color? = null
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dotColor != null) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(text = text, color = contentColor, style = MaterialTheme.typography.labelMedium)
    }
}

/** IDLE is not always "no sign-in": tell the room-code and empty-day cases apart. */
internal fun idleHeadline(recommendationStatus: String, courseName: String = ""): String = when {
    courseName.isNotBlank() -> "本节课暂无签到"
    recommendationStatus == "day_done" -> "今天的课已结束"
    recommendationStatus == "no_course_today" -> "今天没有课"
    recommendationStatus == "unknown_room" -> "教室码还没确认"
    recommendationStatus == "unavailable" -> "课表暂时拿不到"
    else -> "今天没有签到"
}

internal fun idleHint(recommendationStatus: String, courseName: String = ""): String = when {
    courseName.isNotBlank() -> "签到还没开始，到点提醒"
    recommendationStatus == "day_done" -> "有临时课可手动输码"
    recommendationStatus == "no_course_today" -> "有临时课可手动输码"
    recommendationStatus == "unknown_room" -> "去「校务」选一间教室"
    recommendationStatus == "unavailable" -> "下拉刷新可再试"
    else -> "到上课时间自动检查"
}

private fun heroHint(view: MonitorView, monitorEnabled: Boolean, ready: Boolean): String = when {
    ready -> "去微信完成最后一步"
    !monitorEnabled -> "到点才检查 · 默认只提醒"
    view.state == State.WAITING_THRESHOLD -> "未到安全线，只提醒"
    view.state == State.CONFIRMED -> "名单已确认，不用再操作。"
    view.state == State.BLOCKED_NOT_IN_ROSTER -> "名单里没你，去核对姓名"
    view.state == State.BLOCKED_AMBIGUOUS_NAME -> "名单里有同名，去网页端确认身份。"
    view.state == State.BLOCKED -> "签到状态没读到，稍后重试。"
    view.state == State.EXPIRED -> "这次签到已结束，等下一节。"
    view.state == State.IDLE -> idleHint(view.recommendationStatus, view.courseName)
    else -> "只读自己的账号"
}

/**
 * One paper error row: the page's only error surface. The hero never repeats
 * the raw error message.
 */
@Composable
internal fun TodayErrorRow(
    message: String,
    onRetry: () -> Unit,
    showAction: Boolean = true
) {
    val colors = LocalSuiSignColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.06f))
    ) {
        Hairline()
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = message,
                color = colors.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (showAction) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("重试", color = colors.text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Hairline()
    }
}

/**
 * Day timeline: a section header and hairline rows. Slot states are computed
 * live from [nowMinutes] (school clock), so a stale overview can never keep
 * "现在" pinned to the morning class; past rows are struck through and quiet
 * gaps between classes show a rest hint.
 */
@Composable
internal fun TodayTimeline(
    events: List<ApiScheduleEvent>,
    nowMinutes: Int,
    onCourseTap: (ApiScheduleEvent) -> Unit
) {
    val animations = LocalAnimationsEnabled.current
    val states = events.map { classSlotState(it.startTime, it.endTime, nowMinutes) }
    val nextIndex = nextUpcomingIndex(states)
    val hint = restHint(events.map { it.startTime to it.endTime }, nowMinutes)
    // Insert the rest hint after the last past row (before the first row when
    // the day has not started, after the last row when it is over).
    val restAfter = when (hint) {
        null -> -1
        "还没开课" -> -1
        "今天课上完了" -> events.lastIndex
        else -> states.indexOfLast { it == ClassSlotState.PAST }
    }
    Column {
        SectionHeader(title = "今日课程", meta = courseCountLabel(events.size))
        if (restAfter < 0 && hint != null) RestRow(hint)
        events.forEachIndexed { index, event ->
            val visible = remember(event.startTime, event.courseName) {
                MutableTransitionState(false).apply { targetState = true }
            }
            AnimatedVisibility(
                visibleState = visible,
                enter = if (animations) {
                    fadeIn(tween(240, delayMillis = index * 45)) +
                        slideInVertically(tween(240, delayMillis = index * 45)) { it / 5 }
                } else {
                    fadeIn(tween(0))
                }
            ) {
                TodayTimelineRow(
                    event = event,
                    slot = states[index],
                    isNext = index == nextIndex,
                    onCourseTap = { onCourseTap(event) }
                )
            }
            if (index == restAfter && hint != null) RestRow(hint)
        }
    }
}

/** A quiet gap line: centered, secondary, no interactive surface. */
@Composable
private fun RestRow(text: String) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)
    ) {
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(colors.textSecondary.copy(alpha = 0.5f))
        )
        Text(
            text = text,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun TodayTimelineRow(
    event: ApiScheduleEvent,
    slot: ClassSlotState,
    isNext: Boolean,
    onCourseTap: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    val dark = colors.canvas.luminance() < 0.4f
    val displayName = courseDisplayName(event.courseName, LocalCourseOverrides.current)
    val ink = courseAccent(displayName, dark)
    val isCurrent = slot == ClassSlotState.CURRENT
    val isPast = slot == ClassSlotState.PAST
    val wash = colors.signal.copy(alpha = 0.07f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(if (isCurrent) wash else Color.Transparent)
                .drawBehind {
                    if (isCurrent) {
                        drawRect(
                            color = colors.signal,
                            topLeft = Offset.Zero,
                            size = Size(2.dp.toPx(), size.height)
                        )
                    }
                }
                // Tap resolves the verified code and monitors the room; the
                // VM falls back with a hint when the room has no code yet.
                .clickable(onClick = onCourseTap)
                .padding(
                    start = if (isCurrent) 12.dp else 0.dp,
                    end = 0.dp,
                    top = 10.dp,
                    bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.widthIn(min = 50.dp)) {
                Text(
                    text = event.startTime,
                    color = if (isPast) colors.textSecondary else colors.text,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1
                )
                if (event.endTime.isNotBlank()) {
                    Text(
                        text = event.endTime,
                        color = if (isCurrent) {
                            colors.text.copy(alpha = 0.80f)
                        } else {
                            colors.textSecondary
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = if (isPast) colors.textSecondary else ink,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (isPast) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = listOfNotNull(
                    if (isCurrent) "现在" else if (isPast) "已上完" else if (isNext) "下一节" else null,
                    event.sectionText.takeIf { it.isNotBlank() },
                    event.location.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        color = if (isCurrent) {
                            colors.text.copy(alpha = 0.80f)
                        } else {
                            colors.textSecondary
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when {
                isCurrent -> Text(
                    "现在",
                    color = colors.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                isPast -> Text(
                    "已上完",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
                isNext -> Text(
                    "下一节",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Hairline()
    }
}

/** The single next/current class kept in minimal mode. */
@Composable
internal fun MinimalNextClass(event: ApiScheduleEvent) {
    val colors = LocalSuiSignColors.current
    val displayName = courseDisplayName(event.courseName, LocalCourseOverrides.current)
    val ink = courseAccent(displayName, colors.canvas.luminance() < 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = event.startTime,
            color = colors.text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(48.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                color = ink,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = listOf(event.location, event.sectionText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** One quiet footer line: server time / last check. */
@Composable
internal fun TodayFooter(
    running: Boolean,
    serverNow: Instant,
    lastCheckedAt: Instant?,
    monitorEnabled: Boolean
) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (running) colors.signal else colors.textSecondary)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = when {
                running -> "监控运行中 · 校园时间 ${formatClock(serverNow)}"
                monitorEnabled -> "监控已开，等上课 · 校园时间 ${formatClock(serverNow)}"
                lastCheckedAt != null -> "最近检查 ${formatClock(lastCheckedAt)}"
                else -> "等待首次检查"
            },
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** "Why" reasons as sheet rows, never as a persistent card. */
@Composable
internal fun TodayDetailsSheet(
    view: MonitorView,
    running: Boolean,
    manualCode: String,
    serverNow: Instant,
    lastCheckedAt: Instant?,
    onDismiss: () -> Unit,
    onClearManual: () -> Unit,
    onOpenSchedule: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    val interval = MonitorPolicy.intervalFor(view.state).inWholeSeconds
    val (signed, total) = view.displayCounts()
    val reached = view.threshold > 0 && signed >= view.threshold
    RinSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("详情", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (running) colors.signal else colors.textSecondary)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (running) "实时更新" else "上次结果",
                color = colors.text,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Hairline()
        TodayDetailRow(
            icon = Icons.Filled.DateRange,
            title = when (view.state) {
                State.READY, State.WAITING_THRESHOLD -> "签到已开始"
                State.NOT_STARTED -> "签到还没开始"
                State.EXPIRED -> "签到已结束"
                State.CONFIRMED -> "本次已确认"
                State.IDLE -> idleHeadline(view.recommendationStatus, view.courseName)
                State.BLOCKED_NOT_IN_ROSTER, State.BLOCKED_AMBIGUOUS_NAME -> "签到已开始"
                State.BLOCKED -> "状态没读到"
            },
            body = when (view.state) {
                State.READY, State.WAITING_THRESHOLD ->
                    "剩余 ${formatCountdown(view.effectiveSignEndAt, serverNow)}"
                State.NOT_STARTED ->
                    "预计 ${formatClock(view.effectiveSignEndAt ?: serverNow)} 结束"
                State.IDLE -> idleHint(view.recommendationStatus, view.courseName)
                State.BLOCKED_NOT_IN_ROSTER, State.BLOCKED_AMBIGUOUS_NAME ->
                    "校园时间 ${formatClock(serverNow)}"
                State.BLOCKED -> "稍后重试，或去网页端看看"
                else -> "校园时间 ${formatClock(serverNow)}"
            }
        )
        Hairline()
        TodayDetailRow(
            icon = Icons.Filled.Person,
            title = when {
                total > 0 -> "$signed / $total 人已签到"
                signed > 0 -> "$signed 人已签到"
                view.roster.known -> "${view.roster.signed} / ${view.roster.total} 人已签到"
                else -> "名单数据待更新"
            },
            body = buildString {
                if (view.roster.known) append(mySignLabel(view.roster.me)).append(" · ")
                append(
                    when {
                        view.threshold == 0 -> "安全线关闭，需要手动确认"
                        signed >= view.threshold -> "已到安全线 ${view.threshold} 人"
                        else -> "还差 ${max(0, view.threshold - signed)} 人到安全线 ${view.threshold}"
                    }
                )
            }
        )
        Hairline()
        TodayDetailRow(
            icon = Icons.Filled.Check,
            title = if (view.threshold == 0) "自动跳转已关闭" else "安全线 ${view.threshold}",
            body = when {
                view.threshold == 0 -> "每次手动打开入口"
                reached -> "已过安全线，可以去签到"
                else -> "不够只提醒，不自动跳转"
            }
        )
        Hairline()
        TodayDetailRow(
            icon = Icons.Filled.Lock,
            title = when (view.state) {
                State.CONFIRMED -> "名单已确认本人签到"
                State.BLOCKED_NOT_IN_ROSTER -> "本人不在本次名单"
                State.BLOCKED_AMBIGUOUS_NAME -> "名单有同名，需人工确认"
                else -> "最后一步在微信完成"
            },
            body = when (view.state) {
                State.CONFIRMED -> "名单已确认本人"
                State.BLOCKED_NOT_IN_ROSTER -> "名单里没有你"
                State.BLOCKED_AMBIGUOUS_NAME -> "去网页端确认身份"
                else -> "扫码后名单会自动更新"
            }
        )
        if (manualCode.isNotBlank()) {
            Hairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "手动教室 $manualCode",
                    color = colors.text,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onClearManual,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("恢复自动", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            text = when {
                running -> "每 $interval 秒检查一次 · 校园时间 ${formatClock(serverNow)}"
                lastCheckedAt != null -> "上次检查 ${formatClock(lastCheckedAt)}"
                else -> "等待首次检查"
            },
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 10.dp)
        )
        TextButton(
            onClick = onOpenSchedule,
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text("查看完整课表", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TodayDetailRow(icon: ImageVector, title: String, body: String) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.signal,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(17.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LoadingPill() {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.text.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(10.dp),
            strokeWidth = 1.5.dp,
            color = colors.textSecondary
        )
        Spacer(Modifier.width(6.dp))
        Text("正在读取", color = colors.text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FailedPill() {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.warning.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.warning)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "读取失败",
            color = colors.text,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
