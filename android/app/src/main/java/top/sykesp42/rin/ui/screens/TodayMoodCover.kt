package top.sykesp42.rin.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.sykesp42.rin.ui.theme.HeroStyle
import top.sykesp42.rin.ui.theme.LocalRinColors
import top.sykesp42.rin.ui.theme.SurfaceStyle
import top.sykesp42.rin.ui.theme.ThemeArtImage
import top.sykesp42.rin.data.PetWeatherSnapshot
import top.sykesp42.rin.ui.theme.ThemeSpec

/**
 * The Today cover is the only place a theme spends its hero art. One shared
 * cover/split/ticket family keeps all sixteen themes on the same layout code.
 */
@Composable
fun TodayMoodCover(
    spec: ThemeSpec,
    dateLine: String,
    week: Int,
    courseCount: Int,
    weather: PetWeatherSnapshot? = null,
    modifier: Modifier = Modifier
) {
    val date = dateLine.substringBefore(" · 第").ifBlank { dateLine }
    val caption = if (courseCount < 0) "课表还没同步" else "今天 $courseCount 门课"
    when (spec.hero) {
        HeroStyle.BOOK -> BookCover(date, caption, week, modifier)
        HeroStyle.CRYSTAL -> CrystalCover(date, caption, week, modifier)
        HeroStyle.COVER -> {
            if (spec.hasArt) {
                CoverHero(spec, date, caption, week, weather, modifier)
            } else {
                BookCover(date, caption, week, modifier)
            }
        }
        HeroStyle.SPLIT -> {
            if (spec.hasArt) {
                SplitHero(spec, date, caption, week, weather, modifier)
            } else {
                BookCover(date, caption, week, modifier)
            }
        }
        HeroStyle.TICKET -> {
            if (spec.hasArt) {
                TicketHero(spec, date, caption, week, weather, modifier)
            } else {
                BookCover(date, caption, week, modifier)
            }
        }
    }
}

@Composable
private fun CoverHero(
    spec: ThemeSpec,
    date: String,
    caption: String,
    week: Int,
    weather: PetWeatherSnapshot?,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    val shape = MaterialTheme.shapes.large
    if (!spec.hasArt) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (spec.dark) 184.dp else 174.dp)
            .clip(shape)
            .background(colors.surface)
    ) {
        ThemeArtImage(
            spec = spec,
            modifier = Modifier.matchParentSize()
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.18f to Color.Transparent,
                        0.56f to colors.canvas.copy(alpha = 0.18f),
                        1f to colors.canvas.copy(alpha = 0.98f)
                    )
                )
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to colors.canvas.copy(alpha = if (spec.dark) 0.55f else 0.34f),
                        0.52f to Color.Transparent
                    )
                )
        )
        WeatherStamp(weather, Modifier.align(Alignment.TopStart).padding(12.dp))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = weekEyebrow(week),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.3.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = date,
                color = colors.text,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = caption,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 16.dp)
                .width(24.dp)
                .height(2.dp)
                .background(colors.accent, MaterialTheme.shapes.extraSmall)
        )
    }
}

@Composable
private fun SplitHero(
    spec: ThemeSpec,
    date: String,
    caption: String,
    week: Int,
    weather: PetWeatherSnapshot?,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    val shape = MaterialTheme.shapes.large
    if (!spec.hasArt) return
    val outlined = spec.surface == SurfaceStyle.STICKER || spec.surface == SurfaceStyle.PIXEL
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(shape)
            .background(colors.surface)
            .border(if (outlined) 2.dp else 1.dp, if (outlined) colors.text else colors.border, shape)
    ) {
        Column(
            modifier = Modifier
                .weight(1.18f)
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                WeatherStamp(weather)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = weekEyebrow(week),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = date,
                    color = colors.text,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = caption,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .weight(0.82f)
                .fillMaxHeight()
                .padding(if (outlined) 7.dp else 0.dp)
                .clip(if (outlined) MaterialTheme.shapes.medium else RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
                .background(colors.canvas)
        ) {
            ThemeArtImage(
                spec = spec,
                modifier = Modifier.matchParentSize()
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to colors.surface.copy(alpha = 0.34f),
                            0.22f to Color.Transparent
                        )
                    )
            )
        }
    }
}

@Composable
private fun TicketHero(
    spec: ThemeSpec,
    date: String,
    caption: String,
    week: Int,
    weather: PetWeatherSnapshot?,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    val shape = MaterialTheme.shapes.large
    if (!spec.hasArt) return
    val hard = spec.surface == SurfaceStyle.PIXEL
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(if (hard) 2.dp else 1.5.dp, if (hard) colors.text else colors.border, shape)
            .padding(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .clip(MaterialTheme.shapes.small)
                .background(colors.canvas)
        ) {
            ThemeArtImage(
                spec = spec,
                modifier = Modifier.matchParentSize()
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.canvas.copy(alpha = 0.38f))
                        )
                    )
            )
            WeatherStamp(weather, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = weekEyebrow(week),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = date,
                    color = colors.text,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = caption,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier
                    .size(width = 4.dp, height = 34.dp)
                    .background(colors.signal, MaterialTheme.shapes.extraSmall)
            )
        }
    }
}

/** Campus temperature and humidity, replacing the old theme name on the cover. */
@Composable
private fun WeatherStamp(snapshot: PetWeatherSnapshot?, modifier: Modifier = Modifier) {
    val colors = LocalRinColors.current
    val temp = snapshot?.temperatureC
    val humidity = snapshot?.humidity
    if (temp == null && humidity == null) return
    Row(
        modifier = modifier
            .background(colors.surface.copy(alpha = 0.92f), MaterialTheme.shapes.extraSmall)
            .border(1.dp, colors.border, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(4.dp)
                .background(colors.signal, RoundedCornerShape(1.dp))
        )
        Text(
            text = listOfNotNull(temp?.let { "$it°" }, humidity?.let { "$it%" }).joinToString(" · "),
            color = colors.text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun BookCover(
    date: String,
    caption: String,
    week: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = weekEyebrow(week),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.text)
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.text.copy(alpha = 0.55f))
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = date,
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = caption,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** A week marker, or "今天" before the term week is known. */
private fun weekEyebrow(week: Int): String = if (week > 0) "第 $week 周" else "今天"

@Composable
private fun CrystalCover(
    date: String,
    caption: String,
    week: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalRinColors.current
    val shape = RoundedCornerShape(4.dp, 24.dp, 4.dp, 24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.border, shape)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = 54.dp, height = 32.dp)
                .clip(RoundedCornerShape(bottomStart = 22.dp))
                .background(colors.warm)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = weekEyebrow(week),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.4.sp
            )
            Text(
                text = date,
                color = colors.text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = caption,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
