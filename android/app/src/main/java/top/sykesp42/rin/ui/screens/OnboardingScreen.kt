package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.sykesp42.rin.data.AppSettings
import top.sykesp42.rin.ui.AppViewModel
import top.sykesp42.rin.ui.components.BrandMark
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.PaperPage
import top.sykesp42.rin.ui.components.SuiPrimaryButton
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.ThemeMode
import top.sykesp42.rin.ui.theme.ThemeArtImage

@Composable
fun OnboardingScreen(vm: AppViewModel, settings: AppSettings) {
    val colors = LocalRinColors.current
    var selected by remember { mutableStateOf(ThemeMode.from(settings.theme)) }
    PaperPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(54.dp))
            BrandMark(size = 64.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Rin",
                color = colors.text,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "把课表、提醒和一点陪伴放进每天",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(28.dp))
            Hairline()
            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureLine("课程表", "手动录入或整理自己的课表，周表与列表随时切换。")
                FeatureLine("上课提醒", "提前提醒下一节课，时间与铃声都由你决定。")
                FeatureLine("签到提醒", "只在本机提醒你留意签到。")
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "选一个喜欢的主题开始",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.starters().forEach { mode ->
                    ThemeStartCard(
                        mode = mode,
                        selected = mode == selected,
                        onClick = {
                            selected = mode
                            vm.setTheme(mode)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            SuiPrimaryButton(
                text = "开始使用",
                onClick = vm::completeOnboarding,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "课表与提醒只保存在本机",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun FeatureLine(title: String, detail: String) {
    val colors = LocalRinColors.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.signal)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = colors.text, style = MaterialTheme.typography.titleSmall)
            Text(detail, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ThemeStartCard(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.signal else colors.border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(listOf(colors.surfaceRaised, colors.canvas))
                )
        ) {
            ThemeArtImage(
                spec = mode.spec,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = mode.spec.name,
            color = if (selected) colors.signal else colors.text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
        )
    }
}
