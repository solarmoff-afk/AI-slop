package com.rotokey.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class ContourOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var frameBitmap: Bitmap? = null
    private val imageRect = RectF()
    private val viewPoints = mutableListOf<PointF>()
    private val drawPath = Path()
    private var isClosed = false

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 230, 118)
        style = Paint.Style.FILL
    }

    fun setFrameBitmap(bitmap: Bitmap) {
        frameBitmap = bitmap
        clearContour()
        computeImageRect()
        invalidate()
    }

    fun clearContour() {
        viewPoints.clear()
        drawPath.reset()
        isClosed = false
        invalidate()
    }

    fun hasContour(): Boolean = viewPoints.size >= 3 && isClosed

    fun getBitmapContourPoints(): List<PointF> {
        val bmp = frameBitmap ?: return emptyList()
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) return emptyList()

        val scaleX = bmp.width / imageRect.width()
        val scaleY = bmp.height / imageRect.height()

        return viewPoints.map { pt ->
            val bx = ((pt.x - imageRect.left) * scaleX).coerceIn(0f, (bmp.width - 1).toFloat())
            val by = ((pt.y - imageRect.top) * scaleY).coerceIn(0f, (bmp.height - 1).toFloat())
            PointF(bx, by)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeImageRect()
    }

    private fun computeImageRect() {
        val bmp = frameBitmap ?: return
        if (width == 0 || height == 0) return

        val scale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f
        imageRect.set(left, top, left + drawW, top + drawH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = frameBitmap ?: return
        canvas.drawBitmap(bmp, null, imageRect, null)

        if (viewPoints.isNotEmpty()) {
            if (isClosed) {
                canvas.drawPath(drawPath, fillPaint)
            }
            canvas.drawPath(drawPath, strokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (frameBitmap == null || !isEnabled) return false

        val x = event.x.coerceIn(imageRect.left, imageRect.right)
        val y = event.y.coerceIn(imageRect.top, imageRect.bottom)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                viewPoints.clear()
                drawPath.reset()
                isClosed = false
                viewPoints.add(PointF(x, y))
                drawPath.moveTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val last = viewPoints.lastOrNull()
                if (last == null || Math.hypot((x - last.x).toDouble(), (y - last.y).toDouble()) > 8.0) {
                    viewPoints.add(PointF(x, y))
                    drawPath.lineTo(x, y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (viewPoints.size >= 3) {
                    drawPath.close()
                    isClosed = true
                } else {
                    clearContour()
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
