package com.example.timeline.export.validation

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TrackType
import com.example.timeline.export.model.ExportSettings
import java.io.File
import java.util.Locale

/**
 * Runtime environment checks for the selected export backend.
 *
 * Codec, container, encoder, timeline-layout, and effect support are validated by the backend
 * itself. Keeping this class limited to source accessibility and storage avoids a second, generic
 * codec validator disagreeing with the Media3 backend used by both the export UI and worker.
 */
object ExportValidator {

    fun validate(context: Context, project: TimelineProject, settings: ExportSettings): List<ExportError> {
        val errors = mutableListOf<ExportError>()
        val activeVideoTrack = project.tracks.singleOrNull { track ->
            track.type == TrackType.VIDEO && track.isVisible && track.clips.any { it.isEnabled }
        }
        val enabledVideoClips = activeVideoTrack?.clips?.filter { it.isEnabled }.orEmpty()

        enabledVideoClips.forEach { clip ->
            val asset = project.mediaAssets.firstOrNull { it.assetId == clip.mediaId } ?: return@forEach
            val sourceUriString = asset.localOriginalUriString
                ?.takeIf { it.isNotBlank() }
                ?: asset.originalUriString
            if (!isSourceReadable(context, sourceUriString)) {
                errors += ExportError(
                    ExportErrorType.MISSING_MEDIA,
                    "Missing or unreadable media file: ${asset.displayName}"
                )
            }
        }

        val projectFps = project.settings.getFpsRational()
        val enabledDurationFrames = runCatching {
            enabledVideoClips.fold(0L) { total, clip -> Math.addExact(total, clip.duration) }
        }.getOrNull()
        if (enabledDurationFrames != null && enabledDurationFrames > 0L &&
            projectFps.numerator > 0 && projectFps.denominator > 0 && settings.videoBitrate > 0
        ) {
            val includesEmbeddedAudio = activeVideoTrack?.isMuted == false && enabledVideoClips.isNotEmpty() &&
                enabledVideoClips.all { clip ->
                    project.mediaAssets.firstOrNull { it.assetId == clip.mediaId }?.metadata?.hasAudio == true
                }
            val totalBitrate = settings.videoBitrate.toDouble() +
                if (includesEmbeddedAudio) settings.audioBitrate.toDouble() else 0.0
            val durationSeconds = enabledDurationFrames.toDouble() *
                projectFps.denominator.toDouble() / projectFps.numerator.toDouble()
            val estimatedSizeBytes = durationSeconds * totalBitrate / 8.0
            if (estimatedSizeBytes.isFinite() && estimatedSizeBytes > 0.0) {
                val outputDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: File(context.filesDir, Environment.DIRECTORY_MOVIES)
                val storageProbe = generateSequence(outputDirectory) { it.parentFile }
                    .firstOrNull { it.exists() && it.isDirectory }
                    ?: context.filesDir
                val usableSpace = storageProbe.usableSpace.toDouble()
                if (usableSpace < estimatedSizeBytes) {
                    errors += ExportError(
                        ExportErrorType.OUT_OF_STORAGE,
                        "Not enough storage space. Estimated: ${(estimatedSizeBytes / (1024.0 * 1024.0)).toLong()} MB, " +
                            "Available: ${storageProbe.usableSpace / (1024 * 1024)} MB"
                    )
                }
            }
        }

        return errors.distinctBy { it.type to it.message }
    }

    private fun isSourceReadable(context: Context, uriString: String): Boolean {
        if (uriString.isBlank()) return false
        val uri = Uri.parse(uriString)
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "content" -> runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
            "file" -> uri.path?.let(::File)?.let { it.isFile && it.canRead() } == true
            null -> File(uriString).let { it.isFile && it.canRead() }
            else -> false
        }
    }
}
