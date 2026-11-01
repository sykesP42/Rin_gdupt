package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.sykesp42.rin.domain.SignAccessApplyOutcome
import top.sykesp42.rin.domain.SignAccessLevel
import top.sykesp42.rin.ui.AppViewModel

private val TerminalBackground = Color(0xFF050806)
private val TerminalInk = Color(0xFF9BE7C4)
private val TerminalMuted = Color(0xFF6F8B80)
private val TerminalError = Color(0xFFE6A17C)

@Composable
internal fun SignAccessTerminal(
    vm: AppViewModel,
    versionName: String,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lines by remember {
        mutableStateOf(
            listOf(
                "Rin debug terminal · v$versionName",
                "link ready",
                ""
            )
        )
    }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        delay(180)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(lines.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    fun submit() {
        val code = input.trim()
        if (code.isEmpty() || busy) return
        input = ""
        busy = true
        lines = lines + "> handshake..."
        scope.launch {
            val result = vm.applySignAccess(code)
            val line = when (result) {
                is SignAccessApplyOutcome.Applied -> when (result.state.status) {
                    SignAccessLevel.ACTIVE -> "ACCESS GRANTED"
                    SignAccessLevel.PENDING -> "access request queued"
                    SignAccessLevel.REJECTED -> "ERR_ACCESS_REJECTED"
                    SignAccessLevel.REVOKED -> "ERR_ACCESS_REVOKED"
                    else -> "ERR_ACCESS_DENIED"
                }
                is SignAccessApplyOutcome.Failed -> when (result.code) {
                    "SIGN_ACCESS_INVALID" -> "ERR_TOKEN_INVALID"
                    "SIGN_ACCESS_VERSION" -> "ERR_VERSION_MISMATCH"
                    "SIGN_ACCESS_CLOSED" -> "ERR_REAPPLY_BLOCKED"
                    "AUTH_REQUIRED" -> "ERR_AUTH_REQUIRED"
                    "LINK_DOWN" -> "ERR_LINK_DOWN"
                    "SIGN_ACCESS_REQUIRED" -> "ERR_ACCESS_DENIED"
                    else -> "ERR_" + result.code.ifBlank { "UNKNOWN" }
                }
            }
            lines = lines + line
            busy = false
            delay(120)
            focusRequester.requestFocus()
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "debug_terminal",
                        color = TerminalInk,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss, enabled = !busy) {
                        Text(
                            text = "×",
                            color = TerminalMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ">",
                        color = TerminalInk,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp
                    )
                    BasicTextField(
                        value = input,
                        onValueChange = { value ->
                            input = value.filter { it.isLetterOrDigit() || it == '-' || it == ' ' }
                                .take(64)
                        },
                        enabled = !busy,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TerminalInk,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(TerminalInk),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .focusRequester(focusRequester)
                    )
                    TextButton(
                        enabled = input.isNotBlank() && !busy,
                        onClick = { submit() }
                    ) {
                        Text(
                            text = if (busy) "..." else "RUN",
                            color = if (busy) TerminalMuted else TerminalInk,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TerminalMuted.copy(alpha = 0.35f))
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    lines.forEach { line ->
                        val error = line.startsWith("ERR_")
                        Text(
                            text = line,
                            color = if (error) TerminalError else TerminalInk,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
