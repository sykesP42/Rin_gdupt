package top.sykesp42.rin.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.sykesp42.rin.ui.theme.LocalSuiSignColors

/**
 * A direct-manipulation crop frame: drag to choose the part that survives,
 * pinch to zoom, then commit once. Avatar uses a circle, cover a wide card.
 */
@Composable
fun ImageFramingEditor(
    bitmap: Bitmap,
    title: String,
    aspectRatio: Float,
    circular: Boolean,
    initialScale: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
    onDone: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onDismiss: () -> Unit,
    onPickAgain: (() -> Unit)? = null
) {
    val colors = LocalSuiSignColors.current
    var scale by remember(bitmap) { mutableFloatStateOf(initialScale.coerceIn(1f, 3f)) }
    var offsetX by remember(bitmap) { mutableFloatStateOf(initialOffsetX.coerceIn(-1f, 1f)) }
    var offsetY by remember(bitmap) { mutableFloatStateOf(initialOffsetY.coerceIn(-1f, 1f)) }
    val shape = if (circular) CircleShape else RoundedCornerShape(22.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 52.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("取消", color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            text = "拖动调整位置 · 双指缩放 · 完成后保存构图",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Spacer(Modifier.weight(0.6f))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 520.dp)
                .align(Alignment.CenterHorizontally)
                .aspectRatio(aspectRatio)
                .clip(shape)
                .background(Color.Black)
                .pointerInput(bitmap) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // Anchor the zoom on the fingers: the image point under
                        // the centroid stays put, then the drag applies 1:1.
                        val zoomed = (scale * zoom).coerceIn(1f, 3f)
                        val applied = zoomed / scale
                        val base = maxOf(
                            size.width / bitmap.width.toFloat(),
                            size.height / bitmap.height.toFloat()
                        )
                        fun reaxis(oldBias: Float, oldOver: Float, newOver: Float, focal: Float, drag: Float): Float {
                            if (newOver <= 0.5f) return 0f
                            val t = oldBias * (oldOver / 2f)
                            val moved = focal * (1f - applied) + t * applied + drag
                            return (moved / (newOver / 2f)).coerceIn(-1f, 1f)
                        }
                        val overXOld = (bitmap.width * base * scale - size.width).coerceAtLeast(0f)
                        val overYOld = (bitmap.height * base * scale - size.height).coerceAtLeast(0f)
                        val overXNew = (bitmap.width * base * zoomed - size.width).coerceAtLeast(0f)
                        val overYNew = (bitmap.height * base * zoomed - size.height).coerceAtLeast(0f)
                        offsetX = reaxis(offsetX, overXOld, overXNew, centroid.x - size.width / 2f, pan.x)
                        offsetY = reaxis(offsetY, overYOld, overYNew, centroid.y - size.height / 2f, pan.y)
                        scale = zoomed
                    }
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "构图预览",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        // Translate in real pixels: bias ±1 == half the current
                        // overflow on that axis, so the frame never shows gaps.
                        val base = maxOf(
                            size.width / bitmap.width.toFloat(),
                            size.height / bitmap.height.toFloat()
                        )
                        val overX = (bitmap.width * base * scale - size.width).coerceAtLeast(0f) / 2f
                        val overY = (bitmap.height * base * scale - size.height).coerceAtLeast(0f) / 2f
                        translationX = offsetX * overX
                        translationY = offsetY * overY
                    }
            )
            Canvas(Modifier.matchParentSize()) {
                val line = Color.White.copy(alpha = 0.34f)
                val stroke = 1.dp.toPx()
                if (!circular) {
                    drawLine(line, androidx.compose.ui.geometry.Offset(size.width / 3f, 0f), androidx.compose.ui.geometry.Offset(size.width / 3f, size.height), stroke)
                    drawLine(line, androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, 0f), androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, size.height), stroke)
                    drawLine(line, androidx.compose.ui.geometry.Offset(0f, size.height / 3f), androidx.compose.ui.geometry.Offset(size.width, size.height / 3f), stroke)
                    drawLine(line, androidx.compose.ui.geometry.Offset(0f, size.height * 2f / 3f), androidx.compose.ui.geometry.Offset(size.width, size.height * 2f / 3f), stroke)
                }
                drawRect(
                    color = Color.White.copy(alpha = 0.65f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                )
            }
        }
        Spacer(Modifier.weight(0.55f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("顶部" to -0.55f, "居中" to 0f, "底部" to 0.55f).forEach { (label, y) ->
                TextButton(
                    onClick = {
                        offsetX = 0f
                        offsetY = y
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onPickAgain != null) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onPickAgain()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("换一张", color = colors.text, style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(
                onClick = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("重置构图", color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
        SuiPrimaryButton(
            text = "完成",
            onClick = {
                onDone(scale, offsetX, offsetY)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        }
    }
}
