package com.example.timeline.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

/** Reads container timing/track metadata without asking MediaExtractor to parse a .mcraw file. */
class McrawMediaAnalyzer(private val context: Context) {
    suspend fun analyze(asset: MediaAsset, uri: Uri): MediaAsset = withContext(Dispatchers.IO) {
        var handle = 0L
        try {
            handle = NativeMcrawDecoder.open(context, uri)
            val frameCount = NativeMcrawDecoder.frameCount(handle)
            if (frameCount <= 0) throw IllegalStateException("MotionCam recording contains no video frames")

            val container = JSONObject(NativeMcrawDecoder.containerMetadataJson(handle))
            val firstFrame = JSONObject(NativeMcrawDecoder.frameMetadataJson(handle, 0))
            val width = firstFrame.optInt("width", 0)
            val height = firstFrame.optInt("height", 0)
            if (width <= 0 || height <= 0) throw IllegalStateException("MotionCam frame dimensions are missing")
            val whiteLevel = container.optDouble("whiteLevel", 65_535.0).coerceAtLeast(1.0)
            val sensorBitDepth = ceil(ln(whiteLevel + 1.0) / ln(2.0)).toInt().coerceIn(1, 16)

            val timestampsNs = LongArray(frameCount) { index ->
                NativeMcrawDecoder.frameTimestampNs(handle, index)
            }
            val positiveIntervalsNs = timestampsNs.asList()
                .zipWithNext { earlier, later -> later - earlier }
                .filter { it > 0L }
            val medianIntervalNs = median(positiveIntervalsNs)
            val nominalFrameRate = readNominalFrameRate(container)
            val frameRate = (if (medianIntervalNs > 0L) {
                (1_000_000_000.0 / medianIntervalNs.toDouble()).toFloat()
            } else {
                nominalFrameRate
            }).coerceIn(1f, 240f)
            val frameRateRational = FrameRate((frameRate * 1000f).roundToInt().coerceAtLeast(1), 1000)
            val durationUs = when {
                timestampsNs.size > 1 && medianIntervalNs > 0L ->
                    ((timestampsNs.last() - timestampsNs.first()).coerceAtLeast(0L) + medianIntervalNs) / 1000L
                else -> (1_000_000.0 / frameRate).toLong().coerceAtLeast(1L)
            }
            val cadenceUs = timestampsNs.map { it / 1000L }
            val cadence = VariableFrameRateDetector.classify(cadenceUs)
            val cadenceVerified = cadence != FrameRateCadence.UNKNOWN

            val sampleRate = NativeMcrawDecoder.audioSampleRateHz(handle).coerceAtLeast(0)
            val channelCount = NativeMcrawDecoder.audioChannelCount(handle).coerceIn(0, 8)
            val hasAudio = sampleRate > 0 && channelCount > 0
            val audioStreams = if (hasAudio) {
                listOf(
                    AudioStreamInfo(
                        codecMime = "audio/raw",
                        sampleRate = sampleRate,
                        channelCount = channelCount,
                        bitrate = (sampleRate.toLong() * channelCount * 16L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        durationUs = durationUs,
                        language = null,
                        hardwareDecodeSupported = false
                    )
                )
            } else {
                emptyList()
            }

            val hasGyro = NativeMcrawDecoder.hasGyroData(handle)
            val hasAccelerometer = NativeMcrawDecoder.hasAccelerometerData(handle)
            val reportWarnings = buildList {
                add("The native preview currently uses a lightweight Bayer demosaic; validate camera color and playback speed on-device.")
                if (hasAudio) {
                    add("Embedded PCM audio is detected and classified, but audio playback and export routing are not connected to the native preview yet.")
                }
                if (hasGyro || hasAccelerometer) {
                    add("MotionCam sensor metadata detected: gyro=$hasGyro, accelerometer=$hasAccelerometer. Stabilization is not applied yet.")
                }
                if (!hasAudio) {
                    add("No embedded PCM audio track was detected.")
                }
            }

            asset.copy(
                mimeType = MotionCamRawFormat.MIME_TYPE,
                assetType = MediaAssetType.VIDEO,
                previewStatus = PreviewStatus.DIRECT_SUPPORTED_PROBABLE,
                proxyStatus = ProxyStatus.NONE,
                mediaErrorStatus = MediaErrorStatus.NONE,
                errorMessage = null,
                metadata = asset.metadata.copy(
                    durationUs = durationUs,
                    durationFramesInSourceRate = frameCount.toLong(),
                    containerFormat = "MotionCam RAW (MCRAW v3)",
                    bitrate = 0L,
                    width = width,
                    height = height,
                    rotationDegrees = firstFrame.optInt("rotationDegrees", 0).normalizedRotation(),
                    pixelAspectRatio = 1.0f,
                    videoStreams = listOf(
                        VideoStreamInfo(
                            codecName = "motioncam-raw",
                            codecMime = MotionCamRawFormat.MIME_TYPE,
                            codecProfile = null,
                            codecLevel = null,
                            width = width,
                            height = height,
                            frameRate = frameRateRational,
                            bitDepth = sensorBitDepth,
                            chromaSubsampling = "Bayer",
                            colorStandard = null,
                            colorTransfer = null,
                            colorRange = null,
                            isHdr = false,
                            isLog = false,
                            detectedLogProfile = null,
                            hardwareDecodeSupported = false,
                            softwareDecodeSupported = true,
                            requiresProxy = false,
                            requiresTranscode = false,
                            decoderSupportUnknown = false
                        )
                    ),
                    audioStreams = audioStreams,
                    hasVideo = true,
                    hasAudio = hasAudio,
                    isVariableFrameRate = cadence == FrameRateCadence.VARIABLE,
                    estimatedFrameRate = frameRateRational,
                    exactFrameRate = null,
                    variableFrameRateDetectionComplete = cadenceVerified
                ),
                technicalReport = ImportTechnicalReport(
                    detectedFormat = MotionCamRawFormat.MIME_TYPE,
                    directPlaybackSupport = true,
                    proxyRecommendation = false,
                    transcodeRecommendation = false,
                    warnings = reportWarnings,
                    userFriendlyMessage = "MotionCam RAW media is ready for native preview."
                )
            )
        } catch (error: Exception) {
            asset.copy(
                mimeType = MotionCamRawFormat.MIME_TYPE,
                assetType = MediaAssetType.VIDEO,
                previewStatus = PreviewStatus.UNSUPPORTED,
                proxyStatus = ProxyStatus.NONE,
                mediaErrorStatus = MediaErrorStatus.ANALYSIS_FAILED,
                errorMessage = MotionCamRawFormat.openErrorMessage(error),
                technicalReport = ImportTechnicalReport(
                    detectedFormat = MotionCamRawFormat.MIME_TYPE,
                    directPlaybackSupport = false,
                    proxyRecommendation = false,
                    transcodeRecommendation = false,
                    warnings = listOf(MotionCamRawFormat.openErrorMessage(error)),
                    userFriendlyMessage = "This MotionCam RAW recording could not be opened."
                )
            )
        } finally {
            if (handle != 0L) NativeMcrawDecoder.close(handle)
        }
    }

    private fun readNominalFrameRate(container: JSONObject): Float {
        val keys = listOf("frameRate", "fps", "captureFrameRate")
        for (key in keys) {
            val value = container.optDouble(key, Double.NaN)
            if (value.isFinite() && value in 1.0..240.0) return value.toFloat()
        }
        return 30f
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            ((sorted[middle - 1].toDouble() + sorted[middle].toDouble()) / 2.0).toLong()
        } else {
            sorted[middle]
        }
    }

    private fun Int.normalizedRotation(): Int = ((this % 360) + 360) % 360
}
