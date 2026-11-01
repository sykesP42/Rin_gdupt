package top.sykesp42.rin.auto

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Publishes the sign QR into the device gallery (Pictures/Rin) so that
 * WeCom's album picker shows it as the newest photo. Own inserts into
 * MediaStore need no permission on API 29+.
 */
class GalleryPublisher(private val context: Context) {

    private var lastUri: Uri? = null

    /** Replaces our previous image (one live QR at a time); true when visible. */
    fun publish(png: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < 29 || png.size < 64) return false
        deleteOwn()
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "suisign_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Rin")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { stream -> stream.write(png) } ?: return false
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            true
        }.getOrDefault(false)
        return if (written) {
            lastUri = uri
            true
        } else {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    /** Remove our image after the attempt so the gallery stays clean. */
    fun deleteOwn() {
        val uri = lastUri ?: return
        lastUri = null
        runCatching { context.contentResolver.delete(uri, null, null) }
    }
}
