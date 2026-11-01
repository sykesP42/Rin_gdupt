package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.formatDateLine
import top.sykesp42.rin.ui.schedule.ExamItem
import top.sykesp42.rin.ui.schedule.nextExamItem
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.courseAccent

/** The nearest dated exam, with a quiet countdown and its real time/room. */
@Composable
internal fun ExamCountdownHero(items: List<ExamItem>, today: LocalDate) {
    val next = nextExamItem(items, today) ?: return
    val colors = LocalRinColors.current
    val ink = courseAccent(next.exam.name, colors.canvas.luminance() < 0.4f)
    val clock = when {
        next.time.start.isNotBlank() && next.time.end.isNotBlank() ->
            "${next.time.start}-${next.time.end}"
        next.time.start.isNotBlank() -> next.time.start
        else -> next.exam.time
    }
    val detail = listOfNotNull(
        clock.takeIf { it.isNotBlank() },
        next.exam.location.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "下一场",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = next.time.date?.let(::formatDateLine) ?: "时间待确认",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            text = next.exam.name,
            color = ink,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = next.countdown,
            color = colors.text,
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp),
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (next.exam.note.isNotBlank()) {
            Text(
                text = next.exam.note,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
    Hairline()
}

/** One exam row: course ink, countdown on the right, raw time/room below. */
@Composable
internal fun ExamRow(item: ExamItem) {
    val colors = LocalRinColors.current
    val ink = courseAccent(item.exam.name, colors.canvas.luminance() < 0.4f)
    val countdownColor = when {
        item.countdown == "已结束" || item.countdown == "时间待确认" -> colors.textSecondary
        item.countdown.startsWith("今天") -> colors.signal
        else -> colors.text
    }
    val detail = listOfNotNull(
        item.exam.time.takeIf { it.isNotBlank() },
        item.exam.location.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = item.exam.name,
                    color = ink,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.countdown,
                    color = countdownColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (item.exam.note.isNotBlank()) {
                Text(
                    text = item.exam.note,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Hairline()
    }
}
