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
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var previewContainer: FrameLayout
    private lateinit var contourView: ContourOverlayView
    private lateinit var videoPreview: VideoView
    private lateinit var progressBar: ProgressBar

    private lateinit var editControlsLayout: LinearLayout
    private lateinit var tvTimeRange: TextView
    private lateinit var rangeSlider: RangeSlider
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private lateinit var btnDeletePoint: Button
    private lateinit var btnDeleteContour: Button
    private lateinit var spinnerColor: Spinner
    private lateinit var btnPickVideo: Button
    private lateinit var btnProcess: Button

    private lateinit var previewControlsLayout: LinearLayout
    private lateinit var btnBackToEdit: Button
    private lateinit var btnReplay: Button
    private lateinit var btnSaveGallery: Button

    private var selectedVideoUri: Uri? = null
    private var videoDurationMs: Long = 0L
    private var lastLoadedStartSec: Float = 0f
    private var currentPreviewFile: File? = null
    private var mediaController: MediaController? = null

    private val chromaColors = listOf(
        "Зелёный (#00FF00)" to Color.GREEN,
        "Синий (#0000FF)" to Color.BLUE,
        "Пурпурный (#FF00FF)" to Color.MAGENTA,
        "Чёрный (#000000)" to Color.BLACK
    )

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedVideoUri = uri
                loadVideoForEditing(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        previewContainer = findViewById(R.id.previewContainer)
        contourView = findViewById(R.id.contourView)
        videoPreview = findViewById(R.id.videoPreview)
        progressBar = findViewById(R.id.progressBar)

        editControlsLayout = findViewById(R.id.editControlsLayout)
        tvTimeRange = findViewById(R.id.tvTimeRange)
        rangeSlider = findViewById(R.id.rangeSlider)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnDeletePoint = findViewById(R.id.btnDeletePoint)
        btnDeleteContour = findViewById(R.id.btnDeleteContour)
        spinnerColor = findViewById(R.id.spinnerColor)
        btnPickVideo = findViewById(R.id.btnPickVideo)
        btnProcess = findViewById(R.id.btnProcess)

        previewControlsLayout = findViewById(R.id.previewControlsLayout)
        btnBackToEdit = findViewById(R.id.btnBackToEdit)
        btnReplay = findViewById(R.id.btnReplay)
        btnSaveGallery = findViewById(R.id.btnSaveGallery)

        if (!OpenCVLoader.initLocal()) {
            tvStatus.text = "Ошибка инициализации OpenCV!"
            btnPickVideo.isEnabled = false
            btnProcess.isEnabled = false
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
        setupButtons()
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
                val startMs = (vals[0] * 1000f).toLong()
                val endMs = (vals[1] * 1000f).toLong()
                updateRangeText(startMs, endMs)
            }
        }

        rangeSlider.addOnSliderTouchListener(object : RangeSlider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: RangeSlider) {}

            override fun onStopTrackingTouch(slider: RangeSlider) {
                val uri = selectedVideoUri ?: return
                val vals = slider.values
                if (vals.size < 2) return

                val startSec = vals[0]
                if (abs(startSec - lastLoadedStartSec) > 0.05f) {
                    lastLoadedStartSec = startSec
                    val timeMs = (startSec * 1000f).toLong()
                    lifecycleScope.launch {
                        val bmp = VideoProcessor.loadFrameAtTime(this@MainActivity, uri, timeMs, exact = true)
                        if (bmp != null) {
                            contourView.setFrameBitmap(bmp, keepContour = contourView.hasContour())
                        }
                    }
                }
            }
        })
    }

    private fun setupButtons() {
        btnPickVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        btnUndo.setOnClickListener { contourView.undo() }
        btnRedo.setOnClickListener { contourView.redo() }
        btnDeletePoint.setOnClickListener { contourView.deleteSelectedPoint() }
        btnDeleteContour.setOnClickListener { contourView.deleteContour() }

        btnProcess.setOnClickListener {
            startProcessing()
        }

        btnBackToEdit.setOnClickListener {
            exitPreviewMode()
        }

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

    private fun loadVideoForEditing(uri: Uri) {
        exitPreviewMode()
        tvStatus.text = "Загрузка видео..."
        rangeSlider.isEnabled = false

        lifecycleScope.launch {
            val meta = VideoProcessor.getVideoMetadata(this@MainActivity, uri)
            if (meta.firstFrame != null && meta.durationMs > 0L) {
                videoDurationMs = meta.durationMs
                val durSec = (meta.durationMs / 1000f).coerceAtLeast(0.5f)
                lastLoadedStartSec = 0f

                rangeSlider.valueFrom = 0f
                rangeSlider.valueTo = durSec
                rangeSlider.values = listOf(0f, durSec)
                rangeSlider.isEnabled = true
                updateRangeText(0L, meta.durationMs)

                contourView.setFrameBitmap(meta.firstFrame, keepContour = false)
                tvStatus.text = "Обведите объект, поправьте точки и нажмите Старт"
            } else {
                tvStatus.text = "Не удалось открыть видео"
            }
        }
    }

    private fun startProcessing() {
        val uri = selectedVideoUri
        if (uri == null) {
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

    private fun enterPreviewMode(videoFile: File) {
        contourView.visibility = View.GONE
        editControlsLayout.visibility = View.GONE
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
        contourView.visibility = View.VISIBLE
        editControlsLayout.visibility = View.VISIBLE
        if (selectedVideoUri != null) {
            tvStatus.text = "Двигайте точки, кликайте по линии для детализации"
        }
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
        btnPickVideo.isEnabled = !busy
        btnProcess.isEnabled = !busy
        rangeSlider.isEnabled = !busy && videoDurationMs > 0L
        contourView.isEnabled = !busy
        spinnerColor.isEnabled = !busy
        btnUndo.isEnabled = !busy && contourView.hasContour() // пересчитается точнее при первом действии
        btnRedo.isEnabled = !busy
        btnDeletePoint.isEnabled = !busy
        btnDeleteContour.isEnabled = !busy && contourView.hasContour()
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPreviewFile?.delete()
    }
}
