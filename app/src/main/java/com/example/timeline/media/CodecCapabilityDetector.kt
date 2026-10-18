package com.example.timeline.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import java.util.Locale

data class VideoDecoderSupport(
    val hardware: Boolean = false,
    val software: Boolean = false,
    val unknown: Boolean = false
) {
    val available: Boolean get() = hardware || software || unknown
}

/** Injectable capability seam so import policy can be unit-tested without device codecs. */
interface VideoDecoderCapabilitySource {
    fun decoderSupportForFormat(
        mimeType: String,
        width: Int,
        height: Int,
        frameRate: Float,
        profile: Int?,
        level: Int?
    ): VideoDecoderSupport
}

class CodecCapabilityDetector : VideoDecoderCapabilitySource {
    private val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos

    override fun decoderSupportForFormat(
        mimeType: String,
        width: Int,
        height: Int,
        frameRate: Float,
        profile: Int?,
        level: Int?
    ): VideoDecoderSupport {
        if (width <= 0 || height <= 0 || !frameRate.isFinite() || frameRate <= 0f) {
            return VideoDecoderSupport()
        }

        var hardware = false
        var software = false
        var unknown = false

        for (info in codecInfos) {
            if (info.isEncoder || !info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) continue

            val capabilities = try {
                info.getCapabilitiesForType(mimeType)
            } catch (_: Exception) {
                continue
            }
            val videoCaps = capabilities.videoCapabilities ?: continue
            if (!videoCaps.isSizeSupported(width, height)) continue
            if (!videoCaps.areSizeAndRateSupported(width, height, frameRate.toDouble())) continue

            var profileClassificationUnknown = false
            if (profile != null && level != null && profile > 0 && level > 0) {
                val declaredProfileLevels = capabilities.profileLevels
                if (declaredProfileLevels.isEmpty()) {
                    profileClassificationUnknown = true
                } else if (declaredProfileLevels.none { it.profile == profile && it.level >= level }) {
                    continue
                }
            }

            if (profileClassificationUnknown) {
                unknown = true
            } else {
                when (decoderKind(info)) {
                    DecoderKind.HARDWARE -> hardware = true
                    DecoderKind.SOFTWARE -> software = true
                    DecoderKind.UNKNOWN -> unknown = true
                }
            }
        }

        return VideoDecoderSupport(hardware = hardware, software = software, unknown = unknown)
    }

    fun hasDecoderForFormat(
        mimeType: String,
        width: Int,
        height: Int,
        frameRate: Float,
        profile: Int?,
        level: Int?
    ): Boolean = decoderSupportForFormat(mimeType, width, height, frameRate, profile, level).available

    private fun decoderKind(info: MediaCodecInfo): DecoderKind {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return when {
                info.isHardwareAccelerated -> DecoderKind.HARDWARE
                info.isSoftwareOnly -> DecoderKind.SOFTWARE
                else -> DecoderKind.UNKNOWN
            }
        }

        // Before API 29 Android did not expose the acceleration flags. Known platform software
        // codec names are filtered; vendor codec names remain a best-effort hardware estimate.
        val name = info.name.lowercase(Locale.US)
        return if (
            name.startsWith("omx.google.") || name.startsWith("c2.android.") ||
            name.startsWith("omx.ffmpeg.") || name.contains("software")
        ) DecoderKind.SOFTWARE else DecoderKind.HARDWARE
    }

    private enum class DecoderKind { HARDWARE, SOFTWARE, UNKNOWN }
}
