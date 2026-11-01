package top.sykesp42.rin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.sykesp42.rin.ui.MainTab
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.SurfaceStyle

/**
 * Flat A2 paper navigation. No floating pill, surface, radius or shadow:
 * a full-width paper strip with a hairline top border and a 2dp sky line
 * under the selected label.
 */
@Composable
fun RinBottomBar(
    tabs: List<MainTab>,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    val animations = LocalAnimationsEnabled.current
    val surface = LocalVisualStyle.current.surface
    val compact = tabs.size <= 2
    val barColor = when (surface) {
        SurfaceStyle.GLASS -> colors.surface.copy(alpha = 0.86f)
        SurfaceStyle.PIXEL -> colors.surface
        SurfaceStyle.OUTLINE -> colors.canvas
        else -> colors.canvas.copy(alpha = 0.98f)
    }
    val lineWidth = when (surface) {
        SurfaceStyle.PIXEL, SurfaceStyle.STICKER -> 2.dp
        SurfaceStyle.OUTLINE -> 1.5.dp
        else -> 1.dp
    }
    val lineColor = when (surface) {
        SurfaceStyle.PIXEL, SurfaceStyle.STICKER -> colors.signal
        SurfaceStyle.GLASS -> colors.signal.copy(alpha = 0.35f)
        else -> colors.border
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(lineWidth)
                .background(lineColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = (if (compact) Modifier.widthIn(max = 260.dp) else Modifier.fillMaxWidth())
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                tabs.forEach { item ->
                    val isSelected = item == selected
                    val tint by animateColorAsState(
                        targetValue = if (isSelected) colors.signal else colors.textSecondary,
                        animationSpec = tween(if (animations) 200 else 0),
                        label = "tab_tint"
                    )
                    val indicator by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = tween(if (animations) 200 else 0),
                        label = "tab_indicator"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(item) },
                                role = Role.Tab
                            )
                            .padding(top = 7.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = tint,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .width(18.dp)
                                .height(2.dp)
                                .background(colors.accent.copy(alpha = indicator))
                        )
                    }
                }
            }
        }
    }
}
