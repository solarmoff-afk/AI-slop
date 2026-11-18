package com.rotokey.app

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.atan2
import kotlin.math.max

class OpenCVShakeTransfer {
    data class MotionDelta(
        val dxNorm: Double,
        val dyNorm: Double,
        val dAngleDeg: Double
    )

    private var smallW = 0
    private var smallH = 0
    private val prevGray = Mat()
    private var prevFeatures = MatOfPoint2f()

    fun initDonorFrame(bitmap: Bitmap) {
        val smallGray = toSmallGray(bitmap)
        smallGray.copyTo(prevGray)
        extractFeatures(smallGray)
        smallGray.release()
    }

    fun stepDonorFrame(bitmap: Bitmap): MotionDelta {
        val smallGray = toSmallGray(bitmap)
        var delta = MotionDelta(0.0, 0.0, 0.0)

        if (!prevFeatures.empty() && prevFeatures.rows() >= 6) {
            val nextFeatures = MatOfPoint2f()
            val status = MatOfByte()
            val err = MatOfFloat()

            Video.calcOpticalFlowPyrLK(prevGray, smallGray, prevFeatures, nextFeatures, status, err)

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

            if (goodPrev.size >= 6) {
                val prevMat = MatOfPoint2f(*goodPrev.toTypedArray())
                val nextMat = MatOfPoint2f(*goodNext.toTypedArray())
                val affine = Calib3d.estimateAffinePartial2D(prevMat, nextMat)

                if (!affine.empty() && affine.rows() >= 2 && affine.cols() >= 3) {
                    val m = DoubleArray(6)
                    affine.get(0, 0, m)
                    // m = [a, -b, tx, b, a, ty]
                    val dxNorm = (m[2] / smallW).coerceIn(-0.15, 0.15)
                    val dyNorm = (m[5] / smallH).coerceIn(-0.15, 0.15)
                    val dAngleDeg = (-Math.toDegrees(atan2(m[3], m[0]))).coerceIn(-10.0, 10.0)
                    delta = MotionDelta(dxNorm, dyNorm, dAngleDeg)
                }

                prevMat.release()
                nextMat.release()
                affine.release()
            }

            nextFeatures.release()
            status.release()
            err.release()
        }

        smallGray.copyTo(prevGray)
        extractFeatures(smallGray)
        smallGray.release()
        return delta
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
        Imgproc.goodFeaturesToTrack(gray, corners, 120, 0.01, 16.0)
        prevFeatures.release()
        prevFeatures = if (!corners.empty()) {
            MatOfPoint2f(*corners.toArray())
        } else {
            MatOfPoint2f()
        }
        corners.release()
    }

    fun release() {
        prevGray.release()
        prevFeatures.release()
    }

    companion object {
        fun bitmapToBgrMat(bitmap: Bitmap): Mat {
            val rgba = Mat()
            Utils.bitmapToMat(bitmap, rgba)
            val bgr = Mat()
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            rgba.release()
            return bgr
        }

        fun renderWarpedFrame(
            srcBgr: Mat,
            outW: Int,
            outH: Int,
            offsetX: Double,
            offsetY: Double,
            angleDeg: Double,
            zoomScale: Double
        ): Bitmap {
            val center = Point(srcBgr.cols() / 2.0, srcBgr.rows() / 2.0)
            val rotMat = Imgproc.getRotationMatrix2D(center, angleDeg, zoomScale)

            val tx = rotMat.get(0, 2)[0] + offsetX
            val ty = rotMat.get(1, 2)[0] + offsetY
            rotMat.put(0, 2, tx)
            rotMat.put(1, 2, ty)

            val warped = Mat()
            Imgproc.warpAffine(
                srcBgr,
                warped,
                rotMat,
                Size(outW.toDouble(), outH.toDouble()),
                Imgproc.INTER_LINEAR,
                Core.BORDER_REFLECT_101
            )

            val rgba = Mat()
            Imgproc.cvtColor(warped, rgba, Imgproc.COLOR_BGR2RGBA)
            val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, outBitmap)

            rotMat.release()
            warped.release()
            rgba.release()
            return outBitmap
        }
    }
}
