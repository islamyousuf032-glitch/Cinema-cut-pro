package com.example.timeline.export.backend

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.colorgrade.ColorGradeLayerStack
import com.example.model.colorgrade.ColorGradeParams
import com.example.model.colorgrade.ColorGradeStack
import com.example.model.colorgrade.ColorLayerType
import com.example.model.colorgrade.ColorMatchParams
import com.example.model.colorgrade.CurveParams
import com.example.model.colorgrade.HdrToneMappingParams
import com.example.model.colorgrade.HslQualifierParams
import com.example.model.colorgrade.LogTransformParams
import com.example.model.colorgrade.LutGradeParams
import com.example.model.colorgrade.SelectiveColorParams
import com.example.model.colorgrade.SkinToneProtectionParams
import com.example.timeline.core.ClipType
import com.example.timeline.core.ProjectColorSpace
import com.example.timeline.core.Rational
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineTrack
import com.example.timeline.core.TrackType
import com.example.timeline.core.transform.ClipTransform
import com.example.timeline.export.model.ExportBackendType
import com.example.timeline.export.model.ExportCodec
import com.example.timeline.export.model.ExportContainer
import com.example.timeline.export.model.ExportJob
import com.example.timeline.export.model.ExportJobStatus
import com.example.timeline.export.model.ExportProgress
import com.example.timeline.export.model.ExportSettings
import com.example.timeline.export.model.ExportValidationResult
import com.example.timeline.media.FrameRate
import com.example.timeline.media.MediaAsset
import com.example.timeline.media.MotionCamRawFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A deliberately bounded real export backend.
 *
 * Media3 owns Android demux/decode/encode/mux orchestration. This adapter accepts a single visible,
 * unlayered video track containing one or more contiguous CFR clips whose edits can be represented
 * exactly by trims and a presentation resize. Gaps must be fixed on the timeline before export.
 * Everything else is rejected before export; it is never handed to a placeholder exporter.
 */
@OptIn(UnstableApi::class)
class Media3TransformerBackend(context: Context) : ExportBackend {
    override val backendType: ExportBackendType = ExportBackendType.MEDIA3_TRANSFORMER

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeExports = ConcurrentHashMap<String, ActiveExport>()
    @Volatile private var projectRef: TimelineProject? = null

    private data class ActiveClip(
        val clip: TimelineClip,
        val track: TimelineTrack,
        val asset: MediaAsset
    )

    private data class ExportClipPlan(
        val sourceUri: Uri,
        val startPositionUs: Long,
        val endPositionUs: Long,
        val adjustmentParams: VideoAdjustmentParams?
    )

    private data class ExportPlan(
        val clips: List<ExportClipPlan>,
        val outputFrameCount: Long,
        val includeAudio: Boolean
    )

    private data class ActiveExport(
        val transformer: Transformer,
        val listener: Transformer.Listener
    )

    private data class VideoEncoderQuery(
        val width: Int,
        val height: Int,
        val frameRate: Float,
        val bitrate: Int,
        val bitrateMode: Int,
        val hardwareAccelerated: Boolean
    )

    private data class AudioEncoderQuery(val sampleRate: Int, val channelCount: Int, val bitrate: Int)

    override fun getCapabilities(): ExportBackendCapability = ExportBackendCapability(
        backendAvailable = true,
        // Media3 delegates codec choice to the Android device; hardware acceleration is not
        // guaranteed on every supported device, so do not advertise it as a promise.
        hardwareAccelerated = false,
        supportedCodecs = listOf(ExportCodec.H264),
        supportedContainers = listOf(ExportContainer.MP4),
        maxResolutionWidth = 3840,
        maxResolutionHeight = 2160,
        maxFps = 120f,
        supportsHDR = false,
        supportsAlpha = false,
        supportsAudioMix = false,
        // Supports sequential single-track compositions, but not overlays or multiple active tracks.
        supportsVideoComposition = true,
        supportsProRes = false,
        supportsAV1 = false,
        supports10Bit = false,
        supportsBatch = true,
        supportsCancel = true
    )

    override fun validate(settings: ExportSettings, project: TimelineProject): ExportValidationResult {
        projectRef = project
        val errors = collectValidationErrors(settings, project)
        return ExportValidationResult(
            isValid = errors.isEmpty(),
            unsupportedFeatures = errors,
            warnings = collectValidationWarnings(settings, project),
            errorMessage = errors.firstOrNull()
        )
    }

    override suspend fun prepare(job: ExportJob): Boolean {
        val project = projectRef ?: return false
        if (job.projectId != project.id || !validate(job.settings, project).isValid) return false
        val outputPath = job.outputUri ?: return false
        val output = File(outputPath)
        if (output.isDirectory) return false
        val parent = output.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        if (!parent.isDirectory || !parent.canWrite()) return false

        // Transformer creates/truncates its output itself. Remove only the target file, never a
        // parent directory; a unique timestamped name is used by the export UI.
        if (output.exists() && !output.delete()) return false
        return true
    }

    override suspend fun render(job: ExportJob, onProgress: (ExportProgress) -> Unit) {
        val startedAtMs = System.currentTimeMillis()
        var activeExport: ActiveExport? = null
        var succeeded = false
        var outputFile: File? = null
        var totalFrames = 0L

        try {
            val project = projectRef ?: error("The project must be validated before export.")
            require(job.projectId == project.id) { "Export job project does not match the validated project." }
            val plan = createPlanOrThrow(job.settings, project)
            totalFrames = plan.outputFrameCount
            val targetOutputFile = File(job.outputUri ?: error("No output file was provided."))
            outputFile = targetOutputFile
            require(targetOutputFile.parentFile?.isDirectory == true) { "The export output directory is unavailable." }
            if (targetOutputFile.exists() && !targetOutputFile.delete()) {
                error("The existing output file could not be replaced.")
            }

            val completion = CompletableDeferred<Throwable?>()
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    completion.complete(null)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    completion.complete(exportException)
                }
            }

            val editedItems = plan.clips.map { clipPlan ->
                val sourceItem = MediaItem.Builder()
                    .setUri(clipPlan.sourceUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionUs(clipPlan.startPositionUs)
                            .setEndPositionUs(clipPlan.endPositionUs)
                            .build()
                    )
                    .build()
                val videoEffects = buildList<Effect> {
                    clipPlan.adjustmentParams?.let { add(createMedia3ClipAdjustmentEffect(it)) }
                    add(
                        Presentation.createForWidthAndHeight(
                            job.settings.resolutionWidth,
                            job.settings.resolutionHeight,
                            Presentation.LAYOUT_SCALE_TO_FIT
                        )
                    )
                }
                EditedMediaItem.Builder(sourceItem)
                    .setRemoveAudio(!plan.includeAudio)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
            }
            val composition = Composition.Builder(
                EditedMediaItemSequence.Builder(editedItems).build()
            ).build()

            val videoBitrateMode = when (job.settings.bitrateMode) {
                ExportSettings.BitrateMode.CBR -> android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                ExportSettings.BitrateMode.AUTO,
                ExportSettings.BitrateMode.VBR -> android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                ExportSettings.BitrateMode.CRF -> error("CRF bitrate mode is not supported by this Android encoder adapter.")
            }
            val videoEncoderSettings = VideoEncoderSettings.Builder()
                .setBitrate(job.settings.videoBitrate)
                .setBitrateMode(videoBitrateMode)
                .setiFrameIntervalSeconds(job.settings.keyframeInterval.toFloat())
                .build()
            val encoderFactoryBuilder = DefaultEncoderFactory.Builder(appContext)
                .setRequestedVideoEncoderSettings(videoEncoderSettings)
            if (plan.includeAudio) {
                encoderFactoryBuilder.setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder().setBitrate(job.settings.audioBitrate).build()
                )
            }
            val encoderFactory = encoderFactoryBuilder.build()

            onProgress(
                progress(
                    jobId = job.jobId,
                    status = ExportJobStatus.ENCODING,
                    percent = 0,
                    totalFrames = totalFrames,
                    startedAtMs = startedAtMs,
                    stage = "Preparing Android encoder"
                )
            )

            val transformer = withContext(Dispatchers.Main.immediate) {
                val builtTransformer = Transformer.Builder(appContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .apply {
                        if (plan.includeAudio) setAudioMimeType(MimeTypes.AUDIO_AAC)
                    }
                    .setEncoderFactory(encoderFactory)
                    .addListener(listener)
                    .build()
                val active = ActiveExport(builtTransformer, listener)
                activeExports[job.jobId] = active
                activeExport = active
                builtTransformer.start(composition, targetOutputFile.absolutePath)
                builtTransformer
            }

            val progressHolder = ProgressHolder()
            var lastReportedPercent = -1
            while (!completion.isCompleted) {
                currentCoroutineContext().ensureActive()
                delay(PROGRESS_POLL_INTERVAL_MS)
                val progressState = withContext(Dispatchers.Main.immediate) {
                    transformer.getProgress(progressHolder)
                }
                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val percent = progressHolder.progress.coerceIn(0, 99)
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress(
                            progress(
                                jobId = job.jobId,
                                status = ExportJobStatus.ENCODING,
                                percent = percent,
                                totalFrames = totalFrames,
                                startedAtMs = startedAtMs,
                                stage = "Encoding MP4"
                            )
                        )
                    }
                }
            }

            val exportFailure = completion.await()
            if (exportFailure != null) throw exportFailure
            if (!outputFile.isFile || outputFile.length() <= 0L) {
                error("Media3 completed without producing a non-empty MP4 file.")
            }

            succeeded = true
            onProgress(
                progress(
                    jobId = job.jobId,
                    status = ExportJobStatus.COMPLETED,
                    percent = 100,
                    totalFrames = totalFrames,
                    startedAtMs = startedAtMs,
                    stage = "MP4 export complete"
                )
            )
        } catch (cancelled: CancellationException) {
            outputFile?.delete()
            throw cancelled
        } catch (failure: Exception) {
            outputFile?.delete()
            onProgress(
                ExportProgress(
                    jobId = job.jobId,
                    status = ExportJobStatus.FAILED,
                    progressPercent = 0f,
                    renderedFrames = 0L,
                    totalFrames = totalFrames,
                    elapsedMs = System.currentTimeMillis() - startedAtMs,
                    estimatedRemainingMs = 0L,
                    currentStage = "Export failed",
                    errorMessage = failure.message ?: failure.javaClass.simpleName
                )
            )
        } finally {
            val active = activeExport ?: activeExports[job.jobId]
            activeExports.remove(job.jobId)
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                active?.let {
                    if (!succeeded) runCatching { it.transformer.cancel() }
                    runCatching { it.transformer.removeListener(it.listener) }
                }
            }
            if (!succeeded) outputFile?.delete()
        }
    }

    override fun cancel(jobId: String) {
        val active = activeExports[jobId] ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { active.transformer.cancel() }
        } else {
            mainHandler.post { runCatching { active.transformer.cancel() } }
        }
    }

    override fun release() {
        activeExports.keys.toList().forEach(::cancel)
        projectRef = null
    }

    private fun collectValidationErrors(settings: ExportSettings, project: TimelineProject): List<String> {
        val errors = mutableListOf<String>()
        val capabilities = getCapabilities()

        if (!capabilities.backendAvailable) errors += "Media3 Transformer is unavailable."
        if (settings.codec != ExportCodec.H264) errors += "This export path supports H.264 video only."
        if (settings.container != ExportContainer.MP4) errors += "This export path supports MP4 containers only."
        if (settings.resolutionWidth <= 0 || settings.resolutionHeight <= 0 ||
            settings.resolutionWidth % 2 != 0 || settings.resolutionHeight % 2 != 0
        ) {
            errors += "H.264 output dimensions must be positive, even pixel dimensions."
        }
        if (settings.resolutionWidth > capabilities.maxResolutionWidth ||
            settings.resolutionHeight > capabilities.maxResolutionHeight
        ) {
            errors += "The requested output resolution exceeds the supported adapter limit."
        }
        if (settings.frameRate.numerator <= 0 || settings.frameRate.denominator <= 0 ||
            !settings.frameRate.floatValue.isFinite() || settings.frameRate.floatValue > capabilities.maxFps
        ) {
            errors += "The requested output frame rate is invalid or exceeds the adapter limit."
        }
        if (settings.videoBitrate <= 0) errors += "The target video bitrate must be positive."
        if (settings.maxBitrate <= 0) errors += "The maximum video bitrate must be positive."
        else if (settings.maxBitrate != settings.videoBitrate) {
            errors += "This export path exposes one target bitrate only; set maximum bitrate equal to the target bitrate."
        }
        if (settings.audioBitrate <= 0) errors += "The target audio bitrate must be positive."
        if (settings.keyframeInterval <= 0) errors += "The keyframe interval must be positive."
        if (settings.bitrateMode == ExportSettings.BitrateMode.CRF) {
            errors += "CRF bitrate mode is not supported by this Android encoder adapter."
        }
        if (settings.profile != null || settings.level != null) {
            errors += "Custom H.264 profile and level selection is not supported by this export path."
        }
        if (!settings.useHardwareEncoder) errors += "This export path cannot force a software-only encoder; hardware acceleration is checked when requested and remains best-effort at render time."
        if (!settings.useOriginalMedia || settings.allowProxyExport) {
            errors += "This export path reads original media only; proxy selection is not implemented."
        }
        if (settings.renderAlpha) errors += "H.264 MP4 export does not support the requested alpha channel."
        if (settings.exportHDR || settings.colorOutput != com.example.timeline.export.model.ExportColorSettings.ColorOutput.Rec709 ||
            settings.toneMappingMode != com.example.timeline.export.model.ExportColorSettings.ToneMappingMode.STANDARD
        ) {
            errors += "This first export path supports Rec.709 SDR output only."
        }
        if (settings.includeWatermark) errors += "Watermark compositing is not supported by this export path yet."
        if (settings.audioCodec != com.example.timeline.export.model.ExportAudioSettings.AudioCodec.AAC) {
            errors += "This export path supports AAC audio only."
        }
        if (settings.normalizeAudio) errors += "Audio normalization is not supported by this export path yet."

        val activeVideoTracks = project.tracks.filter { track ->
            track.type == TrackType.VIDEO && track.isVisible && track.clips.any { it.isEnabled }
        }
        if (activeVideoTracks.size != 1) {
            errors += "Exactly one enabled visible video track is supported by this export path."
        }
        val activeVideoTrack = activeVideoTracks.singleOrNull()
        val enabledVideoClips = activeVideoTrack?.clips?.filter { it.isEnabled }.orEmpty()
        if (enabledVideoClips.isEmpty()) {
            errors += "At least one enabled clip on a visible video track is required."
        }
        val activeClips = enabledVideoClips.mapNotNull { clip ->
            val asset = project.mediaAssets.firstOrNull { it.assetId == clip.mediaId }
            asset?.let { ActiveClip(clip, activeVideoTrack!!, it) }
        }
        if (activeClips.size != enabledVideoClips.size && enabledVideoClips.isNotEmpty()) {
            errors += "One or more active video clips are missing their imported media asset."
        }

        val orderedClips = activeClips.sortedBy { it.clip.timelineStart }
        if (orderedClips.isNotEmpty()) {
            if (orderedClips.first().clip.timelineStart != 0L) {
                errors += "The supported clip must start at timeline frame zero (the first enabled video clip)."
            }
            var expectedTimelineStart = 0L
            orderedClips.forEach { active ->
                val clip = active.clip
                if (clip.timelineStart > expectedTimelineStart) {
                    errors += "Video gaps are not supported by Media3; use Fix automatically to close the gap."
                } else if (clip.timelineStart < expectedTimelineStart) {
                    errors += "Overlapping clips, crossfades, and other NLE transitions are not supported; this export path is cut-only."
                }
                expectedTimelineStart = maxOf(expectedTimelineStart, clip.timelineEnd)
            }
        }

        val visibleAudioClips = project.tracks
            .filter { it.type == TrackType.AUDIO && !it.isMuted }
            .flatMap { it.clips.filter { clip -> clip.isEnabled } }
        if (visibleAudioClips.isNotEmpty()) {
            errors += "Separate audio-track clips and audio mixes are not supported by this export path."
        }
        if (project.tracks.any { track ->
                track.type !in setOf(TrackType.VIDEO, TrackType.AUDIO) && track.clips.any { it.isEnabled }
            }
        ) {
            errors += "Text, adjustment, nested-sequence, and overlay tracks are not supported by this export path."
        }

        if (project.settings.colorSpace !in setOf(ProjectColorSpace.REC_709, ProjectColorSpace.AUTOMATIC) ||
            project.settings.workingColorSpace !in setOf(ProjectColorSpace.REC_709, ProjectColorSpace.AUTOMATIC) ||
            project.settings.bitDepthPreference > 8
        ) {
            errors += "This export path supports an 8-bit Rec.709 SDR project only."
        }

        val projectFps = project.settings.getFpsRational()
        if (projectFps.numerator <= 0 || projectFps.denominator <= 0) {
            errors += "The project frame rate must be valid before exporting."
        }

        var embeddedAudioPresent: Boolean? = null
        val audioFormats = mutableSetOf<Pair<Int, Int>>()
        activeClips.forEach { active ->
            val (clip, track, asset) = active
            if (clip.type != ClipType.MEDIA) errors += "Only regular media clips can be exported by this path."
            if (clip.duration <= 0L) errors += "An active video clip has no duration."
            if (!asset.metadata.hasVideo || asset.assetType != com.example.timeline.media.MediaAssetType.VIDEO) {
                errors += "Every active timeline clip must reference imported video media."
            }
            if (asset.metadata.durationUs <= 0L) errors += "An imported video has no usable duration metadata."
            if (MotionCamRawFormat.isMcrawAsset(asset)) {
                errors += "MotionCam RAW (.mcraw) video export is not implemented by the H.264/MP4 backend. Export a rendered standard video file instead."
            }
            if (asset.metadata.isVariableFrameRate) {
                errors += "Variable-frame-rate media cannot be conformed to project FPS by this Media3 path yet; export is blocked and the original remains unchanged."
            } else if (!asset.metadata.variableFrameRateDetectionComplete) {
                errors += "Video timestamp cadence is unverified; reanalyze the source before export."
            }
            if (asset.metadata.videoStreams.any { it.isHdr || it.isLog || (it.bitDepth ?: 8) > 8 }) {
                errors += "HDR, log, and greater-than-8-bit sources are not supported by this SDR export path."
            }
            if (clip.transform != ClipTransform.defaultTransform()) {
                errors += "Clip transforms, crops, perspective changes, and transform keyframes are not supported by this path yet."
            }
            if (clip.volume != 1f && !track.isMuted) {
                errors += "Per-clip volume changes are not supported by this export path yet."
            }
            val effectiveAdjustmentParams = effectiveAdjustmentParams(clip)
            if (clip.adjustments.enabled) {
                errors += Media3ClipAdjustmentSupport.validationErrors(
                    clip.adjustments.copy(params = effectiveAdjustmentParams)
                )
            } else if (clip.colorGrade.enabled && clip.colorGrade.curves != CurveParams()) {
                errors += Media3ClipAdjustmentSupport.validationErrors(
                    VideoAdjustmentParams.default().copy(curves = effectiveAdjustmentParams.curves)
                )
            }
            if (hasEnabledGrade(clip.colorGrade) || hasEnabledColorLayers(clip.colorLayers, clip.colorGrade)) {
                errors += "Clip color grades and LUT layers are not supported by this export path yet."
            }

            if (clip.sourceIn < 0L || clip.sourceOut < 0L) {
                errors += "Clip trim ranges cannot use negative source frames."
            } else if (projectFps.numerator > 0 && projectFps.denominator > 0) {
                val trimRange = runCatching {
                    framesToUs(clip.sourceIn, projectFps) to framesToUs(clip.sourceOut, projectFps)
                }.getOrNull()
                if (trimRange == null) {
                    errors += "A clip trim range is too large to represent safely."
                } else {
                    val (sourceInUs, sourceOutUs) = trimRange
                    val frameToleranceUs = ((1_000_000.0 / projectFps.toDouble()).roundToLong()).coerceAtLeast(1L)
                    if (sourceOutUs <= sourceInUs || sourceOutUs > asset.metadata.durationUs + frameToleranceUs) {
                        errors += "A clip trim range is outside its imported video's duration."
                    }
                }
            }

            val expectedSourceFrameRate = asset.metadata.exactFrameRate ?: asset.metadata.estimatedFrameRate
            if (expectedSourceFrameRate == null || !frameRatesMatch(expectedSourceFrameRate, settings.frameRate)) {
                errors += "Media3 preserves each source frame rate; set export FPS to match every constant-frame-rate source."
            }
            val sourceUriString = asset.localOriginalUriString?.takeIf { it.isNotBlank() } ?: asset.originalUriString
            if (sourceUriString.isBlank()) errors += "An imported video has no readable source URI."

            if (!track.isMuted) {
                if (embeddedAudioPresent == null) {
                    embeddedAudioPresent = asset.metadata.hasAudio
                } else if (embeddedAudioPresent != asset.metadata.hasAudio) {
                    errors += "All clips on the exported video sequence must either contain embedded audio or be muted."
                }
                if (asset.metadata.hasAudio) {
                    if (asset.metadata.audioStreams.size != 1) {
                        errors += "Exactly one embedded audio stream per clip is supported; separate audio editing is not supported."
                    } else {
                        val audio = asset.metadata.audioStreams.single()
                        audioFormats += (audio.sampleRate to audio.channelCount)
                        if (audio.sampleRate != settings.audioSampleRate || audio.channelCount != settings.audioChannels) {
                            errors += "For this export path, AAC sample rate and channels must match every source clip."
                        }
                        if (audio.channelCount !in 1..2) {
                            errors += "Only mono or stereo embedded audio is supported by this export path."
                        }
                    }
                }
            }
        }
        if (audioFormats.size > 1) {
            errors += "Embedded audio formats must use the same sample rate and channel count across all clips."
        }
        val includeAudio = activeVideoTrack?.isMuted == false && activeClips.isNotEmpty() &&
            activeClips.all { it.asset.metadata.hasAudio }
        if (includeAudio && !hasAudioEncoder(settings.audioSampleRate, settings.audioChannels, settings.audioBitrate)) {
            errors += "No Android AAC encoder supports the requested source audio format."
        }
        if (activeClips.isNotEmpty() && settings.useHardwareEncoder && !hasVideoEncoder(settings)) {
            errors += "No hardware-accelerated Android H.264 encoder supports the requested resolution, frame rate, bitrate, and bitrate mode."
        }

        return errors.distinct()
    }

    private fun collectValidationWarnings(settings: ExportSettings, project: TimelineProject): List<String> {
        val warnings = mutableListOf<String>()
        if (settings.useHardwareEncoder) {
            warnings += "Media3 selects a compatible Android codec; this adapter cannot guarantee hardware acceleration."
        }
        val activeAssets = project.tracks
            .filter { it.type == TrackType.VIDEO && it.isVisible }
            .flatMap { track -> track.clips.filter { it.isEnabled } }
            .mapNotNull { clip -> project.mediaAssets.firstOrNull { it.assetId == clip.mediaId } }
        if (activeAssets.any { it.metadata.exactFrameRate == null && it.metadata.estimatedFrameRate != null }) {
            warnings += "One or more source frame rates are estimated from import metadata; verify they are constant-frame-rate."
        }
        if (activeAssets.size > 1) {
            warnings += "Video clips are exported as a sequential, cut-only Media3 composition after timeline gaps are closed."
            warnings += "Crossfades and other NLE transitions are not implemented by this export path."
        }
        return warnings
    }

    private fun createPlanOrThrow(settings: ExportSettings, project: TimelineProject): ExportPlan {
        val errors = collectValidationErrors(settings, project)
        require(errors.isEmpty()) { errors.joinToString("\n") }
        val track = project.tracks
            .filter { it.type == TrackType.VIDEO && it.isVisible && it.clips.any { clip -> clip.isEnabled } }
            .single()
        val projectFps = project.settings.getFpsRational()
        val enabledClips = track.clips.filter { it.isEnabled }.sortedBy { it.timelineStart }
        val clipPlans = enabledClips.map { timelineClip ->
            val asset = project.mediaAssets.first { it.assetId == timelineClip.mediaId }
            val sourceUriString = asset.localOriginalUriString?.takeIf { it.isNotBlank() } ?: asset.originalUriString
            val parsedUri = Uri.parse(sourceUriString)
            val sourceUri = if (parsedUri.scheme == null) Uri.fromFile(File(sourceUriString)) else parsedUri
            ExportClipPlan(
                sourceUri = sourceUri,
                startPositionUs = framesToUs(timelineClip.sourceIn, projectFps),
                endPositionUs = framesToUs(timelineClip.sourceOut, projectFps),
                adjustmentParams = effectiveAdjustmentParams(timelineClip).takeIf { params ->
                    val gradeCurvesEnabled = timelineClip.colorGrade.enabled &&
                        timelineClip.colorGrade.curves != CurveParams()
                    (timelineClip.adjustments.enabled || gradeCurvesEnabled) &&
                        Media3ClipAdjustmentSupport.hasRenderableAdjustments(params)
                }
            )
        }
        val totalDurationUs = clipPlans.fold(0L) { totalUs, clip ->
            Math.addExact(totalUs, clip.endPositionUs - clip.startPositionUs)
        }
        val outputFrames = framesAtRate(totalDurationUs, settings.frameRate).coerceAtLeast(1L)
        val includeAudio = !track.isMuted && enabledClips.all { clip ->
            project.mediaAssets.first { it.assetId == clip.mediaId }.metadata.hasAudio
        }
        return ExportPlan(
            clips = clipPlans,
            outputFrameCount = outputFrames,
            includeAudio = includeAudio
        )
    }

    private fun effectiveAdjustmentParams(clip: TimelineClip): VideoAdjustmentParams {
        val gradeCurves = clip.colorGrade.curves
        val useGradeCurves = clip.colorGrade.enabled && gradeCurves != CurveParams()
        val params = if (clip.adjustments.enabled) {
            clip.adjustments.params
        } else {
            VideoAdjustmentParams.default()
        }
        return params.copy(curves = if (useGradeCurves) gradeCurves else params.curves)
    }

    private fun hasVideoEncoder(settings: ExportSettings): Boolean {
        val bitrateMode = when (settings.bitrateMode) {
            ExportSettings.BitrateMode.CBR -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
            ExportSettings.BitrateMode.AUTO,
            ExportSettings.BitrateMode.VBR,
            ExportSettings.BitrateMode.CRF -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
        }
        if (!settings.useHardwareEncoder) return false
        val query = VideoEncoderQuery(
            width = settings.resolutionWidth,
            height = settings.resolutionHeight,
            frameRate = settings.frameRate.floatValue,
            bitrate = settings.videoBitrate,
            bitrateMode = bitrateMode,
            hardwareAccelerated = settings.useHardwareEncoder
        )
        return videoEncoderSupportCache.computeIfAbsent(query) {
            runCatching {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { codecInfo ->
                    codecInfo.isEncoder &&
                        isHardwareAcceleratedEncoder(codecInfo) == query.hardwareAccelerated &&
                        codecInfo.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } &&
                        runCatching {
                            val capabilities = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                            val video = capabilities.videoCapabilities ?: return@runCatching false
                            val encoder = capabilities.encoderCapabilities ?: return@runCatching false
                            video.areSizeAndRateSupported(
                                query.width,
                                query.height,
                                query.frameRate.toDouble()
                            ) && video.bitrateRange.contains(query.bitrate) &&
                                encoder.isBitrateModeSupported(query.bitrateMode)
                        }.getOrDefault(false)
                }
            }.getOrDefault(false)
        }
    }

    private fun isHardwareAcceleratedEncoder(codecInfo: MediaCodecInfo): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            return codecInfo.isHardwareAccelerated
        }

        // MediaCodecInfo.isHardwareAccelerated is unavailable before API 29. Android's software
        // codecs use these stable names; unknown vendor encoders are treated as hardware-capable.
        val name = codecInfo.name.lowercase()
        return !name.startsWith("omx.google.") &&
            !name.startsWith("c2.android.") &&
            !name.contains("software")
    }

    private fun hasAudioEncoder(sampleRate: Int, channelCount: Int, bitrate: Int): Boolean {
        val query = AudioEncoderQuery(sampleRate, channelCount, bitrate)
        return audioEncoderSupportCache.computeIfAbsent(query) {
            runCatching {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { codecInfo ->
                    codecInfo.isEncoder && codecInfo.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_AUDIO_AAC, true) } &&
                        runCatching {
                            val capabilities = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC)
                            val audio = capabilities.audioCapabilities ?: return@runCatching false
                            audio.isSampleRateSupported(query.sampleRate) &&
                                query.channelCount <= audio.maxInputChannelCount &&
                                audio.bitrateRange.contains(query.bitrate)
                        }.getOrDefault(false)
                }
            }.getOrDefault(false)
        }
    }

    private fun hasEnabledGrade(grade: ColorGradeStack): Boolean = grade.enabled &&
        (grade.opacity != 1f || grade.blendMode != "NORMAL" || grade.keyframes.isNotEmpty() ||
            grade.inputTransform != LogTransformParams() ||
            // Basic primary corrections and curves are mirrored into VideoAdjustmentParams by the
            // editor and rendered by the shared GPU effect. Unsupported wheel values are rejected
            // by the clip-adjustment validator rather than silently discarded.
            hasExportUnsupportedQualifier(grade.hslAdjustments) ||
            grade.selectiveColor != SelectiveColorParams() ||
            grade.lutStack != LutGradeParams() || grade.colorMatch != ColorMatchParams() ||
            grade.skinToneProtection != SkinToneProtectionParams() || grade.hdrToneMapping != HdrToneMappingParams())

    private fun hasExportUnsupportedQualifier(params: HslQualifierParams): Boolean = params.enabled &&
        (params.showMatte || params.hueShift != 0f || params.saturation != 1f || params.luminance != 0f ||
            params.contrast != 0f || params.temperature != 0f || params.tint != 0f)

    private fun hasEnabledColorLayers(stack: ColorGradeLayerStack, clipGrade: ColorGradeStack): Boolean {
        if (!stack.enabled) return false
        val enabledLayers = stack.layers.filter { it.enabled }
        if (enabledLayers.size == 1) {
            val onlyLayer = enabledLayers.single()
            // The grading editor mirrors its active single primary node onto the clip grade;
            // render it once through the shared effect, not once per stored representation.
            if (onlyLayer.type == ColorLayerType.PRIMARY_CORRECTION && onlyLayer.opacity == 1f &&
                onlyLayer.grade == clipGrade
            ) return false
        }
        return enabledLayers.size > 1 || enabledLayers.any { layer ->
                layer.type != ColorLayerType.PRIMARY_CORRECTION || layer.opacity != 1f ||
                hasEnabledGrade(layer.grade) ||
                (layer.grade.enabled && (
                    layer.grade.primaryCorrections != ColorGradeParams() || layer.grade.curves != CurveParams()
                ))
        }
    }

    private fun frameRatesMatch(sourceRate: FrameRate, exportRate: com.example.timeline.export.model.ExportFrameRate): Boolean {
        if (sourceRate.numerator <= 0 || sourceRate.denominator <= 0) return false
        return abs(sourceRate.fpsAsFloat - exportRate.floatValue) <= FRAME_RATE_TOLERANCE_FPS
    }

    private fun framesToUs(frames: Long, frameRate: Rational): Long {
        require(frames >= 0L && frameRate.numerator > 0 && frameRate.denominator > 0)
        val numerator = BigInteger.valueOf(frames)
            .multiply(BigInteger.valueOf(1_000_000L))
            .multiply(BigInteger.valueOf(frameRate.denominator.toLong()))
        return numerator.divide(BigInteger.valueOf(frameRate.numerator.toLong())).longValueExact()
    }

    private fun framesAtRate(durationUs: Long, frameRate: com.example.timeline.export.model.ExportFrameRate): Long {
        if (durationUs <= 0L || frameRate.numerator <= 0 || frameRate.denominator <= 0) return 0L
        return BigInteger.valueOf(durationUs)
            .multiply(BigInteger.valueOf(frameRate.numerator.toLong()))
            .divide(BigInteger.valueOf(1_000_000L * frameRate.denominator.toLong()))
            .longValueExact()
    }

    private fun progress(
        jobId: String,
        status: ExportJobStatus,
        percent: Int,
        totalFrames: Long,
        startedAtMs: Long,
        stage: String
    ): ExportProgress {
        val elapsed = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
        val fraction = (percent / 100f).coerceIn(0f, 1f)
        val remaining = if (fraction > 0f && fraction < 1f) {
            ((elapsed / fraction) - elapsed).toLong().coerceAtLeast(0L)
        } else 0L
        val rendered = (totalFrames * fraction).toLong().coerceIn(0L, totalFrames)
        return ExportProgress(
            jobId = jobId,
            status = status,
            progressPercent = percent.toFloat(),
            renderedFrames = rendered,
            totalFrames = totalFrames,
            elapsedMs = elapsed,
            estimatedRemainingMs = remaining,
            currentStage = stage
        )
    }

    private companion object {
        const val PROGRESS_POLL_INTERVAL_MS = 250L
        const val FRAME_RATE_TOLERANCE_FPS = 0.01f
        val videoEncoderSupportCache = ConcurrentHashMap<VideoEncoderQuery, Boolean>()
        val audioEncoderSupportCache = ConcurrentHashMap<AudioEncoderQuery, Boolean>()
    }
}
