package com.example.timeline.export.validation

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import com.example.timeline.core.TimelineProject
import com.example.timeline.export.model.ExportSettings
import com.example.timeline.export.model.ExportCodec
import com.example.timeline.export.model.ExportContainer
import java.io.File

object ExportValidator {

    fun validate(context: Context, project: TimelineProject, settings: ExportSettings): List<ExportError> {
        val errors = mutableListOf<ExportError>()

        // 1. Project has timeline clips
        val hasClips = project.tracks.any { it.clips.isNotEmpty() }
        if (!hasClips) {
            errors.add(ExportError(ExportErrorType.MISSING_MEDIA, "Timeline is empty. No clips to export."))
        }

        // 2 & 3. Media files exist
        project.tracks.flatMap { it.clips }.forEach { clip ->
            val assetId = clip.mediaId
            val asset = project.mediaAssets.find { it.assetId == assetId }
            if (asset != null) {
                // If it's a content URI, we assume it exists unless we try to open it. 
                // We will check for file scheme or absolute path.
                val uriString = asset.localOriginalUriString ?: asset.originalUriString
                val uri = Uri.parse(uriString)
                val sourceFile = when (uri.scheme?.lowercase()) {
                    "content" -> null
                    "file" -> uri.path?.let(::File)
                    else -> File(uriString)
                }
                val sourceAvailable = if (uri.scheme.equals("content", ignoreCase = true)) {
                    runCatching {
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                    }.getOrDefault(false)
                } else {
                    sourceFile?.isFile == true && sourceFile.canRead()
                }
                if (!sourceAvailable) {
                    errors.add(ExportError(ExportErrorType.MISSING_MEDIA, "Missing or unreadable media file: ${asset.displayName}"))
                }
            }
        }

        // 4. Output path writable
        // We will assume that if we can create a temporary file, it's writable
        // Usually, output file is provided or outputDirectoryUri.
        
        // 5. Codec supported
        if (!isCodecSupported(settings.codec.name)) {
            errors.add(ExportError(ExportErrorType.CODEC_UNSUPPORTED, "Codec ${settings.codec.name} is not supported on this device."))
        }

        // 6. Container supported
        if (settings.container != ExportContainer.MP4) {
             errors.add(ExportError(ExportErrorType.CONTAINER_UNSUPPORTED, "Only MP4 container is currently supported."))
        }

        // 7. Resolution valid
        if (settings.resolutionWidth <= 0 || settings.resolutionHeight <= 0) {
            errors.add(ExportError(ExportErrorType.UNKNOWN, "Invalid resolution: ${settings.resolutionWidth}x${settings.resolutionHeight}"))
        }

        // 8. FPS valid
        if (settings.frameRate.floatValue <= 0f) {
            errors.add(ExportError(ExportErrorType.UNKNOWN, "Invalid frame rate: ${settings.frameRate.floatValue}"))
        }

        // 9. Bitrate valid
        if (settings.videoBitrate <= 0) {
            errors.add(ExportError(ExportErrorType.UNKNOWN, "Invalid video bitrate: ${settings.videoBitrate}"))
        }

        // 11. HDR/alpha support available
        if (settings.exportHDR) {
            // Need to check HDR support via MediaCodecInfo, simplifying for now
            // errors.add(ExportError(ExportErrorType.CODEC_UNSUPPORTED, "HDR export not fully supported on this device yet."))
        }

        // 12. The Media3 path writes to app-scoped Movies storage; no native export library is
        // required for this bounded H.264/MP4 adapter.

        // 13. Estimate duration in seconds from the project's frame rate, then compare against the
        // same app-scoped volume used by ExportScreen.
        val projectFps = project.settings.getFpsRational()
        val maxTimelineEnd = project.tracks.flatMap { it.clips }.maxOfOrNull { it.timelineEnd } ?: 0L
        val durationSeconds = if (projectFps.numerator > 0 && projectFps.denominator > 0) {
            maxTimelineEnd.toDouble() * projectFps.denominator / projectFps.numerator
        } else {
            0.0
        }
        val estimatedSizeBytes = durationSeconds * (settings.videoBitrate + settings.audioBitrate) / 8.0
        val outputDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, Environment.DIRECTORY_MOVIES)
        val storageProbe = generateSequence(outputDirectory) { it.parentFile }
            .firstOrNull { it.exists() && it.isDirectory }
            ?: context.filesDir
        val usableSpace = storageProbe.usableSpace
        if (usableSpace < estimatedSizeBytes) {
            errors.add(ExportError(ExportErrorType.OUT_OF_STORAGE, "Not enough storage space. Estimated: ${(estimatedSizeBytes / (1024 * 1024)).toLong()} MB, Available: ${usableSpace / (1024 * 1024)} MB"))
        }

        return errors
    }

    private fun isCodecSupported(codecName: String): Boolean {
        val mimeType = when (codecName) {
            "H264" -> MediaFormat.MIMETYPE_VIDEO_AVC
            "HEVC" -> MediaFormat.MIMETYPE_VIDEO_HEVC
            "AV1" -> "video/av01"
            else -> return false
        }
        
        try {
            // Updated to non-deprecated method
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) {
                    val types = codecInfo.supportedTypes
                    for (type in types) {
                        if (type.equals(mimeType, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
