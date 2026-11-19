package com.rotokey.app

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
