package top.sykesp42.rin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.sykesp42.rin.ui.theme.AccentPreset
import top.sykesp42.rin.ui.theme.CustomThemeColorMode
import top.sykesp42.rin.ui.theme.CustomThemeFocus
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.ThemeCatalog
import top.sykesp42.rin.ui.theme.ThemeCollection
import top.sykesp42.rin.ui.theme.ThemeArtImage
import top.sykesp42.rin.ui.theme.ThemeCorners
import top.sykesp42.rin.ui.theme.ThemeMode
import top.sykesp42.rin.ui.theme.ThemeSpec
import top.sykesp42.rin.ui.theme.resolveAppearance

/**
 * One compact card for the sixteen-theme gallery. Every card paints itself with
 * its own palette and hero crop, so browsing never needs per-theme screenshots.
 */
@Composable
fun ThemeGalleryCard(
    spec: ThemeSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preview = remember(spec) { resolveAppearance(spec.mode) }
    val colors = preview.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.canvas)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.signal else colors.border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                role = Role.RadioButton
                contentDescription = "${spec.name}，${spec.en}"
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
        ) {
            ThemeArtPreview(spec, Modifier.matchParentSize())
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.canvas.copy(alpha = 0.10f), colors.canvas.copy(alpha = 0.92f))
                        )
                    )
            )
            Text(
                text = spec.stamp,
                color = colors.text,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 9.dp, bottom = 7.dp)
                    .background(colors.surface.copy(alpha = 0.88f), RoundedCornerShape(4.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(20.dp)
                        .background(colors.signal, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "已选择",
                        tint = colors.signalInk,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = spec.name,
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.en,
                color = colors.textSecondary,
                fontSize = 8.5.sp,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(colors.signal, colors.warm, colors.text).forEach { dot ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(dot, CircleShape)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (spec.dark) "DARK" else "LIGHT",
                    color = colors.textSecondary,
                    fontSize = 8.sp,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

@Composable
private fun ThemeArtPreview(spec: ThemeSpec, modifier: Modifier = Modifier) {
    val art = spec.artThumb ?: spec.art
    if (art != null) {
        Image(
            painter = painterResource(art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = androidx.compose.ui.BiasAlignment(0f, spec.artFocus * 2f - 1f),
            modifier = modifier
        )
    } else {
        val colors = remember(spec) { resolveAppearance(spec.mode).colors }
        Column(
            modifier = modifier
                .background(colors.canvas)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.62f)
                    .height(5.dp)
                    .background(colors.text, RoundedCornerShape(2.dp))
            )
            Box(
                Modifier
                    .fillMaxWidth(0.38f)
                    .height(4.dp)
                    .background(colors.textSecondary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .width(34.dp)
                    .height(11.dp)
                    .background(colors.actionBg, MaterialTheme.shapes.extraSmall)
            )
        }
    }
}

/** Wide current-theme summary used by 我的 · 外观. */
@Composable
fun ThemeSummaryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preview = LocalVisualStyle.current
    val colors = preview.colors
    val spec = preview.spec
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(colors.canvas)
            .border(1.dp, colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(112.dp)
                .height(112.dp)
        ) {
            if (spec.hasArt) {
                ThemeArtImage(spec, Modifier.matchParentSize())
            } else {
                ThemeArtPreview(spec, Modifier.matchParentSize())
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.canvas.copy(alpha = 0.86f))
                        )
                    )
            )
            Text(
                text = spec.stamp,
                color = colors.text,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(colors.surface, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = when {
                    spec.customArtPath != null -> "CUSTOM · 本机"
                    spec.collection == ThemeCollection.RIN -> "RIN · 插画"
                    else -> "CLASSIC · 经典"
                },
                color = colors.signal,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp
            )
            Text(
                text = spec.name,
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.blurb,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "浏览全部 16 套  ›",
                color = colors.text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 7.dp)
            )
        }
    }
}

/** Full gallery sheet, shared by the masthead shortcut and 我的 · 外观. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    var filter by rememberSaveable { mutableStateOf(selected.spec.collection) }
    val colors = LocalSuiSignColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.canvas,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        scrimColor = colors.canvas.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .background(colors.border, CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("主题与外观", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "12 套插画 · 4 套纯色",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = colors.textSecondary)
                }
            }
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CollectionChip(
                    label = "RIN 插画",
                    count = ThemeCatalog.rin.size,
                    selected = filter == ThemeCollection.RIN,
                    onClick = { filter = ThemeCollection.RIN }
                )
                CollectionChip(
                    label = "经典 · 纯色",
                    count = ThemeCatalog.classic.size,
                    selected = filter == ThemeCollection.CLASSIC,
                    onClick = { filter = ThemeCollection.CLASSIC }
                )
            }
            val themes = if (filter == ThemeCollection.RIN) ThemeCatalog.rin else ThemeCatalog.classic
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = if (filter == ThemeCollection.CLASSIC) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 404.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.84f)
                },
                contentPadding = PaddingValues(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(themes, key = { it.mode.key }) { spec ->
                    ThemeGalleryCard(
                        spec = spec,
                        selected = spec.mode == selected,
                        onClick = { onSelect(spec.mode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.actionBg else colors.surface)
            .border(1.dp, if (selected) colors.actionBg else colors.border, CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) colors.actionText else colors.text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = count.toString(),
            color = if (selected) colors.actionText.copy(alpha = 0.7f) else colors.textSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** Seven accent choices; the first one restores the theme's native accent. */
@Composable
fun AccentColorRow(
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = LocalVisualStyle.current
    val colors = LocalSuiSignColors.current
    val nativeSignal = remember(visual.spec) { ThemeCatalog.colorsFor(visual.spec).signal }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeCatalog.accentPresets.forEach { preset ->
            AccentSwatch(
                preset = preset,
                selected = value.equals(preset.hex, ignoreCase = true) ||
                    (value.isBlank() && preset.key == "default"),
                nativeColors = colors,
                currentSign = nativeSignal,
                onClick = { onSelect(preset.hex) }
            )
        }
    }
}

@Composable
private fun AccentSwatch(
    preset: AccentPreset,
    selected: Boolean,
    nativeColors: top.sykesp42.rin.ui.theme.SuiSignColors,
    currentSign: Color,
    onClick: () -> Unit
) {
    val swatch = if (preset.hex.isBlank()) currentSign else ThemeCatalog.parseHex(preset.hex) ?: currentSign
    val ring = if (selected) nativeColors.text else nativeColors.border
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(4.dp)
            .semantics {
                role = Role.RadioButton
                contentDescription = preset.label
            }
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(swatch, CircleShape)
                .border(if (selected) 2.dp else 1.dp, ring, CircleShape)
        ) {
            if (preset.key == "default") {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .background(nativeColors.warm, CircleShape)
                        .border(1.dp, nativeColors.surface, CircleShape)
                )
            }
        }
        Text(
            text = preset.label,
            color = if (selected) nativeColors.text else nativeColors.textSecondary,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

/** Three corner treatments applied on top of any theme's material. */
@Composable
fun CornerStyleRow(
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    val selected = ThemeCorners.from(value)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ThemeCorners.entries.forEach { entry ->
            val active = entry == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (active) colors.signal.copy(alpha = 0.14f) else colors.surface)
                    .border(1.dp, if (active) colors.signal else colors.border, MaterialTheme.shapes.small)
                    .clickable(role = Role.RadioButton) { onSelect(entry.key) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.label,
                    color = colors.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun CustomThemeFocusRow(
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedStyleRow(
        labels = CustomThemeFocus.entries.map { it.key to it.label },
        selectedKey = CustomThemeFocus.from(value).key,
        onSelect = onSelect,
        modifier = modifier
    )
}

@Composable
fun CustomThemePaletteRow(
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedStyleRow(
        labels = CustomThemeColorMode.entries.map { it.key to it.label },
        selectedKey = CustomThemeColorMode.from(value).key,
        onSelect = onSelect,
        modifier = modifier
    )
}

@Composable
private fun SegmentedStyleRow(
    labels: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEach { (key, label) ->
            val active = key == selectedKey
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (active) colors.signal.copy(alpha = 0.14f) else colors.surface)
                    .border(1.dp, if (active) colors.signal else colors.border, MaterialTheme.shapes.small)
                    .clickable(role = Role.RadioButton) { onSelect(key) }
                    .padding(horizontal = 4.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = colors.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}
