package top.sykesp42.rin.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import top.sykesp42.rin.data.ApiScheduleDay
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.ui.components.Hairline
import top.sykesp42.rin.ui.components.LocalCourseOverrides
import top.sykesp42.rin.ui.components.courseDisplayName
import top.sykesp42.rin.ui.schedule.CANVAS_MAX_SCALE
import top.sykesp42.rin.ui.schedule.ShareFormat
import top.sykesp42.rin.ui.schedule.ShareStyle
import top.sykesp42.rin.ui.schedule.WeekExport
import top.sykesp42.rin.ui.schedule.ExportPalette
import top.sykesp42.rin.ui.schedule.canvasMinScale
import top.sykesp42.rin.ui.schedule.clampCanvasOffset
import top.sykesp42.rin.ui.schedule.clockToMinutes
import top.sykesp42.rin.ui.schedule.computeCanvasFitScale
import top.sykesp42.rin.ui.schedule.parseSections
import top.sykesp42.rin.ui.schedule.sectionBounds
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.LocalVisualStyle
import top.sykesp42.rin.ui.theme.courseAccent
import top.sykesp42.rin.ui.theme.courseTint
import top.sykesp42.rin.ui.theme.RinColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt



/**
 * Seven-column full-week table on bare paper: the grid is drawn with hairlines,
 * courses use the shared rainbow inks with matching low-alpha tints, and there
 * is no outer card, padding or shadow. Consecutive sections of the same course
 * merge into one tall block.
 *
 * Gestures: horizontal swipe left/right calls [onSwipeWeek] (±1); a tap on a
 * course block calls [onCourseTap] with the actual [ApiScheduleEvent].
 */
private const val SECTIONS = 10
private val ROW_H = 48.dp

// Wide enough for an 11sp monospace clock at 1.3x font scale; at 360dp the seven
// day columns still get (360 - 46) / 7 ≈ 44.9dp, above the spec's 44dp block target.
private val RAIL_W = 46.dp

internal data class GridSpan(
    val event: ApiScheduleEvent,
    val startSection: Int,
    val span: Int
)

@Composable
fun WeekTable(
    days: List<ApiScheduleDay>,
    today: LocalDate?,
    nowMinutes: Int,
    modifier: Modifier = Modifier,
    onCourseTap: (ApiScheduleEvent) -> Unit = {},
    onSwipeWeek: (Int) -> Unit = {},
    rowHeight: Dp = ROW_H,
    canvasMode: Boolean = false
) {
    val colors = LocalRinColors.current
    val todayString = today?.toString()
    val byWeekday = remember(days) { days.associateBy { it.weekday } }
    val currentSwipe by rememberUpdatedState(onSwipeWeek)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // Canvas mode owns gestures in [WeekTableCanvas]: the content
                // renders at natural height (no inner scroll) and week
                // swiping/panning are one authority instead of two.
                if (canvasMode) Modifier else Modifier.pointerInput(Unit) {
                    var dragAccum = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onHorizontalDrag = { change, amount ->
                            dragAccum += amount
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragAccum < -140f) currentSwipe(1)
                            else if (dragAccum > 140f) currentSwipe(-1)
                        }
                    )
                }
            )
    ) {
        WeekHeader(
            byWeekday = byWeekday,
            todayString = todayString,
            colors = colors
        )
        Hairline()
        WeekBody(
            byWeekday = byWeekday,
            todayString = todayString,
            nowMinutes = nowMinutes,
            colors = colors,
            rowHeight = rowHeight,
            onCourseTap = onCourseTap,
            canvasMode = canvasMode,
            modifier = if (canvasMode) Modifier else Modifier.weight(1f)
        )
    }
}

/** Seven weekday + day-of-month cells. Today is ink with a sky dot, never signal text. */
@Composable
private fun WeekHeader(
    byWeekday: Map<Int, ApiScheduleDay>,
    todayString: String?,
    colors: RinColors
) {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.width(RAIL_W))
        labels.forEachIndexed { index, label ->
            val day = byWeekday[index + 1]
            val isToday = day?.date == todayString
            val date = parseScheduleDate(day?.date.orEmpty())
            val dayNumber = date?.dayOfMonth?.toString()
                ?: day?.date?.takeLast(2)?.trimStart('0').orEmpty()
            val description = buildString {
                append("周").append(label)
                if (dayNumber.isNotBlank()) append(" ").append(dayNumber).append(" 日")
                if (isToday) append(" 今天")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(4.dp)
                        .background(
                            if (isToday) colors.accent else Color.Transparent,
                            CircleShape
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    color = if (isToday) colors.text else colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
                Text(
                    text = dayNumber,
                    color = if (isToday) colors.text else colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Time rail + seven day columns. In normal mode the body scrolls vertically
 * inside its own viewport (no nested scrollable); horizontal drags belong to
 * [WeekTable]. In canvas mode the content renders at natural height — the
 * wrapper scales it instead, so a whole week fits the viewport with zero
 * scrolling.
 */
@Composable
private fun WeekBody(
    byWeekday: Map<Int, ApiScheduleDay>,
    todayString: String?,
    nowMinutes: Int,
    colors: RinColors,
    rowHeight: Dp,
    onCourseTap: (ApiScheduleEvent) -> Unit,
    canvasMode: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val dark = colors.canvas.luminance() < 0.4f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (canvasMode) Modifier else Modifier.verticalScroll(scrollState))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * SECTIONS)
                .drawBehind {
                    val rail = RAIL_W.toPx()
                    val column = (size.width - rail) / 7f
                    val stroke = 1.dp.toPx()
                    for (index in 0..6) {
                        val x = rail + column * index
                        drawLine(
                            color = colors.border,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = stroke
                        )
                    }
                    for (section in 1..SECTIONS) {
                        val y = rowHeight.toPx() * section
                        drawLine(
                            color = colors.border,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = stroke
                        )
                    }
                }
        ) {
            SectionRail(colors = colors, rowHeight = rowHeight)
            for (weekday in 1..7) {
                val day = byWeekday[weekday]
                val isToday = day?.date == todayString
                DayColumn(
                    events = day?.events.orEmpty(),
                    isToday = isToday,
                    nowMinutes = nowMinutes,
                    colors = colors,
                    dark = dark,
                    rowHeight = rowHeight,
                    onCourseTap = onCourseTap,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SectionRail(colors: RinColors, rowHeight: Dp) {
    Column(modifier = Modifier.width(RAIL_W)) {
        for (section in 1..SECTIONS) {
            val start = sectionBounds[section]?.first ?: 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .clearAndSetSemantics {
                        contentDescription = "第 $section 节 ${clockText(start)}"
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = section.toString(),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                    Text(
                        text = clockText(start),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            letterSpacing = 0.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    events: List<ApiScheduleEvent>,
    isToday: Boolean,
    nowMinutes: Int,
    colors: RinColors,
    dark: Boolean,
    rowHeight: Dp,
    onCourseTap: (ApiScheduleEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val spans = remember(events) { buildSpans(events) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(
                if (isToday) {
                    colors.accent.copy(alpha = if (dark) 0.07f else 0.035f)
                } else {
                    Color.Transparent
                }
            )
    ) {
        var cursor = 1
        spans.forEach { span ->
            if (cursor < span.startSection) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight * (span.startSection - cursor))
                )
            }
            val isNow = isToday && spanCoversMinute(span, nowMinutes)
            val isPast = isToday && !isNow &&
                (clockToMinutes(span.event.endTime)?.let { it < nowMinutes } == true)
            CourseCell(
                span = span,
                isNow = isNow,
                isPast = isPast,
                colors = colors,
                dark = dark,
                height = rowHeight * span.span,
                onTap = { onCourseTap(span.event) }
            )
            cursor = span.startSection + span.span
        }
        if (cursor <= SECTIONS) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight * (SECTIONS - cursor + 1))
            )
        }
    }
}

@Composable
private fun CourseCell(
    span: GridSpan,
    isNow: Boolean,
    isPast: Boolean,
    colors: RinColors,
    dark: Boolean,
    height: Dp,
    onTap: () -> Unit
) {
    val shape = RoundedCornerShape(3.dp)
    val animations = LocalAnimationsEnabled.current
    val pulse = rememberInfiniteTransition(label = "now_pulse")
    val pulseAnimated by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "now_pulse_alpha"
    )
    val pulseAlpha = if (animations) pulseAnimated else 1f
    val displayName = courseDisplayName(span.event.courseName, LocalCourseOverrides.current)
    val tint = when {
        isNow -> colors.accent.copy(alpha = if (dark) 0.20f else 0.12f)
        // A past cell keeps the course tint at its normal low alpha; forcing a
        // high alpha turned it into a slab and made the faded label unreadable.
        isPast -> courseTint(displayName, dark).let { it.copy(alpha = it.alpha * 0.6f) }
        else -> courseTint(displayName, dark)
    }
    val accent = courseAccent(displayName, dark)
    val description = courseDescription(span)
    // Tap and semantics sit on the full-width cell before the 2dp visual
    // inset, so at 360dp the target is the whole 46dp column (the 7-column
    // overview's 44dp exception), not the 42dp tinted block.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(role = Role.Button, onClick = onTap)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = "打开课程详情") {
                    onTap()
                    true
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 1.dp)
                .clip(shape)
                .background(tint)
                .drawBehind {
                    drawRect(
                        color = accent,
                        size = Size(2.dp.toPx(), size.height)
                    )
                    if (isNow) {
                        // Gentle breathing on the "in class" marker; gated by
                        // the app-wide 动效 switch.
                        drawRect(
                            color = colors.signal,
                            topLeft = Offset(2.dp.toPx(), 0f),
                            size = Size(2.dp.toPx(), size.height),
                            alpha = pulseAlpha
                        )
                    }
                }
                .clipToBounds()
        ) {
            // A single 64dp row cannot carry three 11sp course lines plus the room
            // line once the system font scale is up, so large type drops the room
            // line (kept in TalkBack and the course sheet) and gives the space to
            // the course name instead.
            val largeType = LocalDensity.current.fontScale >= 1.15f
            val quietCell = largeType && height < 84.dp
            val baseSize = when {
                height >= 120.dp -> 12.sp
                height >= 64.dp -> 11.sp
                else -> 10.sp
            }
            val nameSize = if (displayName.length > 10 && height < 180.dp) 10.sp else baseSize
            val nameLines = when {
                quietCell -> 2
                height >= 180.dp -> 8
                height >= 120.dp -> 5
                else -> 3
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (isNow) 7.dp else 5.dp,
                        end = 3.dp,
                        top = 2.dp,
                        bottom = 2.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = displayName,
                    color = accent,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = nameSize,
                        lineHeight = (nameSize.value + 3).sp,
                        letterSpacing = 0.sp
                    ),
                    fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = nameLines,
                    overflow = TextOverflow.Ellipsis
                )
                // At large font scales every extra line steals a row of course
                // name; the room stays available in the day view and detail sheet.
                if (!largeType && !quietCell && span.event.location.isNotBlank()) {
                    Text(
                        text = compactLocation(span.event.location),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = if (height >= 120.dp) 11.sp else 10.sp,
                            lineHeight = 13.sp,
                            letterSpacing = 0.sp
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = if (height >= 120.dp) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * The room line in a 44dp column only fits a few glyphs. `二教A-505` keeps its
 * building letter in the room code, so the table drops the redundant prefix and
 * shows `A-505`; any other room keeps its full name. TalkBack still reads the
 * full location from [courseDescription].
 */
internal fun compactLocation(value: String): String {
    val trimmed = value.trim()
    val suffix = trimmed.substringAfterLast('教')
    val roomCode = suffix.length in 3..6 &&
        suffix[0].isLetter() &&
        suffix[1] == '-' &&
        suffix.drop(2).all { it.isDigit() }
    // A zero-width space lets a five-glyph code wrap as `B-` / `1001` instead
    // of breaking mid-number when the column is too narrow.
    return if (roomCode) suffix.replaceFirst("-", "-\u200B") else trimmed
}

private fun courseDescription(span: GridSpan): String {
    val event = span.event
    val sections = if (span.span == 1) {
        "第 ${span.startSection} 节"
    } else {
        "第 ${span.startSection}-${span.startSection + span.span - 1} 节"
    }
    val time = when {
        event.startTime.isBlank() -> ""
        event.endTime.isBlank() -> event.startTime
        else -> "${event.startTime}-${event.endTime}"
    }
    return listOf(event.courseName, sections, time, event.location)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

internal fun sectionsOf(event: ApiScheduleEvent): List<Int> =
    parseSections(event.sectionText)

/** Merge occupied rows per weekday into runs of the same course. */
internal fun buildSpans(events: List<ApiScheduleEvent>): List<GridSpan> {
    val occupied = arrayOfNulls<ApiScheduleEvent>(SECTIONS + 1)
    events.sortedBy { sectionsOf(it).firstOrNull() ?: 99 }.forEach { event ->
        sectionsOf(event).forEach { section ->
            if (section in 1..SECTIONS && occupied[section] == null) occupied[section] = event
        }
    }
    val spans = mutableListOf<GridSpan>()
    var section = 1
    while (section <= SECTIONS) {
        val event = occupied[section]
        if (event == null) {
            section++
            continue
        }
        var end = section
        while (end + 1 <= SECTIONS && occupied[end + 1] === event) end++
        spans += GridSpan(
            event = event,
            startSection = section,
            span = end - section + 1
        )
        section = end + 1
    }
    return spans
}

private fun parseScheduleDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()

private fun clockText(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

/**
 * The week table as a canvas. Default state is FIT: the whole week (10
 * sections + header) is scaled to fill the viewport, so nothing scrolls.
 * Pinch zooms around the fingers; once zoomed past fit a single finger pans
 * (clamped so the canvas never detaches); at fit a single horizontal swipe
 * still switches weeks, and taps always reach the course cells.
 */
@Composable
fun WeekTableCanvas(
    days: List<ApiScheduleDay>,
    today: LocalDate?,
    nowMinutes: Int,
    modifier: Modifier = Modifier,
    onCourseTap: (ApiScheduleEvent) -> Unit = {},
    onSwipeWeek: (Int) -> Unit = {},
    onShare: (String) -> Unit = {},
    weekLabel: String = "",
    shareStyle: ShareStyle = ShareStyle.PLAIN,
    shareOwner: String = "",
    appVersion: String = "",
    shareFormat: ShareFormat = ShareFormat.ROWS,
    rowHeight: Dp = ROW_H
) {
    val colors = LocalRinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = ExportPalette(
        canvas = colors.canvas.toArgb(),
        surface = colors.surface.toArgb(),
        text = colors.text.toArgb(),
        textSecondary = colors.textSecondary.toArgb(),
        signal = colors.signal.toArgb(),
        border = colors.border.toArgb(),
        dark = LocalVisualStyle.current.spec.dark
    )
    val currentSwipe by rememberUpdatedState(onSwipeWeek)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val fit = remember(contentSize, viewport) {
        computeCanvasFitScale(
            contentSize.width.toFloat(), contentSize.height.toFloat(),
            viewport.width.toFloat(), viewport.height.toFloat()
        )
    }
    // Paper-slide on week change: the new week drifts in from the direction
    // of the swipe. Zero when 动效 is off.
    val animations = LocalAnimationsEnabled.current
    val weekSignature = days.firstOrNull()?.date.orEmpty() + "#" + days.lastOrNull()?.date.orEmpty()
    var lastWeekSignature by remember { mutableStateOf<String?>(null) }
    val slide = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(weekSignature) {
        val previous = lastWeekSignature
        lastWeekSignature = weekSignature
        if (!animations || previous == null || previous == weekSignature) return@LaunchedEffect
        val direction = if (previous < weekSignature) -1f else 1f
        slide.snapTo(direction * 0.12f)
        slide.animateTo(0f, androidx.compose.animation.core.tween(260))
    }

    // The fit scale is the zoom floor. When the viewport or content changes
    // (first measure, rotation, fold), follow the new fit unless the user had
    // deliberately zoomed away from the previous one.
    var trackedFit by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(fit) {
        if (abs(scale - trackedFit) < 0.001f) {
            scale = fit
            offset = Offset.Zero
        } else {
            scale = scale.coerceIn(canvasMinScale(fit), CANVAS_MAX_SCALE)
            offset = clampCanvasOffset(
                offset, scale,
                contentSize.width.toFloat(), contentSize.height.toFloat(),
                viewport.width.toFloat(), viewport.height.toFloat()
            )
        }
        trackedFit = fit
    }

    fun applyTransform(prevCentroid: Offset, newCentroid: Offset, rawScale: Float) {
        val nextScale = rawScale.coerceIn(canvasMinScale(fit), CANVAS_MAX_SCALE)
        // Keep the content point under the fingers anchored to the centroid,
        // so zoom grows around the pinch instead of the top-left corner.
        val anchor = (prevCentroid - offset) / scale
        scale = nextScale
        offset = clampCanvasOffset(
            newCentroid - anchor * nextScale, nextScale,
            contentSize.width.toFloat(), contentSize.height.toFloat(),
            viewport.width.toFloat(), viewport.height.toFloat()
        )
    }

    val view = LocalView.current
    val hapticTick = {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Paints a share card from the week's data, then hands it to the chooser.
     * Falls back to plain text if the bitmap or the file cannot be produced.
     */
    fun sharePng() {
        scope.launch {
            val bitmap = runCatching {
                withContext(Dispatchers.Default) {
                    WeekExport.renderTable(
                        weekLabel = weekLabel.ifBlank { "本周" },
                        days = days,
                        palette = palette,
                        style = shareStyle,
                        owner = shareOwner,
                        version = appVersion,
                        format = shareFormat
                    )
                }
            }.getOrNull()
            val shared = bitmap != null &&
                WeekExport.share(context, bitmap, weekLabel.ifBlank { "我的课表" })
            if (!shared) onShare(weekAsText(days))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { viewport = it }
                .pointerInput(fit, contentSize) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var prevCentroid: Offset? = null
                        var prevSpan = 0f
                        var swipeAccum = 0f
                        var claimed = false
                        var multi = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val centroid = canvasCentroid(pressed)
                            var moved = false
                            if (pressed.size >= 2) {
                                multi = true
                                val span = canvasSpan(pressed)
                                if (prevSpan > 0f && span > 0f) {
                                    applyTransform(prevCentroid ?: centroid, centroid, scale * span / prevSpan)
                                    moved = true
                                }
                                prevSpan = span
                            } else {
                                prevSpan = 0f
                                if (scale > fit + 0.001f) {
                                    // Zoomed in: one finger pans the canvas.
                                    val prev = prevCentroid ?: centroid
                                    if ((centroid - prev).getDistance() > 0.5f) {
                                        applyTransform(prev, centroid, scale)
                                        moved = true
                                    }
                                } else if (!multi) {
                                    // At fit: horizontal travel accumulates for
                                    // week swiping; claiming cancels cell taps.
                                    swipeAccum += centroid.x - (prevCentroid?.x ?: centroid.x)
                                    if (abs(swipeAccum) > 24f) claimed = true
                                    if (claimed) moved = true
                                }
                            }
                            prevCentroid = centroid
                            if (moved) pressed.forEach { it.consume() }
                        }
                        if (!multi && claimed && abs(swipeAccum) > 140f) {
                            hapticTick()
                            currentSwipe(if (swipeAccum < 0f) 1 else -1)
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The natural content height exceeds the viewport's max
                    // constraint; without unbounded measurement the Box would
                    // be capped at the viewport and fit-scale could never see
                    // the real content size.
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = offset.x + slide.value * size.width
                        translationY = offset.y
                        alpha = 1f - abs(slide.value) * 2f
                    }
            ) {
                WeekTable(
                    days = days,
                    today = today,
                    nowMinutes = nowMinutes,
                    onCourseTap = onCourseTap,
                    rowHeight = rowHeight,
                    canvasMode = true,
                    // Recorded before the transform, so a share always shows the
                    // whole week at fit instead of whichever corner was zoomed.
                    // Recording is opt-in: rasterising the layer every frame
                    // would double the cost of an already heavy canvas.
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { contentSize = it }
                )
            }

            CanvasCornerControls(
                zoomed = abs(scale - fit) > 0.001f,
                percent = if (fit > 0f) (scale / fit * 100f).toInt() else 100,
                onReset = {
                    scale = fit
                    trackedFit = fit
                    offset = Offset.Zero
                },
                onShare = { sharePng() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }
    }
}

/** The zoom read-out doubles as the reset, so it only exists while zoomed. */
@Composable
private fun CanvasCornerControls(
    zoomed: Boolean,
    percent: Int,
    onReset: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.surface.copy(alpha = 0.92f))
            .border(1.dp, colors.border, CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (zoomed) {
            Text(
                text = "$percent%",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onReset)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onShare),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "分享课表",
                tint = colors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/** Plain-text week, used whenever the canvas cannot be captured or written. */
internal fun weekAsText(days: List<ApiScheduleDay>): String = buildString {
    days.forEach { day ->
        appendLine(day.date)
        if (day.events.isEmpty()) {
            appendLine("  无课")
        } else {
            day.events.forEach { event ->
                appendLine("  ${event.startTime}-${event.endTime} ${event.courseName} ${event.location}".trimEnd())
            }
        }
    }
}


private fun canvasCentroid(points: List<PointerInputChange>): Offset =
    if (points.size == 1) {
        points[0].position
    } else {
        Offset(
            points.fold(0f) { acc, change -> acc + change.position.x } / points.size,
            points.fold(0f) { acc, change -> acc + change.position.y } / points.size
        )
    }

private fun canvasSpan(points: List<PointerInputChange>): Float {
    if (points.size < 2) return 0f
    val dx = points[0].position.x - points[1].position.x
    val dy = points[0].position.y - points[1].position.y
    return sqrt(dx * dx + dy * dy)
}

private fun spanCoversMinute(span: GridSpan, nowMinutes: Int): Boolean {
    val start = sectionBounds[span.startSection]?.first ?: return false
    val end = sectionBounds[span.startSection + span.span - 1]?.second ?: return false
    return start <= nowMinutes && nowMinutes <= end
}

