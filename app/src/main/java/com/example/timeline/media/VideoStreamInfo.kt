package com.example.timeline.media

import kotlinx.serialization.Serializable

@Serializable
data class VideoStreamInfo(
    val codecName: String,
    val codecMime: String,
    val codecProfile: Int?,
    val codecLevel: Int?,
    val width: Int,
    val height: Int,
    val frameRate: FrameRate,
    val bitDepth: Int?,
    val chromaSubsampling: String?,
    val colorStandard: Int?,
    val colorTransfer: Int?,
    val colorRange: Int?,
    val hdrStaticMetadata: ByteArray? = null,
    val hdrDynamicMetadata: ByteArray? = null,
    val isHdr: Boolean,
    val isLog: Boolean,
    val detectedLogProfile: String?,
    val hardwareDecodeSupported: Boolean,
    val softwareDecodeSupported: Boolean,
    val requiresProxy: Boolean,
    val requiresTranscode: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VideoStreamInfo

        if (codecName != other.codecName) return false
        if (codecMime != other.codecMime) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = codecName.hashCode()
        result = 31 * result + codecMime.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}
