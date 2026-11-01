package top.sykesp42.rin.handoff

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.sykesp42.rin.MainActivity
import top.sykesp42.rin.data.ApiError
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.data.HistoryKind
import top.sykesp42.rin.domain.SignUrl
import top.sykesp42.rin.domain.decodeQrPng
import top.sykesp42.rin.monitor.MonitorService
import top.sykesp42.rin.monitor.REASON_CONFIRM
import top.sykesp42.rin.ui.ErrorCopy
import top.sykesp42.rin.ui.components.BrandMark
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.SuiSignTheme
import top.sykesp42.rin.ui.theme.ThemeMode

/**
 * User-visible launch pad for the WeChat handoff. The notification action and
 * the Today button start this Activity instead of calling startActivity from a
 * background receiver, which Android 10+ may silently block.
 */
class HandoffActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = AppContainer.get(this)
        val activity = this
        setContent {
            val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val mode = ThemeMode.from(settings.theme)
            val customTheme = remember(settings) {
                top.sykesp42.rin.ui.theme.CustomThemeState(
                    enabled = settings.customThemeEnabled,
                    imageFileName = settings.customThemeImage,
                    seed = top.sykesp42.rin.ui.theme.CustomThemePalette.decode(settings.customThemeSeed),
                    focus = top.sykesp42.rin.ui.theme.CustomThemeFocus.from(settings.customThemeFocus).value,
                    colorMode = top.sykesp42.rin.ui.theme.CustomThemeColorMode.from(settings.customThemePalette),
                    scale = settings.customThemeScale,
                    offsetX = settings.customThemeOffsetX,
                    offsetY = settings.customThemeOffsetY,
                    blur = settings.customThemeBlur
                )
            }
            val appearance = remember(
                mode,
                customTheme,
                settings.themeAccent,
                settings.themeCorners,
                settings.themeDecorations
            ) {
                top.sykesp42.rin.ui.theme.resolveAppearance(
                    mode = mode,
                    accentOverride = settings.themeAccent.ifBlank { null },
                    corners = top.sykesp42.rin.ui.theme.ThemeCorners.from(settings.themeCorners),
                    decorations = settings.themeDecorations,
                    customTheme = customTheme
                )
            }
            val dark = appearance.dark
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            SuiSignTheme(
                mode = mode,
                animationsEnabled = settings.animationsEnabled,
                accentOverride = settings.themeAccent,
                corners = top.sykesp42.rin.ui.theme.ThemeCorners.from(settings.themeCorners),
                decorationsEnabled = settings.themeDecorations,
                customTheme = customTheme
            ) {
                HandoffScreen(
                    container = container,
                    roomCode = intent.getStringExtra(EXTRA_ROOM_CODE).orEmpty(),
                    signId = intent.getStringExtra(EXTRA_SIGN_ID).orEmpty(),
                    courseName = intent.getStringExtra(EXTRA_COURSE_NAME).orEmpty(),
                    onClose = { finish() },
                    onOpenApp = {
                        startActivity(Intent(activity, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ROOM_CODE = "room_code"
        const val EXTRA_SIGN_ID = "sign_id"
        const val EXTRA_COURSE_NAME = "course_name"

        fun intent(context: Context, roomCode: String, signId: String, courseName: String): Intent =
            Intent(context, HandoffActivity::class.java)
                .putExtra(EXTRA_ROOM_CODE, roomCode)
                .putExtra(EXTRA_SIGN_ID, signId)
                .putExtra(EXTRA_COURSE_NAME, courseName)
    }
}

private sealed interface HandoffState {
    data object Working : HandoffState
    data class Failed(
        val title: String,
        val message: String,
        val needsApp: Boolean = false
    ) : HandoffState
}

@Composable
private fun HandoffScreen(
    container: AppContainer,
    roomCode: String,
    signId: String,
    courseName: String,
    onClose: () -> Unit,
    onOpenApp: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalSuiSignColors.current
    val scope = rememberCoroutineScope()
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    val controller = remember { HandoffController(context) }
    var state by remember { mutableStateOf<HandoffState>(HandoffState.Working) }
    var url by remember { mutableStateOf("") }
    var canWeCom by remember { mutableStateOf(false) }
    var canShareWeChat by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var noteIsFailure by remember { mutableStateOf(true) }

    fun setNote(text: String, failure: Boolean = true) {
        note = text
        noteIsFailure = failure
    }

    suspend fun recordFailure() {
        runCatching {
            container.history.append(HistoryKind.HANDOFF_FAILED, courseName, roomCode, "FAILED")
        }
    }

    suspend fun beginConfirmation(kind: String, mode: String) {
        val pending = pendingConfirmation(roomCode, signId, courseName, Instant.now())
        if (pending == null) {
            state = HandoffState.Failed("链接信息不完整", "回Rin重新发起签到。", needsApp = true)
            recordFailure()
            return
        }
        HandoffRuntime.begin(pending)
        // Survive a process kill during the 60s window; the service (or the
        // next app launch) resumes from this record.
        container.handoffJournal.save(pending)
        runCatching { container.settings.setLastHandledSignId(pending.signId) }
        runCatching { container.history.append(kind, courseName, roomCode, mode) }
        runCatching { MonitorService.start(context.applicationContext, REASON_CONFIRM) }
        onClose()
    }

    suspend fun completeHandoff() = beginConfirmation(HistoryKind.HANDOFF_OPENED, "READY")

    LaunchedEffect(Unit) {
        if (roomCode.isBlank() || signId.isBlank()) {
            state = HandoffState.Failed("链接信息不完整", "回Rin重新发起签到。", needsApp = true)
            return@LaunchedEffect
        }
        if (!container.signAccess.proEnabled()) {
            state = HandoffState.Failed(
                "当前仅支持提醒与名单",
                "完整功能未启用，回 Rin 查看当前名单。",
                needsApp = true
            )
            return@LaunchedEffect
        }
        val currentSettings = container.settings.current()
        val monitor = container.monitorApi(currentSettings)
        if (monitor == null) {
            state = HandoffState.Failed("签到接口不可用", "回Rin刷新后重试。", needsApp = true)
            recordFailure()
            return@LaunchedEffect
        }
        val liveCourse = try {
            withContext(Dispatchers.IO) { monitor.course(roomCode) }
        } catch (error: ApiError) {
            recordFailure()
            state = HandoffState.Failed("读不到当前课堂", ErrorCopy.forCode(error.code, error.message))
            return@LaunchedEffect
        } catch (error: Exception) {
            recordFailure()
            state = HandoffState.Failed("读不到当前课堂", ErrorCopy.from(error))
            return@LaunchedEffect
        }
        val targetCheck = checkHandoffTarget(liveCourse, signId)
        if (targetCheck is HandoffTargetCheck.Rejected) {
            recordFailure()
            state = HandoffState.Failed(targetCheck.title, targetCheck.message, needsApp = true)
            return@LaunchedEffect
        }
        val raw: String = if (currentSettings.localMode) {
            var localFailure: HandoffState.Failed? = null
            val png = try {
                withContext(Dispatchers.IO) { monitor.qrPng(roomCode) }
            } catch (error: ApiError) {
                localFailure = HandoffState.Failed("拿不到签到码", ErrorCopy.forCode(error.code, error.message))
                null
            } catch (error: Exception) {
                localFailure = HandoffState.Failed("拿不到签到码", ErrorCopy.from(error))
                null
            }
            var decoded: String? = null
            if (png == null) {
                if (localFailure == null) {
                    localFailure = HandoffState.Failed(
                        "拿不到签到码",
                        "人数没到安全线，或签到已结束。回Rin刷新后再试。"
                    )
                }
            } else {
                decoded = decodeQrPng(png)
                if (decoded == null) {
                    localFailure = HandoffState.Failed("签到码无法解析", "回Rin刷新后重试。", needsApp = true)
                }
            }
            // 本地拿不到码时，云端保底站点直接铸造反代链接，同一轮不再失败。
            decoded ?: run {
                val fallback = if (currentSettings.serverUrl.isNotBlank() && container.sessionStore.load() != null) {
                    val api = container.client(currentSettings.serverUrl)
                    runCatching {
                        withContext(Dispatchers.IO) { api?.signURL(roomCode) }
                    }.getOrNull()?.takeIf { SignUrl.isValid(it) }
                } else {
                    null
                }
                fallback ?: run {
                    recordFailure()
                    state = localFailure ?: HandoffState.Failed("拿不到签到码", "回Rin刷新后再试。")
                    return@LaunchedEffect
                }
            }
        } else {
            if (currentSettings.serverUrl.isBlank() || container.sessionStore.load() == null) {
                state = HandoffState.Failed("还没登录", "打开Rin重新登录。", needsApp = true)
                recordFailure()
                return@LaunchedEffect
            }
            val api = container.client(currentSettings.serverUrl)
            if (api == null) {
                state = HandoffState.Failed("服务地址无效", "打开Rin检查服务地址。", needsApp = true)
                recordFailure()
                return@LaunchedEffect
            }
            try {
                withContext(Dispatchers.IO) { api.signURL(roomCode) }
            } catch (error: ApiError) {
                recordFailure()
                if (error.code == "SIGN_ACCESS_REQUIRED") {
                    container.signAccess.clear()
                    state = HandoffState.Failed(
                        "完整功能已停用",
                        "回 Rin 刷新后再试。当前仅支持提醒与名单。",
                        needsApp = true
                    )
                } else if (ErrorCopy.isAuth(error.code, error.status)) {
                    state = HandoffState.Failed("登录已失效", "打开Rin重新登录。", needsApp = true)
                } else {
                    state = HandoffState.Failed("拿不到签到入口", ErrorCopy.forCode(error.code, error.message))
                }
                return@LaunchedEffect
            } catch (error: Exception) {
                recordFailure()
                state = HandoffState.Failed("拿不到签到入口", ErrorCopy.from(error))
                return@LaunchedEffect
            }
        }
        if (!SignUrl.isValid(raw)) {
            recordFailure()
            state = HandoffState.Failed(
                "签到链接未通过校验",
                "链接不是学校签到入口，已停止交接。回Rin刷新后再试。",
                needsApp = true
            )
            return@LaunchedEffect
        }
        url = raw
        when (val result = controller.openInWeChat(raw)) {
            HandoffResult.Opened -> completeHandoff()
            HandoffResult.NotInstalled, HandoffResult.Blocked -> {
                val wechatInstalled = controller.isInstalled(HandoffController.WECHAT_PACKAGE)
                canWeCom = controller.isInstalled(HandoffController.WECOM_PACKAGE)
                canShareWeChat = wechatInstalled
                // Honor/WeChat rejects ACTION_VIEW for the real http school
                // link. If WeChat is installed, fall through to its proven
                // ACTION_SEND share flow without an intermediate error page.
                if (wechatInstalled && controller.shareToWeChat(raw)) {
                    Toast.makeText(
                        context,
                        "已打开微信分享，选一个聊天发送后点开链接",
                        Toast.LENGTH_LONG
                    ).show()
                    beginConfirmation(HistoryKind.HANDOFF_SHARED, "SHARED")
                    return@LaunchedEffect
                }
                recordFailure()
                state = HandoffState.Failed(
                    if (wechatInstalled) "微信没有打开链接" else "这台手机没有安装微信",
                    when {
                        wechatInstalled -> "可以复制链接，到微信里粘贴打开。"
                        canWeCom -> "可以改用企业微信，或复制链接后手动打开。"
                        else -> "可以复制链接，发到装了微信的手机或平板上打开。"
                    }
                )
            }
            HandoffResult.InvalidUrl -> {
                recordFailure()
                state = HandoffState.Failed("签到链接无效", "已停止打开。")
            }
        }
    }

    fun copyLink() {
        if (url.isBlank()) return
        clipboard.setPrimaryClip(ClipData.newPlainText("签到链接", url))
        setNote("链接已复制，60 秒后清除", failure = false)
        scope.launch {
            delay(60_000)
            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (current == url) {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    DisposableEffect(url) {
        onDispose {
            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (url.isNotBlank() && current == url) {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    // Spec 2.2: 24dp page inset, tightened to 20dp on narrow phones.
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp

    PaperPage {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageInset, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HandoffMasthead(onClose = onClose)
            Hairline()
            when (val current = state) {
                HandoffState.Working -> {
                    HandoffStateLine("正在打开微信", failure = false)
                    Text(
                        "正在校验链接并获取签到入口。链接不保存，也不进通知。",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is HandoffState.Failed -> {
                    HandoffStateLine(current.title, failure = true)
                    Text(
                        current.message,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (note.isNotBlank()) {
                        HandoffNoteLine(note, failure = noteIsFailure)
                    }
                    val hasLink = url.isNotBlank()
                    val actions = buildList {
                        if (hasLink) {
                            // Auto-open failed; the most reliable fallback is the
                            // clipboard, not retrying the same app handoff.
                            add(HandoffAction("复制链接") { copyLink() })
                            if (canShareWeChat) {
                                add(
                                    HandoffAction("分享到微信") {
                                        val shared = controller.shareToWeChat(url)
                                        if (shared) {
                                            setNote("已打开微信分享，选聊天发送后点开链接", failure = false)
                                            scope.launch {
                                                beginConfirmation(HistoryKind.HANDOFF_SHARED, "SHARED")
                                            }
                                        } else {
                                            setNote("微信没接受分享，可复制链接。")
                                        }
                                    }
                                )
                            }
                            if (canWeCom) {
                                add(
                                    HandoffAction("用企业微信打开") {
                                        scope.launch {
                                            when (controller.openInWeCom(url)) {
                                                HandoffResult.Opened -> completeHandoff()
                                                else -> setNote("企业微信也没打开，可分享或复制链接。")
                                            }
                                        }
                                    }
                                )
                            }
                            add(
                                HandoffAction("分享链接") {
                                    val opened = controller.share(url)
                                    setNote(if (opened) "已打开分享面板" else "打不开分享面板", failure = !opened)
                                }
                            )
                        }
                        if (current.needsApp) add(HandoffAction("打开 Rin", onOpenApp))
                    }
                    // Spec 2.1: one high-emphasis action; the rest stay secondary, same order.
                    actions.forEachIndexed { index, action ->
                        if (index == 0) {
                            SuiPrimaryButton(
                                text = action.label,
                                onClick = action.onClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            SuiSecondaryButton(
                                text = action.label,
                                onClick = action.onClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (hasLink) {
                        Text(
                            "链接只用于这次签到，用完可以关闭。",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/** One fallback action of the handoff page; `label` never carries the sign link. */
private class HandoffAction(val label: String, val onClick: () -> Unit)

/** A2 brand anchor for the transient handoff page: logo + serif wordmark + sky rule. */
@Composable
private fun HandoffMasthead(onClose: () -> Unit) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Rin",
                color = colors.text,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 27.sp,
                letterSpacing = (-0.4).sp
            )
            Text(
                text = "用微信完成签到",
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                letterSpacing = 0.4.sp
            )
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .width(22.dp)
                    .height(2.dp)
                    .background(colors.accent.copy(alpha = 0.75f), RoundedCornerShape(1.dp))
            )
        }
        TextButton(
            onClick = onClose,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("关闭", color = colors.text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** State line: the colour lives in the dot, the wording stays ink (AA at 20sp/12sp). */
@Composable
private fun HandoffStateLine(title: String, failure: Boolean) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            Modifier
                .padding(top = 10.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(if (failure) colors.warning else colors.signal)
        )
        Text(
            text = title,
            color = colors.text,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HandoffNoteLine(text: String, failure: Boolean) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (failure) colors.warning else colors.signal)
        )
        Text(
            text = text,
            color = colors.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}
