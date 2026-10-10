package com.example.timeline.export.pipeline

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

class MediaMuxerWriter(outputFilePath: String, format: Int) {
    private val muxer = MediaMuxer(outputFilePath, format)
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isStarted = false

    private data class CachedSample(
        val isVideo: Boolean,
        val buffer: ByteBuffer,
        val bufferInfo: MediaCodec.BufferInfo
    )
    private val cachedSamples = mutableListOf<CachedSample>()

    fun addVideoTrack(format: MediaFormat): Int {
        videoTrackIndex = muxer.addTrack(format)
        return videoTrackIndex
    }

    fun addAudioTrack(format: MediaFormat): Int {
        audioTrackIndex = muxer.addTrack(format)
        return audioTrackIndex
    }

    fun start() {
        if (!isStarted) {
            muxer.start()
            isStarted = true
            flushCache()
        }
    }

    private fun flushCache() {
        for (sample in cachedSamples) {
            if (sample.isVideo && videoTrackIndex >= 0) {
                muxer.writeSampleData(videoTrackIndex, sample.buffer, sample.bufferInfo)
            } else if (!sample.isVideo && audioTrackIndex >= 0) {
                muxer.writeSampleData(audioTrackIndex, sample.buffer, sample.bufferInfo)
            }
        }
        cachedSamples.clear()
    }

    fun writeVideo(byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (isStarted && videoTrackIndex >= 0) {
            muxer.writeSampleData(videoTrackIndex, byteBuf, bufferInfo)
        } else if (!isStarted) {
            val cloneBuf = ByteBuffer.allocateDirect(byteBuf.capacity())
            byteBuf.rewind()
            cloneBuf.put(byteBuf)
            cloneBuf.flip()
            
            val cloneInfo = MediaCodec.BufferInfo()
            cloneInfo.set(bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
            cachedSamples.add(CachedSample(true, cloneBuf, cloneInfo))
        }
    }

    fun writeAudio(byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (isStarted && audioTrackIndex >= 0) {
            muxer.writeSampleData(audioTrackIndex, byteBuf, bufferInfo)
        } else if (!isStarted) {
            val cloneBuf = ByteBuffer.allocateDirect(byteBuf.capacity())
            byteBuf.rewind()
            cloneBuf.put(byteBuf)
            cloneBuf.flip()
            
            val cloneInfo = MediaCodec.BufferInfo()
            cloneInfo.set(bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
            cachedSamples.add(CachedSample(false, cloneBuf, cloneInfo))
        }
    }

    fun stopAndRelease() {
        if (isStarted) {
            try {
                muxer.stop()
            } catch (e: Exception) {
                // Ignore exceptions on stop if no frames written
            }
        }
        muxer.release()
        isStarted = false
    }
}

