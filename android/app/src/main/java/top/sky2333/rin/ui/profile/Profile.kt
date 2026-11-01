package top.sky2333.rin.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.sky2333.rin.data.AppSettings

/** What the masthead needs to draw the user's own avatar. */
data class ProfileState(
    val avatarFile: String = "",
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

/**
 * The avatar's file and framing, mirrored out of DataStore so any masthead can
 * draw it without threading settings through every screen.
 */
object Profile {
    private const val DIR = "profile"
    private const val FILE = "avatar.jpg"

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun publish(settings: AppSettings) {
        _state.value = ProfileState(
            avatarFile = settings.avatarImage,
            scale = settings.avatarScale,
            offsetX = settings.avatarOffsetX,
            offsetY = settings.avatarOffsetY
        )
    }

    fun file(context: Context, name: String = FILE): File = File(File(context.filesDir, DIR), name)

    /** Copies the picked image into app storage, downscaled so avatars stay small. */
    fun install(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val source = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null
        // Keep the whole frame: the crop happens interactively in the editor,
        // so a tall photo must not lose its top and bottom before framing.
        val longest = maxOf(source.width, source.height).coerceAtLeast(1)
        val ratio = (1024f / longest).coerceAtMost(1f)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * ratio).toInt().coerceAtLeast(1),
                (source.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }
        FileOutputStream(File(dir, FILE)).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        FILE
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun decode(context: Context, name: String): Bitmap? =
        runCatching { BitmapFactory.decodeFile(file(context, name).absolutePath) }.getOrNull()
}
