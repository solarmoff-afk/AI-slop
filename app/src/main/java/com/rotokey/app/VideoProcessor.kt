package com.rotokey.app

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
