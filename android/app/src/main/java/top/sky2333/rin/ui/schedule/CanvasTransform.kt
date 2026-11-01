package top.sky2333.rin.ui.schedule

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * Pure math for the week-table canvas: fit-to-viewport scaling and pan
 * clamping. Internal so the JVM tests can pin the edge cases the gestures
 * rely on (fit never enlarges, zoomed content never leaves the viewport).
 */

/** Zoom ceiling for the week canvas; 4x turns 10sp cell text into ~40sp. */
internal const val CANVAS_MAX_SCALE = 4f

/**
 * How far below fit the canvas may still shrink. Fit only guarantees the whole
 * week is visible; this is the "see it all at a glance" end of the range.
 */
internal const val CANVAS_MIN_RATIO = 0.55f

/** Zoom floor for a given fit scale. */
internal fun canvasMinScale(fit: Float): Float = fit * CANVAS_MIN_RATIO

/**
 * The scale that shows the whole canvas inside the viewport. Never enlarges
 * (fit caps at 1 — the grid's natural size is already the comfortable one) and
 * never shrinks below a floor where cell text stops being legible.
 */
internal fun computeCanvasFitScale(
    contentWidth: Float,
    contentHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float
): Float {
    if (contentWidth <= 0f || contentHeight <= 0f ||
        viewportWidth <= 0f || viewportHeight <= 0f
    ) {
        return 1f
    }
    val fit = min(viewportWidth / contentWidth, viewportHeight / contentHeight)
    return fit.coerceIn(0.3f, 1f)
}

/**
 * Top-start-anchored pan clamp: the scaled content may not detach from the
 * viewport. When the scaled content is smaller than the viewport on an axis
 * (only possible at fit scale), the slack is zero and the offset pins to 0 —
 * which is exactly the state where single-finger horizontal drags must stay
 * free for week swiping.
 */
internal fun clampCanvasOffset(
    offset: Offset,
    scale: Float,
    contentWidth: Float,
    contentHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float
): Offset {
    val slackX = max(contentWidth * scale - viewportWidth, 0f)
    val slackY = max(contentHeight * scale - viewportHeight, 0f)
    return Offset(
        offset.x.coerceIn(-slackX, 0f),
        offset.y.coerceIn(-slackY, 0f)
    )
}
