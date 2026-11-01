package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.format.DateTimeFormatter
import top.sykesp42.rin.data.ApiScheduleEvent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.remember
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.schedule.ClassSlotState
import top.sykesp42.rin.ui.schedule.classSlotState
import top.sykesp42.rin.ui.schedule.courseCountLabel
import top.sykesp42.rin.ui.schedule.nextUpcomingIndex
import top.sykesp42.rin.ui.schedule.restHint
import top.sykesp42.rin.ui.components.Hairline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import top.sykesp42.rin.ui.components.LocalCourseOverrides
import top.sykesp42.rin.ui.components.courseDisplayName
import top.sykesp42.rin.ui.theme.courseAccent
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.schedule.clockToMinutes
import top.sykesp42.rin.ui.schedule.schoolZone
import top.sykesp42.rin.ui.theme.LocalSuiSignColors


@Composable
internal fun TodaySourceStrip(
    sessionExpired: Boolean,
    fromCache: Boolean,
    cachedAt: Instant?,
) {
    if (!sessionExpired && !fromCache) return
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
        else -> "实时数据 · 名单还没加载"
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
