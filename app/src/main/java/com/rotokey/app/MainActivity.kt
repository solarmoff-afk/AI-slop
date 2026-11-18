package com.rotokey.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var contourView: ContourOverlayView
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerColor: Spinner
    private lateinit var btnPickVideo: Button
    private lateinit var btnClear: Button
    private lateinit var btnProcess: Button

    private var selectedVideoUri: Uri? = null

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
                loadPreview(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        contourView = findViewById(R.id.contourView)
        progressBar = findViewById(R.id.progressBar)
        spinnerColor = findViewById(R.id.spinnerColor)
        btnPickVideo = findViewById(R.id.btnPickVideo)
        btnClear = findViewById(R.id.btnClear)
        btnProcess = findViewById(R.id.btnProcess)

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

        btnPickVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        btnClear.setOnClickListener {
            contourView.clearContour()
        }

        btnProcess.setOnClickListener {
            startProcessing()
        }
    }

    private fun loadPreview(uri: Uri) {
        tvStatus.text = "Загрузка первого кадра..."
        lifecycleScope.launch {
            val bmp = VideoProcessor.loadFirstFrame(this@MainActivity, uri)
            if (bmp != null) {
                contourView.setFrameBitmap(bmp)
                tvStatus.text = "Обведите объект пальцем и нажмите Старт"
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

        val contour = contourView.getBitmapContourPoints()
        val chosenColor = chromaColors[spinnerColor.selectedItemPosition].second

        setUiBusy(true)
        progressBar.progress = 0
        tvStatus.text = "Обработка..."

        lifecycleScope.launch {
            try {
                VideoProcessor.processVideo(
                    context = this@MainActivity,
                    videoUri = uri,
                    contourPoints = contour,
                    chromaColor = chosenColor
                ) { current, total ->
                    runOnUiThread {
                        progressBar.max = total
                        progressBar.progress = current
                        tvStatus.text = "Кадр $current / $total"
                    }
                }
                tvStatus.text = "Готово! Сохранено в Movies/RotoKey"
                Toast.makeText(this@MainActivity, "Видео сохранено в Галерею (Movies/RotoKey)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                tvStatus.text = "Ошибка: ${e.message}"
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setUiBusy(false)
            }
        }
    }

    private fun setUiBusy(busy: Boolean) {
        btnPickVideo.isEnabled = !busy
        btnClear.isEnabled = !busy
        btnProcess.isEnabled = !busy
        contourView.isEnabled = !busy
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
    }
}
