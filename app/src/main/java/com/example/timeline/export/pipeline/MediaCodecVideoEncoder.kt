package com.example.timeline.export.pipeline

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer

class MediaCodecVideoEncoder(
    private val width: Int,
    private val height: Int,
    private val bitRate: Int,
    private val frameRate: Int,
    private val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC
) {
    private var encoder: MediaCodec? = null

    fun prepare() {
        val format = MediaFormat.createVideoFormat(mimeType, width, height)
        // Using byte buffer input format to pass raw YUV frames
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        encoder = MediaCodec.createEncoderByType(mimeType)
        encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder?.start()
    }

    fun getEncoder(): MediaCodec? = encoder

    fun release() {
        encoder?.stop()
        encoder?.release()
        encoder = null
    }

    fun encodeFrame(yuvBuffer: ByteBuffer, presentationTimeUs: Long) {
        val codec = encoder ?: return
        val inputBufIndex = codec.dequeueInputBuffer(10000)
        if (inputBufIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputBufIndex)
            inputBuffer?.clear()
            inputBuffer?.put(yuvBuffer)
            codec.queueInputBuffer(
                inputBufIndex,
                0,
                yuvBuffer.limit(),
                presentationTimeUs,
                0
            )
        }
    }

    fun signalEndOfInputStream() {
        val codec = encoder ?: return
        val inputBufIndex = codec.dequeueInputBuffer(10000)
        if (inputBufIndex >= 0) {
            codec.queueInputBuffer(
                inputBufIndex,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }
    }
}
