package top.sykesp42.rin.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.User
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.Identity
import top.sykesp42.rin.ui.components.Avatar
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.ImageFramingEditor
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.profile.Profile
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.components.suiTextFieldColors

internal fun profileSummary(settings: AppSettings, user: User): String {
    val real = user.displayName.ifBlank { user.username }
    val name = Identity.displayName(settings, real)
    return if (settings.hidePersonal) "$name · 已隐藏身份" else name
}

/**
 * 个人资料: the name and avatar the app is allowed to show, plus the privacy
 * switch that hides the real name and account id everywhere.
 */
@Composable
internal fun SettingsProfileContent(
    vm: AppViewModel,
    settings: AppSettings,
    user: User,
    onBack: () -> Unit
) {
    val colors = LocalRinColors.current
    val context = LocalContext.current
    val realName = user.displayName.ifBlank { user.username }
    val effective = Identity.displayName(settings, realName)
    var draft by remember(settings.profileName) { mutableStateOf(settings.profileName) }
    var editorOpen by remember { mutableStateOf(false) }
    var pickNonce by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = Profile.install(context, uri)
        if (name != null) {
            vm.setAvatar(name)
            pickNonce++
            editorOpen = true
        } else {
            vm.notify("头像读取失败，换一张试试")
        }
    }
    fun pickAvatar() {
        runCatching {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }.onFailure { vm.notify("这台设备没有可用的图片选择器") }
    }
    val avatarBitmap = remember(settings.avatarImage, pickNonce, editorOpen) {
        settings.avatarImage.takeIf { it.isNotBlank() }
            ?.let { Profile.decode(context, it) }
    }

    SettingsSubpage(title = "个人资料", subtitle = "头像、称呼与隐私", onBack = onBack) {
        item(key = "avatar") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "头像", meta = if (settings.avatarImage.isBlank()) "默认" else "本机图片")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Avatar(effective, size = 56.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuiSecondaryButton(
                            text = if (settings.avatarImage.isBlank()) "选择图片" else "更换图片",
                            onClick = { pickAvatar() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (settings.avatarImage.isNotBlank()) {
                            SuiSecondaryButton(
                                text = "调整构图",
                                onClick = { editorOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                            SuiSecondaryButton(
                                text = "使用默认头像",
                                onClick = {
                                    Profile.clear(context)
                                    vm.setAvatar("")
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                if (settings.avatarImage.isNotBlank()) {
                    Text(
                        text = "预览按圆形显示，点「调整构图」可拖动和缩放。",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item(key = "name") {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "用户名", meta = effective)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(16) },
                    label = { Text("显示用名") },
                    supportingText = { Text("空着就沿用原称呼") },
                    singleLine = true,
                    colors = suiTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                SuiPrimaryButton(
                    text = "保存",
                    onClick = { vm.setProfileName(draft) },
                    enabled = draft.trim() != settings.profileName,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item(key = "privacy") {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                SectionHeader(title = "隐私")
                PaperSwitchRow(
                    title = "隐藏个人信息",
                    description = "真名与个人信息不再出现在界面和分享里",
                    checked = settings.hidePersonal,
                    onCheckedChange = vm::setHidePersonal,
                    contentDescription = "隐藏个人信息",
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (settings.hidePersonal && settings.profileName.isBlank()) {
                    Text(
                        text = "未设置用户名，将使用化名「$effective」",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (editorOpen && avatarBitmap != null) {
        ImageFramingEditor(
            bitmap = avatarBitmap,
            title = "调整头像",
            aspectRatio = 1f,
            circular = true,
            initialScale = settings.avatarScale,
            initialOffsetX = settings.avatarOffsetX,
            initialOffsetY = settings.avatarOffsetY,
            onDone = { scale, x, y -> vm.setAvatarFrame(scale, x, y) },
            onDismiss = { editorOpen = false },
            onPickAgain = { pickAvatar() }
        )
    }
}
