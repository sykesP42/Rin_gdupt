package top.sykesp42.rin.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.sykesp42.rin.R
import top.sykesp42.rin.data.ApiScheduleOverview
import top.sykesp42.rin.data.LocalCache
import top.sykesp42.rin.ui.schedule.nowMinutesAt
import top.sykesp42.rin.ui.schedule.todayAnchor
import top.sykesp42.rin.ui.schedule.todayAtCampusClock
import top.sykesp42.rin.ui.theme.LocalAnimationsEnabled
import top.sykesp42.rin.ui.theme.RinTokens

// A2 palette (companion/design/tokens.json → RinTokens). The paper and the veil mirror
// `RinBackdrop`'s surface→canvas gradient so the upward reveal lands on the Today page
// without a colour step. Muted is the AA-safe secondary, not the old #6B7C8F.
private val SplashPaper = RinTokens.canvas
private val SplashVeilTop = RinTokens.surface
private val SplashVeilBottom = RinTokens.canvas
private val SplashSky = RinTokens.accent
private val SplashSkyDeep = RinTokens.signal
private val SplashInk = RinTokens.text
private val SplashMuted = RinTokens.textSecondary
private val SplashLine = RinTokens.border
private val SplashCream = RinTokens.warm

private data class SplashPalette(
    val paper: Color,
    val veilTop: Color,
    val veilBottom: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val cream: Color
)

private val LightSplashPalette = SplashPalette(
    paper = SplashPaper,
    veilTop = SplashVeilTop,
    veilBottom = SplashVeilBottom,
    ink = SplashInk,
    muted = SplashMuted,
    line = SplashLine,
    cream = SplashCream
)

/** Black paper for dark mode; the blue bloom and its timing stay identical. */
private val DarkSplashPalette = SplashPalette(
    paper = Color(0xFF06090D),
    veilTop = Color(0xFF0A0F16),
    veilBottom = Color(0xFF06090D),
    ink = Color(0xFFF2F6FA),
    muted = Color(0xFFA4B4C5),
    line = Color(0xFF263340),
    cream = Color(0xFF121109)
)

private val WelcomeLines = listOf(
    "今天也从容一点。",
    "上课和提醒交给 Rin，时间留给自己。",
    "早八也能慢慢来。",
    "不慌，时间还够。",
    "今天也要好好上课。",
    "琐事交给 Rin，轻一点走。",
    "新的一天，慢慢展开。",
    "慢慢来，比较快。"
)

/**
 * Rin splash: the icon's sky blue blooms out of the logo, then settles into a
 * paper wash. The wordmark, today's courses and a quiet greeting follow.
 */
@Composable
fun RinSplash(dark: Boolean, onFinished: () -> Unit) {
    val palette = if (dark) DarkSplashPalette else LightSplashPalette
    val context = LocalContext.current
    val view = LocalView.current
    val finish by rememberUpdatedState(onFinished)
    val animationsEnabled = LocalAnimationsEnabled.current
    val welcome = rememberSaveable { WelcomeLines.random() }

    var schedule by remember { mutableStateOf<SplashScheduleState>(SplashScheduleState.Loading) }
    var stage by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var logoCenter by remember { mutableStateOf<Offset?>(null) }

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.86f) }
    val bloom = remember { Animatable(0f) }
    val veil = remember { Animatable(0f) }

    val pulse = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "splash_pulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "splash_pulse_value"
        ).value
    } else {
        0.5f
    }

    val contentSpec = if (animationsEnabled) {
        tween<Float>(durationMillis = 520, easing = FastOutSlowInEasing)
    } else {
        tween<Float>(0)
    }
    val wordReveal by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = contentSpec,
        label = "splash_word"
    )
    val scheduleReveal by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(480, easing = FastOutSlowInEasing) else tween(0),
        label = "splash_schedule"
    )
    val welcomeReveal by animateFloatAsState(
        targetValue = if (stage >= 3) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(520, easing = FastOutSlowInEasing) else tween(0),
        label = "splash_welcome"
    )

    LaunchedEffect(Unit) {
        schedule = withContext(Dispatchers.IO) { loadTodaySchedule(context.applicationContext) }
    }

    LaunchedEffect(Unit) {
        val controller = context.findActivity()
            ?.window
            ?.let { WindowCompat.getInsetsController(it, view) }

        fun systemIcons(forDarkBackground: Boolean) {
            controller?.apply {
                isAppearanceLightStatusBars = !forDarkBackground
                isAppearanceLightNavigationBars = !forDarkBackground
            }
        }

        if (!animationsEnabled) {
            systemIcons(dark)
            logoAlpha.snapTo(1f)
            logoScale.snapTo(1f)
            bloom.snapTo(1f)
            veil.snapTo(if (dark) 0.96f else 0.90f)
            stage = 3
            delay(900)
            if (!finished) {
                finished = true
                finish()
            }
            return@LaunchedEffect
        }

        systemIcons(dark)
        launch {
            logoAlpha.animateTo(1f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            delay(150)
            bloom.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(700)
            veil.animateTo(
                targetValue = if (dark) 0.96f else 0.90f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
        if (!dark) {
            launch {
                snapshotFlow { bloom.value }.first { it >= 0.5f }
                systemIcons(true)
            }
            launch {
                snapshotFlow { veil.value }.first { it >= 0.55f }
                systemIcons(false)
            }
        }

        delay(1200)
        stage = 1
        delay(220)
        stage = 2
        delay(220)
        stage = 3
        delay(620)
        if (!finished) {
            finished = true
            finish()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(palette.paper)
    ) {
        val compact = maxHeight < 640.dp
        val logoSize = if (compact) 88.dp else 104.dp
        val logoShape = RoundedCornerShape(if (compact) 24.dp else 28.dp)
        val titleSize = if (compact) 54.sp else 66.sp
        val rowLimit = if (compact) 2 else 3
        val contentPadding = if (compact) 24.dp else 30.dp

        Canvas(Modifier.fillMaxSize()) {
            val center = logoCenter ?: Offset(size.width / 2f, size.height * 0.30f)
            val maxRadius = hypot(
                max(center.x, size.width - center.x),
                max(center.y, size.height - center.y)
            ) * 1.65f

            if (bloom.value > 0f) {
                val radius = maxRadius * bloom.value
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to SplashSky,
                        0.78f to SplashSkyDeep,
                        1f to SplashSkyDeep.copy(alpha = 0f),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            if (veil.value > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            palette.veilTop.copy(alpha = veil.value),
                            palette.veilBottom.copy(alpha = veil.value)
                        ),
                        startY = 0f,
                        endY = size.height
                    )
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SplashSky.copy(alpha = 0.10f + pulse * 0.035f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = if (compact) 220.dp.toPx() else 300.dp.toPx()
                ),
                radius = if (compact) 220.dp.toPx() else 300.dp.toPx(),
                center = center
            )

            // Same warm corner light as RinBackdrop, so the two pages read as one paper.
            val warmCenter = Offset(size.width * 0.06f, size.height * 0.92f)
            val warmRadius = size.width * 0.78f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.cream.copy(alpha = if (dark) 0.22f else 0.11f),
                        Color.Transparent
                    ),
                    center = warmCenter,
                    radius = warmRadius
                ),
                radius = warmRadius,
                center = warmCenter
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = contentPadding, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.95f))

            Box(
                modifier = Modifier
                    .size(logoSize)
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInRoot()
                        logoCenter = Offset(
                            position.x + coordinates.size.width / 2f,
                            position.y + coordinates.size.height / 2f
                        )
                    }
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        translationY = -pulse * 2.dp.toPx()
                    }
                    .shadow(
                        elevation = 18.dp,
                        shape = logoShape,
                        ambientColor = SplashSky.copy(alpha = 0.28f),
                        spotColor = SplashSky.copy(alpha = 0.28f)
                    )
                    .clip(logoShape)
                    .background(SplashSky)
            ) {
                Image(
                    painter = painterResource(R.drawable.rin_logo),
                    contentDescription = "Rin",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, palette.ink.copy(alpha = if (dark) 0.28f else 0.55f), logoShape)
                )
            }

            Spacer(Modifier.height(if (compact) 14.dp else 20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = wordReveal
                        translationY = (1f - wordReveal) * 14.dp.toPx()
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Rin",
                    color = palette.ink,
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = titleSize,
                        letterSpacing = (-2.0).sp
                    )
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .padding(bottom = if (compact) 7.dp else 11.dp)
                        .size(if (compact) 6.dp else 7.dp)
                        .background(SplashSky, CircleShape)
                )
            }

            Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
            Box(
                Modifier
                    .width(30.dp)
                    .height(1.dp)
                    .background(palette.line)
            )
            Spacer(Modifier.height(if (compact) 22.dp else 30.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = scheduleReveal
                        translationY = (1f - scheduleReveal) * 18.dp.toPx()
                    },
                contentAlignment = Alignment.Center
            ) {
                ScheduleBlock(
                    state = schedule,
                    rowLimit = rowLimit,
                    compact = compact,
                    palette = palette
                )
            }

            Spacer(Modifier.weight(1.05f))

            Row(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = welcomeReveal
                        translationY = (1f - welcomeReveal) * 10.dp.toPx()
                    }
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .size(4.dp)
                        .background(SplashSky, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = welcome,
                    color = palette.muted,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.2.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ScheduleBlock(
    state: SplashScheduleState,
    rowLimit: Int,
    compact: Boolean,
    palette: SplashPalette
) {
    val fallbackDateLabel = remember {
        todayAtCampusClock().format(DateTimeFormatter.ofPattern("M月d日 · E", Locale.CHINA))
    }
    val dateLabel = (state as? SplashScheduleState.Ready)?.dateLabel ?: fallbackDateLabel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "今日课程",
                color = palette.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = dateLabel,
                color = palette.muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.line)
        )
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))

        when (state) {
            SplashScheduleState.Loading -> ScheduleHint("正在整理今日课程", palette = palette)
            SplashScheduleState.Missing -> ScheduleHint("课表尚未同步", "进入后会加载今天的课", palette)
            SplashScheduleState.Empty -> ScheduleHint("今天没有课", "把时间留给自己", palette)
            is SplashScheduleState.Ready -> {
                state.items.take(rowLimit).forEach { course ->
                    CourseRow(course = course, compact = compact, palette = palette)
                }
                val remaining = state.total - rowLimit.coerceAtMost(state.items.size)
                if (remaining > 0) {
                    Text(
                        text = "还有 $remaining 门课",
                        color = palette.muted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleHint(
    title: String,
    detail: String? = null,
    palette: SplashPalette
) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            color = palette.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        if (detail != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = detail,
                color = palette.muted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CourseRow(course: SplashCourse, compact: Boolean, palette: SplashPalette) {
    val dimmed = course.status == SplashCourseStatus.Past
    // No alpha-diluted text: 0.45–0.9 fades land under 4.5:1 at 10–14sp on the paper.
    // The hierarchy is ink vs. the token secondary; the sky accent stays in the dot only.
    val nameColor = if (dimmed) palette.muted else palette.ink
    val timeColor = if (course.status == SplashCourseStatus.Current) palette.ink else palette.muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 5.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = course.start,
            modifier = Modifier.width(if (compact) 46.dp else 52.dp),
            color = timeColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = course.name,
                color = nameColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (course.location.isNotBlank() && !compact) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = course.location,
                    color = palette.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        when (course.status) {
            SplashCourseStatus.Current -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(4.dp)
                        .background(SplashSky, CircleShape)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "正在上",
                    color = palette.ink,
                    fontSize = 11.sp,
                    letterSpacing = 0.4.sp
                )
            }
            SplashCourseStatus.Next -> Text(
                text = "下一节",
                color = palette.muted,
                fontSize = 11.sp,
                letterSpacing = 0.4.sp
            )
            else -> Unit
        }
    }
}

private sealed interface SplashScheduleState {
    data object Loading : SplashScheduleState
    data object Missing : SplashScheduleState
    data object Empty : SplashScheduleState
    data class Ready(
        val items: List<SplashCourse>,
        val total: Int,
        val dateLabel: String
    ) : SplashScheduleState
}

private data class SplashCourse(
    val start: String,
    val name: String,
    val location: String,
    val status: SplashCourseStatus
)

private enum class SplashCourseStatus {
    Current,
    Next,
    Past,
    Upcoming
}

private fun loadTodaySchedule(context: Context): SplashScheduleState {
    val overview = runCatching {
        LocalCache(context).read("schedule_week0", ApiScheduleOverview.serializer())?.second
    }.getOrNull() ?: return SplashScheduleState.Missing

    val deviceDay = todayAtCampusClock().toString()
    val events = overview.today.ifEmpty {
        overview.days.firstOrNull { it.date == overview.date }?.events
            ?: overview.days.firstOrNull { it.date == deviceDay }?.events
            ?: emptyList()
    }
    if (events.isEmpty()) return SplashScheduleState.Empty
    val scheduleDate = todayAnchor(overview.date) ?: todayAtCampusClock()
    val dateLabel = scheduleDate.format(DateTimeFormatter.ofPattern("M月d日 · E", Locale.CHINA))

    val sorted = events.sortedBy { startMinutes(it.startTime) }
    val now = nowMinutesAt()
    val currentIndex = sorted.indexOfLast { event ->
        startMinutes(event.startTime) <= now && now <= endMinutes(event.endTime)
    }
    val nextIndex = sorted.indexOfFirst { startMinutes(it.startTime) > now }
    val items = sorted.mapIndexed { index, event ->
        val status = when {
            index == currentIndex -> SplashCourseStatus.Current
            index == nextIndex && currentIndex < 0 -> SplashCourseStatus.Next
            endMinutes(event.endTime) < now -> SplashCourseStatus.Past
            else -> SplashCourseStatus.Upcoming
        }
        SplashCourse(
            start = event.startTime,
            name = event.courseName,
            location = event.location,
            status = status
        )
    }
    return SplashScheduleState.Ready(items = items, total = items.size, dateLabel = dateLabel)
}

private fun startMinutes(clock: String): Int = clockMinutes(clock)

private fun endMinutes(clock: String): Int = clockMinutes(clock)

private fun clockMinutes(clock: String): Int {
    val parts = clock.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
        (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
