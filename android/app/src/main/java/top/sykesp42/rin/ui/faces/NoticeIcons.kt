package top.sykesp42.rin.ui.faces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import kotlin.math.min

/**
 * Composes the notification's colour icon from the face atlas.
 *
 * Two things the system does badly for pixel art, we do on purpose: it resamples
 * any bitmap larger than the large-icon slot with bilinear filtering (which turns
 * hard pixels into mud), and OEM shades may mask the icon into a circle. So the
 * bitmap arrives already at the slot size, scaled nearest-neighbour, and trimmed
 * to a circle on the plate the user picked.
 */
object NoticeIcons {
    private const val COLUMNS = 8
    private const val CELL = 192

    @Volatile
    private var sheets: Map<String, Bitmap?> = emptyMap()
    private val cache = LruCache<String, Bitmap>(32)

    /** The system's own large-icon slot, so our bitmap needs no resample at all. */
    fun slotPx(context: Context): Int =
        runCatching { context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width) }
            .getOrDefault((40 * context.resources.displayMetrics.density).toInt())
            .coerceAtLeast(32)

    fun largeIcon(context: Context, icon: NoticeIcon): Bitmap {
        val px = slotPx(context)
        val outfit = NoticeFaces.prefs.value.outfit
        return cached("n-${outfit.key}-${icon.ordinal}@$px") {
            val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val circle = Path().apply { addCircle(px / 2f, px / 2f, px / 2f, Path.Direction.CW) }
            NoticeFaces.plateArgb(context)?.let { plate ->
                canvas.drawPath(circle, Paint().apply { color = plate })
            }
            canvas.clipPath(circle)
            drawCell(context, canvas, outfit, icon, RectF(0f, 0f, px.toFloat(), px.toFloat()))
            out
        }
    }

    /** Raw square cell for in-app badges; [outfit] defaults to the user's choice. */
    fun cellBitmap(context: Context, icon: NoticeIcon, px: Int, outfit: NoticeOutfit? = null): Bitmap {
        val size = min(px, 512).coerceAtLeast(16)
        val chosen = outfit ?: NoticeFaces.prefs.value.outfit
        return cached("c-${chosen.key}-${icon.ordinal}@$size") {
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawCell(context, Canvas(out), chosen, icon, RectF(0f, 0f, size.toFloat(), size.toFloat()))
            out
        }
    }

    private fun drawCell(
        context: Context,
        canvas: Canvas,
        outfit: NoticeOutfit,
        icon: NoticeIcon,
        dst: RectF
    ) {
        val src = sheet(context, outfit) ?: return
        val index = icon.ordinal
        val cell = Rect(
            (index % COLUMNS) * CELL,
            (index / COLUMNS) * CELL,
            (index % COLUMNS + 1) * CELL,
            (index / COLUMNS + 1) * CELL
        )
        canvas.drawBitmap(src, cell, dst, Paint().apply { isFilterBitmap = false })
    }

    private fun sheet(context: Context, outfit: NoticeOutfit): Bitmap? {
        sheets[outfit.key]?.let { return it }
        synchronized(this) {
            sheets[outfit.key]?.let { return it }
            val decoded = runCatching { BitmapFactory.decodeStream(context.assets.open(outfit.asset)) }
                .getOrNull()
            sheets = sheets + (outfit.key to decoded)
            return decoded
        }
    }

    private fun cached(key: String, build: () -> Bitmap): Bitmap {
        cache.get(key)?.let { return it }
        return build().also { cache.put(key, it) }
    }
}
