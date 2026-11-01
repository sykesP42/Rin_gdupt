package top.sky2333.rin.ui.components

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.sky2333.rin.R
import top.sky2333.rin.ui.theme.LocalRinColors
import top.sky2333.rin.ui.theme.ThemeArtImage
import top.sky2333.rin.ui.theme.ThemeSpec

/**
 * One A2 paper page: canvas, the sky/warm RinBackdrop wash and safe-area padding.
 * Screens own their scrolling; this primitive never scrolls.
 *
 * Insets: only [WindowInsets.systemBars] and [WindowInsets.displayCutout] are padded here.
 * `safeDrawing` would also consume the IME and then double up with each screen's own
 * `imePadding()`, so the keyboard stays a per-screen concern.
 *
 * [skyWash] paints the 晴空 wash at page level: it reaches both screen edges and fades to
 * zero alpha, which a hero-sized gradient could not do without ending in a horizontal seam.
 */
@Composable
fun PaperPage(
    modifier: Modifier = Modifier,
    skyWash: Boolean = false,
    ambientSpec: ThemeSpec? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalRinColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        RinBackdrop(Modifier.matchParentSize())
        if (ambientSpec?.hasArt == true) {
            ThemeAmbient(
                spec = ambientSpec,
                modifier = Modifier.matchParentSize()
            )
        }
        if (skyWash) SkyWash(Modifier.align(Alignment.TopStart))
        Box(
            modifier = Modifier
                .matchParentSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
        ) {
            content()
        }
    }
}

/**
 * The same theme art, enlarged and softened behind the page header. The sharp
 * cover card still owns the illustration; this layer carries its colour into
 * the status bar, masthead and page gutters instead of stopping at the card.
 */
@Composable
private fun ThemeAmbient(
    spec: ThemeSpec,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    val blur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(24.dp)
    } else {
        Modifier
    }
    Box(modifier) {
        ThemeArtImage(
            spec = spec,
            alpha = if (spec.dark) 0.42f else 0.22f,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .graphicsLayer {
                    scaleX = 1.12f
                    scaleY = 1.12f
                }
                .then(blur)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp)
                .background(
                    Brush.verticalGradient(
                        0f to colors.canvas.copy(alpha = 0.08f),
                        0.34f to colors.canvas.copy(alpha = 0.42f),
                        0.66f to colors.canvas.copy(alpha = 0.84f),
                        1f to colors.canvas
                    )
                )
        )
    }
}

/**
 * Full-bleed sky wash for the page top (spec 4.1 晴空区). Drawn as narrow full-height stripes:
 * every stripe is its own vertical gradient that always ends at transparent alpha, and only
 * the peak differs across the width (right-weighted, like the old hero glow). That keeps the
 * wash edge-to-edge, makes the stripe steps invisible, and — unlike a flat horizontal or
 * radial gradient — it can never be cut by a line at the bottom of the band.
 * Never a card: no border, radius or shadow, and it sits behind the content.
 */
@Composable
internal fun SkyWash(
    modifier: Modifier = Modifier,
    height: Dp = 320.dp
) {
    val colors = LocalRinColors.current
    val boost = if (colors.canvas.luminance() < 0.4f) 1.4f else 1f
    val sky = colors.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val stripes = 8
                val stripeWidth = size.width / stripes
                for (index in 0 until stripes) {
                    // 0.34 at the left edge up to 1.0 at the right edge: the light comes
                    // from the top-right, the same corner RinBackdrop glows in.
                    val weight = 0.34f + 0.66f * ((index + 0.5f) / stripes)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to sky.copy(alpha = 0.17f * boost * weight),
                            0.40f to sky.copy(alpha = 0.06f * boost * weight),
                            1f to Color.Transparent
                        ),
                        topLeft = Offset(index * stripeWidth, 0f),
                        size = Size(stripeWidth, size.height)
                    )
                }
            }
    )
}

/**
 * Page masthead: 28dp Rin mark + serif title + 11sp date line + actions.
 * Minimal mode keeps only refresh so the header stays quiet.
 */
@Composable
fun RinMasthead(
    dateLine: String,
    title: String,
    avatarText: String,
    onRefresh: () -> Unit,
    onTheme: () -> Unit,
    compact: Boolean = false,
    onAvatarClick: (() -> Unit)? = null
) {
    val colors = LocalRinColors.current
    val appearance = top.sky2333.rin.ui.theme.LocalVisualStyle.current
    val themeIcon = if (colors.canvas.luminance() < 0.4f) {
        R.drawable.ic_theme_dark
    } else {
        R.drawable.ic_theme_light
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text,
                fontFamily = appearance.typography.headlineMedium.fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 27.sp,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateLine,
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .width(22.dp)
                    .height(2.dp)
                    .background(colors.accent.copy(alpha = 0.75f), RoundedCornerShape(1.dp))
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (!compact) {
                IconButton(onClick = onTheme, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(themeIcon),
                        contentDescription = "主题与外观",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                val avatarNode = @Composable { Avatar(avatarText, size = 30.dp) }
                if (onAvatarClick != null) {
                    Box(Modifier.clip(CircleShape).clickable(onClick = onAvatarClick)) { avatarNode() }
                } else {
                    avatarNode()
                }
            }
        }
    }
}

/** Label + optional meta + a single hairline. No container card. */
@Composable
fun SectionHeader(title: String, meta: String? = null) {
    val colors = LocalRinColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Hairline()
    }
}

@Composable
fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalRinColors.current.border)
    )
}

/** Warm data band: a soft paper stripe, never a bordered card. */
@Composable
fun DataBand(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalRinColors.current
    val dark = colors.canvas.luminance() < 0.4f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(
                colors.warm.copy(alpha = if (dark) 0.16f else 0.62f),
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

