package com.example.timeline.export.pipeline

import com.example.timeline.export.native.NativeExportCore
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioMixer(private val sampleRate: Int, private val channelCount: Int) {
    // Basic accumulator buffer
    private var mixBuffer: ByteBuffer? = null
    
    fun allocateBuffer(numSamples: Int): ByteBuffer {
        val bytes = numSamples * channelCount * 2 // 16-bit PCM
        val buf = ByteBuffer.allocateDirect(bytes)
        buf.order(ByteOrder.nativeOrder())
        return buf
    }
    
    fun mixAndScale(srcBuffer: ByteBuffer, destBuffer: ByteBuffer, numSamples: Int, volume: Float) {
        if (NativeExportCore.isAvailable()) {
            NativeExportCore.nativeMixAudio(destBuffer, srcBuffer, numSamples * channelCount, volume)
        } else {
            // Software fallback
            val validSamples = minOf(
                 numSamples * channelCount,
                 srcBuffer.remaining() / 2,
                 destBuffer.remaining() / 2
            )
            val srcArray = ShortArray(validSamples)
            val destArray = ShortArray(validSamples)
            
            srcBuffer.position(0)
            srcBuffer.asShortBuffer().get(srcArray, 0, validSamples)
            
            destBuffer.position(0)
            destBuffer.asShortBuffer().get(destArray, 0, validSamples)
            
            for (i in 0 until validSamples) {
                var sample = (destArray[i] + srcArray[i] * volume).toInt()
                if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE.toInt()
                if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE.toInt()
                destArray[i] = sample.toShort()
            }
            
            destBuffer.position(0)
            destBuffer.asShortBuffer().put(destArray, 0, validSamples)
        }
    }
}
