package top.sky2333.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import top.sky2333.rin.ui.components.Hairline
import top.sky2333.rin.ui.components.PaperPage
import top.sky2333.rin.ui.components.SuiSwitch
import top.sky2333.rin.ui.theme.LocalRinColors

@Composable
internal fun SettingsSubpage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    val pageInset = if (LocalConfiguration.current.screenWidthDp < 360) 20.dp else 24.dp
    val colors = LocalRinColors.current
    PaperPage {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.canvas)
            ) {
                SettingsSubpageHeader(
                    title = title,
                    subtitle = subtitle,
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = pageInset)
                )
                Hairline()
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = pageInset,
                    end = pageInset,
                    top = 18.dp,
                    bottom = 92.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSubpageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = colors.text
            )
        }
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.text)
            Text(
                text = subtitle,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SettingsNavigationCard(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    val colors = LocalRinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surface)
            .border(1.dp, colors.border, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.text, style = MaterialTheme.typography.titleMedium)
            Text(
                text = summary,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun PaperSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(
                    selected = checked,
                    role = Role.Switch
                ) { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            )
            SuiSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                contentDescription = contentDescription
            )
        }
        Text(
            text = description,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 7.dp)
        )
    }
}

@Composable
internal fun PaperInfoRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            color = colors.text,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = subtitle,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
internal fun StatusNote(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            color = colors.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}
