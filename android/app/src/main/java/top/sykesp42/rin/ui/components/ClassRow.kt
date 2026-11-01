package top.sykesp42.rin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.ui.extractRoomCode
import top.sykesp42.rin.ui.theme.LocalSuiSignColors

@Composable
fun ClassRow(
    event: ApiScheduleEvent,
    current: ApiScheduleEvent?,
    onMonitor: (String) -> Unit
) {
    val colors = LocalSuiSignColors.current
    val isCurrent = current != null &&
        current.courseName == event.courseName &&
        current.startTime == event.startTime
    val roomCode = extractRoomCode(event.location)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.width(52.dp)) {
                Text(
                    text = event.startTime,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                )
                Text(
                    text = event.endTime,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.courseName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(event.sectionText, event.location)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                isCurrent -> Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.signal.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(colors.signal)
                    )
                    Text(
                        "进行中",
                        color = colors.signal,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                roomCode != null -> TextButton(
                    onClick = { onMonitor(roomCode) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("监控", style = MaterialTheme.typography.labelMedium)
                }
                else -> Text(
                    "—",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        HorizontalDivider(color = colors.border)
    }
}
