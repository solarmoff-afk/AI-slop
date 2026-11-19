from pathlib import Path

BASE_DIR = Path(".")

FILES = {
    "app/src/main/res/drawable/ic_fx_edit.xml": r'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="20dp"
    android:height="20dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z" />
</vector>
''',

    "app/src/main/res/drawable/ic_fx_code.xml": r'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="20dp"
    android:height="20dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z" />
</vector>
''',

    "app/src/main/res/drawable/ic_fx_preset.xml": r'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="20dp"
    android:height="20dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFD54F"
        android:pathData="M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z" />
</vector>
''',

    "app/src/main/res/drawable/ic_fx_delete.xml": r'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="20dp"
    android:height="20dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF5252"
        android:pathData="M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z" />
</vector>
''',

    "app/src/main/java/com/rotokey/app/EffectModels.kt": r'''package com.rotokey.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ShaderEffectLayer(
    val id: Long,
    var name: String,
    var fragmentCode: String,
    var posX: Float = 0.5f,
    var posY: Float = 0.5f,
    var scale: Float = 0.45f,
    var rotationRad: Float = 0f
)

data class ShaderPreset(
    val name: String,
    val code: String
)

object PresetStorage {

    private const val PREFS_NAME = "rotokey_shader_presets"
    private const val KEY_PRESETS = "presets_json"
    private const val KEY_SEEDED = "seeded_defaults"

    val DEFAULT_ORB_SHADER: String = listOf(
        "precision mediump float;",
        "uniform sampler2D uTexture;",
        "uniform vec2 uResolution;",
        "uniform float uTime;",
        "uniform float uFrame;",
        "uniform vec2 uPos;",
        "uniform vec2 uSize;",
        "uniform float uRotation;",
        "uniform mat3 uInvHomography;",
        "varying vec2 vTexCoord;",
        "",
        "vec2 getEffectUv() {",
        "    vec3 p = uInvHomography * vec3(vTexCoord, 1.0);",
        "    vec2 uv = p.xy / max(abs(p.z), 0.0001);",
        "    vec2 d = uv - uPos;",
        "    d.x *= uResolution.x / uResolution.y;",
        "    float c = cos(-uRotation), s = sin(-uRotation);",
        "    d = mat2(c, -s, s, c) * d;",
        "    return d / max(uSize, vec2(0.01));",
        "}",
        "",
        "void main() {",
        "    vec4 video = texture2D(uTexture, vTexCoord);",
        "    vec2 e = getEffectUv();",
        "    float dist = length(e);",
        "    float pulse = 0.75 + 0.25 * sin(uTime * 6.0);",
        "    float glow = exp(-dist * 5.0) * pulse;",
        "    vec3 col = vec3(0.2, 0.85, 1.0) * glow;",
        "    gl_FragColor = vec4(clamp(video.rgb + col, 0.0, 1.0), video.a);",
        "}"
    ).joinToString("\n")

    private val DEFAULT_RING_SHADER: String = listOf(
        "precision mediump float;",
        "uniform sampler2D uTexture;",
        "uniform vec2 uResolution;",
        "uniform float uTime;",
        "uniform float uFrame;",
        "uniform vec2 uPos;",
        "uniform vec2 uSize;",
        "uniform float uRotation;",
        "uniform mat3 uInvHomography;",
        "varying vec2 vTexCoord;",
        "",
        "vec2 getEffectUv() {",
        "    vec3 p = uInvHomography * vec3(vTexCoord, 1.0);",
        "    vec2 uv = p.xy / max(abs(p.z), 0.0001);",
        "    vec2 d = uv - uPos;",
        "    d.x *= uResolution.x / uResolution.y;",
        "    float c = cos(-uRotation), s = sin(-uRotation);",
        "    d = mat2(c, -s, s, c) * d;",
        "    return d / max(uSize, vec2(0.01));",
        "}",
        "",
        "void main() {",
        "    vec4 video = texture2D(uTexture, vTexCoord);",
        "    vec2 e = getEffectUv();",
        "    float r = length(e);",
        "    float a = atan(e.y, e.x) + uTime * 3.0;",
        "    float ring = smoothstep(0.06, 0.0, abs(r - 0.38));",
        "    float arc = 0.5 + 0.5 * sin(a * 3.0);",
        "    vec3 neon = mix(vec3(1.0, 0.2, 0.7), vec3(0.2, 1.0, 0.8), arc);",
        "    gl_FragColor = vec4(clamp(video.rgb + neon * ring * 1.4, 0.0, 1.0), video.a);",
        "}"
    ).joinToString("\n")

    private val DEFAULT_WAVE_SHADER: String = listOf(
        "precision mediump float;",
        "uniform sampler2D uTexture;",
        "uniform vec2 uResolution;",
        "uniform float uTime;",
        "uniform float uFrame;",
        "uniform vec2 uPos;",
        "uniform vec2 uSize;",
        "uniform float uRotation;",
        "uniform mat3 uInvHomography;",
        "varying vec2 vTexCoord;",
        "",
        "vec2 getEffectUv() {",
        "    vec3 p = uInvHomography * vec3(vTexCoord, 1.0);",
        "    vec2 uv = p.xy / max(abs(p.z), 0.0001);",
        "    vec2 d = uv - uPos;",
        "    d.x *= uResolution.x / uResolution.y;",
        "    float c = cos(-uRotation), s = sin(-uRotation);",
        "    d = mat2(c, -s, s, c) * d;",
        "    return d / max(uSize, vec2(0.01));",
        "}",
        "",
        "void main() {",
        "    vec2 e = getEffectUv();",
        "    float dist = length(e);",
        "    float mask = smoothstep(0.55, 0.0, dist);",
        "    vec2 offset = normalize(e + vec2(0.0001)) * sin(dist * 24.0 - uTime * 10.0) * 0.02 * mask;",
        "    vec4 video = texture2D(uTexture, clamp(vTexCoord + offset, 0.0, 1.0));",
        "    gl_FragColor = video;",
        "}"
    ).joinToString("\n")

    fun loadPresets(context: Context): List<ShaderPreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            val defaults = listOf(
                ShaderPreset("Светящийся шар", DEFAULT_ORB_SHADER),
                ShaderPreset("Неоновое кольцо", DEFAULT_RING_SHADER),
                ShaderPreset("Волна искажения", DEFAULT_WAVE_SHADER)
            )
            saveAll(prefs, defaults)
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            return defaults
        }

        val raw = prefs.getString(KEY_PRESETS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<ShaderPreset>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            out.add(ShaderPreset(obj.getString("name"), obj.getString("code")))
        }
        return out
    }

    fun savePreset(context: Context, name: String, code: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = loadPresets(context).toMutableList()
        val idx = current.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = ShaderPreset(name, code)
        } else {
            current.add(0, ShaderPreset(name, code))
        }
        saveAll(prefs, current)
    }

    private fun saveAll(prefs: android.content.SharedPreferences, list: List<ShaderPreset>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("code", p.code)
            })
        }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
    }
}
''',

    "app/src/main/java/com/rotokey/app/OpenCVHomographyTracker.kt": r'''package com.rotokey.app

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.abs
import kotlin.math.max

class OpenCVHomographyTracker {

    private var smallW = 0
    private var smallH = 0
    private val prevGray = Mat()
    private var prevFeatures = MatOfPoint2f()

    private var totalH = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, 1.0, 0.0,
        0.0, 0.0, 1.0
    )

    fun initFirstFrame(bitmap: Bitmap): FloatArray {
        val gray = toSmallGray(bitmap)
        gray.copyTo(prevGray)
        extractFeatures(gray)
        gray.release()
        totalH = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        return identityColMajor()
    }

    fun stepFrame(bitmap: Bitmap): FloatArray {
        val currGray = toSmallGray(bitmap)

        if (!prevFeatures.empty() && prevFeatures.rows() >= 8) {
            val nextFeatures = MatOfPoint2f()
            val status = MatOfByte()
            val err = MatOfFloat()

            Video.calcOpticalFlowPyrLK(prevGray, currGray, prevFeatures, nextFeatures, status, err)

            val pArr = prevFeatures.toArray()
            val nArr = nextFeatures.toArray()
            val sArr = status.toArray()

            val goodPrev = ArrayList<Point>()
            val goodNext = ArrayList<Point>()
            for (i in sArr.indices) {
                if (sArr[i].toInt() == 1 && i < pArr.size && i < nArr.size) {
                    goodPrev.add(pArr[i])
                    goodNext.add(nArr[i])
                }
            }

            if (goodPrev.size >= 8) {
                val pMat = MatOfPoint2f(*goodPrev.toTypedArray())
                val nMat = MatOfPoint2f(*goodNext.toTypedArray())
                val hPx = Calib3d.findHomography(pMat, nMat, Calib3d.RANSAC, 3.0)

                if (!hPx.empty() && hPx.rows() == 3 && hPx.cols() == 3) {
                    val d = DoubleArray(9)
                    hPx.get(0, 0, d)
                    val hNorm = pxToNormalized(d, smallW.toDouble(), smallH.toDouble())
                    if (isValidStepHomography(hNorm)) {
                        totalH = normalizeH(mul3x3(hNorm, totalH))
                    }
                }

                pMat.release()
                nMat.release()
                hPx.release()
            }

            nextFeatures.release()
            status.release()
            err.release()
        }

        currGray.copyTo(prevGray)
        extractFeatures(currGray)
        currGray.release()

        val inv = invert3x3(totalH) ?: return identityColMajor()
        return toColMajorFloat(inv)
    }

    private fun toSmallGray(bitmap: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)

        val maxSide = max(bgr.cols(), bgr.rows())
        val scale = if (maxSide > 320) 320.0 / maxSide else 1.0
        smallW = max(16, (bgr.cols() * scale).toInt())
        smallH = max(16, (bgr.rows() * scale).toInt())

        val smallBgr = Mat()
        Imgproc.resize(bgr, smallBgr, Size(smallW.toDouble(), smallH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

        val smallGray = Mat()
        Imgproc.cvtColor(smallBgr, smallGray, Imgproc.COLOR_BGR2GRAY)

        rgba.release()
        bgr.release()
        smallBgr.release()
        return smallGray
    }

    private fun extractFeatures(gray: Mat) {
        val corners = MatOfPoint()
        Imgproc.goodFeaturesToTrack(gray, corners, 150, 0.01, 14.0)
        prevFeatures.release()
        prevFeatures = if (!corners.empty()) {
            MatOfPoint2f(*corners.toArray())
        } else {
            MatOfPoint2f()
        }
        corners.release()
    }

    private fun pxToNormalized(h: DoubleArray, w: Double, hPx: Double): DoubleArray {
        val s = doubleArrayOf(
            1.0 / w, 0.0, 0.0,
            0.0, 1.0 / hPx, 0.0,
            0.0, 0.0, 1.0
        )
        val sInv = doubleArrayOf(
            w, 0.0, 0.0,
            0.0, hPx, 0.0,
            0.0, 0.0, 1.0
        )
        return normalizeH(mul3x3(mul3x3(s, h), sInv))
    }

    private fun isValidStepHomography(h: DoubleArray): Boolean {
        if (abs(h[8]) < 1e-6) return false
        val det2 = h[0] * h[4] - h[1] * h[3]
        if (det2 < 0.5 || det2 > 2.0) return false
        val z = h[6] * 0.5 + h[7] * 0.5 + h[8]
        if (abs(z) < 0.2) return false
        val cx = (h[0] * 0.5 + h[1] * 0.5 + h[2]) / z
        val cy = (h[3] * 0.5 + h[4] * 0.5 + h[5]) / z
        return abs(cx - 0.5) < 0.25 && abs(cy - 0.5) < 0.25
    }

    private fun normalizeH(h: DoubleArray): DoubleArray {
        val scale = if (abs(h[8]) > 1e-8) h[8] else 1.0
        return DoubleArray(9) { h[it] / scale }
    }

    private fun mul3x3(a: DoubleArray, b: DoubleArray): DoubleArray {
        val c = DoubleArray(9)
        for (r in 0..2) {
            for (col in 0..2) {
                c[r * 3 + col] =
                    a[r * 3 + 0] * b[0 * 3 + col] +
                    a[r * 3 + 1] * b[1 * 3 + col] +
                    a[r * 3 + 2] * b[2 * 3 + col]
            }
        }
        return c
    }

    private fun invert3x3(m: DoubleArray): DoubleArray? {
        val a00 = m[0]; val a01 = m[1]; val a02 = m[2]
        val a10 = m[3]; val a11 = m[4]; val a12 = m[5]
        val a20 = m[6]; val a21 = m[7]; val a22 = m[8]

        val b01 = a22 * a11 - a12 * a21
        val b11 = -a22 * a10 + a12 * a20
        val b21 = a21 * a10 - a11 * a20

        val det = a00 * b01 + a01 * b11 + a02 * b21
        if (abs(det) < 1e-9) return null
        val invDet = 1.0 / det

        return doubleArrayOf(
            b01 * invDet,
            (-a22 * a01 + a02 * a21) * invDet,
            (a12 * a01 - a02 * a11) * invDet,
            b11 * invDet,
            (a22 * a00 - a02 * a20) * invDet,
            (-a12 * a00 + a02 * a10) * invDet,
            b21 * invDet,
            (-a21 * a00 + a01 * a20) * invDet,
            (a11 * a00 - a01 * a10) * invDet
        )
    }

    private fun toColMajorFloat(m: DoubleArray): FloatArray = floatArrayOf(
        m[0].toFloat(), m[3].toFloat(), m[6].toFloat(),
        m[1].toFloat(), m[4].toFloat(), m[7].toFloat(),
        m[2].toFloat(), m[5].toFloat(), m[8].toFloat()
    )

    fun release() {
        prevGray.release()
        prevFeatures.release()
    }

    companion object {
        fun identityColMajor(): FloatArray = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )
    }
}
''',

    "app/src/main/java/com/rotokey/app/ShaderRenderer.kt": r'''package com.rotokey.app

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ShaderEngine {

    companion object {
        private const val VERTEX_SHADER =
            "attribute vec2 aPos;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    vTexCoord = aTexCoord;\n" +
            "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "}\n"

        private const val OES_VERTEX_SHADER =
            "attribute vec2 aPos;\n" +
            "attribute vec2 aTexCoord;\n" +
            "uniform mat4 uStMatrix;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    vTexCoord = (uStMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;\n" +
            "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "}\n"

        private const val OES_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uOesTex;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(uOesTex, vTexCoord);\n" +
            "}\n"

        private const val COPY_FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "uniform sampler2D uTexture;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
            "}\n"
    }

    private val fboQuadBuf: FloatBuffer = createFloatBuffer(
        floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        )
    )

    private val screenQuadBuf: FloatBuffer = createFloatBuffer(
        floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        )
    )

    private val oesToFboQuadBuf: FloatBuffer = createFloatBuffer(
        floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        )
    )

    private var copyProgram = 0
    private var oesProgram = 0

    private var fboWidth = 0
    private var fboHeight = 0
    private val fboIds = IntArray(3)
    private val fboTexIds = IntArray(3)
    private var bitmapTexId = 0
    var oesTexId = 0
        private set

    private data class CachedProgram(val codeHash: Int, val programId: Int, val error: String?)
    private val shaderCache = HashMap<Long, CachedProgram>()

    fun initGl() {
        copyProgram = compileProgram(VERTEX_SHADER, COPY_FRAGMENT_SHADER).first
        oesProgram = compileProgram(OES_VERTEX_SHADER, OES_FRAGMENT_SHADER).first

        val tex = IntArray(2)
        GLES20.glGenTextures(2, tex, 0)
        bitmapTexId = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        oesTexId = tex[1]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun uploadBitmap(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTexId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    fun renderFromBitmap(
        width: Int,
        height: Int,
        vpLeft: Int,
        vpBottom: Int,
        vpWidth: Int,
        vpHeight: Int,
        effects: List<ShaderEffectLayer>,
        timeSec: Float,
        frameIdx: Float,
        invHomography: FloatArray
    ): String? {
        ensureFbos(width, height)
        return runEffectChain(
            sourceTexId = bitmapTexId,
            texW = width,
            texH = height,
            vpLeft = vpLeft,
            vpBottom = vpBottom,
            vpWidth = vpWidth,
            vpHeight = vpHeight,
            effects = effects,
            timeSec = timeSec,
            frameIdx = frameIdx,
            invHomography = invHomography
        )
    }

    fun renderFromOes(
        stMatrix: FloatArray,
        width: Int,
        height: Int,
        vpLeft: Int,
        vpBottom: Int,
        vpWidth: Int,
        vpHeight: Int,
        effects: List<ShaderEffectLayer>,
        timeSec: Float,
        frameIdx: Float,
        invHomography: FloatArray
    ): String? {
        ensureFbos(width, height)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[2])
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(oesProgram)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(oesProgram, "uOesTex"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(oesProgram, "uStMatrix"), 1, false, stMatrix, 0)
        drawQuad(oesProgram, oesToFboQuadBuf)

        return runEffectChain(
            sourceTexId = fboTexIds[2],
            texW = width,
            texH = height,
            vpLeft = vpLeft,
            vpBottom = vpBottom,
            vpWidth = vpWidth,
            vpHeight = vpHeight,
            effects = effects,
            timeSec = timeSec,
            frameIdx = frameIdx,
            invHomography = invHomography
        )
    }

    private fun runEffectChain(
        sourceTexId: Int,
        texW: Int,
        texH: Int,
        vpLeft: Int,
        vpBottom: Int,
        vpWidth: Int,
        vpHeight: Int,
        effects: List<ShaderEffectLayer>,
        timeSec: Float,
        frameIdx: Float,
        invHomography: FloatArray
    ): String? {
        var currentTex = sourceTexId
        var pingIdx = 0
        var firstError: String? = null

        for (fx in effects) {
            val cached = getOrCompileEffect(fx)
            if (cached.programId == 0) {
                if (firstError == null) firstError = "${fx.name}: ${cached.error}"
                continue
            }

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[pingIdx])
            GLES20.glViewport(0, 0, texW, texH)
            GLES20.glUseProgram(cached.programId)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, currentTex)

            setUniform1i(cached.programId, "uTexture", 0)
            setUniform2f(cached.programId, "uResolution", texW.toFloat(), texH.toFloat())
            setUniform1f(cached.programId, "uTime", timeSec)
            setUniform1f(cached.programId, "uFrame", frameIdx)
            setUniform2f(cached.programId, "uPos", fx.posX, fx.posY)
            setUniform2f(cached.programId, "uSize", fx.scale, fx.scale)
            setUniform1f(cached.programId, "uRotation", fx.rotationRad)

            val locH = GLES20.glGetUniformLocation(cached.programId, "uInvHomography")
            if (locH >= 0) {
                GLES20.glUniformMatrix3fv(locH, 1, false, invHomography, 0)
            }

            drawQuad(cached.programId, fboQuadBuf)
            currentTex = fboTexIds[pingIdx]
            pingIdx = 1 - pingIdx
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(vpLeft, vpBottom, vpWidth, vpHeight)
        GLES20.glUseProgram(copyProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, currentTex)
        setUniform1i(copyProgram, "uTexture", 0)
        drawQuad(copyProgram, screenQuadBuf)

        return firstError
    }

    private fun getOrCompileEffect(fx: ShaderEffectLayer): CachedProgram {
        val hash = fx.fragmentCode.hashCode()
        val old = shaderCache[fx.id]
        if (old != null && old.codeHash == hash) return old

        if (old != null && old.programId != 0) {
            GLES20.glDeleteProgram(old.programId)
        }

        val (prog, err) = compileProgram(VERTEX_SHADER, fx.fragmentCode)
        val newEntry = CachedProgram(hash, prog, err)
        shaderCache[fx.id] = newEntry
        return newEntry
    }

    private fun ensureFbos(w: Int, h: Int) {
        if (fboWidth == w && fboHeight == h && fboIds[0] != 0) return
        if (fboIds[0] != 0) {
            GLES20.glDeleteFramebuffers(3, fboIds, 0)
            GLES20.glDeleteTextures(3, fboTexIds, 0)
        }
        fboWidth = w
        fboHeight = h
        GLES20.glGenFramebuffers(3, fboIds, 0)
        GLES20.glGenTextures(3, fboTexIds, 0)

        for (i in 0 until 3) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexIds[i])
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[i])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, fboTexIds[i], 0
            )
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun drawQuad(program: Int, buf: FloatBuffer) {
        val posLoc = GLES20.glGetAttribLocation(program, "aPos")
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        buf.position(0)
        if (posLoc >= 0) {
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, buf)
        }
        buf.position(2)
        if (texLoc >= 0) {
            GLES20.glEnableVertexAttribArray(texLoc)
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, buf)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        if (posLoc >= 0) GLES20.glDisableVertexAttribArray(posLoc)
        if (texLoc >= 0) GLES20.glDisableVertexAttribArray(texLoc)
    }

    private fun compileProgram(vsSrc: String, fsSrc: String): Pair<Int, String?> {
        val vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        GLES20.glShaderSource(vs, vsSrc)
        GLES20.glCompileShader(vs)

        val fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        GLES20.glShaderSource(fs, fsSrc)
        GLES20.glCompileShader(fs)

        val fsOk = IntArray(1)
        GLES20.glGetShaderiv(fs, GLES20.GL_COMPILE_STATUS, fsOk, 0)
        if (fsOk[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(fs)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return 0 to log
        }

        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        val linkOk = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkOk, 0)
        if (linkOk[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            return 0 to log
        }
        return prog to null
    }

    private fun setUniform1i(prog: Int, name: String, v: Int) {
        val loc = GLES20.glGetUniformLocation(prog, name)
        if (loc >= 0) GLES20.glUniform1i(loc, v)
    }

    private fun setUniform1f(prog: Int, name: String, v: Float) {
        val loc = GLES20.glGetUniformLocation(prog, name)
        if (loc >= 0) GLES20.glUniform1f(loc, v)
    }

    private fun setUniform2f(prog: Int, name: String, x: Float, y: Float) {
        val loc = GLES20.glGetUniformLocation(prog, name)
        if (loc >= 0) GLES20.glUniform2f(loc, x, y)
    }

    private fun createFloatBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(arr); position(0) }

    fun release() {
        if (copyProgram != 0) GLES20.glDeleteProgram(copyProgram)
        if (oesProgram != 0) GLES20.glDeleteProgram(oesProgram)
        shaderCache.values.forEach { if (it.programId != 0) GLES20.glDeleteProgram(it.programId) }
        shaderCache.clear()
        if (fboIds[0] != 0) {
            GLES20.glDeleteFramebuffers(3, fboIds, 0)
            GLES20.glDeleteTextures(3, fboTexIds, 0)
        }
        if (bitmapTexId != 0 || oesTexId != 0) {
            GLES20.glDeleteTextures(2, intArrayOf(bitmapTexId, oesTexId), 0)
        }
    }
}
''',

    "app/src/main/java/com/rotokey/app/EffectPreviewView.kt": r'''package com.rotokey.app

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
''',

    "app/src/main/java/com/rotokey/app/VideoProcessor.kt": r'''package com.rotokey.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.PointF
import android.graphics.Rect
import android.media.*
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLExt
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object VideoProcessor {

    data class VideoMetadata(
        val durationMs: Long,
        val fps: Int,
        val firstFrame: Bitmap?
    )

    suspend fun getVideoMetadata(context: Context, videoUri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val fps = fpsStr?.toFloatOrNull()?.roundToInt() ?: 30
            val first = retriever.getFrameAtIndex(0)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            VideoMetadata(durMs, fps.coerceIn(10, 60), first)
        } catch (_: Exception) {
            VideoMetadata(0L, 30, null)
        } finally {
            retriever.release()
        }
    }

    suspend fun loadFrameAtTime(
        context: Context,
        videoUri: Uri,
        timeMs: Long,
        exact: Boolean
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            retriever.getFrameAtTime(timeMs * 1000L, option)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun loadPhotoBitmap(context: Context, photoUri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, photoUri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val maxS = max(w, h)
            if (maxS > 1280) {
                val scale = 1280.0 / maxS
                decoder.setTargetSize(max(2, (w * scale).roundToInt()), max(2, (h * scale).roundToInt()))
            }
        }
    }

    suspend fun analyzeSegmentHomographies(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        onProgress: (current: Int, total: Int) -> Unit
    ): List<FloatArray> = withContext(Dispatchers.IO) {
        val info = readVideoInfo(context, videoUri)
        val range = getSegmentIndices(info.timestampsUs, startMs, endMs)
        if (range.timestamps.isEmpty()) return@withContext emptyList()

        val result = ArrayList<FloatArray>(range.timestamps.size)
        val tracker = OpenCVHomographyTracker()
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)

        try {
            val total = range.timestamps.size
            var count = 0
            val canBatch = info.reportedFrameCount > 0 && info.reportedFrameCount == info.timestampsUs.size
            if (canBatch) {
                var idx = range.startIdx
                while (idx <= range.endIdx) {
                    val c = min(4, range.endIdx - idx + 1)
                    val frames = try { retriever.getFramesAtIndex(idx, c) } catch (_: Exception) { emptyList() }
                    if (frames.isEmpty()) break
                    for (bmp in frames) {
                        val h = if (count == 0) tracker.initFirstFrame(bmp) else tracker.stepFrame(bmp)
                        result.add(h)
                        bmp.recycle()
                        count++
                        onProgress(count, total)
                    }
                    idx += frames.size
                }
            }
            if (count == 0) {
                for (tsUs in range.timestamps) {
                    val bmp = retriever.getFrameAtTime(tsUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                    val h = if (count == 0) tracker.initFirstFrame(bmp) else tracker.stepFrame(bmp)
                    result.add(h)
                    bmp.recycle()
                    count++
                    onProgress(count, total)
                }
            }
        } finally {
            tracker.release()
            retriever.release()
        }
        result
    }

    suspend fun renderEffectsVideo(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        effects: List<ShaderEffectLayer>,
        lockToCamera: Boolean,
        precomputedHomographies: List<FloatArray>?,
        onProgress: (phase: String, current: Int, total: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {

        val videoInfo = readVideoInfo(context, videoUri)
        val range = getSegmentIndices(videoInfo.timestampsUs, startMs, endMs)
        if (range.timestamps.isEmpty()) throw IllegalStateException("В выбранном отрезке нет кадров")

        val homographies = if (lockToCamera) {
            if (!precomputedHomographies.isNullOrEmpty()) {
                precomputedHomographies
            } else {
                analyzeSegmentHomographies(context, videoUri, startMs, endMs) { cur, tot ->
                    onProgress("3D-трекинг камеры", cur, tot)
                }
            }
        } else {
            emptyList()
        }

        val firstBmp = loadFrameAtTime(context, videoUri, startMs, exact = true)
            ?: throw IllegalStateException("Не удалось прочитать первый кадр")
        val (outW, outH) = computeEvenSize(firstBmp.width, firstBmp.height)
        firstBmp.recycle()

        val fps = videoInfo.fps.coerceIn(10, 60)
        val frameDurationUs = 1_000_000L / fps
        val audioStartUs = range.timestamps.first()
        val audioEndUs = range.timestamps.last() + frameDurationUs

        val previewFile = File(context.cacheDir, "rotokey_fx_${System.currentTimeMillis()}.mp4")
        if (previewFile.exists()) previewFile.delete()

        val eglSession = EglMuxedVideoSession(
            context = context,
            outputFile = previewFile,
            outW = outW,
            outH = outH,
            fps = fps,
            audioSourceUri = videoUri,
            audioStartUs = audioStartUs,
            audioEndUs = audioEndUs
        )

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)

        try {
            eglSession.makeCurrent()
            val shaderEngine = ShaderEngine().apply { initGl() }
            val total = range.timestamps.size
            var frameCounter = 0

            fun processFrameBitmap(rawBmp: Bitmap) {
                shaderEngine.uploadBitmap(rawBmp)
                val invH = if (homographies.isNotEmpty()) {
                    homographies[frameCounter.coerceIn(0, homographies.lastIndex)]
                } else {
                    OpenCVHomographyTracker.identityColMajor()
                }
                shaderEngine.renderFromBitmap(
                    width = rawBmp.width,
                    height = rawBmp.height,
                    vpLeft = 0,
                    vpBottom = 0,
                    vpWidth = outW,
                    vpHeight = outH,
                    effects = effects,
                    timeSec = frameCounter.toFloat() / fps,
                    frameIdx = frameCounter.toFloat(),
                    invHomography = invH
                )
                eglSession.swapAndDrain(frameCounter * frameDurationUs)
                frameCounter++
                onProgress("Рендер эффектов", frameCounter, total)
            }

            val canBatch = videoInfo.reportedFrameCount > 0 &&
                videoInfo.reportedFrameCount == videoInfo.timestampsUs.size

            if (canBatch) {
                var idx = range.startIdx
                while (idx <= range.endIdx) {
                    val count = min(4, range.endIdx - idx + 1)
                    val frames = try { retriever.getFramesAtIndex(idx, count) } catch (_: Exception) { emptyList() }
                    if (frames.isEmpty()) break
                    for (bmp in frames) {
                        processFrameBitmap(bmp)
                        bmp.recycle()
                    }
                    idx += frames.size
                }
            }

            if (frameCounter == 0) {
                for (tsUs in range.timestamps) {
                    val bmp = retriever.getFrameAtTime(tsUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                    processFrameBitmap(bmp)
                    bmp.recycle()
                }
            }

            shaderEngine.release()
            eglSession.finish()
        } finally {
            retriever.release()
            eglSession.release()
        }

        previewFile
    }

    suspend fun processVideoSegment(
        context: Context,
        videoUri: Uri,
        contourPoints: List<PointF>,
        chromaColor: Int,
        startMs: Long,
        endMs: Long,
        onProgress: (current: Int, total: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {

        val videoInfo = readVideoInfo(context, videoUri)
        val segmentRange = getSegmentIndices(videoInfo.timestampsUs, startMs, endMs)
        val segmentTimestamps = segmentRange.timestamps
        if (segmentTimestamps.isEmpty()) {
            throw IllegalStateException("В выбранном отрезке нет кадров")
        }

        val fps = videoInfo.fps.coerceIn(10, 60)
        val audioStartUs = segmentTimestamps.first()
        val audioEndUs = segmentTimestamps.last() + (1_000_000L / fps)

        val firstBmp = loadFrameAtTime(context, videoUri, startMs, exact = true)
            ?: throw IllegalStateException("Не удалось прочитать начальный кадр")

        val (outW, outH) = computeEvenSize(firstBmp.width, firstBmp.height)
        firstBmp.recycle()

        val tracker = OpenCVTracker(chromaColor)
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)

        val previewFile = File(context.cacheDir, "rotokey_preview_${System.currentTimeMillis()}.mp4")
        if (previewFile.exists()) previewFile.delete()

        val session = MuxedVideoSession(
            context = context,
            outputFile = previewFile,
            outW = outW,
            outH = outH,
            fps = fps,
            audioSourceUri = videoUri,
            audioStartUs = audioStartUs,
            audioEndUs = audioEndUs
        )

        try {
            val totalSegmentFrames = segmentTimestamps.size
            var frameCounter = 0

            val canUseBatch = videoInfo.reportedFrameCount > 0 &&
                videoInfo.reportedFrameCount == videoInfo.timestampsUs.size

            if (canUseBatch) {
                val batchSize = 4
                var idx = segmentRange.startIdx
                while (idx <= segmentRange.endIdx) {
                    val count = min(batchSize, segmentRange.endIdx - idx + 1)
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

                        session.writeFrame(processed)
                        processed.recycle()

                        frameCounter++
                        onProgress(frameCounter, totalSegmentFrames)
                    }
                    idx += frames.size
                }
            }

            if (frameCounter == 0) {
                for (tsUs in segmentTimestamps) {
                    val rawBmp = retriever.getFrameAtTime(tsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: continue

                    val processed = if (frameCounter == 0) {
                        tracker.initFirstFrame(rawBmp, contourPoints)
                    } else {
                        tracker.processNextFrame(rawBmp)
                    }
                    rawBmp.recycle()

                    session.writeFrame(processed)
                    processed.recycle()

                    frameCounter++
                    onProgress(frameCounter, totalSegmentFrames)
                }
            }

            session.finish()
        } finally {
            tracker.release()
            retriever.release()
            session.release()
        }

        previewFile
    }

    suspend fun processShakeTransfer(
        context: Context,
        donorVideoUri: Uri,
        donorStartMs: Long,
        donorEndMs: Long,
        targetPhotoUri: Uri?,
        targetVideoUri: Uri?,
        strength: Float,
        zoomScale: Float,
        onProgress: (phase: String, current: Int, total: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {

        if (targetPhotoUri == null && targetVideoUri == null) {
            throw IllegalStateException("Выберите фото или видео, на которое перенести качку")
        }

        val donorInfo = readVideoInfo(context, donorVideoUri)
        val donorRange = getSegmentIndices(donorInfo.timestampsUs, donorStartMs, donorEndMs)
        if (donorRange.timestamps.isEmpty()) {
            throw IllegalStateException("В отрезке донора нет кадров")
        }

        val deltas = ArrayList<OpenCVShakeTransfer.MotionDelta>(donorRange.timestamps.size)
        val shakeAnalyzer = OpenCVShakeTransfer()
        val donorRetriever = MediaMetadataRetriever()
        donorRetriever.setDataSource(context, donorVideoUri)

        try {
            val totalDonor = donorRange.timestamps.size
            var donorCount = 0
            val canBatchDonor = donorInfo.reportedFrameCount > 0 &&
                donorInfo.reportedFrameCount == donorInfo.timestampsUs.size

            if (canBatchDonor) {
                var idx = donorRange.startIdx
                while (idx <= donorRange.endIdx) {
                    val count = min(4, donorRange.endIdx - idx + 1)
                    val frames = try {
                        donorRetriever.getFramesAtIndex(idx, count)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (frames.isEmpty()) break

                    for (bmp in frames) {
                        if (donorCount == 0) {
                            shakeAnalyzer.initDonorFrame(bmp)
                            deltas.add(OpenCVShakeTransfer.MotionDelta(0.0, 0.0, 0.0))
                        } else {
                            deltas.add(shakeAnalyzer.stepDonorFrame(bmp))
                        }
                        bmp.recycle()
                        donorCount++
                        onProgress("Анализ донора", donorCount, totalDonor)
                    }
                    idx += frames.size
                }
            }

            if (donorCount == 0) {
                for (tsUs in donorRange.timestamps) {
                    val bmp = donorRetriever.getFrameAtTime(tsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: continue
                    if (donorCount == 0) {
                        shakeAnalyzer.initDonorFrame(bmp)
                        deltas.add(OpenCVShakeTransfer.MotionDelta(0.0, 0.0, 0.0))
                    } else {
                        deltas.add(shakeAnalyzer.stepDonorFrame(bmp))
                    }
                    bmp.recycle()
                    donorCount++
                    onProgress("Анализ донора", donorCount, totalDonor)
                }
            }
        } finally {
            shakeAnalyzer.release()
            donorRetriever.release()
        }

        if (deltas.isEmpty()) {
            throw IllegalStateException("Не удалось извлечь движение с видео-донора")
        }

        val previewFile = File(context.cacheDir, "rotokey_shake_${System.currentTimeMillis()}.mp4")
        if (previewFile.exists()) previewFile.delete()

        if (targetPhotoUri != null) {
            val photoBmp = loadPhotoBitmap(context, targetPhotoUri)
            val (outW, outH) = computeEvenSize(photoBmp.width, photoBmp.height)
            val srcBgr = OpenCVShakeTransfer.bitmapToBgrMat(photoBmp)
            photoBmp.recycle()

            val fps = donorInfo.fps.coerceIn(10, 60)
            val session = MuxedVideoSession(
                context = context,
                outputFile = previewFile,
                outW = outW,
                outH = outH,
                fps = fps,
                audioSourceUri = null,
                audioStartUs = 0L,
                audioEndUs = 0L
            )

            var cumX = 0.0
            var cumY = 0.0
            var cumAngle = 0.0

            try {
                for (i in deltas.indices) {
                    val d = deltas[i]
                    cumX = cumX * 0.96 + d.dxNorm * outW * strength
                    cumY = cumY * 0.96 + d.dyNorm * outH * strength
                    cumAngle = cumAngle * 0.96 + d.dAngleDeg * strength

                    val warpedBmp = OpenCVShakeTransfer.renderWarpedFrame(
                        srcBgr = srcBgr,
                        outW = outW,
                        outH = outH,
                        offsetX = cumX,
                        offsetY = cumY,
                        angleDeg = cumAngle,
                        zoomScale = zoomScale.toDouble()
                    )
                    session.writeFrame(warpedBmp)
                    warpedBmp.recycle()
                    onProgress("Рендер фото", i + 1, deltas.size)
                }
                session.finish()
            } finally {
                srcBgr.release()
                session.release()
            }
        } else if (targetVideoUri != null) {
            val targetInfo = readVideoInfo(context, targetVideoUri)
            if (targetInfo.timestampsUs.isEmpty()) {
                throw IllegalStateException("В целевом видео нет кадров")
            }

            val firstTargetBmp = loadFrameAtTime(context, targetVideoUri, 0L, exact = true)
                ?: throw IllegalStateException("Не удалось прочитать целевое видео")
            val (outW, outH) = computeEvenSize(firstTargetBmp.width, firstTargetBmp.height)
            firstTargetBmp.recycle()

            val fps = targetInfo.fps.coerceIn(10, 60)
            val audioStartUs = targetInfo.timestampsUs.first()
            val audioEndUs = targetInfo.timestampsUs.last() + (1_000_000L / fps)

            val session = MuxedVideoSession(
                context = context,
                outputFile = previewFile,
                outW = outW,
                outH = outH,
                fps = fps,
                audioSourceUri = targetVideoUri,
                audioStartUs = audioStartUs,
                audioEndUs = audioEndUs
            )

            val targetRetriever = MediaMetadataRetriever()
            targetRetriever.setDataSource(context, targetVideoUri)

            var cumX = 0.0
            var cumY = 0.0
            var cumAngle = 0.0

            fun processTargetBitmap(rawBmp: Bitmap, frameIdx: Int) {
                val d = deltas[frameIdx % deltas.size]
                cumX = cumX * 0.96 + d.dxNorm * outW * strength
                cumY = cumY * 0.96 + d.dyNorm * outH * strength
                cumAngle = cumAngle * 0.96 + d.dAngleDeg * strength

                val srcBgr = OpenCVShakeTransfer.bitmapToBgrMat(rawBmp)
                val warpedBmp = OpenCVShakeTransfer.renderWarpedFrame(
                    srcBgr = srcBgr,
                    outW = outW,
                    outH = outH,
                    offsetX = cumX,
                    offsetY = cumY,
                    angleDeg = cumAngle,
                    zoomScale = zoomScale.toDouble()
                )
                srcBgr.release()
                session.writeFrame(warpedBmp)
                warpedBmp.recycle()
            }

            try {
                val totalTarget = targetInfo.timestampsUs.size
                var frameCounter = 0
                val canBatchTarget = targetInfo.reportedFrameCount > 0 &&
                    targetInfo.reportedFrameCount == targetInfo.timestampsUs.size

                if (canBatchTarget) {
                    var idx = 0
                    while (idx < targetInfo.reportedFrameCount) {
                        val count = min(4, targetInfo.reportedFrameCount - idx)
                        val frames = try {
                            targetRetriever.getFramesAtIndex(idx, count)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        if (frames.isEmpty()) break

                        for (rawBmp in frames) {
                            processTargetBitmap(rawBmp, frameCounter)
                            rawBmp.recycle()
                            frameCounter++
                            onProgress("Рендер видео", frameCounter, totalTarget)
                        }
                        idx += frames.size
                    }
                }

                if (frameCounter == 0) {
                    for (tsUs in targetInfo.timestampsUs) {
                        val rawBmp = targetRetriever.getFrameAtTime(tsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                            ?: continue
                        processTargetBitmap(rawBmp, frameCounter)
                        rawBmp.recycle()
                        frameCounter++
                        onProgress("Рендер видео", frameCounter, totalTarget)
                    }
                }

                session.finish()
            } finally {
                targetRetriever.release()
                session.release()
            }
        }

        previewFile
    }

    suspend fun saveToGallery(context: Context, file: File): Uri = withContext(Dispatchers.IO) {
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
        uri
    }

    private open class BaseMuxedSession(
        context: Context,
        outputFile: File,
        outW: Int,
        outH: Int,
        fps: Int,
        audioSourceUri: Uri?,
        private val audioStartUs: Long,
        private val audioEndUs: Long
    ) {
        protected val frameDurationUs = 1_000_000L / fps
        protected val encoder: MediaCodec
        protected val inputSurface: Surface
        private val muxer: MediaMuxer

        private var videoTrackIndex = -1
        private var audioTrackIndex = -1
        private var muxerStarted = false
        private var encodedFrames = 0L

        private var audioExtractor: MediaExtractor? = null
        private var audioFormat: MediaFormat? = null

        init {
            if (audioSourceUri != null) {
                val ext = MediaExtractor()
                try {
                    ext.setDataSource(context, audioSourceUri, null)
                    for (i in 0 until ext.trackCount) {
                        val fmt = ext.getTrackFormat(i)
                        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            audioFormat = fmt
                            ext.selectTrack(i)
                            audioExtractor = ext
                            break
                        }
                    }
                    if (audioExtractor == null) ext.release()
                } catch (_: Exception) {
                    ext.release()
                }
            }

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, outW * outH * 4)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }

        fun finish() {
            drainEncoder(true)
            copyAudioSamples()
        }

        protected fun drainEncoder(endOfStream: Boolean) {
            if (endOfStream) encoder.signalEndOfInputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                        audioFormat?.let { fmt ->
                            try {
                                audioTrackIndex = muxer.addTrack(fmt)
                            } catch (_: Exception) {
                                audioTrackIndex = -1
                            }
                        }
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
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        encodedFrames++
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }

        private fun copyAudioSamples() {
            val ext = audioExtractor ?: return
            if (audioTrackIndex < 0 || !muxerStarted) return

            val maxInputSize = audioFormat?.let {
                if (it.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) it.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 256 * 1024
            } ?: (256 * 1024)

            val buffer = ByteBuffer.allocate(max(256 * 1024, maxInputSize))
            val info = MediaCodec.BufferInfo()

            ext.seekTo(audioStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var lastPts = -1L

            while (true) {
                val sampleSize = ext.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTime = ext.sampleTime
                if (sampleTime > audioEndUs) break

                if (sampleTime >= audioStartUs) {
                    val pts = sampleTime - audioStartUs
                    if (pts > lastPts) {
                        buffer.position(0)
                        buffer.limit(sampleSize)
                        info.offset = 0
                        info.size = sampleSize
                        info.presentationTimeUs = pts
                        info.flags = if ((ext.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                        muxer.writeSampleData(audioTrackIndex, buffer, info)
                        lastPts = pts
                    }
                }
                ext.advance()
            }
        }

        open fun release() {
            try { audioExtractor?.release() } catch (_: Exception) {}
            try { inputSurface.release() } catch (_: Exception) {}
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer.stop() } catch (_: Exception) {}
            }
            try { muxer.release() } catch (_: Exception) {}
        }
    }

    private class MuxedVideoSession(
        context: Context,
        outputFile: File,
        private val outW: Int,
        private val outH: Int,
        fps: Int,
        audioSourceUri: Uri?,
        audioStartUs: Long,
        audioEndUs: Long
    ) : BaseMuxedSession(context, outputFile, outW, outH, fps, audioSourceUri, audioStartUs, audioEndUs) {
        fun writeFrame(bmp: Bitmap) {
            val canvas = inputSurface.lockCanvas(null)
            try {
                canvas.drawBitmap(bmp, null, Rect(0, 0, outW, outH), null)
            } finally {
                inputSurface.unlockCanvasAndPost(canvas)
            }
            drainEncoder(false)
        }
    }

    private class EglMuxedVideoSession(
        context: Context,
        outputFile: File,
        outW: Int,
        outH: Int,
        fps: Int,
        audioSourceUri: Uri?,
        audioStartUs: Long,
        audioEndUs: Long
    ) : BaseMuxedSession(context, outputFile, outW, outH, fps, audioSourceUri, audioStartUs, audioEndUs) {

        private val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val eglContext: android.opengl.EGLContext
        private val eglSurface: android.opengl.EGLSurface

        init {
            val ver = IntArray(2)
            EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)

            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0)

            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0
            )
            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                configs[0],
                inputSurface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun swapAndDrain(ptsUs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsUs * 1000L)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            drainEncoder(false)
        }

        override fun release() {
            try {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            } catch (_: Exception) {}
            super.release()
        }
    }

    private fun computeEvenSize(srcW: Int, srcH: Int): Pair<Int, Int> {
        val scaleOut = if (max(srcW, srcH) > 1280) 1280.0 / max(srcW, srcH) else 1.0
        val outW = max(2, ((srcW * scaleOut).roundToInt() / 2) * 2)
        val outH = max(2, ((srcH * scaleOut).roundToInt() / 2) * 2)
        return outW to outH
    }

    private data class SegmentRange(
        val startIdx: Int,
        val endIdx: Int,
        val timestamps: List<Long>
    )

    private fun getSegmentIndices(timestampsUs: List<Long>, startMs: Long, endMs: Long): SegmentRange {
        if (timestampsUs.isEmpty()) return SegmentRange(0, -1, emptyList())
        val startUs = startMs * 1000L
        val endUs = endMs * 1000L
        val startIdx = timestampsUs.indexOfFirst { it >= startUs }.let { if (it >= 0) it else 0 }
        val endIdx = timestampsUs.indexOfLast { it <= endUs }.let { if (it >= startIdx) it else timestampsUs.lastIndex }
        val list = if (startIdx <= endIdx) timestampsUs.subList(startIdx, endIdx + 1) else emptyList()
        return SegmentRange(startIdx, endIdx, list)
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
}
''',

    "app/src/main/res/layout/activity_main.xml": r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#121212"
    android:orientation="vertical"
    android:padding="10dp">

    <LinearLayout
        android:id="@+id/modeSwitchLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingBottom="4dp">
        <Button
            android:id="@+id/btnModeRoto"
            android:layout_width="0dp"
            android:layout_height="38dp"
            android:layout_marginEnd="4dp"
            android:layout_weight="1"
            android:paddingHorizontal="2dp"
            android:text="Ротоскоп"
            android:textSize="11sp" />
        <Button
            android:id="@+id/btnModeShake"
            android:layout_width="0dp"
            android:layout_height="38dp"
            android:layout_marginEnd="4dp"
            android:layout_weight="1"
            android:paddingHorizontal="2dp"
            android:text="Качка"
            android:textSize="11sp" />
        <Button
            android:id="@+id/btnModeEffects"
            android:layout_width="0dp"
            android:layout_height="38dp"
            android:layout_weight="1"
            android:paddingHorizontal="2dp"
            android:text="Эффекты"
            android:textSize="11sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingBottom="4dp"
        android:text="Выберите видео и обведите объект пальцем"
        android:textColor="#FFFFFF"
        android:textSize="13sp" />

    <FrameLayout
        android:id="@+id/previewContainer"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:background="#1E1E1E">

        <com.rotokey.app.ContourOverlayView
            android:id="@+id/contourView"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

        <ImageView
            android:id="@+id/targetImageView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="fitCenter"
            android:visibility="gone" />

        <com.rotokey.app.EffectStageView
            android:id="@+id/effectStageView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone" />

        <VideoView
            android:id="@+id/videoPreview"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:layout_gravity="center"
            android:visibility="gone" />
    </FrameLayout>

    <ProgressBar
        android:id="@+id/progressBar"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:visibility="gone" />

    <LinearLayout
        android:id="@+id/timelineLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:orientation="vertical">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">
            <TextView
                android:id="@+id/tvTimelineLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Отрезок: "
                android:textColor="#CCCCCC"
                android:textSize="12sp" />
            <TextView
                android:id="@+id/tvTimeRange"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="00:00.0 — 00:00.0"
                android:textColor="#FFFFFF"
                android:textSize="12sp" />
        </LinearLayout>

        <com.google.android.material.slider.RangeSlider
            android:id="@+id/rangeSlider"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:enabled="false"
            android:valueFrom="0.0"
            android:valueTo="1.0" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/rotoControlsLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">
            <Button
                android:id="@+id/btnUndo"
                android:layout_width="0dp"
                android:layout_height="38dp"
                android:layout_marginEnd="4dp"
                android:layout_weight="1"
                android:enabled="false"
                android:minWidth="0dp"
                android:paddingHorizontal="4dp"
                android:text="↶"
                android:textSize="16sp" />
            <Button
                android:id="@+id/btnRedo"
                android:layout_width="0dp"
                android:layout_height="38dp"
                android:layout_marginEnd="4dp"
                android:layout_weight="1"
                android:enabled="false"
                android:minWidth="0dp"
                android:paddingHorizontal="4dp"
                android:text="↷"
                android:textSize="16sp" />
            <Button
                android:id="@+id/btnDeletePoint"
                android:layout_width="0dp"
                android:layout_height="38dp"
                android:layout_marginEnd="4dp"
                android:layout_weight="1.3"
                android:enabled="false"
                android:minWidth="0dp"
                android:paddingHorizontal="4dp"
                android:text="- Точка"
                android:textSize="11sp" />
            <Button
                android:id="@+id/btnDeleteContour"
                android:layout_width="0dp"
                android:layout_height="38dp"
                android:layout_weight="1.3"
                android:enabled="false"
                android:minWidth="0dp"
                android:paddingHorizontal="4dp"
                android:text="Удалить"
                android:textSize="11sp" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Фон: "
                android:textColor="#CCCCCC"
                android:textSize="12sp" />
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
            android:layout_marginTop="4dp"
            android:orientation="horizontal">
            <Button
                android:id="@+id/btnPickVideo"
                android:layout_width="0dp"
                android:layout_height="40dp"
                android:layout_marginEnd="6dp"
                android:layout_weight="1"
                android:text="Видео"
                android:textSize="13sp" />
            <Button
                android:id="@+id/btnProcessRoto"
                android:layout_width="0dp"
                android:layout_height="40dp"
                android:layout_weight="1"
                android:text="Старт"
                android:textSize="13sp" />
        </LinearLayout>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/shakeControlsLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:visibility="gone">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">
            <Button
                android:id="@+id/btnPickDonor"
                android:layout_width="0dp"
                android:layout_height="38dp"
                android:layout_marginEnd="4dp"
                android:layout_weight="1.2"
                android:paddingHorizontal="4dp"
                android:text="Донор (видео)"
                android:textSize="11sp" />
            <Button
                android:id="@+id/btnPickTargetPhoto"
                android:layout_width="0dp"
                android:layout_height="38dp"
                android:layout_marginEnd="4dp"
                android:layout_weight="1"
                android:paddingHorizontal="4dp"
                android:text="Цель: Фото"
                android:textSize="11sp" />
            <Button
                android:id="@+id/btnPickTargetVideo"
                android:layout_width="0dp"
                android:layout_height="38dp"
                android:layout_weight="1"
                android:paddingHorizontal="4dp"
                android:text="Цель: Видео"
                android:textSize="11sp" />
        </LinearLayout>

        <TextView
            android:id="@+id/tvShakeInfo"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingTop="4dp"
            android:text="Донор: не выбран | Цель: не выбрана"
            android:textColor="#CCCCCC"
            android:textSize="12sp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">
            <TextView
                android:id="@+id/tvShakeStrength"
                android:layout_width="110dp"
                android:layout_height="wrap_content"
                android:text="Сила: 1.0x"
                android:textColor="#FFFFFF"
                android:textSize="12sp" />
            <com.google.android.material.slider.Slider
                android:id="@+id/sliderStrength"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:stepSize="0.1"
                android:value="1.0"
                android:valueFrom="0.0"
                android:valueTo="2.0" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">
            <TextView
                android:id="@+id/tvShakeZoom"
                android:layout_width="110dp"
                android:layout_height="wrap_content"
                android:text="Зум краёв: 1.20x"
                android:textColor="#FFFFFF"
                android:textSize="12sp" />
            <com.google.android.material.slider.Slider
                android:id="@+id/sliderZoom"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:stepSize="0.05"
                android:value="1.2"
                android:valueFrom="1.0"
                android:valueTo="1.5" />
        </LinearLayout>

        <Button
            android:id="@+id/btnProcessShake"
            android:layout_width="match_parent"
            android:layout_height="40dp"
            android:text="Перенести качку"
            android:textSize="13sp" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/effectsControlsLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:visibility="gone">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">
            <Button
                android:id="@+id/btnPickEffectsVideo"
                android:layout_width="0dp"
                android:layout_height="38dp"
                android:layout_marginEnd="6dp"
                android:layout_weight="1"
                android:paddingHorizontal="4dp"
                android:text="Видео"
                android:textSize="12sp" />

            <androidx.appcompat.widget.SwitchCompat
                android:id="@+id/switchLockCamera"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="6dp"
                android:text="3D-трекинг "
                android:textColor="#FFFFFF"
                android:textSize="12sp" />

            <Button
                android:id="@+id/btnAddEffect"
                android:layout_width="wrap_content"
                android:layout_height="38dp"
                android:minWidth="52dp"
                android:text="+"
                android:textSize="18sp" />
        </LinearLayout>

        <ScrollView
            android:layout_width="match_parent"
            android:layout_height="110dp"
            android:layout_marginTop="4dp"
            android:background="#1A1A1A"
            android:padding="4dp">

            <LinearLayout
                android:id="@+id/effectsListContainer"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />
        </ScrollView>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:orientation="horizontal">
            <Button
                android:id="@+id/btnLiveEffectsPreview"
                android:layout_width="0dp"
                android:layout_height="40dp"
                android:layout_marginEnd="6dp"
                android:layout_weight="1"
                android:text="Предпросмотр"
                android:textSize="12sp" />
            <Button
                android:id="@+id/btnRenderEffects"
                android:layout_width="0dp"
                android:layout_height="40dp"
                android:layout_weight="1"
                android:text="Рендер"
                android:textSize="12sp" />
        </LinearLayout>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/previewControlsLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="6dp"
        android:orientation="horizontal"
        android:visibility="gone">
        <Button
            android:id="@+id/btnBackToEdit"
            android:layout_width="0dp"
            android:layout_height="40dp"
            android:layout_marginEnd="6dp"
            android:layout_weight="1.2"
            android:paddingHorizontal="4dp"
            android:text="К правке"
            android:textSize="12sp" />
        <Button
            android:id="@+id/btnReplay"
            android:layout_width="0dp"
            android:layout_height="40dp"
            android:layout_marginEnd="6dp"
            android:layout_weight="1"
            android:paddingHorizontal="4dp"
            android:text="Повтор"
            android:textSize="12sp" />
        <Button
            android:id="@+id/btnSaveGallery"
            android:layout_width="0dp"
            android:layout_height="40dp"
            android:layout_weight="1.2"
            android:paddingHorizontal="4dp"
            android:text="Сохранить"
            android:textSize="12sp" />
    </LinearLayout>

</LinearLayout>
''',

    "app/src/main/java/com/rotokey/app/MainActivity.kt": r'''package com.rotokey.app

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
''',
}


def main():
    for rel_path, content in FILES.items():
        full_path = BASE_DIR / rel_path
        full_path.parent.mkdir(parents=True, exist_ok=True)
        full_path.write_text(content, encoding="utf-8", newline="\n")
        print(f"Обновлён/создан: {full_path}")

    print("\nГотово! Все файлы записаны без ошибок.")


if __name__ == "__main__":
    main()
