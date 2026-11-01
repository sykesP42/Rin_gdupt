package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.sykesp42.rin.data.AttendanceRecord
import top.sykesp42.rin.data.AttendanceStatus
import top.sykesp42.rin.data.CourseAttendance
import top.sykesp42.rin.ui.AttendanceUiState
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.SectionHeader
import top.sykesp42.rin.ui.theme.LocalSuiSignColors

private val schoolZone = ZoneId.of("Asia/Shanghai")
private val recordTime = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(schoolZone)

internal fun attendanceStatusLabel(status: String): String = when (status) {
    AttendanceStatus.SIGNED -> "已签到"
    AttendanceStatus.MISSED -> "未签到"
    else -> "未确认"
}

/**
 * Per-course attendance receipt built only from terminal monitor outcomes.
 * The ledger is local; it never claims a sign-in that the roster did not show.
 */
@Composable
internal fun AttendanceSection(state: AttendanceUiState) {
    val colors = LocalSuiSignColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "本学期到课",
            meta = if (state.courses.isEmpty()) null else "${state.courses.size} 门课"
        )
        when {
            !state.loaded -> Unit
            state.records.isEmpty() -> Text(
                text = "按课程累计已确认的签到结果",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            else -> {
                state.courses.forEach { course ->
                    CourseAttendanceRow(course)
                }
                val recent = state.records.take(6)
                if (recent.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Hairline()
                    recent.forEach { record ->
                        AttendanceRecordRow(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseAttendanceRow(course: CourseAttendance) {
    val colors = LocalSuiSignColors.current
    val confirmed = course.signed + course.missed
    val rate = if (confirmed > 0) course.signed.toFloat() / confirmed else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = course.courseName,
                color = colors.text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (confirmed > 0) {
                    "${course.signed} / $confirmed"
                } else {
                    "未确认 ${course.unknown}"
                },
                color = if (course.missed > 0) colors.warning else colors.text,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.text.copy(alpha = 0.08f))
        ) {
            if (rate > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(rate)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (course.missed > 0) colors.warning else colors.signal)
                )
            }
        }
        val detail = if (confirmed == 0 && course.unknown > 0) {
            "名单未读到"
        } else {
            buildList {
                if (course.missed > 0) add("漏签 ${course.missed}")
                if (course.unknown > 0) add("未确认 ${course.unknown}")
                add("共 ${course.total} 次")
            }.joinToString(" · ")
        }
        Text(
            text = detail,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AttendanceRecordRow(record: AttendanceRecord) {
    val colors = LocalSuiSignColors.current
    val ink = when (record.status) {
        AttendanceStatus.SIGNED -> colors.signal
        AttendanceStatus.MISSED -> colors.warning
        else -> colors.textSecondary
    }
    val at = runCatching { Instant.parse(record.at) }.getOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(ink)
        )
        Text(
            text = at?.let { recordTime.format(it) } ?: record.date,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = record.courseName,
            color = colors.text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = attendanceStatusLabel(record.status),
            color = ink,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (record.status == AttendanceStatus.MISSED) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
