package com.rotokey.app

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
