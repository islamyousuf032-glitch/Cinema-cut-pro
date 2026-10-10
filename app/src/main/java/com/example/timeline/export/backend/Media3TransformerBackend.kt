package com.example.timeline.export.backend

import android.content.Context
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
import com.example.timeline.export.native.NativeExportCore
import com.example.timeline.media.FrameRate
import com.example.timeline.media.MediaAsset
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
 * Media3 owns Android demux/decode/encode/mux orchestration. This adapter only accepts a single,
 * unlayered CFR video clip whose edits can be represented exactly by a trim and a presentation
 * resize. Everything else is rejected before export; it is never handed to one of the placeholder
 * exporters.
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

    private data class ExportPlan(
        val sourceUri: Uri,
        val startPositionUs: Long,
        val endPositionUs: Long,
        val outputFrameCount: Long,
        val includeAudio: Boolean,
        val adjustmentParams: VideoAdjustmentParams?
    )

    private data class ActiveExport(
        val transformer: Transformer,
        val listener: Transformer.Listener
    )

    private data class VideoEncoderQuery(
        val width: Int,
        val height: Int,
        val frameRate: Float,
        val bitrate: Int
    )

    private data class AudioEncoderQuery(val sampleRate: Int, val channelCount: Int)

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
        supportsVideoComposition = false,
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

            val sourceItem = MediaItem.Builder()
                .setUri(plan.sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(plan.startPositionUs)
                        .setEndPositionUs(plan.endPositionUs)
                        .build()
                )
                .build()
            val videoEffects = buildList<Effect> {
                plan.adjustmentParams?.let { add(createMedia3ClipAdjustmentEffect(it)) }
                add(
                    Presentation.createForWidthAndHeight(
                        job.settings.resolutionWidth,
                        job.settings.resolutionHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
            }
            val editedItem = EditedMediaItem.Builder(sourceItem)
                .setRemoveAudio(!plan.includeAudio)
                .setEffects(Effects(emptyList(), videoEffects))
                .build()

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
                builtTransformer.start(editedItem, targetOutputFile.absolutePath)
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
            settings.frameRate.floatValue > capabilities.maxFps
        ) {
            errors += "The requested output frame rate is invalid or exceeds the adapter limit."
        }
        if (settings.videoBitrate <= 0) errors += "The target video bitrate must be positive."
        if (settings.audioBitrate <= 0) errors += "The target audio bitrate must be positive."
        if (settings.keyframeInterval <= 0) errors += "The keyframe interval must be positive."
        if (settings.bitrateMode == ExportSettings.BitrateMode.CRF) {
            errors += "CRF bitrate mode is not supported by this Android encoder adapter."
        }
        if (settings.profile != null || settings.level != null) {
            errors += "Custom H.264 profile and level selection is not supported by this export path."
        }
        if (!settings.useHardwareEncoder) errors += "This export path requires the Android platform encoder."
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

        val enabledVisibleVideoClips = project.tracks
            .filter { it.type == TrackType.VIDEO && it.isVisible }
            .flatMap { track -> track.clips.filter { it.isEnabled }.map { track to it } }
        if (enabledVisibleVideoClips.size != 1) {
            errors += "Exactly one enabled clip on a visible video track is supported."
        }

        val activeTrackClip = enabledVisibleVideoClips.singleOrNull()?.let { (track, clip) ->
            val asset = project.mediaAssets.firstOrNull { it.assetId == clip.mediaId }
            if (asset != null) ActiveClip(clip, track, asset) else null
        }
        if (activeTrackClip == null && enabledVisibleVideoClips.size == 1) {
            errors += "The active video clip is missing its imported media asset."
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

        val clipData = activeTrackClip
        if (clipData != null) {
            val (clip, track, asset) = clipData
            if (clip.type != ClipType.MEDIA) errors += "Only a regular media clip can be exported by this path."
            if (clip.timelineStart != 0L) errors += "The supported clip must start at timeline frame zero."
            if (clip.duration <= 0L) errors += "The active clip has no duration."
            if (!asset.metadata.hasVideo || asset.assetType != com.example.timeline.media.MediaAssetType.VIDEO) {
                errors += "The active timeline clip must reference imported video media."
            }
            if (asset.metadata.durationUs <= 0L) errors += "The imported video has no usable duration metadata."
            if (asset.metadata.isVariableFrameRate) {
                errors += "Variable-frame-rate source video is not supported until frame-accurate retiming is implemented."
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
            val adjustmentStack = clip.adjustments
            val nativeAdjustmentEngineAvailable =
                !Media3ClipAdjustmentSupport.hasRenderableAdjustments(adjustmentStack) || NativeExportCore.isAvailable()
            errors += Media3ClipAdjustmentSupport.validationErrors(
                adjustmentStack,
                nativeEngineAvailable = nativeAdjustmentEngineAvailable
            )
            if (hasEnabledGrade(clip.colorGrade) || hasEnabledColorLayers(clip.colorLayers)) {
                errors += "Clip color grades and LUT layers are not supported by this export path yet."
            }

            val projectFps = project.settings.getFpsRational()
            if (projectFps.numerator <= 0 || projectFps.denominator <= 0) {
                errors += "The project frame rate must be valid before exporting."
            } else if (clip.sourceIn < 0L || clip.sourceOut < 0L) {
                errors += "The clip trim range cannot use negative source frames."
            } else {
                val trimRange = runCatching {
                    framesToUs(clip.sourceIn, projectFps) to framesToUs(clip.sourceOut, projectFps)
                }.getOrNull()
                if (trimRange == null) {
                    errors += "The clip trim range is too large to represent safely."
                } else {
                    val (sourceInUs, sourceOutUs) = trimRange
                    val frameToleranceUs = ((1_000_000.0 / projectFps.toDouble()).roundToLong()).coerceAtLeast(1L)
                    if (sourceOutUs <= sourceInUs || sourceOutUs > asset.metadata.durationUs + frameToleranceUs) {
                        errors += "The clip trim range is outside the imported video's duration."
                    }
                }
            }

            val expectedSourceFrameRate = asset.metadata.exactFrameRate ?: asset.metadata.estimatedFrameRate
            if (expectedSourceFrameRate == null || !frameRatesMatch(expectedSourceFrameRate, settings.frameRate)) {
                errors += "Media3 preserves the source frame rate; set export FPS to match the constant-frame-rate source."
            }

            val sourceUriString = asset.localOriginalUriString?.takeIf { it.isNotBlank() } ?: asset.originalUriString
            if (sourceUriString.isBlank()) errors += "The imported video has no readable source URI."

            val includeAudio = asset.metadata.hasAudio && !track.isMuted
            if (includeAudio) {
                if (asset.metadata.audioStreams.size != 1) {
                    errors += "Exactly one embedded audio stream is supported; separate audio editing is not supported."
                } else {
                    val audio = asset.metadata.audioStreams.single()
                    if (audio.sampleRate != settings.audioSampleRate || audio.channelCount != settings.audioChannels) {
                        errors += "For this export path, AAC sample rate and channels must match the source audio (${audio.sampleRate} Hz, ${audio.channelCount} channel(s))."
                    }
                    if (audio.channelCount !in 1..2) {
                        errors += "Only mono or stereo embedded audio is supported by this export path."
                    }
                    if (!hasAudioEncoder(settings.audioSampleRate, settings.audioChannels)) {
                        errors += "No Android AAC encoder supports the requested source audio format."
                    }
                }
            }

            if (!hasVideoEncoder(settings)) {
                errors += "No Android H.264 encoder supports the requested resolution, frame rate, and bitrate."
            }
        }

        return errors.distinct()
    }

    private fun collectValidationWarnings(settings: ExportSettings, project: TimelineProject): List<String> {
        val warnings = mutableListOf<String>()
        if (settings.useHardwareEncoder) {
            warnings += "Media3 selects a compatible Android codec; this adapter cannot guarantee hardware acceleration."
        }
        if (settings.bitrateMode in setOf(ExportSettings.BitrateMode.AUTO, ExportSettings.BitrateMode.VBR) &&
            settings.maxBitrate > 0 && settings.maxBitrate != settings.videoBitrate
        ) {
            warnings += "Media3 requests the target average bitrate; a separate maximum bitrate is not independently enforced by this adapter."
        }
        val activePair = project.tracks
            .filter { it.type == TrackType.VIDEO && it.isVisible }
            .flatMap { track -> track.clips.filter { it.isEnabled }.map { track to it } }
            .singleOrNull()
        val activeClip = activePair?.second
        if (activeClip != null && Media3ClipAdjustmentSupport.hasRenderableAdjustments(activeClip.adjustments)) {
            warnings += "Clip color adjustments use native CPU frame processing and can increase export time, especially at high resolution."
        }
        val asset = activeClip?.let { clip -> project.mediaAssets.firstOrNull { it.assetId == clip.mediaId } }
        if (asset?.metadata?.exactFrameRate == null && asset?.metadata?.estimatedFrameRate != null) {
            warnings += "The source frame rate is estimated from import metadata; verify the source is constant-frame-rate."
        }
        return warnings
    }

    private fun createPlanOrThrow(settings: ExportSettings, project: TimelineProject): ExportPlan {
        val errors = collectValidationErrors(settings, project)
        require(errors.isEmpty()) { errors.joinToString("\n") }
        val clip = project.tracks
            .asSequence()
            .filter { it.type == TrackType.VIDEO && it.isVisible }
            .flatMap { track -> track.clips.asSequence().filter { it.isEnabled }.map { track to it } }
            .single()
        val track = clip.first
        val timelineClip = clip.second
        val asset = project.mediaAssets.single { it.assetId == timelineClip.mediaId }
        val sourceUriString = asset.localOriginalUriString?.takeIf { it.isNotBlank() } ?: asset.originalUriString
        val parsedUri = Uri.parse(sourceUriString)
        val sourceUri = if (parsedUri.scheme == null) Uri.fromFile(File(sourceUriString)) else parsedUri
        val projectFps = project.settings.getFpsRational()
        val startUs = framesToUs(timelineClip.sourceIn, projectFps)
        val endUs = framesToUs(timelineClip.sourceOut, projectFps)
        val includeAudio = asset.metadata.hasAudio && !track.isMuted
        val durationUs = endUs - startUs
        val outputFrames = framesAtRate(durationUs, settings.frameRate).coerceAtLeast(1L)
        return ExportPlan(
            sourceUri = sourceUri,
            startPositionUs = startUs,
            endPositionUs = endUs,
            outputFrameCount = outputFrames,
            includeAudio = includeAudio,
            adjustmentParams = timelineClip.adjustments.params.takeIf {
                Media3ClipAdjustmentSupport.hasRenderableAdjustments(timelineClip.adjustments)
            }
        )
    }

    private fun hasVideoEncoder(settings: ExportSettings): Boolean {
        val query = VideoEncoderQuery(
            settings.resolutionWidth,
            settings.resolutionHeight,
            settings.frameRate.floatValue,
            settings.videoBitrate
        )
        return videoEncoderSupportCache.computeIfAbsent(query) {
            runCatching {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { codecInfo ->
                    codecInfo.isEncoder && codecInfo.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } &&
                        runCatching {
                            val capabilities = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                            val video = capabilities.videoCapabilities ?: return@runCatching false
                            video.areSizeAndRateSupported(
                                query.width,
                                query.height,
                                query.frameRate.toDouble()
                            ) && video.bitrateRange.contains(query.bitrate)
                        }.getOrDefault(false)
                }
            }.getOrDefault(false)
        }
    }

    private fun hasAudioEncoder(sampleRate: Int, channelCount: Int): Boolean {
        val query = AudioEncoderQuery(sampleRate, channelCount)
        return audioEncoderSupportCache.computeIfAbsent(query) {
            runCatching {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { codecInfo ->
                    codecInfo.isEncoder && codecInfo.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_AUDIO_AAC, true) } &&
                        runCatching {
                            val audio = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC).audioCapabilities
                                ?: return@runCatching false
                            audio.isSampleRateSupported(query.sampleRate) && query.channelCount <= audio.maxInputChannelCount
                        }.getOrDefault(false)
                }
            }.getOrDefault(false)
        }
    }

    private fun hasEnabledGrade(grade: ColorGradeStack): Boolean = grade.enabled &&
        (grade.opacity != 1f || grade.blendMode != "NORMAL" || grade.keyframes.isNotEmpty() ||
            grade.inputTransform != LogTransformParams() ||
            grade.primaryCorrections != ColorGradeParams() || grade.curves != CurveParams() ||
            grade.hslAdjustments != HslQualifierParams() || grade.selectiveColor != SelectiveColorParams() ||
            grade.lutStack != LutGradeParams() || grade.colorMatch != ColorMatchParams() ||
            grade.skinToneProtection != SkinToneProtectionParams() || grade.hdrToneMapping != HdrToneMappingParams())

    private fun hasEnabledColorLayers(stack: ColorGradeLayerStack): Boolean {
        if (!stack.enabled) return false
        return stack.layers.any { layer ->
            layer.enabled && (layer.type != ColorLayerType.PRIMARY_CORRECTION || layer.opacity != 1f || hasEnabledGrade(layer.grade))
        } || stack.layers.count { it.enabled } > 1
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
