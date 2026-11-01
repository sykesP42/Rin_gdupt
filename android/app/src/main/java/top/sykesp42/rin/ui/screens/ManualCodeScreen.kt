package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.sykesp42.rin.R
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.components.SuiSecondaryButton
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.rememberThemeArtPainter

private fun manualPhoto(dark: Boolean): Int =
    if (dark) R.drawable.rin_manual_night else R.drawable.rin_manual_sky

/**
 * Full manual-room-code page: a summer photo header, a floating card, one large
 * four-digit field and the same callbacks as the old inline section. The photo
 * is edged into the card so the page reads like the Today hero, not a settings form.
 */
@Composable
fun ManualCodeScreen(
    initialCode: String,
    currentManualCode: String,
    onBack: () -> Unit,
    onMonitor: (String) -> Unit,
    onClearManual: () -> Unit,
    onOpenSchedule: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    val dark = colors.canvas.luminance() < 0.4f
    var code by rememberSaveable(initialCode) { mutableStateOf(initialCode.take(4)) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 24.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "← 返回",
                    color = colors.text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "手动教室码",
                    color = colors.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "只影响检查目标",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
        ) {
            Image(
                painter = rememberThemeArtPainter(LocalVisualStyle.current.spec)
                    ?: painterResource(manualPhoto(dark)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xCC0D243A),
                                Color(0x330D243A),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 44.dp)
            ) {
                Text(
                    text = "MANUAL / 4 DIGITS",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "输入四位教室码",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "主校区 · 手动",
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Column(modifier = Modifier.offset(y = (-30).dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = shape,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 10.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "主教 · 主校区",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (currentManualCode.isNotBlank()) {
                            ManualStatusTag("手动中")
                        } else {
                            Text(
                                text = "未设置",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "四位码",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        BasicTextField(
                            value = code,
                            onValueChange = { value ->
                                code = value.filter { it.isDigit() }.take(4)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 46.sp,
                                lineHeight = 52.sp,
                                letterSpacing = 8.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.accent
                            ),
                            cursorBrush = SolidColor(colors.signal),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 2.dp)
                                .onFocusChanged { focused = it.isFocused },
                            decorationBox = { inner ->
                                Box {
                                    if (code.isEmpty()) {
                                        Text(
                                            text = "0129",
                                            color = colors.textSecondary.copy(alpha = 0.35f),
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 46.sp,
                                                lineHeight = 52.sp,
                                                letterSpacing = 8.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                        Text(
                            text = "CODE",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 10.dp, start = 8.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .height(1.5.dp)
                            .background(if (focused) colors.signal else colors.border)
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "只影响检查目标",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    SuiPrimaryButton(
                        text = if (code.length == 4) {
                            "用 $code 开始监控"
                        } else {
                            "输入四位码后开始"
                        },
                        onClick = { onMonitor(code) },
                        enabled = code.length == 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuiSecondaryButton(
                            text = "恢复自动",
                            onClick = onClearManual,
                            enabled = currentManualCode.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )
                        if (onOpenSchedule != null) {
                            SuiSecondaryButton(
                                text = "查看课表",
                                onClick = onOpenSchedule,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Text(
                text = "恢复自动识别会清除手动码",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp)
            )
        }
    }
}

/** One paper row in SearchScreen; the full page owns the input and actions. */
@Composable
fun ManualCodeEntry(
    currentCode: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    val dark = colors.canvas.luminance() < 0.4f
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(title = "手动教室码")
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberThemeArtPainter(LocalVisualStyle.current.spec)
                    ?: painterResource(manualPhoto(dark)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "四位教室码",
                        color = colors.text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (currentCode.isNotBlank()) {
                            "当前 $currentCode · 手动中"
                        } else {
                            "教务未连接也能直接输入"
                        },
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = "›",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ManualStatusTag(text: String) {
    val colors = LocalSuiSignColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.warning.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            color = colors.warning,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
