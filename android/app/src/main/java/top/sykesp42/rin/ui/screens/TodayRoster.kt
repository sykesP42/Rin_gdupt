package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import top.sykesp42.rin.data.RosterSnapshot
import top.sykesp42.rin.monitor.MonitorRoster
import top.sykesp42.rin.monitor.MonitorView
import top.sykesp42.rin.monitor.MySignState
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.RinSheet
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.components.ThresholdBar
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.theme.LocalSuiSignColors

/** One-line state for the signed-in student, used on the card and in details. */
internal fun mySignLabel(me: MySignState): String = when (me) {
    MySignState.SIGNED -> "本人已签到"
    MySignState.UNSIGNED -> "本人未签到"
    MySignState.NOT_IN_ROSTER -> "本人不在名单"
    MySignState.AMBIGUOUS -> "名单有同名"
    MySignState.UNKNOWN -> "名单尚未返回"
}

internal fun mySignHint(me: MySignState, canAct: Boolean): String = when (me) {
    MySignState.SIGNED -> "本人已签到"
    MySignState.UNSIGNED ->
        if (canAct) "去微信完成签到"
        else "还没记录到你"
    MySignState.NOT_IN_ROSTER -> "名单里没有你，去网页端核对姓名。"
    MySignState.AMBIGUOUS -> "名单里有同名，去网页端确认身份。"
    MySignState.UNKNOWN -> "签到开始后会自动读取名单。"
}

internal fun rosterCountsLine(roster: MonitorRoster, authoritativeTotal: Int = 0): String {
    if (!roster.known) return "名单尚未返回"
    val returned = roster.signed + roster.unsigned
    if (authoritativeTotal > returned) {
        // Partial slices only know this user, so their 0/1 split reads like a
        // full-roster result and contradicts the authoritative 12/40 above.
        val teachers = if (roster.teachers > 0) " · 教师 ${roster.teachers} 人不计" else ""
        return "名单只显示 $returned 人（共 $authoritativeTotal 人）$teachers"
    }
    val parts = mutableListOf("已签 ${roster.signed}", "未签 ${roster.unsigned}")
    if (roster.teachers > 0) parts += "教师 ${roster.teachers} 人不计"
    return "名单里 $returned 人 · " + parts.joinToString(" · ")
}

/**
 * signAccess carries the counts the threshold decision actually used; the
 * roster endpoint may only return a visible slice (or just the current user).
 * Prefer the authoritative counts, fall back to the returned list.
 */
internal fun MonitorView.displayCounts(): Pair<Int, Int> {
    val total = rosterCount.takeIf { it > 0 } ?: roster.total
    val signed = if (rosterCount > 0 || signedCount > 0) signedCount else roster.signed
    return signed to total
}

/**
 * Compact roster receipt under the hero. It deliberately shows counts and the
 * signed-in student's own status only: classmates' names are never rendered.
 */
@Composable
internal fun TodayRosterCard(
    view: MonitorView,
    lastCheckedAt: Instant?,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    val roster = view.roster
    val accent = when (roster.me) {
        MySignState.SIGNED -> colors.signal
        MySignState.UNSIGNED -> colors.warning
        else -> colors.textSecondary
    }
    val (signed, total) = view.displayCounts()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "签到名单",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = lastCheckedAt?.let { "更新 ${formatClock(it)}" } ?: "",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            RefreshTextButton(onRefresh)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (total > 0 || signed > 0 || roster.known) "$signed" else "—",
                color = colors.text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (total > 0) " / $total 已签" else " 已签",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = mySignLabel(roster.me),
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (total > 0) {
            Spacer(Modifier.height(8.dp))
            ThresholdBar(
                signed = signed,
                roster = total,
                threshold = view.threshold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = rosterCountsLine(roster, total),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Last persisted receipt, shown when no live roster is available (cold start,
 * session expired, school unreachable). Clearly labelled as a cache.
 */
@Composable
internal fun TodayCachedRosterCard(
    snapshot: RosterSnapshot,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    val me = snapshot.myState
    val roster = MonitorRoster(
        signed = snapshot.signed,
        unsigned = snapshot.unsigned,
        teachers = snapshot.teachers,
        me = me
    )
    val accent = when (me) {
        MySignState.SIGNED -> colors.signal
        MySignState.UNSIGNED -> colors.warning
        else -> colors.textSecondary
    }
    val savedAt = runCatching { Instant.parse(snapshot.savedAt) }.getOrNull()
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "上次签到名单",
                color = colors.text,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "缓存",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = savedAt?.let { formatClock(it) } ?: "",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            RefreshTextButton(onRefresh)
        }
        if (snapshot.courseName.isNotBlank()) {
            Text(
                text = snapshot.courseName,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${snapshot.signed}",
                color = colors.text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (roster.total > 0) " / ${roster.total} 已签" else " 已签",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = mySignLabel(me),
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (roster.total > 0) {
            Spacer(Modifier.height(8.dp))
            ThresholdBar(
                signed = roster.signed,
                roster = roster.total,
                threshold = snapshot.threshold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = rosterCountsLine(roster),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun RosterListSection(
    title: String,
    rows: List<top.sykesp42.rin.monitor.RosterEntry>,
    signed: Boolean
) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = colors.text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${rows.size} 人",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
    if (rows.isEmpty()) {
        Text(
            text = "暂无",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }
    val visible = rows.take(50)
    visible.forEach { entry ->
        val ink = if (entry.isMe) colors.text else colors.textSecondary
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.name,
                color = ink,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (entry.isMe) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
            if (entry.isMe) {
                Spacer(Modifier.width(5.dp))
                Text("我", color = colors.signal, style = MaterialTheme.typography.labelSmall)
            }
            if (entry.isTeacher) {
                Spacer(Modifier.width(4.dp))
                Text("（教师）", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
            if (signed) {
                Text(
                    text = listOfNotNull(
                        entry.signedAt?.let { formatClock(it) },
                        when {
                            entry.signModeLabel.isNotBlank() && entry.isLate ->
                                "${entry.signModeLabel}（迟到）"
                            entry.signModeLabel.isNotBlank() -> entry.signModeLabel
                            entry.isLate -> "迟到"
                            else -> null
                        }
                    ).joinToString(" · ").ifBlank { "—" },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            } else {
                Text("—", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (rows.size > visible.size) {
        Text(
            text = "还有 ${rows.size - visible.size} 人未展开",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 48dp tap target: the card itself opens details, this refreshes in place. */
@Composable
private fun RefreshTextButton(onRefresh: () -> Unit) {
    val colors = LocalSuiSignColors.current
    TextButton(
        onClick = onRefresh,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(
            text = "刷新",
            color = colors.text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Roster receipt sheet. Counts + own status only; no classmate names.
 */
@Composable
internal fun TodayRosterSheet(
    view: MonitorView,
    lastCheckedAt: Instant?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    val roster = view.roster
    val (signed, total) = view.displayCounts()
    RinSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("签到名单", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(
                text = view.courseName.ifBlank { "本节课" },
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        when (roster.me) {
                            MySignState.SIGNED -> colors.signal
                            MySignState.UNSIGNED -> colors.warning
                            else -> colors.textSecondary
                        }
                    )
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = mySignLabel(roster.me),
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = mySignHint(roster.me, view.canAct),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        Hairline()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (total > 0 || signed > 0 || roster.known) "$signed" else "—",
                color = colors.text,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (total > 0) " / $total 人已签到" else " 人已签到",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (total > 0) {
            ThresholdBar(
                signed = signed,
                roster = total,
                threshold = view.threshold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Text(
            text = rosterCountsLine(roster, total),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = if (view.threshold > 0) {
                "安全线 ${view.threshold} 人 · 不够只提醒"
            } else {
                "安全线已关闭 · 每次手动"
            },
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 3.dp)
        )
        if (view.rosterPeople.known) {
            Hairline()
            Spacer(Modifier.height(10.dp))
            RosterListSection("已签到", view.rosterPeople.signed, signed = true)
            RosterListSection("未签到", view.rosterPeople.unsigned, signed = false)
            if (view.rosterPeople.excluded > 0) {
                Text(
                    text = "其他状态 ${view.rosterPeople.excluded} 人",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else if (view.roster.known) {
            Hairline()
            Text(
                text = "名单未加载",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lastCheckedAt?.let { "最近更新 ${formatClock(it)}" } ?: "还没读取过",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.weight(1f))
            SuiSecondaryButton(text = "刷新名单", onClick = onRefresh)
        }
        Hairline()
        Text(
            text = "已脱敏 · 仅本机显示",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
