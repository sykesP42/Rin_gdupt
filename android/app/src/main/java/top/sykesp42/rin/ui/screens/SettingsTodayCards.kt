package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.ui.today.TodayCardId
import top.sykesp42.rin.ui.today.TodayCards
import top.sykesp42.rin.ui.theme.LocalSuiSignColors

/** Every Today card can be switched off and moved without touching code. */
@Composable
internal fun SettingsTodayCardsContent(
    settings: AppSettings,
    onBack: () -> Unit,
    onToggle: (TodayCardId, Boolean) -> Unit,
    onMove: (TodayCardId, Int) -> Unit,
    onReset: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    val order = TodayCards.ordered(settings)
    val enabled = TodayCards.enabled(settings)
    SettingsSubpage(
        title = "今日卡片",
        subtitle = "开关、顺序与模块",
        onBack = onBack
    ) {
        item(key = "summary") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已开启 ${enabled.size} / ${order.size} 个卡片",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onReset) {
                    Text("恢复默认", color = colors.signal, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item(key = "hint") {
            Text(
                text = "签到消息固定在顶部；其余卡片可在此关闭或上下移动。",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        items(order, key = { "card-${it.key}" }) { card ->
            val index = order.indexOf(card)
            TodayCardSettingRow(
                card = card,
                enabled = TodayCards.isEnabled(settings, card),
                canUp = index > 0,
                canDown = index < order.lastIndex,
                onToggle = { onToggle(card, it) },
                onUp = { onMove(card, -1) },
                onDown = { onMove(card, 1) }
            )
        }
    }
}

@Composable
private fun TodayCardSettingRow(
    card: TodayCardId,
    enabled: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.label,
                    color = colors.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = card.description,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = canUp,
                onClick = onUp,
                modifier = Modifier.heightIn(min = 40.dp).clip(CircleShape)
                    .border(1.dp, colors.border, CircleShape)
            ) { Text("上移", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall) }
            TextButton(
                enabled = canDown,
                onClick = onDown,
                modifier = Modifier.padding(start = 8.dp).heightIn(min = 40.dp).clip(CircleShape)
                    .border(1.dp, colors.border, CircleShape)
            ) { Text("下移", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
