package com.rotokey.app

import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

class EffectStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val glView = GLSurfaceView(context)
    private val gizmoOverlay = GizmoOverlayView(context)
    private val renderer = StageRenderer()

    private var mediaPlayer: MediaPlayer? = null
    private var playerSurface: Surface? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isLivePlaying = false

    private var playStartMs = 0L
    private var playEndMs = 0L
    private var playFps = 30
    private var playHomographies: List<FloatArray> = emptyList()

    var onShaderError: ((String) -> Unit)? = null
    var onEffectTransformChanged: (() -> Unit)? = null

    init {
        glView.setEGLContextClientVersion(2)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        addView(glView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(gizmoOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setStaticFrame(bitmap: Bitmap) {
        stopLivePreview()
        renderer.updateBitmap(bitmap)
        gizmoOverlay.setVideoSize(bitmap.width, bitmap.height)
        glView.requestRender()
    }

    fun setEffects(effects: List<ShaderEffectLayer>, selectedId: Long?) {
        val copies = effects.map { it.copy() }
        renderer.setEffects(copies)
        gizmoOverlay.setActiveEffect(effects.firstOrNull { it.id == selectedId })
        if (!isLivePlaying) glView.requestRender()
    }

    fun startLivePreview(
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        fps: Int,
        homographies: List<FloatArray>
    ) {
        stopLivePreview()
        playStartMs = startMs
        playEndMs = endMs
        playFps = fps.coerceAtLeast(1)
        playHomographies = homographies
        isLivePlaying = true
        gizmoOverlay.isEditingEnabled = false
        gizmoOverlay.invalidate()

        glView.queueEvent {
            val st = renderer.prepareSurfaceTexture()
            st.setOnFrameAvailableListener {
                renderer.markOesFrameAvailable()
                glView.requestRender()
            }
            mainHandler.post {
                if (!isLivePlaying) return@post
                try {
                    val surf = Surface(st)
                    playerSurface = surf
                    val mp = MediaPlayer().apply {
                        setDataSource(context, videoUri)
                        setSurface(surf)
                        setOnPreparedListener { player ->
                            player.seekTo(startMs.toInt())
                            player.start()
                            scheduleLoopCheck()
                        }
                        setOnCompletionListener { player ->
                            if (isLivePlaying) {
                                player.seekTo(startMs.toInt())
                                player.start()
                            }
                        }
                        prepareAsync()
                    }
                    mediaPlayer = mp
                } catch (e: Exception) {
                    stopLivePreview()
                    onShaderError?.invoke("Ошибка предпросмотра: ${e.message}")
                }
            }
        }
    }

    fun stopLivePreview() {
        isLivePlaying = false
        mainHandler.removeCallbacks(loopChecker)
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        playerSurface?.release()
        playerSurface = null

        renderer.setLiveMode(false)
        gizmoOverlay.isEditingEnabled = true
        gizmoOverlay.invalidate()
        glView.requestRender()
    }

    fun isPlaying(): Boolean = isLivePlaying

    private val loopChecker = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (!isLivePlaying || mp == null) return
            try {
                val pos = mp.currentPosition.toLong()
                if (pos >= playEndMs) {
                    mp.seekTo(playStartMs.toInt())
                }
                val relMs = (pos - playStartMs).coerceAtLeast(0L)
                val frameIdx = ((relMs * playFps) / 1000L).toInt()
                val h = if (playHomographies.isNotEmpty()) {
                    playHomographies[frameIdx.coerceIn(0, playHomographies.lastIndex)]
                } else {
                    OpenCVHomographyTracker.identityColMajor()
                }
                renderer.setLiveFrameParams(relMs / 1000f, frameIdx.toFloat(), h)
            } catch (_: Exception) {}
            mainHandler.postDelayed(this, 25L)
        }
    }

    private fun scheduleLoopCheck() {
        mainHandler.removeCallbacks(loopChecker)
        mainHandler.post(loopChecker)
    }

    fun onPauseView() {
        stopLivePreview()
        glView.onPause()
    }

    fun onResumeView() {
        glView.onResume()
    }

    private inner class StageRenderer : GLSurfaceView.Renderer {
        private var engine: ShaderEngine? = null
        private var viewW = 1
        private var viewH = 1
        private var srcW = 1
        private var srcH = 1

        @Volatile private var pendingBitmap: Bitmap? = null
        @Volatile private var hasBitmapUploaded = false
        @Volatile private var effectsList: List<ShaderEffectLayer> = emptyList()

        private var surfaceTexture: SurfaceTexture? = null
        private val stMatrix = FloatArray(16)
        @Volatile private var liveMode = false
        @Volatile private var oesFrameAvailable = false
        @Volatile private var liveTimeSec = 0f
        @Volatile private var liveFrameIdx = 0f
        @Volatile private var liveInvH = OpenCVHomographyTracker.identityColMajor()

        fun updateBitmap(bmp: Bitmap) {
            pendingBitmap = bmp
            srcW = bmp.width
            srcH = bmp.height
            liveMode = false
        }

        fun setEffects(list: List<ShaderEffectLayer>) {
            effectsList = list
        }

        fun setLiveMode(enabled: Boolean) {
            liveMode = enabled
        }

        fun setLiveFrameParams(timeSec: Float, frameIdx: Float, invH: FloatArray) {
            liveTimeSec = timeSec
            liveFrameIdx = frameIdx
            liveInvH = invH
        }

        fun markOesFrameAvailable() {
            oesFrameAvailable = true
        }

        fun prepareSurfaceTexture(): SurfaceTexture {
            surfaceTexture?.release()
            val eng = engine ?: ShaderEngine().also { it.initGl(); engine = it }
            val st = SurfaceTexture(eng.oesTexId)
            surfaceTexture = st
            liveMode = true
            oesFrameAvailable = false
            return st
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            engine?.release()
            engine = ShaderEngine().apply { initGl() }
            GLES20.glClearColor(0.12f, 0.12f, 0.12f, 1f)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewW = width.coerceAtLeast(1)
            viewH = height.coerceAtLeast(1)
        }

        override fun onDrawFrame(gl: GL10?) {
            val eng = engine ?: return
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            val bmp = pendingBitmap
            if (bmp != null && !bmp.isRecycled) {
                eng.uploadBitmap(bmp)
                pendingBitmap = null
                hasBitmapUploaded = true
            }

            val scale = min(viewW.toFloat() / srcW, viewH.toFloat() / srcH)
            val vpW = (srcW * scale).toInt().coerceAtLeast(1)
            val vpH = (srcH * scale).toInt().coerceAtLeast(1)
            val vpLeft = (viewW - vpW) / 2
            val vpBottom = (viewH - vpH) / 2

            var err: String? = null
            if (liveMode && surfaceTexture != null) {
                if (oesFrameAvailable) {
                    oesFrameAvailable = false
                    surfaceTexture?.updateTexImage()
                    surfaceTexture?.getTransformMatrix(stMatrix)
                }
                err = eng.renderFromOes(
                    stMatrix = stMatrix,
                    width = srcW,
                    height = srcH,
                    vpLeft = vpLeft,
                    vpBottom = vpBottom,
                    vpWidth = vpW,
                    vpHeight = vpH,
                    effects = effectsList,
                    timeSec = liveTimeSec,
                    frameIdx = liveFrameIdx,
                    invHomography = liveInvH
                )
            } else if (hasBitmapUploaded) {
                err = eng.renderFromBitmap(
                    width = srcW,
                    height = srcH,
                    vpLeft = vpLeft,
                    vpBottom = vpBottom,
                    vpWidth = vpW,
                    vpHeight = vpH,
                    effects = effectsList,
                    timeSec = 0f,
                    frameIdx = 0f,
                    invHomography = OpenCVHomographyTracker.identityColMajor()
                )
            }

            if (err != null) {
                mainHandler.post { onShaderError?.invoke(err) }
            }
        }
    }

    private inner class GizmoOverlayView(context: Context) : View(context) {
        private var srcW = 1
        private var srcH = 1
        private val videoRect = RectF()
        private var activeEffect: ShaderEffectLayer? = null
        var isEditingEnabled = true

        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var prevSpan = 0f
        private var prevAngle = 0f
        private var mode = 0

        private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD54F")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD54F")
            style = Paint.Style.FILL
        }

        fun setVideoSize(w: Int, h: Int) {
            srcW = w.coerceAtLeast(1)
            srcH = h.coerceAtLeast(1)
            computeRect()
            invalidate()
        }

        fun setActiveEffect(effect: ShaderEffectLayer?) {
            activeEffect = effect
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            computeRect()
        }

        private fun computeRect() {
            if (width == 0 || height == 0) return
            val s = min(width.toFloat() / srcW, height.toFloat() / srcH)
            val dw = srcW * s
            val dh = srcH * s
            val l = (width - dw) / 2f
            val t = (height - dh) / 2f
            videoRect.set(l, t, l + dw, t + dh)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!isEditingEnabled) return
            val fx = activeEffect ?: return
            if (videoRect.width() <= 0f) return

            val cx = videoRect.left + fx.posX * videoRect.width()
            val cy = videoRect.top + fx.posY * videoRect.height()
            val halfSize = (fx.scale * 0.5f) * videoRect.height()

            canvas.save()
            canvas.rotate(Math.toDegrees(fx.rotationRad.toDouble()).toFloat(), cx, cy)
            canvas.drawRect(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, boxPaint)
            canvas.drawCircle(cx, cy, 8f, centerPaint)
            canvas.restore()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEditingEnabled) return false
            val fx = activeEffect ?: return false
            if (videoRect.width() <= 0f || videoRect.height() <= 0f) return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mode = 1
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        mode = 2
                        prevSpan = span(event)
                        prevAngle = angle(event)
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == 1 && event.pointerCount == 1) {
                        val dx = (event.x - lastTouchX) / videoRect.width()
                        val dy = (event.y - lastTouchY) / videoRect.height()
                        fx.posX = (fx.posX + dx).coerceIn(0f, 1f)
                        fx.posY = (fx.posY + dy).coerceIn(0f, 1f)
                        lastTouchX = event.x
                        lastTouchY = event.y
                        syncAndRedraw()
                    } else if (mode == 2 && event.pointerCount >= 2) {
                        val curSpan = span(event)
                        val curAngle = angle(event)
                        if (prevSpan > 10f) {
                            fx.scale = (fx.scale * (curSpan / prevSpan)).coerceIn(0.08f, 2.5f)
                        }
                        fx.rotationRad += (curAngle - prevAngle)
                        prevSpan = curSpan
                        prevAngle = curAngle
                        syncAndRedraw()
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount <= 2) {
                        val remainingIdx = if (event.actionIndex == 0) 1 else 0
                        if (remainingIdx < event.pointerCount) {
                            lastTouchX = event.getX(remainingIdx)
                            lastTouchY = event.getY(remainingIdx)
                            mode = 1
                        } else {
                            mode = 0
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mode = 0
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun syncAndRedraw() {
            onEffectTransformChanged?.invoke()
            invalidate()
        }

        private fun span(e: MotionEvent): Float =
            hypot((e.getX(1) - e.getX(0)).toDouble(), (e.getY(1) - e.getY(0)).toDouble()).toFloat()

        private fun angle(e: MotionEvent): Float =
            atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble()).toFloat()
    }
}
