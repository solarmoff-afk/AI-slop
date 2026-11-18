from pathlib import Path

BASE_DIR = Path(".")

FILES = {
    ".github/workflows/build.yml": r"""name: Build Android APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.7'

      - name: Build Debug APK
        run: gradle assembleDebug --no-daemon

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: RotoKey-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
""",

    "settings.gradle.kts": r"""pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RotoKey"
include(":app")
""",

    "build.gradle.kts": r"""plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
""",

    "gradle.properties": r"""org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""",

    "app/proguard-rules.pro": r"""# Keep OpenCV classes
-keep class org.opencv.** { *; }
""",

    "app/build.gradle.kts": r"""plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rotokey.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rotokey.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.opencv:opencv:4.9.0")
}
""",

    "app/src/main/AndroidManifest.xml": r"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.RotoKey">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
""",

    "app/src/main/res/drawable/ic_launcher.xml": r"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#00C853"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,24c12,0 22,10 22,22c0,10 -6,18 -14,21l8,17h-11l-7,-15h-4v15h-10v-60h16zM48,34v20h6c6,0 11,-4 11,-10c0,-6 -5,-10 -11,-10h-6z" />
</vector>
""",

    "app/src/main/res/values/strings.xml": r"""<resources>
    <string name="app_name">RotoKey</string>
</resources>
""",

    "app/src/main/res/values/themes.xml": r"""<resources>
    <style name="Theme.RotoKey" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
""",

    "app/src/main/res/layout/activity_main.xml": r"""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#121212"
    android:orientation="vertical"
    android:padding="12dp">

    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingBottom="8dp"
        android:text="1. Выберите видео и обведите объект пальцем"
        android:textColor="#FFFFFF"
        android:textSize="14sp" />

    <com.rotokey.app.ContourOverlayView
        android:id="@+id/contourView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:background="#1E1E1E" />

    <ProgressBar
        android:id="@+id/progressBar"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:visibility="gone" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Фон: "
            android:textColor="#CCCCCC"
            android:textSize="14sp" />

        <Spinner
            android:id="@+id/spinnerColor"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:backgroundTint="#FFFFFF" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:orientation="horizontal">

        <Button
            android:id="@+id/btnPickVideo"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginEnd="6dp"
            android:layout_weight="1"
            android:text="Видео" />

        <Button
            android:id="@+id/btnClear"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginEnd="6dp"
            android:layout_weight="1"
            android:text="Сброс" />

        <Button
            android:id="@+id/btnProcess"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Старт" />
    </LinearLayout>

</LinearLayout>
""",

    "app/src/main/java/com/rotokey/app/ContourOverlayView.kt": r"""package com.rotokey.app

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
""",

    "app/src/main/java/com/rotokey/app/OpenCVTracker.kt": r"""package com.rotokey.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.max

class OpenCVTracker(private val chromaColorInt: Int) {

    private var smallW = 0
    private var smallH = 0
    private var scaleToSmall = 1.0

    private val prevGray = Mat()
    private val prevMask = Mat()
    private var prevFeatures = MatOfPoint2f()

    private val bgdModel = Mat()
    private val fgdModel = Mat()

    private val kernelSmall = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
    private val kernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))
    private val kernelErode = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))

    private val chromaScalarBgr = Scalar(
        Color.blue(chromaColorInt).toDouble(),
        Color.green(chromaColorInt).toDouble(),
        Color.red(chromaColorInt).toDouble()
    )

    fun initFirstFrame(bitmap: Bitmap, contourPoints: List<PointF>): Bitmap {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)

        val fullBgr = Mat()
        Imgproc.cvtColor(rgba, fullBgr, Imgproc.COLOR_RGBA2BGR)

        val maxSide = max(fullBgr.cols(), fullBgr.rows())
        scaleToSmall = if (maxSide > 320) 320.0 / maxSide else 1.0
        smallW = max(16, (fullBgr.cols() * scaleToSmall).toInt())
        smallH = max(16, (fullBgr.rows() * scaleToSmall).toInt())

        val smallBgr = Mat()
        Imgproc.resize(fullBgr, smallBgr, Size(smallW.toDouble(), smallH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

        val smallGray = Mat()
        Imgproc.cvtColor(smallBgr, smallGray, Imgproc.COLOR_BGR2GRAY)

        val polyMask = Mat.zeros(smallH, smallW, CvType.CV_8UC1)
        val scaledPts = contourPoints.map {
            Point(
                (it.x * scaleToSmall).coerceIn(0.0, (smallW - 1).toDouble()),
                (it.y * scaleToSmall).coerceIn(0.0, (smallH - 1).toDouble())
            )
        }
        val matPts = MatOfPoint(*scaledPts.toTypedArray())
        Imgproc.fillPoly(polyMask, listOf(matPts), Scalar(255.0))

        val gcMask = buildGrabCutMask(polyMask)
        val binaryMask = runGrabCutSafe(smallBgr, gcMask)

        binaryMask.copyTo(prevMask)
        smallGray.copyTo(prevGray)
        extractFeatures(smallGray, binaryMask)

        val resultBitmap = applyChromaKey(fullBgr, binaryMask, bitmap.width, bitmap.height)

        rgba.release()
        fullBgr.release()
        smallBgr.release()
        smallGray.release()
        polyMask.release()
        matPts.release()
        gcMask.release()
        binaryMask.release()

        return resultBitmap
    }

    fun processNextFrame(bitmap: Bitmap): Bitmap {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)

        val fullBgr = Mat()
        Imgproc.cvtColor(rgba, fullBgr, Imgproc.COLOR_RGBA2BGR)

        val smallBgr = Mat()
        Imgproc.resize(fullBgr, smallBgr, Size(smallW.toDouble(), smallH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

        val smallGray = Mat()
        Imgproc.cvtColor(smallBgr, smallGray, Imgproc.COLOR_BGR2GRAY)

        val (dx, dy) = estimateShift(smallGray)

        val shiftedMask = Mat()
        val transMat = Mat(2, 3, CvType.CV_64FC1)
        transMat.put(0, 0, 1.0, 0.0, dx, 0.0, 1.0, dy)
        Imgproc.warpAffine(prevMask, shiftedMask, transMat, prevMask.size())

        val gcMask = buildGrabCutMask(shiftedMask)
        val refinedMask = runGrabCutSafe(smallBgr, gcMask)

        if (Core.countNonZero(refinedMask) > 20) {
            refinedMask.copyTo(prevMask)
        } else {
            shiftedMask.copyTo(prevMask)
        }

        smallGray.copyTo(prevGray)
        extractFeatures(smallGray, prevMask)

        val resultBitmap = applyChromaKey(fullBgr, prevMask, bitmap.width, bitmap.height)

        rgba.release()
        fullBgr.release()
        smallBgr.release()
        smallGray.release()
        shiftedMask.release()
        transMat.release()
        gcMask.release()
        refinedMask.release()

        return resultBitmap
    }

    private fun estimateShift(currGray: Mat): Pair<Double, Double> {
        if (prevFeatures.empty() || prevFeatures.rows() < 4) {
            return 0.0 to 0.0
        }

        val nextFeatures = MatOfPoint2f()
        val status = MatOfByte()
        val err = MatOfFloat()

        Video.calcOpticalFlowPyrLK(prevGray, currGray, prevFeatures, nextFeatures, status, err)

        val pArr = prevFeatures.toArray()
        val nArr = nextFeatures.toArray()
        val sArr = status.toArray()

        val dxs = ArrayList<Double>()
        val dys = ArrayList<Double>()
        for (i in sArr.indices) {
            if (sArr[i].toInt() == 1 && i < pArr.size && i < nArr.size) {
                dxs.add(nArr[i].x - pArr[i].x)
                dys.add(nArr[i].y - pArr[i].y)
            }
        }

        nextFeatures.release()
        status.release()
        err.release()

        if (dxs.size < 3) return 0.0 to 0.0

        dxs.sort()
        dys.sort()
        val mid = dxs.size / 2
        return dxs[mid] to dys[mid]
    }

    private fun extractFeatures(gray: Mat, mask: Mat) {
        val corners = MatOfPoint()
        if (Core.countNonZero(mask) > 20) {
            Imgproc.goodFeaturesToTrack(gray, corners, 60, 0.01, 5.0, mask)
        }
        prevFeatures.release()
        prevFeatures = if (!corners.empty()) {
            MatOfPoint2f(*corners.toArray())
        } else {
            MatOfPoint2f()
        }
        corners.release()
    }

    private fun buildGrabCutMask(objectMask: Mat): Mat {
        val gcMask = Mat(objectMask.size(), CvType.CV_8UC1, Scalar(Imgproc.GC_BGD.toDouble()))

        val dilated = Mat()
        Imgproc.dilate(objectMask, dilated, kernelDilate)
        gcMask.setTo(Scalar(Imgproc.GC_PR_BGD.toDouble()), dilated)

        gcMask.setTo(Scalar(Imgproc.GC_PR_FGD.toDouble()), objectMask)

        val eroded = Mat()
        Imgproc.erode(objectMask, eroded, kernelErode)
        if (Core.countNonZero(eroded) > 10) {
            gcMask.setTo(Scalar(Imgproc.GC_FGD.toDouble()), eroded)
        }

        dilated.release()
        eroded.release()
        return gcMask
    }

    private fun runGrabCutSafe(smallBgr: Mat, gcMask: Mat): Mat {
        val fgCheck1 = Mat()
        val fgCheck2 = Mat()
        val bgCheck1 = Mat()
        val bgCheck2 = Mat()

        Core.inRange(gcMask, Scalar(Imgproc.GC_FGD.toDouble()), Scalar(Imgproc.GC_FGD.toDouble()), fgCheck1)
        Core.inRange(gcMask, Scalar(Imgproc.GC_PR_FGD.toDouble()), Scalar(Imgproc.GC_PR_FGD.toDouble()), fgCheck2)
        Core.inRange(gcMask, Scalar(Imgproc.GC_BGD.toDouble()), Scalar(Imgproc.GC_BGD.toDouble()), bgCheck1)
        Core.inRange(gcMask, Scalar(Imgproc.GC_PR_BGD.toDouble()), Scalar(Imgproc.GC_PR_BGD.toDouble()), bgCheck2)

        val fgCount = Core.countNonZero(fgCheck1) + Core.countNonZero(fgCheck2)
        val bgCount = Core.countNonZero(bgCheck1) + Core.countNonZero(bgCheck2)

        fgCheck1.release(); fgCheck2.release(); bgCheck1.release(); bgCheck2.release()

        if (fgCount < 10 || bgCount < 10) {
            return Mat.zeros(smallBgr.size(), CvType.CV_8UC1)
        }

        val rect = Rect(0, 0, smallBgr.cols() - 1, smallBgr.rows() - 1)
        try {
            Imgproc.grabCut(smallBgr, gcMask, rect, bgdModel, fgdModel, 2, Imgproc.GC_INIT_WITH_MASK)
        } catch (_: Exception) {
            return Mat.zeros(smallBgr.size(), CvType.CV_8UC1)
        }

        val fgd = Mat()
        val prFgd = Mat()
        val combined = Mat()
        Core.inRange(gcMask, Scalar(Imgproc.GC_FGD.toDouble()), Scalar(Imgproc.GC_FGD.toDouble()), fgd)
        Core.inRange(gcMask, Scalar(Imgproc.GC_PR_FGD.toDouble()), Scalar(Imgproc.GC_PR_FGD.toDouble()), prFgd)
        Core.bitwise_or(fgd, prFgd, combined)

        Imgproc.morphologyEx(combined, combined, Imgproc.MORPH_CLOSE, kernelSmall)
        Imgproc.morphologyEx(combined, combined, Imgproc.MORPH_OPEN, kernelSmall)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        val contourInput = combined.clone()
        Imgproc.findContours(contourInput, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val cleanMask = Mat.zeros(combined.size(), CvType.CV_8UC1)
        if (contours.isNotEmpty()) {
            var maxIdx = 0
            var maxArea = 0.0
            for (i in contours.indices) {
                val area = Imgproc.contourArea(contours[i])
                if (area > maxArea) {
                    maxArea = area
                    maxIdx = i
                }
            }
            if (maxArea > 15.0) {
                Imgproc.drawContours(cleanMask, contours, maxIdx, Scalar(255.0), -1)
            }
        }

        fgd.release(); prFgd.release(); combined.release()
        hierarchy.release(); contourInput.release()
        contours.forEach { it.release() }

        return cleanMask
    }

    private fun applyChromaKey(fullBgr: Mat, smallMask: Mat, outW: Int, outH: Int): Bitmap {
        val upMask = Mat()
        Imgproc.resize(smallMask, upMask, Size(outW.toDouble(), outH.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        Imgproc.GaussianBlur(upMask, upMask, Size(7.0, 7.0), 0.0)
        Imgproc.threshold(upMask, upMask, 127.0, 255.0, Imgproc.THRESH_BINARY)

        val outBgr = Mat(fullBgr.size(), fullBgr.type(), chromaScalarBgr)
        fullBgr.copyTo(outBgr, upMask)

        val outRgba = Mat()
        Imgproc.cvtColor(outBgr, outRgba, Imgproc.COLOR_BGR2RGBA)

        val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outRgba, outBitmap)

        upMask.release()
        outBgr.release()
        outRgba.release()

        return outBitmap
    }

    fun release() {
        prevGray.release()
        prevMask.release()
        prevFeatures.release()
        bgdModel.release()
        fgdModel.release()
        kernelSmall.release()
        kernelDilate.release()
        kernelErode.release()
    }
}
""",

    "app/src/main/java/com/rotokey/app/VideoProcessor.kt": r"""package com.rotokey.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.media.*
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.math.min
import kotlin.math.roundToInt

object VideoProcessor {

    suspend fun loadFirstFrame(context: Context, videoUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            retriever.getFrameAtIndex(0) ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun processVideo(
        context: Context,
        videoUri: Uri,
        contourPoints: List<PointF>,
        chromaColor: Int,
        onProgress: (current: Int, total: Int) -> Unit
    ): Uri = withContext(Dispatchers.IO) {

        val videoInfo = readVideoInfo(context, videoUri)
        val firstBmp = loadFirstFrame(context, videoUri)
            ?: throw IllegalStateException("Не удалось прочитать первый кадр видео")

        val scaleOut = if (maxOf(firstBmp.width, firstBmp.height) > 1280) {
            1280.0 / maxOf(firstBmp.width, firstBmp.height)
        } else 1.0
        val outW = ((firstBmp.width * scaleOut).roundToInt() / 2) * 2
        val outH = ((firstBmp.height * scaleOut).roundToInt() / 2) * 2
        firstBmp.recycle()

        val tempFile = File(context.cacheDir, "rotokey_temp_${System.currentTimeMillis()}.mp4")
        if (tempFile.exists()) tempFile.delete()

        val tracker = OpenCVTracker(chromaColor)
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)

        val fps = videoInfo.fps.coerceIn(10, 60)
        val frameDurationUs = 1_000_000L / fps

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, outW * outH * 4)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        var encodedFrames = 0L

        fun drainEncoder(endOfStream: Boolean) {
            if (endOfStream) encoder.signalEndOfInputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (outIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outIndex)
                    if (encodedData != null && bufferInfo.size > 0 &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && muxerStarted
                    ) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        bufferInfo.presentationTimeUs = encodedFrames * frameDurationUs
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        encodedFrames++
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }

        fun drawBitmapToSurface(surface: Surface, bmp: Bitmap) {
            val canvas = surface.lockCanvas(null)
            try {
                canvas.drawBitmap(bmp, null, Rect(0, 0, outW, outH), null)
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        }

        try {
            val totalFrames = videoInfo.timestampsUs.size.coerceAtLeast(1)
            var frameCounter = 0

            val useBatch = videoInfo.reportedFrameCount > 0
            if (useBatch) {
                val batchSize = 4
                var idx = 0
                val total = videoInfo.reportedFrameCount
                while (idx < total) {
                    val count = min(batchSize, total - idx)
                    val frames = try {
                        retriever.getFramesAtIndex(idx, count)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (frames.isEmpty()) break

                    for (rawBmp in frames) {
                        val processed = if (frameCounter == 0) {
                            tracker.initFirstFrame(rawBmp, contourPoints)
                        } else {
                            tracker.processNextFrame(rawBmp)
                        }
                        rawBmp.recycle()

                        drawBitmapToSurface(inputSurface, processed)
                        processed.recycle()
                        drainEncoder(false)

                        frameCounter++
                        onProgress(frameCounter, total)
                    }
                    idx += frames.size
                }
            }

            if (frameCounter == 0) {
                for (i in videoInfo.timestampsUs.indices) {
                    val rawBmp = retriever.getFrameAtTime(
                        videoInfo.timestampsUs[i],
                        MediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: continue

                    val processed = if (frameCounter == 0) {
                        tracker.initFirstFrame(rawBmp, contourPoints)
                    } else {
                        tracker.processNextFrame(rawBmp)
                    }
                    rawBmp.recycle()

                    drawBitmapToSurface(inputSurface, processed)
                    processed.recycle()
                    drainEncoder(false)

                    frameCounter++
                    onProgress(frameCounter, totalFrames)
                }
            }

            drainEncoder(true)
        } finally {
            tracker.release()
            retriever.release()
            inputSurface.release()
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
            if (muxerStarted) {
                try { muxer.stop() } catch (_: Exception) {}
            }
            muxer.release()
        }

        val savedUri = saveToGallery(context, tempFile)
        tempFile.delete()
        savedUri
    }

    private data class VideoInfo(
        val fps: Int,
        val reportedFrameCount: Int,
        val timestampsUs: List<Long>
    )

    private fun readVideoInfo(context: Context, uri: Uri): VideoInfo {
        val retriever = MediaMetadataRetriever()
        var reportedCount = 0
        try {
            retriever.setDataSource(context, uri)
            reportedCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
        } finally {
            retriever.release()
        }

        val extractor = MediaExtractor()
        val timestamps = ArrayList<Long>()
        var fps = 30
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i)
                    if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = fmt.getInteger(MediaFormat.KEY_FRAME_RATE)
                    }
                    while (true) {
                        val t = extractor.sampleTime
                        if (t < 0) break
                        timestamps.add(t)
                        extractor.advance()
                    }
                    break
                }
            }
        } finally {
            extractor.release()
        }

        timestamps.sort()
        return VideoInfo(fps, reportedCount, timestamps)
    }

    private fun saveToGallery(context: Context, file: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "RotoKey_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/RotoKey")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Не удалось создать запись в MediaStore")

        resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(file).use { input ->
                input.copyTo(out)
            }
        }

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
""",

    "app/src/main/java/com/rotokey/app/MainActivity.kt": r"""package com.rotokey.app

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
""",
}


def main():
    for rel_path, content in FILES.items():
        full_path = BASE_DIR / rel_path
        full_path.parent.mkdir(parents=True, exist_ok=True)
        full_path.write_text(content, encoding="utf-8", newline="\n")
        print(f"Создан: {full_path}")

    print("\nГотово! Проект создан в папке:", BASE_DIR.resolve())
    print("Дальше:")
    print("1. Залейте содержимое папки RotoKey в новый репозиторий на GitHub (включая скрытую папку .github!).")
    print("2. Откройте вкладку Actions на GitHub и дождитесь зелёной галочки.")
    print("3. Скачайте готовый APK из раздела Artifacts -> RotoKey-debug-apk.")


if __name__ == "__main__":
    main()
