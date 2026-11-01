package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import top.sykesp42.rin.data.ApiScheduleDay
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.schedule.ClassSlotState
import top.sykesp42.rin.ui.schedule.classSlotState
import top.sykesp42.rin.ui.schedule.courseCountLabel
import top.sykesp42.rin.ui.schedule.nextUpcomingIndex
import top.sykesp42.rin.ui.schedule.restHint
import top.sykesp42.rin.ui.theme.LocalSuiSignColors
import top.sykesp42.rin.ui.theme.courseAccent
import top.sykesp42.rin.ui.theme.courseTint

/**
 * Phone-first week view: seven readable day sections, with the seven-column
 * table available as the alternative overview. Every course gets a full row,
 * so names and rooms are not truncated just to fit a column.
 */
@Composable
internal fun WeekAgenda(
    days: List<ApiScheduleDay>,
    today: LocalDate?,
    nowMinutes: Int,
    fullscreen: Boolean,
    onCourseTap: (ApiScheduleEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSuiSignColors.current
    val dark = colors.canvas.luminance() < 0.4f
    val hPad = if (fullscreen) 20.dp else 24.dp
    val listState = rememberLazyListState()
    // Open on the today section, so a week view never starts on Monday while
    // the live class sits on Thursday. The first composition may still carry
    // yesterday's overview date, so re-land when the resolved day changes.
    var landedOnDate by remember(days) { mutableStateOf<String?>(null) }
    LaunchedEffect(days, today) {
        val target = today?.toString() ?: return@LaunchedEffect
        if (landedOnDate == target) return@LaunchedEffect
        val index = days.indexOfFirst { it.date == target }
        if (index >= 0) {
            listState.scrollToItem(index)
            landedOnDate = target
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (fullscreen) 14.dp else 10.dp)
    ) {
        itemsIndexed(days, key = { _, day -> day.date.ifBlank { day.weekday.toString() } }) { _, day ->
            DaySection(
                day = day,
                today = today,
                nowMinutes = nowMinutes,
                fullscreen = fullscreen,
                dark = dark,
                onCourseTap = onCourseTap
            )
        }
        // Without tail room the list clamps at its end and a late-week "today"
        // section can never reach the top; half a viewport is enough to align
        // any of the seven days while the rest still fills the screen.
        item(key = "week-tail") {
            Spacer(Modifier.fillParentMaxHeight(0.5f))
        }
    }
}

@Composable
private fun DaySection(
    day: ApiScheduleDay,
    today: LocalDate?,
    nowMinutes: Int,
    fullscreen: Boolean,
    dark: Boolean,
    onCourseTap: (ApiScheduleEvent) -> Unit
) {
    val colors = LocalSuiSignColors.current
    val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
    val isToday = today != null && day.date == today.toString()
    val events = day.events.sortedBy { it.startTime }
    // Live slot states only for today: comparing another day's clock to the
    // current minute would strike through a class that has not happened.
    val states = if (isToday) {
        events.map { classSlotState(it.startTime, it.endTime, nowMinutes) }
    } else {
        List(events.size) { null }
    }
    val nextIndex = if (isToday) nextUpcomingIndex(states.filterNotNull()) else -1
    val rest = if (isToday) restHint(events.map { it.startTime to it.endTime }, nowMinutes) else null
    val restAfter = when (rest) {
        null, "还没开课" -> -1
        "今天课上完了" -> events.lastIndex
        else -> states.indexOfLast { it == ClassSlotState.PAST }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "周" + "一二三四五六日"[(day.weekday - 1).coerceIn(0, 6)],
                color = if (isToday) colors.text else colors.textSecondary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = date?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "",
                color = if (isToday) colors.text else colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )
            if (isToday) {
                Spacer(Modifier.width(8.dp))
                BoxDot()
                Spacer(Modifier.width(4.dp))
                Text("今天", color = colors.text, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = courseCountLabel(events.size),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (events.isNotEmpty() && restAfter < 0 && rest != null) RestBanner(rest)
        events.forEachIndexed { index, event ->
            CourseAgendaRow(
                event = event,
                slot = states[index],
                isNext = index == nextIndex,
                fullscreen = fullscreen,
                dark = dark,
                onTap = { onCourseTap(event) }
            )
            if (index == restAfter && rest != null) RestBanner(rest)
            Spacer(Modifier.height(if (fullscreen) 8.dp else 6.dp))
        }
        Box(Modifier.padding(top = if (events.isEmpty()) 2.dp else 4.dp)) {
            Hairline()
        }
    }
}

@Composable
private fun BoxDot() {
    Box(
        Modifier
            .width(4.dp)
            .height(4.dp)
            .background(LocalSuiSignColors.current.accent, CircleShape)
    )
}

@Composable
private fun CourseAgendaRow(
    event: ApiScheduleEvent,
    slot: ClassSlotState?,
    isNext: Boolean,
    fullscreen: Boolean,
    dark: Boolean,
    onTap: () -> Unit
) {
    val colors = LocalSuiSignColors.current
    val accent = courseAccent(event.courseName, dark)
    val tint = courseTint(event.courseName, dark)
    val isCurrent = slot == ClassSlotState.CURRENT
    val isPast = slot == ClassSlotState.PAST
    val ink = if (isPast) colors.textSecondary else accent
    val wash = when {
        isCurrent -> colors.signal.copy(alpha = 0.10f)
        // Keep the tint's own low alpha; forcing 0.5 turned a past row into a
        // saturated slab and dropped its grey text below readable contrast.
        isPast -> tint.copy(alpha = tint.alpha * 0.6f)
        else -> tint
    }
    val statusLabel = when {
        isCurrent -> "现在"
        isPast -> "已上完"
        isNext && slot == ClassSlotState.UPCOMING -> "下一节"
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(wash)
            .clickable(role = Role.Button, onClick = onTap)
            .padding(
                horizontal = if (fullscreen) 14.dp else 12.dp,
                vertical = if (fullscreen) 12.dp else 10.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .width(3.dp)
                .heightIn(min = if (fullscreen) 52.dp else 46.dp)
                .clip(CircleShape)
                .background(if (isPast) colors.textSecondary.copy(alpha = 0.4f) else accent)
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.width(if (fullscreen) 58.dp else 52.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = event.startTime,
                color = if (isPast) colors.textSecondary else colors.text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = if (fullscreen) 13.sp else 12.sp
                )
            )
            Text(
                text = event.endTime,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (fullscreen) 12.sp else 11.sp
                )
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.courseName,
                color = ink,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = if (fullscreen) 17.sp else 15.sp,
                    lineHeight = if (fullscreen) 22.sp else 20.sp
                ),
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (isPast) TextDecoration.LineThrough else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                statusLabel,
                event.sectionText.takeIf { it.isNotBlank() },
                event.location.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = if (isCurrent) colors.text else colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (fullscreen) 13.sp else 12.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
