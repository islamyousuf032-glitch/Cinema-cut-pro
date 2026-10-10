package com.example.timeline.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidMediaAnalyzer(
    private val context: Context,
    private val capabilityDetector: CodecCapabilityDetector = CodecCapabilityDetector(),
    private val decisionEngine: MediaImportDecisionEngine = MediaImportDecisionEngine(capabilityDetector)
) : MediaAnalyzer {

    override suspend fun analyze(asset: MediaAsset, uri: Uri): MediaAsset = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()

        var durationUs = 0L
        var width = 0
        var height = 0
        var frameRateVal = 30f
        var defaultMimeType = asset.mimeType
        var rotation = 0

        val videoStreams = mutableListOf<VideoStreamInfo>()
        val audioStreams = mutableListOf<AudioStreamInfo>()
        var firstVideoTrackIndex: Int? = null

        try {
            retriever.setDataSource(context, uri)
            extractor.setDataSource(context, uri, null)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durationStr != null) {
                durationUs = (durationStr.toLongOrNull() ?: 0L) * 1000L
            }
            
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            if (rotationStr != null) {
                rotation = rotationStr.toIntOrNull() ?: 0
            }

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (trackMime.startsWith("video/")) {
                    if (firstVideoTrackIndex == null) firstVideoTrackIndex = i
                    width = format.getIntegerSafely(MediaFormat.KEY_WIDTH, 0)
                    height = format.getIntegerSafely(MediaFormat.KEY_HEIGHT, 0)

                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRateVal = try {
                            format.getFloat(MediaFormat.KEY_FRAME_RATE)
                        } catch(e: Exception) {
                            format.getIntegerSafely(MediaFormat.KEY_FRAME_RATE, 30).toFloat()
                        }
                    }

                    val colorStandard = if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) format.getInteger(MediaFormat.KEY_COLOR_STANDARD) else null
                    val colorTransfer = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else null
                    val isHdr = colorTransfer == MediaFormat.COLOR_TRANSFER_HLG || colorTransfer == MediaFormat.COLOR_TRANSFER_ST2084

                    videoStreams.add(
                        VideoStreamInfo(
                            codecName = trackMime.replace("video/", ""),
                            codecMime = trackMime,
                            codecProfile = if(format.containsKey(MediaFormat.KEY_PROFILE)) format.getIntegerSafely(MediaFormat.KEY_PROFILE, 0) else null,
                            codecLevel = if(format.containsKey(MediaFormat.KEY_LEVEL)) format.getIntegerSafely(MediaFormat.KEY_LEVEL, 0) else null,
                            width = width,
                            height = height,
                            frameRate = FrameRate((frameRateVal * 1000).toInt(), 1000),
                            bitDepth = null,
                            chromaSubsampling = null,
                            colorStandard = colorStandard,
                            colorTransfer = colorTransfer,
                            colorRange = if (format.containsKey(MediaFormat.KEY_COLOR_RANGE)) format.getInteger(MediaFormat.KEY_COLOR_RANGE) else null,
                            isHdr = isHdr,
                            isLog = false,
                            detectedLogProfile = null,
                            hardwareDecodeSupported = true,
                            softwareDecodeSupported = true,
                            requiresProxy = false, // Engine decides
                            requiresTranscode = false
                        )
                    )
                    defaultMimeType = trackMime
                } else if (trackMime.startsWith("audio/")) {
                    audioStreams.add(
                        AudioStreamInfo(
                            codecMime = trackMime,
                            sampleRate = format.getIntegerSafely(MediaFormat.KEY_SAMPLE_RATE, 44100),
                            channelCount = format.getIntegerSafely(MediaFormat.KEY_CHANNEL_COUNT, 2),
                            bitrate = format.getIntegerSafely(MediaFormat.KEY_BIT_RATE, 128000),
                            durationUs = format.getLongSafely(MediaFormat.KEY_DURATION, 0L),
                            language = if(format.containsKey(MediaFormat.KEY_LANGUAGE)) format.getString(MediaFormat.KEY_LANGUAGE) else null,
                            hardwareDecodeSupported = true
                        )
                    )
                    if (defaultMimeType == "application/octet-stream" || defaultMimeType.startsWith("audio/")) {
                        defaultMimeType = trackMime
                    }
                } else if (trackMime.startsWith("image/")) {
                    width = format.getIntegerSafely(MediaFormat.KEY_WIDTH, width)
                    height = format.getIntegerSafely(MediaFormat.KEY_HEIGHT, height)
                    if (defaultMimeType == "application/octet-stream") {
                        defaultMimeType = trackMime
                    }
                }
            }

            if (width == 0 && videoStreams.isEmpty() && !defaultMimeType.startsWith("audio/")) {
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: width
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: height
            }

            val hasVideo = videoStreams.isNotEmpty() || width > 0
            val hasAudio = audioStreams.isNotEmpty()

            if (hasVideo && videoStreams.isEmpty()) {
                val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                if (fpsStr != null) {
                    frameRateVal = fpsStr.toFloatOrNull() ?: frameRateVal
                }
            }
            
            // Probe presentation timestamps from the selected video track. This does not decode
            // frames, and the bounded sample avoids making media import wait on a full scan.
            val isVariableFrameRate = firstVideoTrackIndex?.let { trackIndex ->
                val timestampSamples = sampleVideoPresentationTimeSegments(extractor, trackIndex, durationUs)
                VariableFrameRateDetector.isVariableFrameRateSegments(timestampSamples)
            } ?: false

            val analysisResult = MediaAnalysisResult(
                durationUs = durationUs,
                width = width,
                height = height,
                rotation = rotation,
                frameRate = frameRateVal,
                defaultMimeType = defaultMimeType,
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                hasVideo = hasVideo,
                hasAudio = hasAudio,
                isVariableFrameRate = isVariableFrameRate
            )

            val mediaStatusResult = decisionEngine.decideImportStatus(analysisResult)

            val finalFrameRate = FrameRate((frameRateVal * 1000).toInt(), 1000)
            val durationFrames = if (hasVideo || hasAudio) ((durationUs / 1000000.0) * frameRateVal).toLong() else 1L

            val metadata = asset.metadata.copy(
                durationUs = durationUs,
                durationFramesInSourceRate = durationFrames,
                containerFormat = "",
                width = width,
                height = height,
                rotationDegrees = rotation,
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                hasVideo = hasVideo,
                hasAudio = hasAudio,
                isVariableFrameRate = isVariableFrameRate,
                estimatedFrameRate = finalFrameRate
            )

            return@withContext asset.copy(
                mimeType = defaultMimeType,
                previewStatus = mediaStatusResult.previewStatus,
                proxyStatus = mediaStatusResult.proxyStatus,
                metadata = metadata,
                technicalReport = mediaStatusResult.report
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext asset.copy(
                previewStatus = PreviewStatus.UNSUPPORTED,
                mediaErrorStatus = MediaErrorStatus.UNKNOWN_ERROR,
                errorMessage = e.message ?: "Analysis failed",
                technicalReport = ImportTechnicalReport(
                    detectedFormat = defaultMimeType,
                    directPlaybackSupport = false,
                    proxyRecommendation = false,
                    transcodeRecommendation = false,
                    warnings = listOf("Analysis failed: ${e.message}"),
                    userFriendlyMessage = "Could not analyze the media file."
                )
            )
        } finally {
            try { retriever.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
        }
    }

    private fun sampleVideoPresentationTimeSegments(
        extractor: MediaExtractor,
        videoTrackIndex: Int,
        durationUs: Long
    ): List<List<Long>> {
        val seekPointsUs = if (durationUs > 0L) {
            listOf(0L, durationUs / 2L, (durationUs - VFR_TAIL_WINDOW_US).coerceAtLeast(0L)).distinct()
        } else {
            listOf(0L)
        }
        val sampleLimit = if (seekPointsUs.size == 1) MAX_VFR_SAMPLE_COUNT else VFR_SAMPLES_PER_SEGMENT
        val segments = mutableListOf<List<Long>>()
        var selectedTrack = false

        try {
            extractor.selectTrack(videoTrackIndex)
            selectedTrack = true
            seekPointsUs.forEach { seekPointUs ->
                extractor.seekTo(seekPointUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val segment = ArrayList<Long>(sampleLimit)
                while (segment.size < sampleLimit) {
                    val sampleTrackIndex = extractor.sampleTrackIndex
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTrackIndex < 0 || sampleTimeUs < 0L) break
                    if (sampleTrackIndex == videoTrackIndex) segment += sampleTimeUs
                    if (!extractor.advance()) break
                }
                if (segment.isNotEmpty()) segments += segment
            }
        } catch (error: Exception) {
            android.util.Log.w("AndroidMediaAnalyzer", "Could not sample video timestamps for VFR detection", error)
            return emptyList()
        } finally {
            if (selectedTrack) runCatching { extractor.unselectTrack(videoTrackIndex) }
        }
        return segments
    }

    private fun MediaFormat.getIntegerSafely(key: String, default: Int): Int {
        return try { getInteger(key) } catch(e: Exception) { default }
    }
    private fun MediaFormat.getLongSafely(key: String, default: Long): Long {
        return try { getLong(key) } catch(e: Exception) { default }
    }

    private companion object {
        const val MAX_VFR_SAMPLE_COUNT = 180
        const val VFR_SAMPLES_PER_SEGMENT = 60
        const val VFR_TAIL_WINDOW_US = 2_000_000L
    }
}
