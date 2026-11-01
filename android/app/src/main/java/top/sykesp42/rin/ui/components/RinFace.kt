package top.sykesp42.rin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.sykesp42.rin.ui.faces.NoticeFaces
import top.sykesp42.rin.ui.faces.NoticeIcon
import top.sykesp42.rin.ui.faces.NoticeOutfit
import top.sykesp42.rin.ui.faces.NoticeIcons

/**
 * The same Rin face the notification shade shows, so a state reads identically in
 * both places. The plate comes from the user's choice rather than the page's own
 * surface: the bust wears a white jacket, which would vanish into a light theme.
 */
@Composable
fun RinFace(
    icon: NoticeIcon,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    ring: Color? = null,
    outfit: NoticeOutfit? = null
) {
    val context = LocalContext.current
    val prefs by NoticeFaces.prefs.collectAsState()
    val chosen = outfit ?: prefs.outfit
    val plateArgb = NoticeFaces.plateArgb(context, chosen)
    val px = with(LocalDensity.current) { size.roundToPx() }
    // The atlas is chosen by outfit, so a switch must recompose the bitmap.
    val bitmap = remember(icon, px, chosen) { NoticeIcons.cellBitmap(context, icon, px, chosen).asImageBitmap() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(if (plateArgb != null) Modifier.background(Color(plateArgb)) else Modifier)
            .then(if (ring != null) Modifier.border(1.dp, ring, CircleShape) else Modifier)
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "${icon.label}的 Rin",
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
            modifier = Modifier.matchParentSize()
        )
    }
}
