package top.sykesp42.rin.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.jwxt.JwxtLoginActivity
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.components.suiTextFieldColors
import top.sykesp42.rin.ui.theme.LocalRinColors

/**
 * 教务连接: read-only timetable source. The login itself happens on the
 * school's web page inside [JwxtLoginActivity]; this screen only stores the
 * encrypted cookie and shows the connection state.
 */
@Composable
internal fun SettingsJwxtContent(
    vm: AppViewModel,
    settings: AppSettings,
    onBack: () -> Unit
) {
    val colors = LocalRinColors.current
    val live by vm.settings.collectAsStateWithLifecycle()
    val current = live ?: settings
    val connected by vm.jwxtConnected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var draft by remember(current.jwxtBaseUrl) { mutableStateOf(current.jwxtBaseUrl) }

    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val cookie = result.data?.getStringExtra(JwxtLoginActivity.EXTRA_COOKIE).orEmpty()
        val base = result.data?.getStringExtra(JwxtLoginActivity.EXTRA_BASE_URL).orEmpty()
        if (cookie.isBlank()) {
            vm.notify("没有拿到登录凭据，请重试")
            return@rememberLauncherForActivityResult
        }
        if (base.isNotBlank() && base != current.jwxtBaseUrl) vm.setJwxtBaseUrl(base)
        vm.saveJwxtSession(cookie)
    }

    SettingsSubpage(
        title = "教务连接",
        subtitle = if (connected) "已连接 · 只读课表" else "未连接 · 仅读取课表",
        onBack = onBack
    ) {
        item(key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(
                    title = "状态",
                    meta = if (connected) "已连接" else "未连接"
                )
                Text(
                    text = if (connected) {
                        "课表来自教务系统，只读，不会提交任何签到或名单操作。"
                    } else {
                        "连接后自动读取本学期课表；登录在学校网页完成，本应用不保存密码。"
                    },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (connected && current.jwxtConnectedAt.isNotBlank()) {
                    Text(
                        text = "连接时间 ${current.jwxtConnectedAt.take(10)}",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        item(key = "address") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "教务地址", meta = "校内直连或 WebVPN")
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("教务地址") },
                    singleLine = true,
                    colors = suiTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                SuiSecondaryButton(
                    text = "保存地址",
                    onClick = { vm.setJwxtBaseUrl(draft) },
                    enabled = draft.trim() != current.jwxtBaseUrl,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = if (connected) "重新登录" else "登录")
                SuiPrimaryButton(
                    text = if (connected) "重新登录教务" else "登录教务",
                    onClick = {
                        runCatching {
                            login.launch(JwxtLoginActivity.intent(context, current.jwxtBaseUrl))
                        }.onFailure { vm.notify("打不开教务页面") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (connected) {
                    SuiSecondaryButton(
                        text = "断开连接",
                        onClick = { vm.disconnectJwxt() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item(key = "privacy") {
            Text(
                text = "隐私：教务登录在系统 WebView 中完成，本应用只保存加密后的会话凭据，" +
                    "不保存密码、不读取通讯录、不上传到任何服务器。断开连接会立即清除凭据。",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
