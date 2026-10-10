package com.example.timeline.export.pipeline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer

class AudioDecodeSource(
    private val context: Context,
    private val uri: Uri
) {
    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    var sampleRate: Int = 44100
        private set
    var channelCount: Int = 2
        private set

    fun prepare(): Boolean {
        extractor = MediaExtractor()
        try {
            extractor?.setDataSource(context, uri, null)
            val numTracks = extractor?.trackCount ?: 0
            for (i in 0 until numTracks) {
                val format = extractor?.getTrackFormat(i)
                val mime = format?.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    extractor?.selectTrack(i)
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                    decoder = MediaCodec.createDecoderByType(mime)
                    decoder?.configure(format, null, null, 0)
                    decoder?.start()
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun seekTo(timeUs: Long) {
        extractor?.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        decoder?.flush()
    }

    fun getNextFrame(timeoutUs: Long): DecodedFrame? {
        val ext = extractor ?: return null
        val dec = decoder ?: return null

        // Feed input to decoder
        val inputIndex = dec.dequeueInputBuffer(timeoutUs)
        if (inputIndex >= 0) {
            val inputBuf = dec.getInputBuffer(inputIndex)
            val sampleSize = if (inputBuf != null) ext.readSampleData(inputBuf, 0) else -1
            if (sampleSize < 0) {
                dec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                val presentationTimeUs = ext.sampleTime
                dec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                ext.advance()
            }
        }

        // Output from decoder
        val bufferInfo = MediaCodec.BufferInfo()
        val outputIndex = dec.dequeueOutputBuffer(bufferInfo, timeoutUs)
        if (outputIndex >= 0) {
            val outputBuf = dec.getOutputBuffer(outputIndex)
            val isEndOfStream = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
            
            return DecodedFrame(outputBuf, bufferInfo.presentationTimeUs, isEndOfStream, outputIndex)
        }
        return null
    }

    fun releaseFrame(index: Int) {
        decoder?.releaseOutputBuffer(index, false)
    }

    fun release() {
        decoder?.stop()
        decoder?.release()
        decoder = null

        extractor?.release()
        extractor = null
    }
}
