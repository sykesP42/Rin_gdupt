package top.sky2333.rin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A user-chosen photo for the decorative frame card, kept in app-private storage. */
class TodayFrameStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "today_frame.jpg")

    suspend fun install(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@runCatching false
            val longest = max(bitmap.width, bitmap.height).coerceAtLeast(1)
            val ratio = (1440f / longest).coerceAtMost(1f)
            val saved = if (ratio < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            FileOutputStream(file).use { out ->
                saved.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            true
        }.getOrDefault(false)
    }

    fun decode(): Bitmap? = runCatching {
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }.getOrNull()

    fun exists(): Boolean = file.exists()

    fun clear() {
        runCatching { file.delete() }
    }
}
