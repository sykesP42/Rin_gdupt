package top.sykesp42.rin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import top.sykesp42.rin.ui.theme.CustomThemePalette
import top.sykesp42.rin.ui.theme.ThemeArtFiles
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CustomThemeInstall(val fileName: String, val seed: String)

/**
 * Copies a picked photo into app-private storage and reduces it to the only two
 * representations the UI needs: a JPEG cover and a small, contrast-safe seed.
 */
class CustomThemeStore(private val context: Context) {
    suspend fun install(uri: Uri): CustomThemeInstall? = withContext(Dispatchers.IO) {
        val bitmap = decodeUri(uri, maxDim = 1440) ?: return@withContext null
        try {
            val dir = File(context.filesDir, "custom_theme").apply { mkdirs() }
            val file = File(dir, ThemeArtFiles.COVER_FILE)
            file.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, stream)
            }
            val pixels = samplePixels(bitmap)
            val seed = CustomThemePalette.extract(pixels, SAMPLE_SIZE, SAMPLE_SIZE)
            CustomThemeInstall(ThemeArtFiles.COVER_FILE, CustomThemePalette.encode(seed))
        } catch (error: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    fun delete(fileName: String) {
        if (fileName != ThemeArtFiles.COVER_FILE) return
        runCatching { ThemeArtFiles.file(context, fileName).delete() }
    }

    private fun decodeUri(uri: Uri, maxDim: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > maxDim) {
                    val ratio = maxDim.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * ratio).roundToInt().coerceAtLeast(1),
                        (info.size.height * ratio).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            decodeLegacy(uri, maxDim)
        }
    }.getOrNull()

    private fun decodeLegacy(uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun samplePixels(bitmap: Bitmap): IntArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        scaled.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        if (scaled != bitmap) scaled.recycle()
        return pixels
    }

    private fun sampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0) return 1
        val longest = max(width, height)
        var sample = 1
        while (longest / sample > maxDim) sample *= 2
        return min(sample, 8)
    }

    private companion object {
        const val SAMPLE_SIZE = 64
    }
}
