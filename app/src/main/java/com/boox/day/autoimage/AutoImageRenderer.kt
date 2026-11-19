package com.boox.day.autoimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.Offset
import com.boox.day.ai.MemoPageContent
import com.boox.day.editor.drawing.drawStroke
import com.boox.day.io.MemoPageRenderer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the auto-saved images (Nov 2026, [AutoImages]) in page pixels, off the main thread. Memo pages
 * through [MemoPageRenderer] like every other output; Tasks pages add what the screen draws over the
 * ink — row lines, checkboxes, priorities and typed text — because the app keeps those out of the page.
 */
object AutoImageRenderer {

    /** Room kept past content at the page's edges. */
    private const val EDGE = 50f

    /** Caption sizes as parts of the image's width. */
    private const val CAPTION_TEXT_SIZE = 0.045f
    private const val CAPTION_PADDING = 0.04f
    private const val CAPTION_VERTICAL_PADDING = 0.007f
    private const val CAPTION_MAX_LINES = 3

    /** One Tasks row as the image shows it. */
    data class TaskRow(
        val rowIndex: Int,
        val hasContent: Boolean,
        val completed: Boolean,
        val priority: String,
        /** Typed or converted text, drawn when the row has no ink. */
        val text: String?,
        val isSubtask: Boolean,
    )

    /**
     * A memo page [widthPx] wide and as tall as its content, at least [minHeightPx]; null when the page is
     * empty. Content left of or above the page is taken in, as exports do.
     */
    fun memoPage(context: Context, content: MemoPageContent.Page, widthPx: Int, minHeightPx: Int): Bitmap? {
        if (!MemoPageRenderer.hasContent(content)) return null
        val bounds = MemoPageRenderer.contentBounds(content) ?: return null
        val left = if (bounds.left < 0f) bounds.left - EDGE else 0f
        val top = if (bounds.top < 0f) bounds.top - EDGE else 0f
        val right = max(widthPx.toFloat(), bounds.right + EDGE)
        val bottom = max(minHeightPx.toFloat(), bounds.bottom + EDGE)
        val bitmap = Bitmap.createBitmap(ceil(right - left).toInt(), ceil(bottom - top).toInt(), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            MemoPageRenderer.drawMemoPage(context, canvas, content, Offset(left, top))
        } catch (e: Throwable) {
            bitmap.recycle()
            throw e
        }
        return bitmap
    }

    /**
     * A Tasks page rendered cleanly as `[ ] Task` for incomplete tasks and `[✓] Task` (crossed through)
     * for completed tasks, fitted within [widthPx].
     */
    fun tasksPage(
        context: Context,
        content: MemoPageContent.Page,
        rows: List<TaskRow>,
        rowCount: Int,
        rowsHeight: Float,
        topOffset: Float,
        widthPx: Int,
        columnPx: Float,
    ): Bitmap {
        val height = ceil(rowsHeight).toInt()
        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val rowHeight = (rowsHeight - topOffset) / rowCount
            val rightMargin = rowHeight * 0.30f

            rows.filter { it.hasContent && it.rowIndex in 0 until rowCount }.forEach { row ->
                val rowTop = topOffset + row.rowIndex * rowHeight
                val rowBottom = rowTop + rowHeight
                val cy = rowTop + rowHeight / 2f
                val taskLeft = drawCheckbox(canvas, row, rowTop, rowHeight)

                val rowStrokes = content.strokes.filter { stroke ->
                    val midY = (stroke.top + stroke.bottom) / 2f
                    midY >= rowTop && midY < rowBottom
                }
                if (rowStrokes.isNotEmpty()) {
                    val minLeft = rowStrokes.minOf { it.left }
                    val maxRight = rowStrokes.maxOf { it.right }
                    val strokeSpan = (maxRight - minLeft).coerceAtLeast(1f)
                    val availableWidth = (widthPx - rightMargin - taskLeft).coerceAtLeast(1f)
                    val scaleX = min(1f, availableWidth / strokeSpan)

                    canvas.save()
                    canvas.clipRect(taskLeft, rowTop, widthPx - rightMargin, rowBottom)
                    canvas.translate(taskLeft, cy)
                    canvas.scale(scaleX, scaleX)
                    canvas.translate(-minLeft, -cy)
                    rowStrokes.forEach { stroke ->
                        drawStroke(canvas, stroke, Offset.Zero)
                    }
                    canvas.restore()

                    if (row.completed) {
                        val strikeRight = (taskLeft + strokeSpan * scaleX).coerceAtMost(widthPx - rightMargin)
                        val strikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.BLACK
                            style = Paint.Style.STROKE
                            strokeWidth = max(3f, rowHeight * 0.045f)
                            strokeCap = Paint.Cap.ROUND
                        }
                        canvas.drawLine(taskLeft, cy, strikeRight, cy, strikePaint)
                    }
                } else {
                    row.text?.let { drawTypedText(canvas, it, row, rowTop, rowHeight, taskLeft, widthPx - rightMargin) }
                }
            }
        } catch (e: Throwable) {
            bitmap.recycle()
            throw e
        }
        return bitmap
    }

    /**
     * [image] on a white [widthPx] × [heightPx] screen, as large as fits, centred across and at the top:
     * images shorter than the screen (a Tasks page) stay readable at the top.
     */
    fun fitToScreen(image: Bitmap, widthPx: Int, heightPx: Int): Bitmap {
        val screen = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screen)
        canvas.drawColor(Color.WHITE)
        val scale = min(widthPx.toFloat() / image.width, heightPx.toFloat() / image.height)
        val w = image.width * scale
        val left = (widthPx - w) / 2f
        canvas.drawBitmap(image, null, RectF(left, 0f, left + w, image.height * scale), Paint(Paint.FILTER_BITMAP_FLAG))
        return screen
    }

    /**
     * [image] with [text] in white on a black band across its bottom, wrapped to at most [CAPTION_MAX_LINES]
     * lines. Draws on [image] when it can be changed, else on a copy (and recycles [image]).
     */
    fun withCaption(image: Bitmap, text: String): Bitmap {
        val caption = text.trim()
        if (caption.isEmpty()) return image
        val target = if (image.isMutable) image else image.copy(Bitmap.Config.ARGB_8888, true).also { image.recycle() }
        val width = target.width
        val padding = width * CAPTION_PADDING
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = width * CAPTION_TEXT_SIZE
            typeface = Typeface.DEFAULT_BOLD
        }
        val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, paint, max(1, (width - 2 * padding).toInt()))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(CAPTION_MAX_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        val verticalPadding = width * CAPTION_VERTICAL_PADDING
        val bandTop = target.height - layout.height - 2 * verticalPadding
        val canvas = Canvas(target)
        canvas.drawRect(0f, bandTop, width.toFloat(), target.height.toFloat(), Paint().apply { color = Color.BLACK })
        canvas.save()
        canvas.translate(padding, bandTop + verticalPadding)
        layout.draw(canvas)
        canvas.restore()
        return target
    }

    /** Draws `[ ]` or `[✓]` and returns the X coordinate where the task text/ink starts. */
    private fun drawCheckbox(canvas: Canvas, row: TaskRow, rowTop: Float, rowHeight: Float): Float {
        val box = rowHeight * 0.42f
        val left = rowHeight * (if (row.isSubtask) 0.85f else 0.45f)
        val cy = rowTop + rowHeight / 2f
        val rect = RectF(left, cy - box / 2f, left + box, cy + box / 2f)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(3.5f, box / 8f)
        }
        canvas.drawRoundRect(rect, box / 6f, box / 6f, boxPaint)
        if (row.completed) {
            val tick = Path().apply {
                moveTo(rect.left + box * 0.2f, rect.top + box * 0.52f)
                lineTo(rect.left + box * 0.42f, rect.top + box * 0.74f)
                lineTo(rect.left + box * 0.8f, rect.top + box * 0.28f)
            }
            canvas.drawPath(tick, Paint(boxPaint).apply { strokeWidth = max(3.5f, box / 6.5f); strokeCap = Paint.Cap.ROUND })
        }
        return rect.right + rowHeight * 0.30f
    }

    private fun drawTypedText(
        canvas: Canvas, text: String, row: TaskRow, rowTop: Float, rowHeight: Float, left: Float, right: Float
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = rowHeight * (if (row.isSubtask) 0.34f else 0.40f)
            isStrikeThruText = row.completed
        }
        val line = text.lineSequence().first().trim()
        val shown = TextUtils.ellipsize(line, paint, (right - left).coerceAtLeast(1f), TextUtils.TruncateAt.END)
        val baseline = rowTop + rowHeight / 2f + paint.textSize / 3f
        canvas.drawText(shown, 0, shown.length, left, baseline, paint)
        if (row.completed && shown.isNotEmpty()) {
            val textWidth = paint.measureText(shown, 0, shown.length)
            val strikeY = rowTop + rowHeight / 2f
            val strikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = max(2.5f, rowHeight * 0.04f)
            }
            canvas.drawLine(left, strikeY, (left + textWidth).coerceAtMost(right), strikeY, strikePaint)
        }
    }
}
