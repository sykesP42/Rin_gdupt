package top.sykesp42.rin.ui.pet

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.sykesp42.rin.data.PetWeather
import top.sykesp42.rin.data.PetWeatherSnapshot
import top.sykesp42.rin.ui.schedule.schoolZone
import top.sykesp42.rin.ui.theme.HeroStyle

private const val WEATHER_FRESH_MS = 3 * 60 * 60_000L
private const val ACTION_HOLD_MS = 40_000L
private const val EVENT_HOLD_MS = 22_000L
private const val REACTION_HOLD_MS = 6_000L

private val RIN_TAP_REACTIONS = listOf(
    PetAction.HEART,
    PetAction.BECKON,
    PetAction.STRETCH,
    PetAction.NOD,
)

enum class RinPerchSide { START, END }

/** Fractions of the cover box; the pet straddles the card edge, not the artwork. */
data class RinSpot(val x: Float, val y: Float, val side: RinPerchSide)

/**
 * Safe perches for each hero family. Split keeps Rin on the text panel's left
 * rim so the illustration on the right stays clean; cover/ticket use the right
 * rim. Each list runs high-to-low along the same edge, never across the art.
 */
internal fun rinSpots(hero: HeroStyle, artFocus: Float): List<RinSpot> {
    val focus = artFocus.coerceIn(0f, 1f)
    return when (hero) {
        HeroStyle.SPLIT -> listOf(
            RinSpot(x = 0.005f, y = 0.56f, side = RinPerchSide.START),
            RinSpot(x = 0.005f, y = 0.70f, side = RinPerchSide.START),
        )
        HeroStyle.TICKET -> listOf(
            RinSpot(x = 0.995f, y = 0.67f, side = RinPerchSide.END),
            RinSpot(x = 0.995f, y = 0.84f, side = RinPerchSide.END),
        )
        HeroStyle.COVER -> if (focus < 0.5f) {
            listOf(
                RinSpot(x = 0.995f, y = 0.72f, side = RinPerchSide.END),
                RinSpot(x = 0.995f, y = 0.86f, side = RinPerchSide.END),
            )
        } else {
            listOf(
                RinSpot(x = 0.005f, y = 0.38f, side = RinPerchSide.START),
                RinSpot(x = 0.005f, y = 0.52f, side = RinPerchSide.START),
            )
        }
        else -> listOf(
            RinSpot(x = 0.995f, y = 0.62f, side = RinPerchSide.END),
            RinSpot(x = 0.995f, y = 0.80f, side = RinPerchSide.END),
        )
    }
}

/**
 * High-to-low slot for the current context. Ready and success stay on the low
 * rim next to the action; rush hour and morning climb toward the date; night
 * settles low. With no context the two safe slots alternate with the action.
 */
internal fun rinSpotIndex(count: Int, event: PetEvent, hour: Int, rotation: Int): Int {
    if (count <= 1) return 0
    val night = hour >= 23 || hour < 6
    return when {
        event == PetEvent.SIGN_READY || event == PetEvent.SIGN_SUCCESS -> count - 1
        event == PetEvent.RUSH_TO_CLASS -> 0
        night -> count - 1
        hour in 6..8 -> 0
        else -> rotation.mod(count)
    }
}

/**
 * Rin on the cover rim. The sprite is a sibling of TodayMoodCover, so its
 * outside half can breathe into the page gutter; the inner half only grazes
 * the card edge. The perch follows sign state and time, the entrance is a
 * sideways peek, and tapping Rin answers with a short reaction.
 */
@Composable
fun PetPerch(
    enabled: Boolean,
    contextAware: Boolean,
    weather: PetWeatherSnapshot?,
    event: PetEvent,
    motion: Boolean,
    hero: HeroStyle,
    artFocus: Float,
    petSize: Dp,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val store = remember(context) { PetSpriteStore(context.applicationContext) }
    var hour by remember { mutableIntStateOf(ZonedDateTime.now(schoolZone).hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hour = ZonedDateTime.now(schoolZone).hour
        }
    }
    val freshWeather = weather
        ?.takeIf { System.currentTimeMillis() - it.fetchedAt < WEATHER_FRESH_MS }
        ?.weather
        ?: PetWeather.UNKNOWN
    val petContext = remember(hour, freshWeather, event, contextAware) {
        PetContext(
            hour = hour,
            weather = if (contextAware) freshWeather else PetWeather.UNKNOWN,
            event = if (contextAware) event else PetEvent.NONE,
        )
    }
    val candidates = remember(petContext) { PetDirector.candidates(petContext) }
    var rotation by remember(petContext) { mutableIntStateOf(0) }
    LaunchedEffect(petContext, motion) {
        rotation = 0
        if (!motion) return@LaunchedEffect
        while (true) {
            delay(if (petContext.event == PetEvent.NONE) ACTION_HOLD_MS else EVENT_HOLD_MS)
            rotation += 1
        }
    }
    val passiveAction = if (contextAware) candidates[rotation % candidates.size] else PetAction.LEAF
    val spots = remember(hero, artFocus) { rinSpots(hero, artFocus) }
    val spot = spots[rinSpotIndex(spots.size, petContext.event, petContext.hour, rotation)]

    var reaction by remember { mutableStateOf<PetAction?>(null) }
    LaunchedEffect(reaction) {
        if (reaction != null) {
            delay(REACTION_HOLD_MS)
            reaction = null
        }
    }
    val action = reaction ?: passiveAction
    val tapInteraction = remember { MutableInteractionSource() }
    val pressed by tapInteraction.collectIsPressedAsState()
    val tapScale by animateFloatAsState(
        targetValue = if (pressed && motion) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "rin_tap_scale",
    )
    val onTap: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        reaction = RIN_TAP_REACTIONS.filter { it != action }.random()
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(enabled, motion) {
        if (!enabled) {
            appeared = false
            return@LaunchedEffect
        }
        if (motion) delay(280)
        appeared = true
    }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val petPx = with(density) { petSize.toPx() }
        val placed = petPlacement(
            stageWidth = constraints.maxWidth.toFloat(),
            stageHeight = constraints.maxHeight.toFloat(),
            petPx = petPx,
            spotX = spot.x,
            spotY = spot.y,
            offsetX = offsetX,
            offsetY = offsetY
        )
        val targetX = placed.x
        val targetY = placed.y
        val x by animateFloatAsState(
            targetValue = targetX,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = 260f,
            ),
            label = "rin_perch_x",
        )
        val y by animateFloatAsState(
            targetValue = targetY,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = 260f,
            ),
            label = "rin_perch_y",
        )
        val hop = remember { Animatable(0f) }
        LaunchedEffect(petContext.event, motion, petPx) {
            hop.snapTo(0f)
            if (!motion || petContext.event != PetEvent.SIGN_SUCCESS) return@LaunchedEffect
            hop.animateTo(-petPx * 0.16f, tween(150))
            hop.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 360f))
        }
        val fromStart = spot.side == RinPerchSide.START
        val originX = if (fromStart) 0f else 1f
        val enter = if (motion) {
            slideInHorizontally(
                animationSpec = spring(
                    dampingRatio = 0.64f,
                    stiffness = 360f,
                ),
                initialOffsetX = { width -> if (fromStart) -width / 2 else width / 2 },
            ) + fadeIn(tween(220)) + scaleIn(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                initialScale = 0.86f,
                transformOrigin = TransformOrigin(originX, 0.84f),
            )
        } else {
            EnterTransition.None
        }
        val exit = if (motion) {
            slideOutHorizontally(
                animationSpec = tween(200),
                targetOffsetX = { width -> if (fromStart) -width / 2 else width / 2 },
            ) + fadeOut(tween(160)) + scaleOut(
                targetScale = 0.9f,
                transformOrigin = TransformOrigin(originX, 0.84f),
            )
        } else {
            ExitTransition.None
        }

        AnimatedVisibility(
            visible = enabled && appeared,
            enter = enter,
            exit = exit,
        ) {
            AnimatedContent(
                targetState = action,
                transitionSpec = {
                    if (motion) {
                        (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 10 }) togetherWith
                            fadeOut(tween(160))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "rin_perch_action",
            ) { current ->
                PetSprite(
                    action = current,
                    store = store,
                    motion = motion,
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), (y + hop.value).roundToInt()) }
                        .size(petSize)
                        .clickable(
                            interactionSource = tapInteraction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "和 Rin 互动",
                            onClick = onTap,
                        )
                        .graphicsLayer {
                            scaleX = tapScale
                            scaleY = tapScale
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .semantics { contentDescription = "Rin，轻点互动" },
                )
            }
        }
    }
}

@Composable
private fun PetSprite(
    action: PetAction,
    store: PetSpriteStore,
    motion: Boolean,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(action) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(action) {
        bitmap.value = withContext(Dispatchers.IO) { store.load(action.asset) }
    }
    val frame = remember(action, motion) { mutableIntStateOf(0) }
    LaunchedEffect(action, motion) {
        if (!motion) {
            frame.intValue = 0
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            frame.intValue = (((now - start) / 1_000_000L) / action.frameMillis)
                .toInt()
                .mod(action.frameCount)
        }
    }
    Canvas(modifier) {
        val sheet = bitmap.value ?: return@Canvas
        val cellWidth = sheet.width / action.columns
        val cellHeight = sheet.height / action.rows
        val index = frame.intValue
        drawImage(
            image = sheet,
            srcOffset = IntOffset(
                x = (index % action.columns) * cellWidth,
                y = (index / action.columns) * cellHeight,
            ),
            srcSize = IntSize(cellWidth, cellHeight),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None,
        )
    }
}

private class PetSpriteStore(context: Context) {
    private val assets = context.assets
    private val cache = LruCache<String, ImageBitmap>(4)

    fun load(asset: String): ImageBitmap? {
        cache.get(asset)?.let { return it }
        return runCatching {
            assets.open("pet/$asset.png").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()?.also { cache.put(asset, it) }
            }
        }.getOrNull()
    }
}

/**
 * Where the sprite's top-left goes, clamped so it never leaves the stage.
 *
 * The success hop lifts the sprite by 18% of its height, so the top clamp
 * reserves that much: at any scale or offset Rin stays fully visible.
 */
internal fun petPlacement(
    stageWidth: Float,
    stageHeight: Float,
    petPx: Float,
    spotX: Float,
    spotY: Float,
    offsetX: Float,
    offsetY: Float
): androidx.compose.ui.geometry.Offset {
    val hopMargin = petPx * 0.18f
    val maxX = (stageWidth - petPx).coerceAtLeast(0f)
    val maxY = (stageHeight - petPx - hopMargin).coerceAtLeast(0f)
    val minY = minOf(hopMargin, maxY)
    return androidx.compose.ui.geometry.Offset(
        (stageWidth * spotX - petPx / 2f + stageWidth * offsetX).coerceIn(0f, maxX),
        (stageHeight * spotY - petPx / 2f + stageHeight * offsetY).coerceIn(minY, maxY)
    )
}
