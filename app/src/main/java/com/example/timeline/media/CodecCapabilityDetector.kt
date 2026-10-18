package com.example.timeline.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList

class CodecCapabilityDetector {
    private val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
    private val codecInfos = codecList.codecInfos

    fun hasDecoderForFormat(
        mimeType: String,
        width: Int,
        height: Int,
        frameRate: Float,
        profile: Int?,
        level: Int?
    ): Boolean {
        for (info in codecInfos) {
            if (info.isEncoder) continue
            val types = info.supportedTypes
            if (!types.contains(mimeType)) continue

            val capabilities = try {
                info.getCapabilitiesForType(mimeType)
            } catch (e: Exception) {
                continue
            }

            val videoCaps = capabilities.videoCapabilities
            if (videoCaps != null) {
                if (!videoCaps.isSizeSupported(width, height)) continue
                if (!videoCaps.areSizeAndRateSupported(width, height, frameRate.toDouble())) continue
            }

            if (profile != null && level != null && profile > 0 && level > 0) {
                val profileLevelSupported = capabilities.profileLevels.any {
                    it.profile == profile && it.level >= level
                }
                if (!profileLevelSupported) continue
            }

            return true
        }
        return false
    }
}
