package com.rotokey.app

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
