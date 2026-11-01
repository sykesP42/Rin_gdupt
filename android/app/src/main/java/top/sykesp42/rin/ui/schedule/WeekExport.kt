package top.sykesp42.rin.ui.schedule

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import top.sykesp42.rin.data.ApiScheduleDay
import top.sykesp42.rin.data.ApiScheduleEvent
import top.sykesp42.rin.ui.screens.GridSpan
import top.sykesp42.rin.ui.screens.buildSpans
import top.sykesp42.rin.ui.schedule.sectionBounds
import top.sykesp42.rin.ui.theme.courseAccent

/** Flat colours handed to the exporter so it never touches Compose state. */
data class ExportPalette(
    val canvas: Int,
    val surface: Int,
    val text: Int,
    val textSecondary: Int,
    val signal: Int,
    val border: Int,
    val dark: Boolean
)

/** 竖排 = one full-width row per day (portrait, nothing truncated). */
enum class ShareFormat(val label: String) {
    COLUMNS("横排"),
    ROWS("竖排")
}

/** What the card says about its owner. */
enum class ShareStyle(val label: String) {
    PLAIN("无水印"),
    WATERMARK("带水印"),
    SIGNED("带署名")
}

/**
 * Paints share cards from schedule data.
 *
 * Rendering from data rather than capturing the live canvas is deliberate: the
 * layer capture once produced a correctly sized but fully transparent PNG, and a
 * screen capture would carry whatever zoom the reader happened to be in.
 */
object WeekExport {
    fun renderAgenda(
        weekLabel: String,
        days: List<ApiScheduleDay>,
        palette: ExportPalette,
        style: ShareStyle,
        owner: String,
        version: String,
        format: ShareFormat = ShareFormat.COLUMNS
    ): Bitmap = CardPainter(palette, style, owner, version).let {
        if (format == ShareFormat.ROWS) it.rows(weekLabel, days) else it.agenda(weekLabel, days)
    }

    /**
     * The table shares a portrait, full-information card: seven stacked day rows
     * with complete course names, because a seven-column grid cannot fit them.
     */
    fun renderTable(
        weekLabel: String,
        days: List<ApiScheduleDay>,
        palette: ExportPalette,
        style: ShareStyle,
        owner: String,
        version: String,
        format: ShareFormat = ShareFormat.ROWS
    ): Bitmap = CardPainter(palette, style, owner, version).let {
        if (format == ShareFormat.ROWS) it.rows(weekLabel, days) else it.table(weekLabel, days)
    }

    /** The semester report card: label/value rows plus one summary line. */
    fun renderReport(
        report: SemesterReport,
        palette: ExportPalette,
        style: ShareStyle,
        owner: String,
        version: String
    ): Bitmap = CardPainter(palette, style, owner, version).report(report)

    /** Writes into cache/share/ (the only path file_paths.xml exposes) and opens the chooser. */
    fun share(context: Context, bitmap: Bitmap, subject: String): Boolean = runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "week.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "分享课表"))
        true
    }.getOrDefault(false)
}

/**
 * Drawing state shared by both layouts: the header text depends on the signature
 * style, and every card ends with the same quiet build mark.
 */
private class CardPainter(
    private val palette: ExportPalette,
    private val style: ShareStyle,
    private val owner: String,
    private val version: String
) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun text(bold: Boolean, color: Int, size: Float) {
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        paint.color = color
        paint.textSize = size
    }

    /** Signed cards put the name in the title line; plain ones just name the week. */
    private fun title(weekLabel: String): String =
        if (style == ShareStyle.SIGNED && owner.isNotBlank()) "${owner}的${weekLabel}课表" else weekLabel

    private fun drawHeader(canvas: Canvas, weekLabel: String, x: Float, y: Float, size: Float, dates: String?) {
        text(true, palette.text, size)
        canvas.drawText(title(weekLabel), x, y, paint)

        text(false, palette.textSecondary, size * 0.44f)
        val sub = if (style == ShareStyle.WATERMARK && owner.isNotBlank()) "${owner}的课表" else dates.orEmpty()
        if (sub.isNotBlank()) canvas.drawText(sub, x, y + size * 0.58f, paint)
    }

    private fun drawFooter(canvas: Canvas, width: Float, height: Float) {
        text(false, palette.textSecondary, 22f)
        paint.alpha = 120
        val label = "Rin v$version"
        canvas.drawText(label, width - paint.measureText(label) - 34f, height - 18f, paint)
        paint.alpha = 255
    }

    private fun card(width: Int, height: Int, body: (Canvas) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.canvas)
        body(canvas)
        drawFooter(canvas, width.toFloat(), height.toFloat())
        return bitmap
    }

    private fun dateRange(days: List<ApiScheduleDay>): String? {
        val dates = days.mapNotNull { it.date.takeIf(String::isNotBlank) }
        return if (dates.size >= 2) "${dates.first()} – ${dates.last()}" else null
    }

    /** The semester report: headline numbers, two charts, course bars, fun stats. */
    fun report(report: SemesterReport): Bitmap {
        val pad = 56f
        val width = 1080
        val statGap = 14f
        val statH = 122f
        val statRows = (report.stats.size + 1) / 2
        val courseRows = report.topCourses.size
        val funRows = (report.rows.size + 1) / 2
        val headerH = 186f
        val statsH = if (statRows > 0) statRows * statH + (statRows - 1) * statGap + 30f else 0f
        val weekH = 52f + 248f
        val weekdayH = 52f + 7 * 44f
        val periodH = 52f + 3 * 40f
        val courseH = if (courseRows > 0) 52f + courseRows * 84f else 0f
        val funH = if (funRows > 0) 52f + funRows * 62f else 0f
        val noteH = 64f
        val sectionGap = 36f
        val height = (
            headerH + statsH + weekH + weekdayH + periodH + courseH + funH +
                noteH + sectionGap * 4 + 64f
            ).toInt()

        return card(width, height) { canvas ->
            val title = if (style == ShareStyle.SIGNED && owner.isNotBlank()) "${owner}的学期报告" else report.title
            text(true, palette.text, 52f)
            canvas.drawText(
                TextUtils.ellipsize(title, paint, width - pad * 2, TextUtils.TruncateAt.END).toString(),
                pad, 88f, paint
            )
            text(false, palette.textSecondary, 25f)
            val subtitle = if (style == ShareStyle.WATERMARK && owner.isNotBlank()) {
                "${owner} · ${report.subtitle}"
            } else {
                report.subtitle
            }
            canvas.drawText(
                TextUtils.ellipsize(subtitle, paint, width - pad * 2, TextUtils.TruncateAt.END).toString(),
                pad, 128f, paint
            )
            fill.color = palette.border
            canvas.drawRect(pad, 158f, width - pad, 159f, fill)

            var y = headerH

            if (report.stats.isNotEmpty()) {
                val colW = (width - pad * 2 - statGap) / 2
                report.stats.forEachIndexed { index, stat ->
                    val left = pad + (index % 2) * (colW + statGap)
                    val top = y + (index / 2) * (statH + statGap)
                    fill.color = palette.surface
                    canvas.drawRoundRect(RectF(left, top, left + colW, top + statH), 20f, 20f, fill)
                    text(false, palette.textSecondary, 23f)
                    canvas.drawText(stat.label, left + 24f, top + 38f, paint)
                    text(true, palette.text, 50f)
                    val valueWidth = paint.measureText(stat.value)
                    canvas.drawText(stat.value, left + 24f, top + 94f, paint)
                    text(false, palette.textSecondary, 22f)
                    canvas.drawText(stat.unit, left + 30f + valueWidth, top + 94f, paint)
                }
                y += statsH
            }

            y = reportHeading(canvas, "各周课次", report.peakWeek?.let {
                "峰值 第 ${it.week} 周 · ${it.sessions} 次课"
            }, y, width, pad)

            weekBars(canvas, report.weeks, report.currentWeek, width, pad, y)
            y += 248f + sectionGap

            y = reportHeading(canvas, "星期分布", "学期累计", y, width, pad)
            val weekdayMax = report.weekdays.maxOfOrNull { it.sessions }?.coerceAtLeast(1) ?: 1
            report.weekdays.forEachIndexed { index, day ->
                val top = y + index * 44f
                text(false, palette.textSecondary, 22f)
                canvas.drawText(weekdayLabel(day.weekday), pad, top + 27f, paint)
                val trackLeft = pad + 76f
                val trackRight = width - pad - 82f
                fill.color = palette.border
                canvas.drawRoundRect(RectF(trackLeft, top + 15f, trackRight, top + 23f), 4f, 4f, fill)
                fill.color = palette.signal
                fill.alpha = if (day.weekday >= 6) 255 else 190
                val fillRight = trackLeft + (trackRight - trackLeft) * (day.sessions.toFloat() / weekdayMax)
                if (fillRight > trackLeft) {
                    canvas.drawRoundRect(RectF(trackLeft, top + 15f, fillRight, top + 23f), 4f, 4f, fill)
                }
                fill.alpha = 255
                text(true, palette.text, 23f)
                canvas.drawText("${day.sessions} 次课", trackRight + 16f, top + 27f, paint)
            }
            y += 308f + sectionGap

            y = reportHeading(canvas, "时段分布", "学期累计", y, width, pad)
            val periodMax = report.periods.maxOfOrNull { it.sessions }?.coerceAtLeast(1) ?: 1
            report.periods.forEachIndexed { index, period ->
                val top = y + index * 40f
                text(false, palette.textSecondary, 22f)
                canvas.drawText(period.label, pad, top + 25f, paint)
                val trackLeft = pad + 76f
                val trackRight = width - pad - 86f
                fill.color = palette.border
                canvas.drawRoundRect(RectF(trackLeft, top + 13f, trackRight, top + 21f), 4f, 4f, fill)
                fill.color = palette.signal
                fill.alpha = 190
                val fillRight = trackLeft + (trackRight - trackLeft) * (period.sessions.toFloat() / periodMax)
                if (fillRight > trackLeft) {
                    canvas.drawRoundRect(RectF(trackLeft, top + 13f, fillRight, top + 21f), 4f, 4f, fill)
                }
                fill.alpha = 255
                text(true, palette.text, 23f)
                canvas.drawText("${period.sessions} 次课", trackRight + 16f, top + 25f, paint)
            }
            y += 120f + sectionGap

            if (report.topCourses.isNotEmpty()) {
                y = reportHeading(canvas, "课程课次", "按学期课次排序", y, width, pad)
                val courseMax = report.topCourses.maxOfOrNull { it.sessions }?.coerceAtLeast(1) ?: 1
                report.topCourses.forEachIndexed { index, course ->
                    val top = y + index * 84f
                    text(true, palette.text, 28f)
                    canvas.drawText(
                        TextUtils.ellipsize(course.name, paint, width - pad * 2 - 120f, TextUtils.TruncateAt.END).toString(),
                        pad, top + 30f, paint
                    )
                    text(true, palette.text, 24f)
                    val value = "${course.sessions} 次课"
                    canvas.drawText(value, width - pad - paint.measureText(value), top + 30f, paint)
                    fill.color = palette.border
                    canvas.drawRoundRect(RectF(pad, top + 42f, width - pad, top + 46f), 2f, 2f, fill)
                    fill.color = palette.signal
                    fill.alpha = 190
                    canvas.drawRoundRect(
                        RectF(pad, top + 42f, pad + (width - pad * 2) * (course.sessions.toFloat() / courseMax), top + 46f),
                        2f, 2f, fill
                    )
                    fill.alpha = 255
                    text(false, palette.textSecondary, 20f)
                    canvas.drawText(
                        "第 ${course.firstWeek}-${course.lastWeek} 周 · 本周 ${course.meetingsPerWeek} 次课" +
                            " · 共 ${course.sections} 课时",
                        pad, top + 72f, paint
                    )
                }
                y += courseRows * 84f + sectionGap
            }

            if (report.rows.isNotEmpty()) {
                y = reportHeading(canvas, "趣味统计", null, y, width, pad)
                val colW = (width - pad * 2 - 24f) / 2
                report.rows.forEachIndexed { index, row ->
                    val left = pad + (index % 2) * (colW + 24f)
                    val top = y + (index / 2) * 62f
                    text(false, palette.textSecondary, 22f)
                    canvas.drawText(row.label, left, top + 26f, paint)
                    text(true, palette.text, 24f)
                    canvas.drawText(row.value, left + colW - paint.measureText(row.value), top + 26f, paint)
                    fill.color = palette.border
                    canvas.drawRect(left, top + 44f, left + colW, top + 45f, fill)
                }
                y += funRows * 62f
            }

            text(false, palette.textSecondary, 22f)
            canvas.drawText(
                TextUtils.ellipsize(report.note, paint, width - pad * 2, TextUtils.TruncateAt.END).toString(),
                pad, y + 30f, paint
            )
        }
    }

    private fun reportHeading(
        canvas: Canvas,
        title: String,
        meta: String?,
        top: Float,
        width: Int,
        pad: Float
    ): Float {
        text(true, palette.text, 30f)
        canvas.drawText(title, pad, top + 24f, paint)
        if (!meta.isNullOrBlank()) {
            text(false, palette.textSecondary, 21f)
            canvas.drawText(meta, width - pad - paint.measureText(meta), top + 24f, paint)
        }
        fill.color = palette.border
        canvas.drawRect(pad, top + 36f, width - pad, top + 37f, fill)
        return top + 52f
    }

    private fun weekBars(
        canvas: Canvas,
        weeks: List<WeekLoad>,
        currentWeek: Int,
        width: Int,
        pad: Float,
        top: Float
    ) {
        if (weeks.isEmpty()) return
        val max = weeks.maxOfOrNull { it.sessions }?.coerceAtLeast(1) ?: 1
        val baseline = top + 204f
        val slot = (width - pad * 2) / weeks.size
        val barW = (slot - 4f).coerceAtLeast(4f)
        weeks.forEachIndexed { index, week ->
            val left = pad + index * slot + 2f
            val heightPx = 204f * (week.sessions.toFloat() / max)
            fill.color = palette.signal
            fill.alpha = if (week.week == currentWeek) 255 else 80
            if (heightPx > 0f) {
                canvas.drawRoundRect(
                    RectF(left, baseline - heightPx, left + barW, baseline),
                    5f, 5f, fill
                )
            }
            fill.alpha = 255
            if (week.week == 1 || week.week % 5 == 0 || week.week == weeks.last().week) {
                text(false, palette.textSecondary, 18f)
                val label = week.week.toString()
                canvas.drawText(label, left + barW / 2f - paint.measureText(label) / 2f, baseline + 24f, paint)
            }
        }
        fill.color = palette.border
        canvas.drawRect(pad, baseline, width - pad, baseline + 1.5f, fill)
    }

    // ---------------------------------------------------------------- agenda

    /** Seven vertical day cards side by side: what 周 mode shares. */
    fun agenda(weekLabel: String, days: List<ApiScheduleDay>): Bitmap {
        val columns = days.ifEmpty { placeholderWeek() }
        val pad = 48f
        val gap = 16f
        val cardW = 232f
        val width = (pad * 2 + columns.size * cardW + (columns.size - 1) * gap).toInt()
        val headerH = 168f
        val labelH = 82f
        val chipH = 96f
        val chipGap = 12f
        val busiest = columns.maxOfOrNull { it.events.size } ?: 0
        val height = (headerH + labelH + busiest * (chipH + chipGap) + 84f).toInt()

        return card(width, height) { canvas ->
            drawHeader(canvas, weekLabel, pad, 92f, 56f, dateRange(columns))
            fill.color = palette.border
            canvas.drawRect(pad, headerH - 26f, width - pad, headerH - 25f, fill)

            columns.forEachIndexed { index, day ->
                val left = pad + index * (cardW + gap)
                val top = headerH
                fill.color = palette.surface
                canvas.drawRoundRect(RectF(left, top, left + cardW, top + labelH), 20f, 20f, fill)

                text(true, palette.text, 30f)
                canvas.drawText(weekdayLabel(day.weekday), left + 18f, top + 40f, paint)
                text(false, palette.textSecondary, 22f)
                canvas.drawText(day.date.takeLast(5), left + 18f, top + 68f, paint)

                if (day.events.isEmpty()) {
                    text(false, palette.textSecondary, 24f)
                    canvas.drawText("无课", left + 18f, top + labelH + 44f, paint)
                    return@forEachIndexed
                }
                day.events.forEachIndexed { eventIndex, event ->
                    drawChip(
                        canvas, event, left,
                        top + labelH + eventIndex * (chipH + chipGap),
                        cardW, chipH
                    )
                }
            }
        }
    }

    private fun drawChip(canvas: Canvas, event: ApiScheduleEvent, left: Float, top: Float, w: Float, h: Float) {
        val chip = RectF(left, top, left + w, top + h)
        fill.color = palette.surface
        canvas.drawRoundRect(chip, 16f, 16f, fill)
        fill.color = palette.border
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = 2f
        canvas.drawRoundRect(chip, 16f, 16f, fill)
        fill.style = Paint.Style.FILL

        // One rainbow ink per course, the same palette the app's table uses.
        fill.color = courseAccent(event.courseName, palette.dark).toArgb()
        canvas.drawRoundRect(RectF(left, top, left + 6f, top + h), 3f, 3f, fill)

        val textLeft = left + 20f
        val textWidth = w - 34f
        text(true, palette.text, 24f)
        canvas.drawText(
            TextUtils.ellipsize(event.courseName.ifBlank { "课程" }, paint, textWidth, TextUtils.TruncateAt.END).toString(),
            textLeft, top + 40f, paint
        )
        text(false, palette.textSecondary, 19f)
        canvas.drawText(
            TextUtils.ellipsize(metaLine(event), paint, textWidth, TextUtils.TruncateAt.END).toString(),
            textLeft, top + 70f, paint
        )
    }

    /** Portrait full-information card: one stacked row per course. */
    fun rows(weekLabel: String, days: List<ApiScheduleDay>): Bitmap {
        val columns = days.ifEmpty { placeholderWeek() }
        val pad = 48f
        val headerH = 156f
        val rowH = 88f
        val dayGap = 18f
        val totalRows = columns.sumOf { maxOf(1, it.events.size) }
        // Each day also draws a 42px date line above its course rows.
        val height = (
            headerH + columns.size * 42f + totalRows * rowH +
                (columns.size - 1) * dayGap + 84f
            ).toInt()
        val width = 1080

        return card(width, height) { canvas ->
            drawHeader(canvas, weekLabel, pad, 90f, 52f, dateRange(columns))
            fill.color = palette.border
            canvas.drawRect(pad, headerH - 26f, width - pad, headerH - 25f, fill)

            var top = headerH
            columns.forEach { day ->
                text(true, palette.text, 28f)
                canvas.drawText(
                    weekdayLabel(day.weekday) + "  " + day.date.takeLast(5),
                    pad, top + 30f, paint
                )
                top += 42f
                if (day.events.isEmpty()) {
                    text(false, palette.textSecondary, 24f)
                    canvas.drawText("无课", pad + 8f, top + 34f, paint)
                    top += rowH
                } else {
                    day.events.forEach { event ->
                        drawFullRow(canvas, event, pad, top, width - pad * 2, rowH - 8f)
                        top += rowH
                    }
                }
                top += dayGap
            }
        }
    }

    /** Full-width course row: complete name on top, time · room underneath. */
    private fun drawFullRow(canvas: Canvas, event: ApiScheduleEvent, left: Float, top: Float, w: Float, h: Float) {
        val rect = RectF(left, top, left + w, top + h)
        fill.color = palette.surface
        canvas.drawRoundRect(rect, 16f, 16f, fill)
        fill.color = courseAccent(event.courseName, palette.dark).toArgb()
        canvas.drawRoundRect(RectF(left, top, left + 6f, top + h), 3f, 3f, fill)

        text(true, palette.text, 30f)
        canvas.drawText(
            TextUtils.ellipsize(event.courseName.ifBlank { "课程" }, paint, w - 40f, TextUtils.TruncateAt.END).toString(),
            left + 24f, top + 36f, paint
        )
        text(false, palette.textSecondary, 23f)
        canvas.drawText(
            TextUtils.ellipsize(metaLine(event), paint, w - 40f, TextUtils.TruncateAt.END).toString(),
            left + 24f, top + 66f, paint
        )
    }

    // ----------------------------------------------------------------- table

    /** The grid: time rail plus seven dated columns, courses spanning sections. */
    fun table(weekLabel: String, days: List<ApiScheduleDay>): Bitmap {
        val columns = days.ifEmpty { placeholderWeek() }
        val pad = 40f
        val railW = 92f
        val headerH = 148f
        val dayHeaderH = 62f
        val rowH = 86f
        val sections = 10
        val colW = (1320f - pad * 2 - railW) / columns.size
        val width = (pad * 2 + railW + colW * columns.size).toInt()
        val height = (headerH + dayHeaderH + rowH * sections + 76f).toInt()

        return card(width, height) { canvas ->
            drawHeader(canvas, weekLabel, pad, 90f, 50f, dateRange(columns))
            fill.color = palette.border
            canvas.drawRect(pad, headerH - 24f, width - pad, headerH - 23f, fill)

            columns.forEachIndexed { index, day ->
                val left = pad + railW + index * colW
                text(true, palette.text, 24f)
                canvas.drawText(weekdayLabel(day.weekday), left + 8f, headerH + 26f, paint)
                text(false, palette.textSecondary, 19f)
                canvas.drawText(day.date.takeLast(5), left + 8f, headerH + 50f, paint)
            }

            val gridTop = headerH + dayHeaderH
            for (section in 1..sections) {
                val top = gridTop + (section - 1) * rowH
                sectionBounds[section]?.first?.let { start ->
                    text(false, palette.textSecondary, 20f)
                    canvas.drawText("%02d:%02d".format(start / 60, start % 60), pad, top + 30f, paint)
                }
                fill.color = palette.border
                canvas.drawRect(pad, top, width - pad, top + 1.5f, fill)
            }

            columns.forEachIndexed { index, day ->
                val left = pad + railW + index * colW
                buildSpans(day.events).forEach { span ->
                    drawSpan(canvas, span, left, gridTop, colW, rowH)
                }
            }
        }
    }

    private fun drawSpan(canvas: Canvas, span: GridSpan, left: Float, gridTop: Float, colW: Float, rowH: Float) {
        val top = gridTop + (span.startSection - 1) * rowH + 4f
        val bottom = top + span.span * rowH - 8f
        val rect = RectF(left + 3f, top, left + colW - 5f, bottom)
        val accent = courseAccent(span.event.courseName, palette.dark).toArgb()

        fill.color = palette.surface
        canvas.drawRoundRect(rect, 14f, 14f, fill)
        fill.color = accent
        fill.alpha = 46
        canvas.drawRoundRect(rect, 14f, 14f, fill)
        fill.alpha = 255
        canvas.drawRoundRect(RectF(rect.left, rect.top, rect.left + 5f, rect.bottom), 2.5f, 2.5f, fill)

        val textLeft = rect.left + 14f
        val textWidth = rect.width() - 24f
        text(true, palette.text, 20f)
        canvas.drawText(
            TextUtils.ellipsize(span.event.courseName.ifBlank { "课程" }, paint, textWidth, TextUtils.TruncateAt.END).toString(),
            textLeft, rect.top + 30f, paint
        )
        text(false, palette.textSecondary, 18f)
        canvas.drawText(
            TextUtils.ellipsize(span.event.location, paint, textWidth, TextUtils.TruncateAt.END).toString(),
            textLeft, rect.top + 56f, paint
        )
    }

    // ----------------------------------------------------------------- utils

    private fun metaLine(event: ApiScheduleEvent): String =
        listOfNotNull(
            listOf(event.startTime, event.endTime).filter(String::isNotBlank).joinToString("–")
                .takeIf(String::isNotBlank),
            event.location.takeIf(String::isNotBlank)
        ).joinToString(" · ")

    private fun weekdayLabel(weekday: Int): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            .getOrElse(weekday - 1) { "—" }

    private fun placeholderWeek(): List<ApiScheduleDay> =
        (1..7).map { ApiScheduleDay(date = "", weekday = it, events = emptyList()) }
}
