package top.sykesp42.rin.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.sykesp42.rin.ui.SignQrState
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.RinSheet
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.formatClock
import top.sykesp42.rin.ui.theme.LocalSuiSignColors

/** True once the effective school deadline has been reached. */
internal fun qrExpiredAt(effectiveEnd: java.time.Instant?, now: java.time.Instant): Boolean =
    effectiveEnd != null && !now.isBefore(effectiveEnd)

/** Always-present entry: a QR destination exists even while no sign is live. */
@Composable
internal fun TodayQrEntry(status: String, ready: Boolean, onOpen: () -> Unit) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("签到二维码", color = colors.text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(8.dp))
        Text(
            text = status,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (ready) "查看" else "刷新",
            color = colors.text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
internal fun TodayQrSheet(
    state: SignQrState,
    now: java.time.Instant,
    onRefresh: () -> Unit,
    onOpenWechat: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    // `now` is the server-anchored school clock from the page. Using the phone's
    // own clock here made the sheet hide a QR while the hero still counted down.
    val expired = qrExpiredAt(state.effectiveEnd, now)
    val latestExpired by rememberUpdatedState(expired)
    // Dynamic QR rotates on the school's own schedule; stop once the window is over.
    LaunchedEffect(state.type, state.refreshSeconds) {
        if (state.type == "DYNAMIC" && state.refreshSeconds > 0) {
            while (!latestExpired) {
                delay(state.refreshSeconds * 1000L)
                if (latestExpired) break
                onRefresh()
            }
        }
    }
    RinSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("签到二维码", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (state.type == "STATIC") "固定码" else "自动刷新",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(14.dp))
        val bitmap = remember(state.png) {
            state.png?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
        when {
            expired -> Text(
                text = "签到已结束，二维码已隐藏",
                color = colors.warning,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = "签到二维码",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )
            state.loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.signal,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("正在获取签到码…", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            else -> Text(
                text = state.error.ifBlank { "这次没取到签到码" },
                color = colors.warning,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = state.effectiveEnd?.let { "签到截止 ${formatClock(it)}" }
                ?: " ",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        if (state.error.isNotBlank() && bitmap != null && !expired) {
            Text(
                text = state.error,
                color = colors.warning,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Hairline()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bitmap != null && !expired) {
                SuiPrimaryButton(
                    text = "用微信签到",
                    onClick = onOpenWechat,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                SuiSecondaryButton(
                    text = "刷新",
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SuiPrimaryButton(
                    text = "重新获取",
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    loading = state.loading
                )
                Spacer(Modifier.width(10.dp))
                SuiSecondaryButton(
                    text = "用微信签到",
                    onClick = onOpenWechat,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = "到安全线后才会出现二维码",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
