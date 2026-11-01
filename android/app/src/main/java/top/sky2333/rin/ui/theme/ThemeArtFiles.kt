package top.sky2333.rin.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** User photos live in app-private storage, never in the system media store. */
object ThemeArtFiles {
    const val COVER_FILE = "cover.jpg"
    private const val DIRECTORY = "custom_theme"

    fun file(context: Context, fileName: String): File =
        File(File(context.filesDir, DIRECTORY), fileName)

    fun version(context: Context, fileName: String): Long {
        val file = file(context, fileName)
        return if (file.exists()) file.lastModified() else 0L
    }

    fun decode(context: Context, fileName: String, maxDim: Int = 1200): Bitmap? {
        val file = file(context, fileName)
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()
    }

    private fun sampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0) return 1
        val longest = max(width, height)
        var sample = 1
        while (longest / sample > maxDim) sample *= 2
        return min(sample, 8)
    }
}
