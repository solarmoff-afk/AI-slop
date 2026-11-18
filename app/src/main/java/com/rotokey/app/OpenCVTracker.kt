package com.rotokey.app

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
