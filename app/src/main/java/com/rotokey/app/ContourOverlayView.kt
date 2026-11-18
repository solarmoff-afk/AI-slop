package com.rotokey.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

class ContourOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var frameBitmap: Bitmap? = null
    private val imageRect = RectF()

    // Контрольные точки хранятся в координатах пикселей исходного Bitmap
    private var controlPoints = mutableListOf<PointF>()
    private var selectedIndex = -1
    private var dragIndex = -1
    private var dragStartSnapshot: List<PointF>? = null
    private var didMoveDuringDrag = false

    // Временный штрих при первичном рисовании
    private val rawStrokeView = mutableListOf<PointF>()
    private val rawDrawPath = Path()
    private val polyDrawPath = Path()

    private val undoStack = ArrayDeque<List<PointF>>()
    private val redoStack = ArrayDeque<List<PointF>>()

    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean, hasContour: Boolean, canDeletePoint: Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val pointHitRadius = 26f * density
    private val edgeHitRadius = 20f * density
    private val handleRadius = 7f * density

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 0, 230, 118)
        style = Paint.Style.FILL
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFAB00")
        style = Paint.Style.FILL
    }

    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00C853")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    fun setFrameBitmap(bitmap: Bitmap, keepContour: Boolean = false) {
        frameBitmap = bitmap
        if (!keepContour) {
            controlPoints.clear()
            rawStrokeView.clear()
            rawDrawPath.reset()
            undoStack.clear()
            redoStack.clear()
            selectedIndex = -1
        } else {
            // Подгоняем точки в границы нового кадра на всякий случай
            controlPoints.forEach { pt ->
                pt.x = pt.x.coerceIn(0f, (bitmap.width - 1).toFloat())
                pt.y = pt.y.coerceIn(0f, (bitmap.height - 1).toFloat())
            }
        }
        computeImageRect()
        notifyHistory()
        invalidate()
    }

    fun hasContour(): Boolean = controlPoints.size >= 3

    fun getBitmapContourPoints(): List<PointF> = controlPoints.map { PointF(it.x, it.y) }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(copyPoints(controlPoints))
        controlPoints = undoStack.removeLast().map { PointF(it.x, it.y) }.toMutableList()
        selectedIndex = -1
        notifyHistory()
        invalidate()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(copyPoints(controlPoints))
        controlPoints = redoStack.removeLast().map { PointF(it.x, it.y) }.toMutableList()
        selectedIndex = -1
        notifyHistory()
        invalidate()
    }

    fun deleteSelectedPoint() {
        if (selectedIndex in controlPoints.indices && controlPoints.size > 3) {
            pushUndo(copyPoints(controlPoints))
            controlPoints.removeAt(selectedIndex)
            selectedIndex = -1
            notifyHistory()
            invalidate()
        }
    }

    fun deleteContour() {
        if (controlPoints.isEmpty()) return
        pushUndo(copyPoints(controlPoints))
        controlPoints.clear()
        selectedIndex = -1
        notifyHistory()
        invalidate()
    }

    private fun pushUndo(snapshot: List<PointF>) {
        undoStack.addLast(snapshot)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun copyPoints(src: List<PointF>): List<PointF> = src.map { PointF(it.x, it.y) }

    private fun notifyHistory() {
        onHistoryChanged?.invoke(
            undoStack.isNotEmpty(),
            redoStack.isNotEmpty(),
            controlPoints.size >= 3,
            selectedIndex in controlPoints.indices && controlPoints.size > 3
        )
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

    private fun bitmapToView(pt: PointF): PointF {
        val bmp = frameBitmap ?: return PointF()
        val vx = imageRect.left + (pt.x / bmp.width) * imageRect.width()
        val vy = imageRect.top + (pt.y / bmp.height) * imageRect.height()
        return PointF(vx, vy)
    }

    private fun viewToBitmap(vx: Float, vy: Float): PointF {
        val bmp = frameBitmap ?: return PointF()
        val bx = ((vx - imageRect.left) / imageRect.width() * bmp.width)
            .coerceIn(0f, (bmp.width - 1).toFloat())
        val by = ((vy - imageRect.top) / imageRect.height() * bmp.height)
            .coerceIn(0f, (bmp.height - 1).toFloat())
        return PointF(bx, by)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = frameBitmap ?: return
        canvas.drawBitmap(bmp, null, imageRect, null)

        if (rawStrokeView.isNotEmpty()) {
            canvas.drawPath(rawDrawPath, strokePaint)
        } else if (controlPoints.size >= 3) {
            polyDrawPath.reset()
            val viewPts = controlPoints.map { bitmapToView(it) }
            polyDrawPath.moveTo(viewPts[0].x, viewPts[0].y)
            for (i in 1 until viewPts.size) {
                polyDrawPath.lineTo(viewPts[i].x, viewPts[i].y)
            }
            polyDrawPath.close()

            canvas.drawPath(polyDrawPath, fillPaint)
            canvas.drawPath(polyDrawPath, strokePaint)

            for (i in viewPts.indices) {
                val p = viewPts[i]
                val r = if (i == selectedIndex) handleRadius * 1.25f else handleRadius
                val fill = if (i == selectedIndex) handleSelectedPaint else handleFillPaint
                canvas.drawCircle(p.x, p.y, r, fill)
                canvas.drawCircle(p.x, p.y, r, handleBorderPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (frameBitmap == null || !isEnabled || imageRect.width() <= 0f) return false

        val vx = event.x.coerceIn(imageRect.left, imageRect.right)
        val vy = event.y.coerceIn(imageRect.top, imageRect.bottom)

        // Режим 1: контура ещё нет — рисуем от руки
        if (controlPoints.size < 3) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rawStrokeView.clear()
                    rawDrawPath.reset()
                    rawStrokeView.add(PointF(vx, vy))
                    rawDrawPath.moveTo(vx, vy)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val last = rawStrokeView.lastOrNull()
                    if (last == null || hypot((vx - last.x).toDouble(), (vy - last.y).toDouble()) > 6.0) {
                        rawStrokeView.add(PointF(vx, vy))
                        rawDrawPath.lineTo(vx, vy)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (rawStrokeView.size >= 5) {
                        val simplified = simplifyStrokeToBitmapPoints(rawStrokeView)
                        if (simplified.size >= 3) {
                            pushUndo(emptyList())
                            controlPoints = simplified.toMutableList()
                            selectedIndex = -1
                        }
                    }
                    rawStrokeView.clear()
                    rawDrawPath.reset()
                    notifyHistory()
                    invalidate()
                    return true
                }
            }
            return true
        }

        // Режим 2: контур есть — двигаем контрольные точки или добавляем новые на ребро
        val viewPts = controlPoints.map { bitmapToView(it) }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 1. Попадание в контрольную точку
                val hitPointIdx = findNearestPointIndex(viewPts, vx, vy, pointHitRadius)
                if (hitPointIdx >= 0) {
                    dragIndex = hitPointIdx
                    selectedIndex = hitPointIdx
                    dragStartSnapshot = copyPoints(controlPoints)
                    didMoveDuringDrag = false
                    notifyHistory()
                    invalidate()
                    return true
                }

                // 2. Попадание на ребро между точками — вставляем новую точку и сразу тащим её
                val edgeHit = findNearestEdgeProjection(viewPts, vx, vy, edgeHitRadius)
                if (edgeHit != null) {
                    val (insertAfterIdx, projViewPt) = edgeHit
                    dragStartSnapshot = copyPoints(controlPoints)
                    val newIdx = insertAfterIdx + 1
                    controlPoints.add(newIdx, viewToBitmap(projViewPt.x, projViewPt.y))
                    dragIndex = newIdx
                    selectedIndex = newIdx
                    didMoveDuringDrag = true // новая точка — уже изменение для Undo
                    notifyHistory()
                    invalidate()
                    return true
                }

                // Тап мимо точек и линий — просто снимаем выделение с точки, но контур НЕ удаляем
                if (selectedIndex != -1) {
                    selectedIndex = -1
                    notifyHistory()
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragIndex in controlPoints.indices) {
                    controlPoints[dragIndex] = viewToBitmap(vx, vy)
                    didMoveDuringDrag = true
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragIndex >= 0 && didMoveDuringDrag) {
                    dragStartSnapshot?.let { pushUndo(it) }
                    notifyHistory()
                }
                dragIndex = -1
                dragStartSnapshot = null
                didMoveDuringDrag = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun simplifyStrokeToBitmapPoints(strokeView: List<PointF>): List<PointF> {
        // Проверяем, что штрих не случайный микро-тап
        var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
        for (p in strokeView) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        if (hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()) < 24f * density) {
            return emptyList()
        }

        // Оставляем точки с шагом ~28dp (но не больше ~24 точек, чтобы было удобно двигать)
        var minDist = 28f * density
        var filtered = filterByDistance(strokeView, minDist)
        while (filtered.size > 24) {
            minDist *= 1.25f
            filtered = filterByDistance(strokeView, minDist)
        }
        if (filtered.size < 4 && strokeView.size >= 4) {
            val step = strokeView.size / 4
            filtered = listOf(strokeView[0], strokeView[step], strokeView[step * 2], strokeView[step * 3])
        }
        return filtered.map { viewToBitmap(it.x, it.y) }
    }

    private fun filterByDistance(pts: List<PointF>, minDist: Float): List<PointF> {
        val out = mutableListOf<PointF>()
        for (p in pts) {
            val last = out.lastOrNull()
            if (last == null || hypot((p.x - last.x).toDouble(), (p.y - last.y).toDouble()) >= minDist) {
                out.add(p)
            }
        }
        if (out.size >= 2) {
            val first = out.first()
            val last = out.last()
            if (hypot((first.x - last.x).toDouble(), (first.y - last.y).toDouble()) < minDist * 0.5f) {
                out.removeAt(out.lastIndex)
            }
        }
        return out
    }

    private fun findNearestPointIndex(viewPts: List<PointF>, x: Float, y: Float, maxDist: Float): Int {
        var bestIdx = -1
        var bestDist = maxDist
        for (i in viewPts.indices) {
            val d = hypot((x - viewPts[i].x).toDouble(), (y - viewPts[i].y).toDouble()).toFloat()
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun findNearestEdgeProjection(
        viewPts: List<PointF>,
        px: Float,
        py: Float,
        maxDist: Float
    ): Pair<Int, PointF>? {
        var bestIdx = -1
        var bestDist = maxDist
        var bestProj = PointF()

        val n = viewPts.size
        for (i in 0 until n) {
            val a = viewPts[i]
            val b = viewPts[(i + 1) % n]
            val abx = b.x - a.x
            val aby = b.y - a.y
            val lenSq = abx * abx + aby * aby
            if (lenSq < 1f) continue

            val t = (((px - a.x) * abx + (py - a.y) * aby) / lenSq).coerceIn(0f, 1f)
            val projX = a.x + t * abx
            val projY = a.y + t * aby
            val d = hypot((px - projX).toDouble(), (py - projY).toDouble()).toFloat()
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
                bestProj = PointF(projX, projY)
            }
        }
        return if (bestIdx >= 0) bestIdx to bestProj else null
    }
}
