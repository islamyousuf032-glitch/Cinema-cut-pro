package com.example.timeline.export.pipeline

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer

class AudioEncoder(
    private val sampleRate: Int,
    private val channels: Int,
    private val bitRate: Int,
    private val mimeType: String = MediaFormat.MIMETYPE_AUDIO_AAC
) {
    private var encoder: MediaCodec? = null

    fun prepare() {
        val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channels)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)

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

    fun encodeFrame(pcmBuffer: ByteBuffer, presentationTimeUs: Long) {
        val codec = encoder ?: return
        val inputBufIndex = codec.dequeueInputBuffer(10000)
        if (inputBufIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputBufIndex)
            inputBuffer?.clear()
            inputBuffer?.put(pcmBuffer)
            codec.queueInputBuffer(
                inputBufIndex,
                0,
                pcmBuffer.limit(),
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
