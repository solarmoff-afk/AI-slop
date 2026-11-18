package com.rotokey.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private enum class AppMode { ROTO, SHAKE }
    private var currentMode = AppMode.ROTO

    private lateinit var btnModeRoto: Button
    private lateinit var btnModeShake: Button
    private lateinit var tvStatus: TextView
    private lateinit var previewContainer: FrameLayout
    private lateinit var contourView: ContourOverlayView
    private lateinit var targetImageView: ImageView
    private lateinit var videoPreview: VideoView
    private lateinit var progressBar: ProgressBar

    private lateinit var timelineLayout: LinearLayout
    private lateinit var tvTimelineLabel: TextView
    private lateinit var tvTimeRange: TextView
    private lateinit var rangeSlider: RangeSlider

    // Ротоскоп
    private lateinit var rotoControlsLayout: LinearLayout
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private lateinit var btnDeletePoint: Button
    private lateinit var btnDeleteContour: Button
    private lateinit var spinnerColor: Spinner
    private lateinit var btnPickVideo: Button
    private lateinit var btnProcessRoto: Button

    // Перенос качки
    private lateinit var shakeControlsLayout: LinearLayout
    private lateinit var btnPickDonor: Button
    private lateinit var btnPickTargetPhoto: Button
    private lateinit var btnPickTargetVideo: Button
    private lateinit var tvShakeInfo: TextView
    private lateinit var tvShakeStrength: TextView
    private lateinit var sliderStrength: Slider
    private lateinit var tvShakeZoom: TextView
    private lateinit var sliderZoom: Slider
    private lateinit var btnProcessShake: Button

    // Предпросмотр
    private lateinit var previewControlsLayout: LinearLayout
    private lateinit var btnBackToEdit: Button
    private lateinit var btnReplay: Button
    private lateinit var btnSaveGallery: Button

    // Состояние
    private var rotoVideoUri: Uri? = null
    private var rotoDurationMs: Long = 0L
    private var lastRotoStartSec: Float = 0f

    private var donorVideoUri: Uri? = null
    private var donorDurationMs: Long = 0L
    private var lastDonorStartSec: Float = 0f
    private var targetPhotoUri: Uri? = null
    private var targetVideoUri: Uri? = null

    private var currentPreviewFile: File? = null
    private var mediaController: MediaController? = null

    private val chromaColors = listOf(
        "Зелёный (#00FF00)" to Color.GREEN,
        "Синий (#0000FF)" to Color.BLUE,
        "Пурпурный (#FF00FF)" to Color.MAGENTA,
        "Чёрный (#000000)" to Color.BLACK
    )

    private val pickRotoVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                rotoVideoUri = uri
                loadRotoVideo(uri)
            }
        }

    private val pickDonorVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                donorVideoUri = uri
                loadDonorVideo(uri)
            }
        }

    private val pickTargetPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                targetPhotoUri = uri
                targetVideoUri = null
                loadTargetPhotoPreview(uri)
            }
        }

    private val pickTargetVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                targetVideoUri = uri
                targetPhotoUri = null
                loadTargetVideoPreview(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()

        if (!OpenCVLoader.initLocal()) {
            tvStatus.text = "Ошибка инициализации OpenCV!"
            btnPickVideo.isEnabled = false
            btnProcessRoto.isEnabled = false
            btnPickDonor.isEnabled = false
            btnProcessShake.isEnabled = false
            return
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            chromaColors.map { it.first }
        )
        spinnerColor.adapter = adapter

        setupContourCallbacks()
        setupTimelineSlider()
        setupShakeSliders()
        setupButtons()
        switchMode(AppMode.ROTO)
    }

    private fun bindViews() {
        btnModeRoto = findViewById(R.id.btnModeRoto)
        btnModeShake = findViewById(R.id.btnModeShake)
        tvStatus = findViewById(R.id.tvStatus)
        previewContainer = findViewById(R.id.previewContainer)
        contourView = findViewById(R.id.contourView)
        targetImageView = findViewById(R.id.targetImageView)
        videoPreview = findViewById(R.id.videoPreview)
        progressBar = findViewById(R.id.progressBar)

        timelineLayout = findViewById(R.id.timelineLayout)
        tvTimelineLabel = findViewById(R.id.tvTimelineLabel)
        tvTimeRange = findViewById(R.id.tvTimeRange)
        rangeSlider = findViewById(R.id.rangeSlider)

        rotoControlsLayout = findViewById(R.id.rotoControlsLayout)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnDeletePoint = findViewById(R.id.btnDeletePoint)
        btnDeleteContour = findViewById(R.id.btnDeleteContour)
        spinnerColor = findViewById(R.id.spinnerColor)
        btnPickVideo = findViewById(R.id.btnPickVideo)
        btnProcessRoto = findViewById(R.id.btnProcessRoto)

        shakeControlsLayout = findViewById(R.id.shakeControlsLayout)
        btnPickDonor = findViewById(R.id.btnPickDonor)
        btnPickTargetPhoto = findViewById(R.id.btnPickTargetPhoto)
        btnPickTargetVideo = findViewById(R.id.btnPickTargetVideo)
        tvShakeInfo = findViewById(R.id.tvShakeInfo)
        tvShakeStrength = findViewById(R.id.tvShakeStrength)
        sliderStrength = findViewById(R.id.sliderStrength)
        tvShakeZoom = findViewById(R.id.tvShakeZoom)
        sliderZoom = findViewById(R.id.sliderZoom)
        btnProcessShake = findViewById(R.id.btnProcessShake)

        previewControlsLayout = findViewById(R.id.previewControlsLayout)
        btnBackToEdit = findViewById(R.id.btnBackToEdit)
        btnReplay = findViewById(R.id.btnReplay)
        btnSaveGallery = findViewById(R.id.btnSaveGallery)
    }

    private fun switchMode(mode: AppMode) {
        currentMode = mode
        exitPreviewMode()

        if (mode == AppMode.ROTO) {
            btnModeRoto.alpha = 1.0f
            btnModeShake.alpha = 0.5f
            rotoControlsLayout.visibility = View.VISIBLE
            shakeControlsLayout.visibility = View.GONE
            contourView.visibility = View.VISIBLE
            targetImageView.visibility = View.GONE
            tvTimelineLabel.text = "Отрезок: "

            if (rotoDurationMs > 0L) {
                configureSliderForDuration(rotoDurationMs)
                tvStatus.text = "Двигайте точки, кликайте по линии для детализации"
            } else {
                rangeSlider.isEnabled = false
                updateRangeText(0L, 0L)
                tvStatus.text = "Выберите видео и обведите объект пальцем"
            }
        } else {
            btnModeRoto.alpha = 0.5f
            btnModeShake.alpha = 1.0f
            rotoControlsLayout.visibility = View.GONE
            shakeControlsLayout.visibility = View.VISIBLE
            contourView.visibility = View.GONE
            targetImageView.visibility = View.VISIBLE
            tvTimelineLabel.text = "Донор: "

            if (donorDurationMs > 0L) {
                configureSliderForDuration(donorDurationMs)
            } else {
                rangeSlider.isEnabled = false
                updateRangeText(0L, 0L)
            }
            updateShakeInfoText()
            tvStatus.text = "1) Выберите видео-донор с рук  2) Выберите фото/видео"
        }
    }

    private fun setupContourCallbacks() {
        contourView.onHistoryChanged = { canUndo, canRedo, hasContour, canDeletePoint ->
            btnUndo.isEnabled = canUndo
            btnRedo.isEnabled = canRedo
            btnDeleteContour.isEnabled = hasContour
            btnDeletePoint.isEnabled = canDeletePoint
        }
    }

    private fun setupTimelineSlider() {
        rangeSlider.addOnChangeListener { slider, _, _ ->
            val vals = slider.values
            if (vals.size == 2) {
                updateRangeText((vals[0] * 1000f).toLong(), (vals[1] * 1000f).toLong())
            }
        }

        rangeSlider.addOnSliderTouchListener(object : RangeSlider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: RangeSlider) {}

            override fun onStopTrackingTouch(slider: RangeSlider) {
                val vals = slider.values
                if (vals.size < 2) return
                val startSec = vals[0]

                if (currentMode == AppMode.ROTO) {
                    val uri = rotoVideoUri ?: return
                    if (abs(startSec - lastRotoStartSec) > 0.05f) {
                        lastRotoStartSec = startSec
                        val timeMs = (startSec * 1000f).toLong()
                        lifecycleScope.launch {
                            val bmp = VideoProcessor.loadFrameAtTime(this@MainActivity, uri, timeMs, exact = true)
                            if (bmp != null) {
                                contourView.setFrameBitmap(bmp, keepContour = contourView.hasContour())
                            }
                        }
                    }
                } else {
                    lastDonorStartSec = startSec
                }
            }
        })
    }

    private fun setupShakeSliders() {
        sliderStrength.addOnChangeListener { _, value, _ ->
            tvShakeStrength.text = String.format(Locale.US, "Сила: %.1fx", value)
        }
        sliderZoom.addOnChangeListener { _, value, _ ->
            tvShakeZoom.text = String.format(Locale.US, "Зум краёв: %.2fx", value)
        }
    }

    private fun setupButtons() {
        btnModeRoto.setOnClickListener { switchMode(AppMode.ROTO) }
        btnModeShake.setOnClickListener { switchMode(AppMode.SHAKE) }

        btnPickVideo.setOnClickListener { pickRotoVideoLauncher.launch("video/*") }
        btnUndo.setOnClickListener { contourView.undo() }
        btnRedo.setOnClickListener { contourView.redo() }
        btnDeletePoint.setOnClickListener { contourView.deleteSelectedPoint() }
        btnDeleteContour.setOnClickListener { contourView.deleteContour() }
        btnProcessRoto.setOnClickListener { startRotoProcessing() }

        btnPickDonor.setOnClickListener { pickDonorVideoLauncher.launch("video/*") }
        btnPickTargetPhoto.setOnClickListener { pickTargetPhotoLauncher.launch("image/*") }
        btnPickTargetVideo.setOnClickListener { pickTargetVideoLauncher.launch("video/*") }
        btnProcessShake.setOnClickListener { startShakeProcessing() }

        btnBackToEdit.setOnClickListener { exitPreviewMode() }
        btnReplay.setOnClickListener {
            videoPreview.seekTo(0)
            videoPreview.start()
        }
        btnSaveGallery.setOnClickListener {
            val file = currentPreviewFile ?: return@setOnClickListener
            btnSaveGallery.isEnabled = false
            lifecycleScope.launch {
                try {
                    VideoProcessor.saveToGallery(this@MainActivity, file)
                    Toast.makeText(this@MainActivity, "Сохранено в Movies/RotoKey", Toast.LENGTH_LONG).show()
                    tvStatus.text = "Сохранено в Галерею (Movies/RotoKey)"
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    btnSaveGallery.isEnabled = true
                }
            }
        }
    }

    private fun loadRotoVideo(uri: Uri) {
        exitPreviewMode()
        tvStatus.text = "Загрузка видео..."
        rangeSlider.isEnabled = false

        lifecycleScope.launch {
            val meta = VideoProcessor.getVideoMetadata(this@MainActivity, uri)
            if (meta.firstFrame != null && meta.durationMs > 0L) {
                rotoDurationMs = meta.durationMs
                lastRotoStartSec = 0f
                configureSliderForDuration(meta.durationMs)
                contourView.setFrameBitmap(meta.firstFrame, keepContour = false)
                tvStatus.text = "Обведите объект, поправьте точки и нажмите Старт"
            } else {
                tvStatus.text = "Не удалось открыть видео"
            }
        }
    }

    private fun loadDonorVideo(uri: Uri) {
        exitPreviewMode()
        tvStatus.text = "Загрузка донора..."
        rangeSlider.isEnabled = false

        lifecycleScope.launch {
            val meta = VideoProcessor.getVideoMetadata(this@MainActivity, uri)
            if (meta.durationMs > 0L) {
                donorDurationMs = meta.durationMs
                lastDonorStartSec = 0f
                configureSliderForDuration(meta.durationMs)
                if (targetPhotoUri == null && targetVideoUri == null && meta.firstFrame != null) {
                    targetImageView.setImageBitmap(meta.firstFrame)
                } else {
                    meta.firstFrame?.recycle()
                }
                updateShakeInfoText()
                tvStatus.text = "Донор загружен. Выберите цель (фото или видео)"
            } else {
                tvStatus.text = "Не удалось открыть видео-донор"
            }
        }
    }

    private fun loadTargetPhotoPreview(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bmp = VideoProcessor.loadPhotoBitmap(this@MainActivity, uri)
                targetImageView.setImageBitmap(bmp)
                updateShakeInfoText()
                tvStatus.text = "Фото выбрано. Настройте силу/зум и нажмите Перенести качку"
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка фото: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTargetVideoPreview(uri: Uri) {
        lifecycleScope.launch {
            val frame = VideoProcessor.loadFrameAtTime(this@MainActivity, uri, 0L, exact = false)
            if (frame != null) {
                targetImageView.setImageBitmap(frame)
            }
            updateShakeInfoText()
            tvStatus.text = "Целевое видео выбрано. Нажмите Перенести качку"
        }
    }

    private fun configureSliderForDuration(durationMs: Long) {
        val durSec = (durationMs / 1000f).coerceAtLeast(0.5f)
        rangeSlider.valueFrom = 0f
        rangeSlider.valueTo = durSec
        rangeSlider.values = listOf(0f, durSec)
        rangeSlider.isEnabled = true
        updateRangeText(0L, durationMs)
    }

    private fun updateShakeInfoText() {
        val donorTxt = if (donorVideoUri != null) String.format(Locale.US, "%.1fс", donorDurationMs / 1000f) else "нет"
        val targetTxt = when {
            targetPhotoUri != null -> "Фото"
            targetVideoUri != null -> "Видео"
            else -> "нет"
        }
        tvShakeInfo.text = "Донор: $donorTxt  |  Цель: $targetTxt"
    }

    private fun startRotoProcessing() {
        val uri = rotoVideoUri ?: run {
            Toast.makeText(this, "Сначала выберите видео", Toast.LENGTH_SHORT).show()
            return
        }
        if (!contourView.hasContour()) {
            Toast.makeText(this, "Обведите объект на кадре", Toast.LENGTH_SHORT).show()
            return
        }

        val vals = rangeSlider.values
        val startMs = (vals[0] * 1000f).toLong()
        val endMs = (vals[1] * 1000f).toLong()
        if (endMs - startMs < 200L) {
            Toast.makeText(this, "Выберите отрезок длиннее 0.2 сек", Toast.LENGTH_SHORT).show()
            return
        }

        val contour = contourView.getBitmapContourPoints()
        val chosenColor = chromaColors[spinnerColor.selectedItemPosition].second

        setUiBusy(true)
        progressBar.progress = 0
        tvStatus.text = "Обработка..."

        lifecycleScope.launch {
            try {
                currentPreviewFile?.delete()
                val outFile = VideoProcessor.processVideoSegment(
                    context = this@MainActivity,
                    videoUri = uri,
                    contourPoints = contour,
                    chromaColor = chosenColor,
                    startMs = startMs,
                    endMs = endMs
                ) { current, total ->
                    runOnUiThread {
                        progressBar.max = total
                        progressBar.progress = current
                        tvStatus.text = "Кадр $current / $total"
                    }
                }
                currentPreviewFile = outFile
                enterPreviewMode(outFile)
            } catch (e: Exception) {
                tvStatus.text = "Ошибка: ${e.message}"
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setUiBusy(false)
            }
        }
    }

    private fun startShakeProcessing() {
        val donorUri = donorVideoUri ?: run {
            Toast.makeText(this, "Выберите видео-донор с качкой", Toast.LENGTH_SHORT).show()
            return
        }
        if (targetPhotoUri == null && targetVideoUri == null) {
            Toast.makeText(this, "Выберите фото или видео (цель)", Toast.LENGTH_SHORT).show()
            return
        }

        val vals = rangeSlider.values
        val startMs = (vals[0] * 1000f).toLong()
        val endMs = (vals[1] * 1000f).toLong()
        if (endMs - startMs < 200L) {
            Toast.makeText(this, "Отрезок донора должен быть длиннее 0.2 сек", Toast.LENGTH_SHORT).show()
            return
        }

        val strength = sliderStrength.value
        val zoom = sliderZoom.value

        setUiBusy(true)
        progressBar.progress = 0
        tvStatus.text = "Анализ движения донора..."

        lifecycleScope.launch {
            try {
                currentPreviewFile?.delete()
                val outFile = VideoProcessor.processShakeTransfer(
                    context = this@MainActivity,
                    donorVideoUri = donorUri,
                    donorStartMs = startMs,
                    donorEndMs = endMs,
                    targetPhotoUri = targetPhotoUri,
                    targetVideoUri = targetVideoUri,
                    strength = strength,
                    zoomScale = zoom
                ) { phase, current, total ->
                    runOnUiThread {
                        progressBar.max = total
                        progressBar.progress = current
                        tvStatus.text = "$phase: $current / $total"
                    }
                }
                currentPreviewFile = outFile
                enterPreviewMode(outFile)
            } catch (e: Exception) {
                tvStatus.text = "Ошибка: ${e.message}"
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setUiBusy(false)
            }
        }
    }

    private fun enterPreviewMode(videoFile: File) {
        contourView.visibility = View.GONE
        targetImageView.visibility = View.GONE
        timelineLayout.visibility = View.GONE
        rotoControlsLayout.visibility = View.GONE
        shakeControlsLayout.visibility = View.GONE
        modeSwitchLayoutVisibility(false)

        videoPreview.visibility = View.VISIBLE
        previewControlsLayout.visibility = View.VISIBLE

        if (mediaController == null) {
            mediaController = MediaController(this).apply {
                setAnchorView(previewContainer)
            }
        }
        videoPreview.setMediaController(mediaController)
        videoPreview.setVideoURI(Uri.fromFile(videoFile))
        videoPreview.setOnPreparedListener { mp ->
            mp.isLooping = true
            videoPreview.start()
        }
        tvStatus.text = "Проверьте результат. Можно вернуться к правке или сохранить"
    }

    private fun exitPreviewMode() {
        if (videoPreview.isPlaying) {
            videoPreview.stopPlayback()
        }
        videoPreview.visibility = View.GONE
        previewControlsLayout.visibility = View.GONE
        timelineLayout.visibility = View.VISIBLE
        modeSwitchLayoutVisibility(true)

        if (currentMode == AppMode.ROTO) {
            contourView.visibility = View.VISIBLE
            targetImageView.visibility = View.GONE
            rotoControlsLayout.visibility = View.VISIBLE
            shakeControlsLayout.visibility = View.GONE
        } else {
            contourView.visibility = View.GONE
            targetImageView.visibility = View.VISIBLE
            rotoControlsLayout.visibility = View.GONE
            shakeControlsLayout.visibility = View.VISIBLE
        }
    }

    private fun modeSwitchLayoutVisibility(visible: Boolean) {
        findViewById<View>(R.id.modeSwitchLayout).visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateRangeText(startMs: Long, endMs: Long) {
        val dur = ((endMs - startMs).coerceAtLeast(0L)) / 1000f
        tvTimeRange.text = String.format(
            Locale.US,
            "%s — %s (%.1fс)",
            formatMs(startMs),
            formatMs(endMs),
            dur
        )
    }

    private fun formatMs(ms: Long): String {
        val totalTenths = (ms / 100L).toInt()
        val tenths = totalTenths % 10
        val totalSec = totalTenths / 10
        val sec = totalSec % 60
        val min = totalSec / 60
        return String.format(Locale.US, "%02d:%02d.%d", min, sec, tenths)
    }

    private fun setUiBusy(busy: Boolean) {
        btnModeRoto.isEnabled = !busy
        btnModeShake.isEnabled = !busy
        btnPickVideo.isEnabled = !busy
        btnProcessRoto.isEnabled = !busy
        btnPickDonor.isEnabled = !busy
        btnPickTargetPhoto.isEnabled = !busy
        btnPickTargetVideo.isEnabled = !busy
        btnProcessShake.isEnabled = !busy
        sliderStrength.isEnabled = !busy
        sliderZoom.isEnabled = !busy
        contourView.isEnabled = !busy
        spinnerColor.isEnabled = !busy
        rangeSlider.isEnabled = !busy && (
            (currentMode == AppMode.ROTO && rotoDurationMs > 0L) ||
                (currentMode == AppMode.SHAKE && donorDurationMs > 0L)
            )
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPreviewFile?.delete()
    }
}
