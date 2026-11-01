package top.sykesp42.rin.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.sykesp42.rin.data.CourseOverride
import top.sykesp42.rin.R
import top.sykesp42.rin.ui.faces.NoticeFaces
import top.sykesp42.rin.ui.faces.NoticeSlot
import top.sykesp42.rin.ui.theme.DarkRinColors
import top.sykesp42.rin.ui.theme.LightRinColors
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.RinRinColors
import top.sykesp42.rin.ui.theme.resolveAppearance
import top.sykesp42.rin.ui.theme.Spacing
import top.sykesp42.rin.ui.theme.ThemeMode
import androidx.compose.ui.graphics.asImageBitmap

/** Per-course display overrides, provided app-wide from [RinTheme]. */
val LocalCourseOverrides = staticCompositionLocalOf<Map<String, CourseOverride>> { emptyMap() }

/**
 * Display name for a course: the personal alias when set. The color variant
 * rides along as zero-width seeds so the shared accent hash recolors without
 * touching any render site; TalkBack and detail lookups keep the real name.
 */
fun courseDisplayName(raw: String, overrides: Map<String, CourseOverride>): String {
    val override = overrides[raw] ?: return raw
    val alias = override.alias.ifBlank { raw }
    return alias + "​".repeat(override.colorIndex)
}

@Composable
fun BrandMark(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val colors = LocalRinColors.current
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.accent)
    ) {
        Image(
            painter = painterResource(R.drawable.rin_logo),
            contentDescription = "Rin",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.5f), shape)
        )
    }
}

@Composable
fun SuiSurfaceCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalRinColors.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content
        )
    }
}

@Composable
fun SectionTitle(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val colors = LocalRinColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun LiveDot(running: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalRinColors.current
    val accent = if (running) colors.signal else colors.textSecondary
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
        Text(
            text = if (running) "运行中" else "未开启",
            color = accent,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun Avatar(text: String, size: Dp = 32.dp) {
    val colors = LocalRinColors.current
    val profile by top.sykesp42.rin.ui.profile.Profile.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = remember(profile.avatarFile) {
        profile.avatarFile.takeIf { it.isNotBlank() }
            ?.let { top.sykesp42.rin.ui.profile.Profile.decode(context, it) }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.actionBg),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(
                    profile.offsetX.coerceIn(-1f, 1f),
                    profile.offsetY.coerceIn(-1f, 1f)
                ),
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = profile.scale
                        scaleY = profile.scale
                    }
            )
        } else {
            Text(
                text = text.trim().take(1).ifBlank { "R" },
                color = colors.actionText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ThemeIconButton(mode: ThemeMode, onToggle: () -> Unit) {
    val colors = LocalRinColors.current
    val icon = if (mode.spec.dark) {
        R.drawable.ic_theme_dark
    } else {
        R.drawable.ic_theme_light
    }
    IconButton(
        onClick = onToggle,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = "主题与外观",
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun PageHeader(
    dateLine: String,
    title: String,
    running: Boolean?,
    themeMode: ThemeMode,
    avatarText: String,
    onToggleTheme: () -> Unit,
    compact: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = LocalRinColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLine,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 2.dp)
            )
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .width(22.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.75f))
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (compact) {
                trailing?.invoke()
            } else {
                if (running != null) {
                    LiveDot(running, modifier = Modifier.padding(end = 4.dp))
                }
                trailing?.invoke()
                ThemeIconButton(themeMode, onToggleTheme)
                Avatar(avatarText, size = 30.dp)
            }
        }
    }
}

@Composable
fun InlineError(message: String, onRetry: (() -> Unit)? = null) {
    val colors = LocalRinColors.current
    Surface(
        color = colors.warning.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                color = colors.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("重试", color = colors.warning, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RinSketch()
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            SuiSecondaryButton(actionLabel, onAction)
        }
    }
}


/**
 * Empty-state line art: Rin's bob hair and a drifting schedule sheet, drawn
 * with a few hairline strokes on paper — no bitmap, theme-aware.
 */
@Composable
private fun RinSketch(modifier: Modifier = Modifier) {
    val colors = LocalRinColors.current
    Canvas(modifier = modifier.size(96.dp, 72.dp)) {
        val ink = colors.textSecondary
        val signal = colors.signal
        val stroke = 1.5.dp.toPx()
        // Schedule sheet: rounded page with a folded corner.
        val pageLeft = size.width * 0.30f
        val pageTop = size.height * 0.16f
        val pageRight = size.width * 0.86f
        val pageBottom = size.height * 0.92f
        val fold = size.width * 0.12f
        val path = Path().apply {
            moveTo(pageLeft, pageTop)
            lineTo(pageRight - fold, pageTop)
            lineTo(pageRight, pageTop + fold)
            lineTo(pageRight, pageBottom)
            quadraticTo(pageRight, pageBottom + 3.dp.toPx(), pageRight - 3.dp.toPx(), pageBottom)
            lineTo(pageLeft + 3.dp.toPx(), pageBottom)
            quadraticTo(pageLeft, pageBottom, pageLeft, pageBottom - 3.dp.toPx())
            close()
        }
        drawPath(path, color = ink, style = Stroke(stroke, cap = StrokeCap.Round))
        // Fold crease.
        drawLine(
            ink, Offset(pageRight - fold, pageTop), Offset(pageRight, pageTop + fold),
            strokeWidth = stroke, cap = StrokeCap.Round
        )
        // Sheet content lines.
        for (index in 0..2) {
            val y = pageTop + fold + 10.dp.toPx() * (index + 1)
            drawLine(
                ink.copy(alpha = 0.55f),
                Offset(pageLeft + 9.dp.toPx(), y),
                Offset(pageRight - 9.dp.toPx(), y),
                strokeWidth = stroke * 0.7f,
                cap = StrokeCap.Round
            )
        }
        // Rin's bob silhouette peeking from the left of the sheet.
        val headCenter = Offset(size.width * 0.20f, size.height * 0.42f)
        drawArc(
            color = signal,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(headCenter.x - 13.dp.toPx(), headCenter.y - 13.dp.toPx()),
            size = Size(26.dp.toPx(), 26.dp.toPx()),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        // Hair falls on both sides of the bob.
        drawLine(
            signal, Offset(headCenter.x - 13.dp.toPx(), headCenter.y),
            Offset(headCenter.x - 13.dp.toPx(), headCenter.y + 9.dp.toPx()),
            strokeWidth = stroke, cap = StrokeCap.Round
        )
        drawLine(
            signal, Offset(headCenter.x + 13.dp.toPx(), headCenter.y),
            Offset(headCenter.x + 13.dp.toPx(), headCenter.y + 9.dp.toPx()),
            strokeWidth = stroke, cap = StrokeCap.Round
        )
        // The gaze: two quiet ticks.
        drawLine(
            ink, Offset(headCenter.x - 5.dp.toPx(), headCenter.y + 2.dp.toPx()),
            Offset(headCenter.x - 5.dp.toPx(), headCenter.y + 5.dp.toPx()),
            strokeWidth = stroke * 0.8f, cap = StrokeCap.Round
        )
        drawLine(
            ink, Offset(headCenter.x + 5.dp.toPx(), headCenter.y + 2.dp.toPx()),
            Offset(headCenter.x + 5.dp.toPx(), headCenter.y + 5.dp.toPx()),
            strokeWidth = stroke * 0.8f, cap = StrokeCap.Round
        )
    }
}

@Composable
fun Radar(running: Boolean, modifier: Modifier = Modifier.size(40.dp)) {
    val colors = LocalRinColors.current
    val accent = if (running) colors.signal else colors.textSecondary
    val animations = LocalAnimationsEnabled.current
    val pulse = if (animations) {
        val transition = rememberInfiniteTransition(label = "radar")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
            label = "pulse"
        ).value
    } else {
        0f
    }
    Canvas(modifier = modifier) {
        drawCircle(
            color = accent.copy(alpha = 0.25f),
            radius = size.minDimension / 2f - 1.dp.toPx(),
            style = Stroke(width = 1.dp.toPx())
        )
        if (running && animations) {
            drawCircle(
                color = accent.copy(alpha = 0.32f * (1f - pulse)),
                radius = size.minDimension / 2f * pulse,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
        drawCircle(color = accent, radius = 2.2.dp.toPx(), center = Offset(size.width / 2f, size.height / 2f))
    }
}

@Composable
fun SuiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val colors = LocalRinColors.current
    val style = LocalVisualStyle.current.surface
    val animations = LocalAnimationsEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && animations) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 700f),
        label = "primary_press_scale"
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .heightIn(min = 48.dp),
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        border = when (style) {
            top.sykesp42.rin.ui.theme.SurfaceStyle.STICKER,
            top.sykesp42.rin.ui.theme.SurfaceStyle.PIXEL -> BorderStroke(2.dp, colors.text)
            top.sykesp42.rin.ui.theme.SurfaceStyle.OUTLINE -> BorderStroke(1.dp, colors.signal.copy(alpha = 0.55f))
            top.sykesp42.rin.ui.theme.SurfaceStyle.GLASS -> BorderStroke(1.dp, colors.signal.copy(alpha = 0.35f))
            else -> null
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.actionBg,
            contentColor = colors.actionText,
            disabledContainerColor = colors.actionBg.copy(alpha = 0.35f),
            disabledContentColor = colors.actionText.copy(alpha = 0.6f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.actionText
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SuiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    val colors = LocalRinColors.current
    val style = LocalVisualStyle.current.surface
    val border = when (style) {
        top.sykesp42.rin.ui.theme.SurfaceStyle.STICKER,
        top.sykesp42.rin.ui.theme.SurfaceStyle.PIXEL -> BorderStroke(2.dp, colors.text)
        top.sykesp42.rin.ui.theme.SurfaceStyle.GLASS -> BorderStroke(1.dp, colors.signal.copy(alpha = 0.35f))
        else -> BorderStroke(1.dp, colors.border)
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = border,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text)
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RefreshButton(onClick: () -> Unit) {
    val colors = LocalRinColors.current
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "刷新",
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Compact first-run picker: four equal paper chips with the theme's own swatches.
 * The full sixteen-theme gallery lives in [ThemePickerSheet].
 */
@Composable
fun ThemeQuickRow(
    modes: List<ThemeMode>,
    mode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        modes.forEach { entry ->
            val selected = entry == mode
            val preview = entry.spec
            val swatch = resolveAppearance(entry).colors
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 58.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(swatch.surface)
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) swatch.signal else swatch.border,
                        MaterialTheme.shapes.small
                    )
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(entry) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(swatch.canvas, swatch.signal, swatch.text).forEach { color ->
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
                Text(
                    text = preview.name,
                    color = swatch.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    val colors = LocalRinColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing()
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val colors = LocalRinColors.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (granted) colors.signal else colors.warning)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            // Ink label: signal/warning text is only 4.50:1 / 4.21:1 on the Rin canvas,
            // below AA for this 12sp label. The state colour survives in the dot.
            Text(
                text = if (granted) "已就绪" else "待设置",
                color = colors.text,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Text(description, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        if (!granted) {
            SuiSecondaryButton(actionLabel, onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun BlueprintGrid(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val step = 32.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += step
        }
    }
}

/** Icon-blue wash, warm corner light and a quiet grid for every screen. */
@Composable
fun RinBackdrop(modifier: Modifier = Modifier) {
    val colors = LocalRinColors.current
    val style = LocalVisualStyle.current
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(colors.surface, colors.canvas),
                startY = 0f,
                endY = size.height
            )
        )
        val glowCenter = Offset(size.width * 0.86f, size.height * 0.03f)
        val glowRadius = size.width * 0.88f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.accent.copy(alpha = 0.13f), Color.Transparent),
                center = glowCenter,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = glowCenter
        )
        val warmCenter = Offset(size.width * 0.06f, size.height * 0.92f)
        val warmRadius = size.width * 0.78f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.warm.copy(alpha = 0.11f), Color.Transparent),
                center = warmCenter,
                radius = warmRadius
            ),
            radius = warmRadius,
            center = warmCenter
        )
        when (style.decoration) {
            top.sykesp42.rin.ui.theme.DecorationStyle.PLAIN -> Unit
            top.sykesp42.rin.ui.theme.DecorationStyle.SKY_GRID -> drawGrid(colors, 32.dp, 0.018f)
            top.sykesp42.rin.ui.theme.DecorationStyle.DIARY -> drawDiary(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.MANGA_INK -> drawInkDiagonals(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.CIRCUIT -> drawCircuit(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.FILM -> drawFilm(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.DEPTH -> drawDepth(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.STICKER -> drawSticker(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.HUD -> drawHud(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.PAPER -> drawPaper(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.SEAL -> drawSeal(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.LACE -> drawLace(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.STORY -> drawStory(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.PIXEL -> drawPixel(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.SNOW -> drawSnow(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.COLLAGE -> drawCollage(colors)
            top.sykesp42.rin.ui.theme.DecorationStyle.CLAY -> drawClay(colors)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    colors: top.sykesp42.rin.ui.theme.RinColors,
    stepDp: Dp,
    alpha: Float
) {
    val step = stepDp.toPx()
    val color = colors.text.copy(alpha = alpha)
    var x = 0f
    while (x <= size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        y += step
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiary(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    val lineX = 18.dp.toPx()
    drawLine(
        colors.accent.copy(alpha = 0.20f),
        Offset(lineX, 0f),
        Offset(lineX, size.height),
        strokeWidth = 1.dp.toPx()
    )
    var y = 30.dp.toPx()
    while (y < size.height) {
        drawCircle(colors.accent.copy(alpha = 0.16f), 1.6.dp.toPx(), Offset(lineX, y))
        y += 26.dp.toPx()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInkDiagonals(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    val ink = colors.text.copy(alpha = 0.035f)
    var offset = -size.height
    while (offset < size.width) {
        drawLine(
            ink,
            Offset(offset, size.height),
            Offset(offset + size.height, 0f),
            strokeWidth = 1.dp.toPx()
        )
        offset += 18.dp.toPx()
    }
    drawCircle(
        color = ink,
        radius = size.width * 0.32f,
        center = Offset(size.width * 1.02f, size.height * 0.10f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircuit(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    val circuit = colors.accent.copy(alpha = 0.10f)
    val step = 46.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(circuit, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += step * 2
    }
    var x = step
    while (x < size.width) {
        drawLine(circuit, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        x += step * 3
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFilm(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    val strip = 9.dp.toPx()
    val hole = 3.dp.toPx()
    drawRect(colors.canvas.copy(alpha = 0.62f), size = Size(size.width, strip))
    drawRect(
        colors.canvas.copy(alpha = 0.62f),
        topLeft = Offset(0f, size.height - strip),
        size = Size(size.width, strip)
    )
    var x = 8.dp.toPx()
    while (x < size.width) {
        drawRect(
            colors.text.copy(alpha = 0.10f),
            Offset(x, strip * 0.28f),
            Size(hole, hole * 0.55f)
        )
        drawRect(
            colors.text.copy(alpha = 0.10f),
            Offset(x, size.height - strip * 0.28f - hole * 0.55f),
            Size(hole, hole * 0.55f)
        )
        x += 17.dp.toPx()
    }
    var y = 36.dp.toPx()
    while (y < size.height) {
        drawLine(colors.text.copy(alpha = 0.028f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += 44.dp.toPx()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDepth(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    val center = Offset(size.width * 0.68f, size.height * 0.22f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(colors.accent.copy(alpha = 0.12f), Color.Transparent),
            center = center,
            radius = size.width * 0.8f
        ),
        radius = size.width * 0.8f,
        center = center
    )
    for (i in 0..3) {
        drawCircle(
            color = colors.accent.copy(alpha = 0.045f),
            radius = size.width * (0.22f + i * 0.15f),
            center = center,
            style = Stroke(1.dp.toPx())
        )
    }
    var y = size.height * 0.72f
    while (y < size.height) {
        drawCircle(colors.accent.copy(alpha = 0.10f), 1.8.dp.toPx(), Offset(size.width * 0.18f, y))
        y += 28.dp.toPx()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSticker(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    var y = 14.dp.toPx()
    while (y < size.height) {
        var x = 14.dp.toPx()
        while (x < size.width) {
            drawCircle(colors.accent.copy(alpha = 0.055f), 2.6.dp.toPx(), Offset(x, y))
            x += 30.dp.toPx()
        }
        y += 30.dp.toPx()
    }
    val outline = Stroke(2.dp.toPx())
    rotate(14f, Offset(size.width * 0.92f, size.height * 0.14f)) {
        drawRoundRect(
            colors.text.copy(alpha = 0.055f),
            Offset(size.width * 0.80f, size.height * 0.03f),
            Size(44.dp.toPx(), 26.dp.toPx()),
            androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
            style = outline
        )
    }
    rotate(-11f, Offset(size.width * 0.06f, size.height * 0.82f)) {
        drawRoundRect(
            colors.signal.copy(alpha = 0.07f),
            Offset(-8.dp.toPx(), size.height * 0.74f),
            Size(52.dp.toPx(), 30.dp.toPx()),
            androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
            style = Stroke(1.5.dp.toPx())
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHud(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    drawGrid(colors, 26.dp, 0.032f)
    var y = 0f
    while (y < size.height) {
        drawLine(colors.accent.copy(alpha = 0.022f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += 5.dp.toPx()
    }
    val bracket = colors.accent.copy(alpha = 0.15f)
    val inset = 14.dp.toPx()
    val arm = 18.dp.toPx()
    listOf(
        Offset(inset, inset),
        Offset(size.width - inset, inset),
        Offset(inset, size.height - inset),
        Offset(size.width - inset, size.height - inset)
    ).forEachIndexed { index, point ->
        val dx = if (index % 2 == 0) arm else -arm
        val dy = if (index < 2) arm else -arm
        drawLine(bracket, point, Offset(point.x + dx, point.y), 1.5.dp.toPx())
        drawLine(bracket, point, Offset(point.x, point.y + dy), 1.5.dp.toPx())
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPaper(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    var y = 24.dp.toPx()
    while (y < size.height) {
        drawLine(colors.text.copy(alpha = 0.045f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += 27.dp.toPx()
    }
    drawLine(
        colors.signal.copy(alpha = 0.20f),
        Offset(22.dp.toPx(), 0f),
        Offset(22.dp.toPx(), size.height),
        1.dp.toPx()
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeal(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    var x = 26.dp.toPx()
    while (x < size.width) {
        drawLine(colors.text.copy(alpha = 0.035f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        x += 38.dp.toPx()
    }
    val center = Offset(size.width * 0.88f, size.height * 0.84f)
    drawCircle(colors.signal.copy(alpha = 0.09f), 27.dp.toPx(), center)
    drawCircle(colors.signal.copy(alpha = 0.18f), 27.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
    drawRect(
        colors.signal.copy(alpha = 0.14f),
        Offset(center.x - 8.dp.toPx(), center.y - 8.dp.toPx()),
        Size(16.dp.toPx(), 16.dp.toPx())
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLace(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(colors.accent.copy(alpha = 0.14f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.12f),
            radius = size.width * 0.8f
        ),
        radius = size.width * 0.8f,
        center = Offset(size.width * 0.5f, size.height * 0.12f)
    )
    val dot = colors.accent.copy(alpha = 0.13f)
    val inset = 10.dp.toPx()
    var x = inset
    while (x < size.width - inset) {
        drawCircle(dot, 1.8.dp.toPx(), Offset(x, inset))
        drawCircle(dot, 1.8.dp.toPx(), Offset(x, size.height - inset))
        x += 15.dp.toPx()
    }
    var y = inset
    while (y < size.height - inset) {
        drawCircle(dot, 1.8.dp.toPx(), Offset(inset, y))
        drawCircle(dot, 1.8.dp.toPx(), Offset(size.width - inset, y))
        y += 15.dp.toPx()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStory(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    val inset = 10.dp.toPx()
    drawRoundRect(
        color = colors.accent.copy(alpha = 0.11f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
        style = Stroke(1.2.dp.toPx())
    )
    val leaf = colors.signal.copy(alpha = 0.07f)
    drawCircle(leaf, 8.dp.toPx(), Offset(size.width * 0.90f, size.height * 0.16f))
    drawCircle(leaf, 5.dp.toPx(), Offset(size.width * 0.83f, size.height * 0.24f))
    drawCircle(leaf, 6.dp.toPx(), Offset(size.width * 0.12f, size.height * 0.78f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixel(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    val cell = 6.dp.toPx()
    val step = 18.dp.toPx()
    var row = 0
    var y = 0f
    while (y < size.height) {
        var column = 0
        var x = 0f
        while (x < size.width) {
            if ((row + column) % 3 == 0) {
                drawRect(colors.text.copy(alpha = 0.035f), Offset(x, y), Size(cell, cell))
            }
            x += step
            column += 1
        }
        y += step
        row += 1
    }
    drawGrid(colors, 48.dp, 0.025f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnow(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    drawCircle(
        brush = Brush.verticalGradient(
            listOf(colors.surface.copy(alpha = 0.7f), Color.Transparent)
        ),
        radius = size.width * 0.75f,
        center = Offset(size.width * 0.5f, 0f)
    )
    val flake = colors.accent.copy(alpha = 0.15f)
    listOf(
        0.12f to 0.18f,
        0.28f to 0.42f,
        0.52f to 0.12f,
        0.70f to 0.34f,
        0.86f to 0.60f,
        0.42f to 0.74f,
        0.16f to 0.86f,
        0.76f to 0.90f
    ).forEach { (fx, fy) ->
        drawCircle(flake, (1.2f + fy * 2.2f).dp.toPx(), Offset(size.width * fx, size.height * fy))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCollage(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    val tear = colors.text.copy(alpha = 0.035f)
    val path = Path().apply {
        moveTo(0f, size.height * 0.22f)
        lineTo(size.width, size.height * 0.10f)
        lineTo(size.width, size.height * 0.20f)
        lineTo(0f, size.height * 0.32f)
        close()
    }
    drawPath(path, tear)
    val second = Path().apply {
        moveTo(0f, size.height * 0.82f)
        lineTo(size.width, size.height * 0.68f)
        lineTo(size.width, size.height * 0.76f)
        lineTo(0f, size.height * 0.90f)
        close()
    }
    drawPath(second, colors.signal.copy(alpha = 0.045f))
    rotate(-6f, Offset(size.width * 0.84f, size.height * 0.28f)) {
        drawRect(
            colors.signal.copy(alpha = 0.08f),
            Offset(size.width * 0.72f, size.height * 0.24f),
            Size(52.dp.toPx(), 18.dp.toPx())
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClay(
    colors: top.sykesp42.rin.ui.theme.RinColors
) {
    val blobs = listOf(
        Triple(0.14f, 0.12f, 0.34f),
        Triple(0.86f, 0.28f, 0.40f),
        Triple(0.24f, 0.86f, 0.36f)
    )
    blobs.forEach { (fx, fy, radius) ->
        val center = Offset(size.width * fx, size.height * fy)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.signal.copy(alpha = 0.07f), Color.Transparent),
                center = center,
                radius = size.width * radius
            ),
            radius = size.width * radius,
            center = center
        )
    }
}

@Composable
fun SuiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val colors = LocalRinColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = if (contentDescription != null) {
            modifier.semantics { this.contentDescription = contentDescription }
        } else {
            modifier
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.signalInk,
            checkedTrackColor = colors.signal,
            checkedBorderColor = colors.signal,
            uncheckedThumbColor = colors.textSecondary,
            uncheckedTrackColor = colors.surfaceRaised,
            uncheckedBorderColor = colors.border
        )
    )
}

/** A paper dialog: same surface and ink as sheets, never the M3 lavender container. */
@Composable
fun SuiAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    val colors = LocalRinColors.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        containerColor = colors.surface,
        titleContentColor = colors.text,
        textContentColor = colors.textSecondary,
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.large
    )
}

@Composable
fun suiTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalRinColors.current.signal,
    unfocusedBorderColor = LocalRinColors.current.border,
    errorBorderColor = LocalRinColors.current.warning,
    cursorColor = LocalRinColors.current.signal,
    focusedLabelColor = LocalRinColors.current.signal,
    unfocusedLabelColor = LocalRinColors.current.textSecondary,
    focusedLeadingIconColor = LocalRinColors.current.signal,
    unfocusedLeadingIconColor = LocalRinColors.current.textSecondary,
    focusedTrailingIconColor = LocalRinColors.current.signal,
    unfocusedTrailingIconColor = LocalRinColors.current.textSecondary
)

/**
 * The one error row a paper page uses: hairline top, warning tint, message and
 * a single retry action. Ported as-is from the full client so both builds fail
 * the same way.
 */
@Composable
fun ScheduleErrorRow(message: String, onRetry: () -> Unit, actionLabel: String = "重试") {
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
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
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

/**
 * Equal-width segmented selector with the sky underline. Ported from the full
 * client: the campus / weekday / block filters all use it, so the 校务 page
 * reads the same in both builds.
 */
@Composable
fun PaperSelectorRow(
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    val colors = LocalRinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (label, value) ->
            val selected = value == selectedValue
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(value) },
                        role = Role.Tab
                    )
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = if (selected) colors.text else colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .background(
                            if (selected) colors.signal else Color.Transparent,
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}
