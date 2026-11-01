package top.sykesp42.rin.ui.theme

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val themeArtCache = LruCache<String, ImageBitmap>(2)

/**
 * Built-in themes use a drawable id; a custom cover uses the app-private file.
 * The resource painter remains the fallback while a photo decodes or if its file
 * was removed, so a custom theme can never blank out the hero.
 */
@Composable
fun rememberThemeArtPainter(spec: ThemeSpec): Painter? {
    val context = LocalContext.current
    val fileName = spec.customArtPath
    val version = if (fileName == null) 0L else ThemeArtFiles.version(context, fileName)
    val filePainter by produceState<Painter?>(null, fileName, version) {
        value = if (fileName == null || version == 0L) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val cacheKey = "$fileName:$version"
                themeArtCache.get(cacheKey)?.let { BitmapPainter(it) } ?: run {
                    ThemeArtFiles.decode(context, fileName)
                        ?.asImageBitmap()
                        ?.also { themeArtCache.put(cacheKey, it) }
                        ?.let { BitmapPainter(it) }
                }
            }
        }
    }
    if (filePainter != null) return filePainter

    val resource = spec.art
    return if (resource != null) painterResource(resource) else null
}

@Composable
fun ThemeArtImage(
    spec: ThemeSpec,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment? = null,
    contentDescription: String? = null
) {
    val painter = rememberThemeArtPainter(spec) ?: return
    val resolvedAlignment = alignment ?: BiasAlignment(
        spec.artOffsetX.coerceIn(-1f, 1f),
        (spec.artFocus.coerceIn(0f, 1f) * 2f - 1f + spec.artOffsetY).coerceIn(-1f, 1f)
    )
    // Custom covers may be zoomed and softened; built-in art keeps 1f / 0f.
    val blur = if (spec.artBlur > 0f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        Modifier.blur(spec.artBlur.dp)
    } else {
        Modifier
    }
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = contentScale,
        alignment = resolvedAlignment,
        alpha = alpha,
        modifier = modifier
            .graphicsLayer {
                scaleX = spec.artScale
                scaleY = spec.artScale
            }
            .then(blur)
    )
}
