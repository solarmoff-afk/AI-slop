package com.rotokey.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private enum class AppMode { ROTO, SHAKE, EFFECTS }
    private var currentMode = AppMode.ROTO

    private lateinit var btnModeRoto: Button
    private lateinit var btnModeShake: Button
    private lateinit var btnModeEffects: Button
    private lateinit var tvStatus: TextView
    private lateinit var previewContainer: FrameLayout
    private lateinit var contourView: ContourOverlayView
    private lateinit var targetImageView: ImageView
    private lateinit var effectStageView: EffectStageView
    private lateinit var videoPreview: VideoView
    private lateinit var progressBar: ProgressBar

    private lateinit var timelineLayout: LinearLayout
    private lateinit var tvTimelineLabel: TextView
    private lateinit var tvTimeRange: TextView
    private lateinit var rangeSlider: RangeSlider

    private lateinit var rotoControlsLayout: LinearLayout
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private lateinit var btnDeletePoint: Button
    private lateinit var btnDeleteContour: Button
    private lateinit var spinnerColor: Spinner
    private lateinit var btnPickVideo: Button
    private lateinit var btnProcessRoto: Button

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

    private lateinit var effectsControlsLayout: LinearLayout
    private lateinit var btnPickEffectsVideo: Button
    private lateinit var switchLockCamera: SwitchCompat
    private lateinit var btnAddEffect: Button
    private lateinit var effectsListContainer: LinearLayout
    private lateinit var btnLiveEffectsPreview: Button
    private lateinit var btnRenderEffects: Button

    private lateinit var previewControlsLayout: LinearLayout
    private lateinit var btnBackToEdit: Button
    private lateinit var btnReplay: Button
    private lateinit var btnSaveGallery: Button

    private var rotoVideoUri: Uri? = null
    private var rotoDurationMs: Long = 0L
    private var lastRotoStartSec: Float = 0f

    private var donorVideoUri: Uri? = null
    private var donorDurationMs: Long = 0L
    private var lastDonorStartSec: Float = 0f
    private var targetPhotoUri: Uri? = null
    private var targetVideoUri: Uri? = null

    private var effectsVideoUri: Uri? = null
    private var effectsDurationMs: Long = 0L
    private var effectsFps: Int = 30
    private var lastEffectsStartSec: Float = 0f
    private val effectsList = mutableListOf<ShaderEffectLayer>()
    private var selectedEffectId: Long? = null
    private var nextEffectId = 1L

    private var cachedHomographyKey: String? = null
    private var cachedHomographies: List<FloatArray>? = null

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

    private val pickEffectsVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                effectsVideoUri = uri
                cachedHomographyKey = null
                cachedHomographies = null
                loadEffectsVideo(uri)
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
            btnPickEffectsVideo.isEnabled = false
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
        setupEffectsCallbacks()
        setupButtons()
        switchMode(AppMode.ROTO)
    }

    private fun bindViews() {
        btnModeRoto = findViewById(R.id.btnModeRoto)
        btnModeShake = findViewById(R.id.btnModeShake)
        btnModeEffects = findViewById(R.id.btnModeEffects)
        tvStatus = findViewById(R.id.tvStatus)
        previewContainer = findViewById(R.id.previewContainer)
        contourView = findViewById(R.id.contourView)
        targetImageView = findViewById(R.id.targetImageView)
        effectStageView = findViewById(R.id.effectStageView)
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

        effectsControlsLayout = findViewById(R.id.effectsControlsLayout)
        btnPickEffectsVideo = findViewById(R.id.btnPickEffectsVideo)
        switchLockCamera = findViewById(R.id.switchLockCamera)
        btnAddEffect = findViewById(R.id.btnAddEffect)
        effectsListContainer = findViewById(R.id.effectsListContainer)
        btnLiveEffectsPreview = findViewById(R.id.btnLiveEffectsPreview)
        btnRenderEffects = findViewById(R.id.btnRenderEffects)

        previewControlsLayout = findViewById(R.id.previewControlsLayout)
        btnBackToEdit = findViewById(R.id.btnBackToEdit)
        btnReplay = findViewById(R.id.btnReplay)
        btnSaveGallery = findViewById(R.id.btnSaveGallery)
    }

    private fun switchMode(mode: AppMode) {
        currentMode = mode
        effectStageView.stopLivePreview()
        btnLiveEffectsPreview.text = "Предпросмотр"
        exitPreviewMode()

        btnModeRoto.alpha = if (mode == AppMode.ROTO) 1.0f else 0.5f
        btnModeShake.alpha = if (mode == AppMode.SHAKE) 1.0f else 0.5f
        btnModeEffects.alpha = if (mode == AppMode.EFFECTS) 1.0f else 0.5f

        rotoControlsLayout.visibility = if (mode == AppMode.ROTO) View.VISIBLE else View.GONE
        shakeControlsLayout.visibility = if (mode == AppMode.SHAKE) View.VISIBLE else View.GONE
        effectsControlsLayout.visibility = if (mode == AppMode.EFFECTS) View.VISIBLE else View.GONE

        contourView.visibility = if (mode == AppMode.ROTO) View.VISIBLE else View.GONE
        targetImageView.visibility = if (mode == AppMode.SHAKE) View.VISIBLE else View.GONE
        effectStageView.visibility = if (mode == AppMode.EFFECTS) View.VISIBLE else View.GONE

        when (mode) {
            AppMode.ROTO -> {
                tvTimelineLabel.text = "Отрезок: "
                if (rotoDurationMs > 0L) {
                    configureSliderForDuration(rotoDurationMs)
                    tvStatus.text = "Двигайте точки, кликайте по линии для детализации"
                } else {
                    rangeSlider.isEnabled = false
                    updateRangeText(0L, 0L)
                    tvStatus.text = "Выберите видео и обведите объект пальцем"
                }
            }
            AppMode.SHAKE -> {
                tvTimelineLabel.text = "Донор: "
                if (donorDurationMs > 0L) {
                    configureSliderForDuration(donorDurationMs)
                } else {
                    rangeSlider.isEnabled = false
                    updateRangeText(0L, 0L)
                }
                updateShakeInfoText()
                tvStatus.text = "1) Видео-донор с рук  2) Выберите фото или видео"
            }
            AppMode.EFFECTS -> {
                tvTimelineLabel.text = "Отрезок: "
                if (effectsDurationMs > 0L) {
                    configureSliderForDuration(effectsDurationMs)
                    tvStatus.text = "Добавьте эффекты (+), двигайте/вращайте 2 пальцами"
                } else {
                    rangeSlider.isEnabled = false
                    updateRangeText(0L, 0L)
                    tvStatus.text = "Выберите видео и нажмите + чтобы добавить шейдер"
                }
                refreshEffectsListUi()
            }
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

    private fun setupEffectsCallbacks() {
        effectStageView.onShaderError = { msg ->
            Toast.makeText(this, "Ошибка шейдера: $msg", Toast.LENGTH_SHORT).show()
        }
        effectStageView.onEffectTransformChanged = {
            effectStageView.setEffects(effectsList, selectedEffectId)
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
            override fun onStartTrackingTouch(slider: RangeSlider) {
                if (currentMode == AppMode.EFFECTS && effectStageView.isPlaying()) {
                    effectStageView.stopLivePreview()
                    btnLiveEffectsPreview.text = "Предпросмотр"
                }
            }

            override fun onStopTrackingTouch(slider: RangeSlider) {
                val vals = slider.values
                if (vals.size < 2) return
                val startSec = vals[0]

                when (currentMode) {
                    AppMode.ROTO -> {
                        val uri = rotoVideoUri ?: return
                        if (abs(startSec - lastRotoStartSec) > 0.05f) {
                            lastRotoStartSec = startSec
                            val timeMs = (startSec * 1000f).toLong()
                            lifecycleScope.launch {
                                val bmp = VideoProcessor.loadFrameAtTime(this@MainActivity, uri, timeMs, exact = true)
                                if (bmp != null) contourView.setFrameBitmap(bmp, keepContour = contourView.hasContour())
                            }
                        }
                    }
                    AppMode.SHAKE -> lastDonorStartSec = startSec
                    AppMode.EFFECTS -> {
                        val uri = effectsVideoUri ?: return
                        if (abs(startSec - lastEffectsStartSec) > 0.05f) {
                            lastEffectsStartSec = startSec
                            val timeMs = (startSec * 1000f).toLong()
                            lifecycleScope.launch {
                                val bmp = VideoProcessor.loadFrameAtTime(this@MainActivity, uri, timeMs, exact = true)
                                if (bmp != null) {
                                    effectStageView.setStaticFrame(bmp)
                                    effectStageView.setEffects(effectsList, selectedEffectId)
                                }
                            }
                        }
                    }
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
        btnModeEffects.setOnClickListener { switchMode(AppMode.EFFECTS) }

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

        btnPickEffectsVideo.setOnClickListener { pickEffectsVideoLauncher.launch("video/*") }
        btnAddEffect.setOnClickListener { showAddEffectDialog() }
        btnLiveEffectsPreview.setOnClickListener { toggleLiveEffectsPreview() }
        btnRenderEffects.setOnClickListener { startRenderEffects() }

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

    private fun showAddEffectDialog() {
        val presets = PresetStorage.loadPresets(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 8)
        }

        val btnPreset = Button(this).apply { text = "Загрузить из пресетов (${presets.size})" }
        val etName = EditText(this).apply {
            hint = "Имя эффекта"
            setText("Эффект ${effectsList.size + 1}")
        }
        val tvHint = TextView(this).apply {
            text = "Юниформы: uTexture, uResolution, uTime, uFrame, uPos, uSize, uRotation, uInvHomography"
            textSize = 11f
            setPadding(0, 8, 0, 6)
        }
        val etCode = EditText(this).apply {
            hint = "GLSL Fragment Shader"
            setText(PresetStorage.DEFAULT_ORB_SHADER)
            typeface = Typeface.MONOSPACE
            textSize = 11f
            minLines = 8
            maxLines = 14
            gravity = Gravity.TOP or Gravity.START
            isHorizontalScrollBarEnabled = true
        }

        btnPreset.setOnClickListener {
            val names = presets.map { it.name }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Выберите пресет")
                .setItems(names) { _, which ->
                    etName.setText(presets[which].name)
                    etCode.setText(presets[which].code)
                }
                .show()
        }

        container.addView(btnPreset)
        container.addView(etName)
        container.addView(tvHint)
        container.addView(etCode)

        AlertDialog.Builder(this)
            .setTitle("Добавить эффект")
            .setView(container)
            .setPositiveButton("Добавить") { _, _ ->
                val name = etName.text.toString().trim().ifEmpty { "Эффект ${effectsList.size + 1}" }
                val code = etCode.text.toString()
                val layer = ShaderEffectLayer(
                    id = nextEffectId++,
                    name = name,
                    fragmentCode = code
                )
                effectsList.add(layer)
                selectedEffectId = layer.id
                refreshEffectsListUi()
                effectStageView.setEffects(effectsList, selectedEffectId)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRenameDialog(layer: ShaderEffectLayer) {
        val et = EditText(this).apply { setText(layer.name) }
        AlertDialog.Builder(this)
            .setTitle("Переименовать эффект")
            .setView(et)
            .setPositiveButton("ОК") { _, _ ->
                val newName = et.text.toString().trim()
                if (newName.isNotEmpty()) {
                    layer.name = newName
                    refreshEffectsListUi()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditShaderDialog(layer: ShaderEffectLayer) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 8)
        }
        val tvHint = TextView(this).apply {
            text = "uTexture, uResolution, uTime, uFrame, uPos, uSize, uRotation, uInvHomography"
            textSize = 11f
            setPadding(0, 0, 0, 6)
        }
        val etCode = EditText(this).apply {
            setText(layer.fragmentCode)
            typeface = Typeface.MONOSPACE
            textSize = 11f
            minLines = 10
            maxLines = 16
            gravity = Gravity.TOP or Gravity.START
        }
        container.addView(tvHint)
        container.addView(etCode)

        AlertDialog.Builder(this)
            .setTitle("Шейдер: ${layer.name}")
            .setView(container)
            .setPositiveButton("Применить") { _, _ ->
                layer.fragmentCode = etCode.text.toString()
                effectStageView.setEffects(effectsList, selectedEffectId)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun refreshEffectsListUi() {
        effectsListContainer.removeAllViews()
        if (effectsList.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Список эффектов пуст. Нажмите +"
                setTextColor(Color.GRAY)
                textSize = 12f
                setPadding(8, 12, 8, 12)
            }
            effectsListContainer.addView(empty)
            return
        }

        val dp = resources.displayMetrics.density
        for (layer in effectsList) {
            val isSelected = layer.id == selectedEffectId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(if (isSelected) Color.parseColor("#2E3B4E") else Color.TRANSPARENT)
                setPadding((6 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                setOnClickListener {
                    selectedEffectId = layer.id
                    refreshEffectsListUi()
                    effectStageView.setEffects(effectsList, selectedEffectId)
                }
            }

            val tvName = TextView(this).apply {
                text = if (isSelected) "● ${layer.name}" else layer.name
                setTextColor(if (isSelected) Color.parseColor("#FFD54F") else Color.WHITE)
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            fun makeIconBtn(resId: Int, desc: String, onClick: () -> Unit): ImageButton =
                ImageButton(this).apply {
                    setImageResource(resId)
                    contentDescription = desc
                    setBackgroundColor(Color.TRANSPARENT)
                    val size = (32 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    setOnClickListener { onClick() }
                }

            val btnRename = makeIconBtn(R.drawable.ic_fx_edit, "Имя") { showRenameDialog(layer) }
            val btnCode = makeIconBtn(R.drawable.ic_fx_code, "Код") { showEditShaderDialog(layer) }
            val btnPreset = makeIconBtn(R.drawable.ic_fx_preset, "Пресет") {
                PresetStorage.savePreset(this, layer.name, layer.fragmentCode)
                Toast.makeText(this, "Сохранено в пресеты: ${layer.name}", Toast.LENGTH_SHORT).show()
            }
            val btnDelete = makeIconBtn(R.drawable.ic_fx_delete, "Удалить") {
                effectsList.removeAll { it.id == layer.id }
                if (selectedEffectId == layer.id) {
                    selectedEffectId = effectsList.firstOrNull()?.id
                }
                refreshEffectsListUi()
                effectStageView.setEffects(effectsList, selectedEffectId)
            }

            row.addView(tvName)
            row.addView(btnRename)
            row.addView(btnCode)
            row.addView(btnPreset)
            row.addView(btnDelete)
            effectsListContainer.addView(row)
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

    private fun loadEffectsVideo(uri: Uri) {
        exitPreviewMode()
        tvStatus.text = "Загрузка видео для эффектов..."
        rangeSlider.isEnabled = false
        lifecycleScope.launch {
            val meta = VideoProcessor.getVideoMetadata(this@MainActivity, uri)
            if (meta.firstFrame != null && meta.durationMs > 0L) {
                effectsDurationMs = meta.durationMs
                effectsFps = meta.fps
                lastEffectsStartSec = 0f
                configureSliderForDuration(meta.durationMs)
                effectStageView.setStaticFrame(meta.firstFrame)
                effectStageView.setEffects(effectsList, selectedEffectId)
                tvStatus.text = "Нажмите + чтобы добавить эффект. Двигайте и вращайте 2 пальцами"
            } else {
                tvStatus.text = "Не удалось открыть видео"
            }
        }
    }

    private fun loadTargetPhotoPreview(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bmp = VideoProcessor.loadPhotoBitmap(this@MainActivity, uri)
                targetImageView.setImageBitmap(bmp)
                updateShakeInfoText()
                tvStatus.text = "Фото выбрано. Нажмите Перенести качку"
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка фото: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTargetVideoPreview(uri: Uri) {
        lifecycleScope.launch {
            val frame = VideoProcessor.loadFrameAtTime(this@MainActivity, uri, 0L, exact = false)
            if (frame != null) targetImageView.setImageBitmap(frame)
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

    private fun toggleLiveEffectsPreview() {
        if (effectStageView.isPlaying()) {
            effectStageView.stopLivePreview()
            btnLiveEffectsPreview.text = "Предпросмотр"
            val uri = effectsVideoUri ?: return
            val startMs = (rangeSlider.values[0] * 1000f).toLong()
            lifecycleScope.launch {
                val bmp = VideoProcessor.loadFrameAtTime(this@MainActivity, uri, startMs, exact = true)
                if (bmp != null) {
                    effectStageView.setStaticFrame(bmp)
                    effectStageView.setEffects(effectsList, selectedEffectId)
                }
            }
            return
        }

        val uri = effectsVideoUri ?: run {
            Toast.makeText(this, "Сначала выберите видео", Toast.LENGTH_SHORT).show()
            return
        }

        val vals = rangeSlider.values
        val startMs = (vals[0] * 1000f).toLong()
        val endMs = (vals[1] * 1000f).toLong()
        if (endMs - startMs < 200L) {
            Toast.makeText(this, "Выберите отрезок длиннее 0.2 сек", Toast.LENGTH_SHORT).show()
            return
        }

        val lockCam = switchLockCamera.isChecked
        val cacheKey = "${uri}_${startMs}_${endMs}"

        if (lockCam && (cachedHomographyKey != cacheKey || cachedHomographies == null)) {
            setUiBusy(true)
            progressBar.progress = 0
            tvStatus.text = "Быстрый анализ камеры для предпросмотра..."
            lifecycleScope.launch {
                try {
                    val hList = VideoProcessor.analyzeSegmentHomographies(this@MainActivity, uri, startMs, endMs) { c, t ->
                        runOnUiThread {
                            progressBar.max = t
                            progressBar.progress = c
                            tvStatus.text = "3D-трекинг: $c / $t"
                        }
                    }
                    cachedHomographyKey = cacheKey
                    cachedHomographies = hList
                    btnLiveEffectsPreview.text = "Стоп"
                    tvStatus.text = "Живой предпросмотр"
                    effectStageView.setEffects(effectsList, selectedEffectId)
                    effectStageView.startLivePreview(uri, startMs, endMs, effectsFps, hList)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    setUiBusy(false)
                }
            }
        } else {
            val hList = if (lockCam) (cachedHomographies ?: emptyList()) else emptyList()
            btnLiveEffectsPreview.text = "Стоп"
            tvStatus.text = "Живой предпросмотр"
            effectStageView.setEffects(effectsList, selectedEffectId)
            effectStageView.startLivePreview(uri, startMs, endMs, effectsFps, hList)
        }
    }

    private fun startRenderEffects() {
        effectStageView.stopLivePreview()
        btnLiveEffectsPreview.text = "Предпросмотр"

        val uri = effectsVideoUri ?: run {
            Toast.makeText(this, "Сначала выберите видео", Toast.LENGTH_SHORT).show()
            return
        }
        val vals = rangeSlider.values
        val startMs = (vals[0] * 1000f).toLong()
        val endMs = (vals[1] * 1000f).toLong()
        if (endMs - startMs < 200L) {
            Toast.makeText(this, "Выберите отрезок длиннее 0.2 сек", Toast.LENGTH_SHORT).show()
            return
        }

        val lockCam = switchLockCamera.isChecked
        val cacheKey = "${uri}_${startMs}_${endMs}"
        val precomputed = if (lockCam && cachedHomographyKey == cacheKey) cachedHomographies else null

        setUiBusy(true)
        progressBar.progress = 0
        tvStatus.text = "Рендер эффектов..."

        lifecycleScope.launch {
            try {
                currentPreviewFile?.delete()
                val outFile = VideoProcessor.renderEffectsVideo(
                    context = this@MainActivity,
                    videoUri = uri,
                    startMs = startMs,
                    endMs = endMs,
                    effects = effectsList.map { it.copy() },
                    lockToCamera = lockCam,
                    precomputedHomographies = precomputed
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
        effectStageView.stopLivePreview()
        contourView.visibility = View.GONE
        targetImageView.visibility = View.GONE
        effectStageView.visibility = View.GONE
        timelineLayout.visibility = View.GONE
        rotoControlsLayout.visibility = View.GONE
        shakeControlsLayout.visibility = View.GONE
        effectsControlsLayout.visibility = View.GONE
        findViewById<View>(R.id.modeSwitchLayout).visibility = View.GONE

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
        if (videoPreview.isPlaying) videoPreview.stopPlayback()
        videoPreview.visibility = View.GONE
        previewControlsLayout.visibility = View.GONE
        timelineLayout.visibility = View.VISIBLE
        findViewById<View>(R.id.modeSwitchLayout).visibility = View.VISIBLE

        contourView.visibility = if (currentMode == AppMode.ROTO) View.VISIBLE else View.GONE
        targetImageView.visibility = if (currentMode == AppMode.SHAKE) View.VISIBLE else View.GONE
        effectStageView.visibility = if (currentMode == AppMode.EFFECTS) View.VISIBLE else View.GONE

        rotoControlsLayout.visibility = if (currentMode == AppMode.ROTO) View.VISIBLE else View.GONE
        shakeControlsLayout.visibility = if (currentMode == AppMode.SHAKE) View.VISIBLE else View.GONE
        effectsControlsLayout.visibility = if (currentMode == AppMode.EFFECTS) View.VISIBLE else View.GONE
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
        btnModeEffects.isEnabled = !busy
        btnPickVideo.isEnabled = !busy
        btnProcessRoto.isEnabled = !busy
        btnPickDonor.isEnabled = !busy
        btnPickTargetPhoto.isEnabled = !busy
        btnPickTargetVideo.isEnabled = !busy
        btnProcessShake.isEnabled = !busy
        sliderStrength.isEnabled = !busy
        sliderZoom.isEnabled = !busy
        btnPickEffectsVideo.isEnabled = !busy
        switchLockCamera.isEnabled = !busy
        btnAddEffect.isEnabled = !busy
        btnLiveEffectsPreview.isEnabled = !busy
        btnRenderEffects.isEnabled = !busy
        contourView.isEnabled = !busy
        spinnerColor.isEnabled = !busy
        rangeSlider.isEnabled = !busy && (
            (currentMode == AppMode.ROTO && rotoDurationMs > 0L) ||
                (currentMode == AppMode.SHAKE && donorDurationMs > 0L) ||
                (currentMode == AppMode.EFFECTS && effectsDurationMs > 0L)
            )
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        effectStageView.onPauseView()
    }

    override fun onResume() {
        super.onResume()
        effectStageView.onResumeView()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPreviewFile?.delete()
    }
}
