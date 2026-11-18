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
            val option = if (exact) {
                MediaMetadataRetriever.OPTION_CLOSEST
            } else {
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            }
            retriever.getFrameAtTime(timeMs * 1000L, option)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
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
        val startUs = startMs * 1000L
        val endUs = endMs * 1000L

        val startIdx = videoInfo.timestampsUs.indexOfFirst { it >= startUs }.let {
            if (it >= 0) it else 0
        }
        val endIdx = videoInfo.timestampsUs.indexOfLast { it <= endUs }.let {
            if (it >= startIdx) it else videoInfo.timestampsUs.lastIndex
        }

        val segmentTimestamps = if (videoInfo.timestampsUs.isNotEmpty() && startIdx <= endIdx) {
            videoInfo.timestampsUs.subList(startIdx, endIdx + 1)
        } else {
            emptyList()
        }

        if (segmentTimestamps.isEmpty()) {
            throw IllegalStateException("В выбранном отрезке нет кадров")
        }

        val firstBmp = loadFrameAtTime(context, videoUri, startMs, exact = true)
            ?: throw IllegalStateException("Не удалось прочитать начальный кадр")

        val scaleOut = if (maxOf(firstBmp.width, firstBmp.height) > 1280) {
            1280.0 / maxOf(firstBmp.width, firstBmp.height)
        } else 1.0
        val outW = ((firstBmp.width * scaleOut).roundToInt() / 2) * 2
        val outH = ((firstBmp.height * scaleOut).roundToInt() / 2) * 2
        firstBmp.recycle()

        val previewFile = File(context.cacheDir, "rotokey_preview_${System.currentTimeMillis()}.mp4")
        if (previewFile.exists()) previewFile.delete()

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

        val muxer = MediaMuxer(previewFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
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
            val totalSegmentFrames = segmentTimestamps.size
            var frameCounter = 0

            val canUseBatch = videoInfo.reportedFrameCount > 0 &&
                videoInfo.reportedFrameCount == videoInfo.timestampsUs.size

            if (canUseBatch) {
                val batchSize = 4
                var idx = startIdx
                while (idx <= endIdx) {
                    val count = min(batchSize, endIdx - idx + 1)
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
                        onProgress(frameCounter, totalSegmentFrames)
                    }
                    idx += frames.size
                }
            }

            // Резервный или точный проход по таймстемпам выбранного отрезка
            if (frameCounter == 0) {
                for (tsUs in segmentTimestamps) {
                    val rawBmp = retriever.getFrameAtTime(
                        tsUs,
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
                    onProgress(frameCounter, totalSegmentFrames)
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
