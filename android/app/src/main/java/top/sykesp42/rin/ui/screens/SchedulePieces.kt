package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.schedule.campusZone
import top.sykesp42.rin.ui.theme.LocalRinColors

/*
 * Small paper pieces of the schedule page, ported from the full client so both
 * builds share one layout language: the week navigation row, the loading
 * surface, the cache footnote and the shared helpers.
 */

/** ‹ 第 N 周 › plus the 日 / 周 / 表 / 学期 entries. */
@Composable
internal fun WeekNavigation(
    week: Int,
    termView: Boolean,
    showReturn: Boolean,
    showWeekAction: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReturn: () -> Unit,
    onToggleTerm: () -> Unit,
    onOpenWeek: () -> Unit,
    onOpenTable: () -> Unit
) {
    val colors = LocalRinColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!termView) {
                TextButton(
                    onClick = onPrev,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "上一周" },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = "‹",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            Text(
                text = "第 $week 周",
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showReturn) {
                ReturnToCurrentCapsule(onClick = onReturn)
            }
            if (!termView) {
                TextButton(
                    onClick = onNext,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "下一周" },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = "›",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onToggleTerm,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (termView) "返回课表" else "学期",
                    color = colors.text,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (!termView) {
                ModeTab("日", selected = true, onClick = {})
                if (showWeekAction) {
                    ModeTab("周", selected = false, onClick = onOpenWeek)
                    ModeTab("表", selected = false, onClick = onOpenTable)
                }
            }
        }
    }
}

@Composable
private fun ReturnToCurrentCapsule(onClick: () -> Unit) {
    val colors = LocalRinColors.current
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "回到本周",
            color = colors.text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(colors.accent.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = 11.dp, vertical = 5.dp)
        )
    }
}

@Composable
internal fun ScheduleLoadingSurface() {
    val colors = LocalRinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = colors.signal
        )
        Text(
            text = "正在读取课表…",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

internal fun hasScheduleData(overview: ApiScheduleOverview): Boolean =
    overview.days.isNotEmpty() ||
        overview.today.isNotEmpty() ||
        overview.current != null ||
        overview.next != null

internal fun cachedLabel(fromCache: Boolean, cachedAt: String, needsLogin: Boolean = false): String {
    if (!fromCache) return ""
    val at = cachedAt.takeIf { it.isNotBlank() }
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?.let { formatClock(it, campusZone) }
    val base = if (at != null) "缓存 · $at" else "缓存数据"
    return if (needsLogin) "教务会话已过期 · $base" else base
}

@Composable
internal fun ScheduleFoot(fromCache: Boolean, cachedAt: String, needsLogin: Boolean = false) {
    val colors = LocalRinColors.current
    val label = cachedLabel(fromCache, cachedAt, needsLogin)
    if (label.isBlank()) return
    Text(
        text = label,
        color = colors.textSecondary,
        style = MaterialTheme.typography.labelSmall
    )
}

/** Warning row used for a failed read or an expired session, with one retry. */
@Composable
internal fun ScheduleErrorRow(message: String, onRetry: () -> Unit, actionLabel: String = "重试") {
    val colors = LocalRinColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.06f))
    ) {
        Hairline()
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
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
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = colors.text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Hairline()
    }
}
