package com.example.timeline.export.pipeline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer

class ExportDecodeSource(
    private val context: Context,
    private val uri: Uri
) {
    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    private var videoTrackIndex = -1

    fun prepare(): Boolean {
        extractor = MediaExtractor()
        try {
            extractor?.setDataSource(context, uri, null)
            val numTracks = extractor?.trackCount ?: 0
            for (i in 0 until numTracks) {
                val format = extractor?.getTrackFormat(i)
                val mime = format?.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    extractor?.selectTrack(i)
                    
                    decoder = MediaCodec.createDecoderByType(mime)
                    // We decode to YUV buffer not surface for now
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

        var isEOS = false
        // Feed input to decoder
        val inputIndex = dec.dequeueInputBuffer(timeoutUs)
        if (inputIndex >= 0) {
            val inputBuf = dec.getInputBuffer(inputIndex)
            val sampleSize = if (inputBuf != null) ext.readSampleData(inputBuf, 0) else -1
            if (sampleSize < 0) {
                dec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                isEOS = true
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
            
            // To simplify, we return the buffer directly. In reality, caller must process and then release.
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

data class DecodedFrame(
    val buffer: ByteBuffer?,
    val presentationTimeUs: Long,
    val isEndOfStream: Boolean,
    val bufferIndex: Int
)
