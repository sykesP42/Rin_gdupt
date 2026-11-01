package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.ui.faces.NoticeFaces
import top.sykesp42.rin.ui.faces.NoticeIcon
import top.sykesp42.rin.ui.faces.NoticeOutfit
import top.sykesp42.rin.ui.faces.NoticePlate
import top.sykesp42.rin.ui.faces.NoticeSlot
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.RinFace
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.SuiAlertDialog
import top.sykesp42.rin.ui.theme.LocalRinColors

/** Home card line: the outfit plus how many slots the user has remapped. */
internal fun noticeFacesSummary(settings: AppSettings): String {
    val changed = NoticeFaces.parse(settings.noticeOverrides).size
    val outfit = NoticeOutfit.from(settings.noticeOutfit).label
    return if (changed == 0) "$outfit · 全部默认" else "$outfit · $changed 项已改"
}

@Composable
internal fun SettingsFacesContent(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val colors = LocalRinColors.current
    val prefs by NoticeFaces.prefs.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<NoticeSlot?>(null) }

    SettingsSubpage(title = "Rin 表情", subtitle = "套装、圆底与状态替换", onBack = onBack) {
        item(key = "outfit") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "服装套装", meta = prefs.outfit.label)
                NoticeOutfit.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { outfit ->
                            OutfitChoiceCard(
                                outfit = outfit,
                                selected = outfit == prefs.outfit,
                                onClick = { vm.setNoticeOutfit(outfit.key) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                            }
        }
        item(key = "plate") {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SectionHeader(title = "圆底", meta = prefs.plate.label)
                NoticePlate.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { plate ->
                            PlateChip(
                                plate = plate,
                                selected = plate == prefs.plate,
                                onClick = { vm.setNoticePlate(plate.key) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        item(key = "scope") {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                SectionHeader(title = "用在哪")
                PaperSwitchRow(
                    title = "通知栏表情",
                    description = "通知右侧显示表情",
                    checked = prefs.inShade,
                    onCheckedChange = vm::setNoticeInShade,
                    contentDescription = "通知栏表情",
                    modifier = Modifier.padding(top = 8.dp)
                )
                Hairline()
                PaperSwitchRow(
                    title = "App 内表情",
                    description = "状态标签里显示表情",
                    checked = prefs.inApp,
                    onCheckedChange = vm::setNoticeInApp,
                    contentDescription = "App 内表情",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        item(key = "slots") {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SectionHeader(
                    title = "逐个状态换表情",
                    meta = if (prefs.overrides.isEmpty()) "全部默认" else "${prefs.overrides.size} 项已改"
                )
                NoticeSlot.entries.forEach { slot ->
                    FaceSlotRow(slot = slot, face = NoticeFaces.face(slot), onClick = { picking = slot })
                }
            }
        }
    }

    picking?.let { slot ->
        FacePickerDialog(
            slot = slot,
            onDismiss = { picking = null },
            onPick = { face ->
                vm.setNoticeSlotFace(slot, face)
                picking = null
            },
            onReset = {
                vm.setNoticeSlotFace(slot, null)
                picking = null
            }
        )
    }
}

@Composable
private fun OutfitChoiceCard(
    outfit: NoticeOutfit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.surfaceRaised else colors.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.signal else colors.border,
                shape = MaterialTheme.shapes.medium
            )
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(NoticeIcon.FOCUS, NoticeIcon.HAPPY, NoticeIcon.PANIC).forEach { icon ->
                RinFace(icon = icon, size = 26.dp, outfit = outfit)
            }
        }
        Text(
            text = outfit.label,
            color = if (selected) colors.signal else colors.text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PlateChip(
    plate: NoticePlate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    Box(
        modifier
            .height(40.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.signal.copy(alpha = 0.10f) else colors.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.signal else colors.border,
                shape = MaterialTheme.shapes.medium
            )
            .selectable(selected = selected, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = plate.label,
            color = if (selected) colors.signal else colors.text,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun FaceSlotRow(
    slot: NoticeSlot,
    face: NoticeIcon,
    onClick: () -> Unit
) {
    val colors = LocalRinColors.current
    val changed = face != slot.default
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RinFace(face, size = 28.dp)
        Column(Modifier.weight(1f)) {
            Text(slot.label, color = colors.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (changed) "默认「${slot.default.label}」· 现用「${face.label}」" else face.label,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (changed) {
            Text("已改", color = colors.signal, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FacePickerDialog(
    slot: NoticeSlot,
    onDismiss: () -> Unit,
    onPick: (NoticeIcon) -> Unit,
    onReset: () -> Unit
) {
    val colors = LocalRinColors.current
    val current = NoticeFaces.face(slot)
    SuiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「${slot.label}」用哪个表情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "当前：${current.label}",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                NoticeIcon.entries.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { icon ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .clickable { onPick(icon) }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                RinFace(
                                    icon = icon,
                                    size = 34.dp,
                                    ring = if (icon == current) colors.signal else null
                                )
                                Text(
                                    text = icon.label,
                                    color = if (icon == current) colors.signal else colors.textSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onReset, enabled = current != slot.default) {
                Text(
                    text = "恢复默认",
                    color = if (current != slot.default) colors.signal else colors.textSecondary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = colors.textSecondary) }
        }
    )
}
