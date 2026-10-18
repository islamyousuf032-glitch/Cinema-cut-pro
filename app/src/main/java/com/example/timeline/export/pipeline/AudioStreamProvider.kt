package com.example.timeline.export.pipeline

import android.content.Context
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioStreamProvider(
    private val context: Context,
    private val uri: Uri,
    val sampleRate: Int = 44100,
    val channelCount: Int = 2
) {
    private val source = AudioDecodeSource(context, uri)
    private var isPrepared = false
    private var residualBuffer: ByteBuffer? = null
    var isEos = false
        private set

    fun prepare(): Boolean {
        isPrepared = source.prepare()
        return isPrepared
    }

    fun seekTo(timeUs: Long) {
        source.seekTo(timeUs)
        residualBuffer = null
        isEos = false
    }

    // Pull exactly requested bytes. Returns number of bytes actually read.
    fun pullBytes(destArray: ByteArray, offset: Int, length: Int): Int {
        var bytesFilled = 0
        while (bytesFilled < length && !isEos) {
            // First take from residual
            if (residualBuffer != null && residualBuffer!!.hasRemaining()) {
                val needed = length - bytesFilled
                val available = residualBuffer!!.remaining()
                val toRead = minOf(needed, available)
                residualBuffer!!.get(destArray, offset + bytesFilled, toRead)
                bytesFilled += toRead
                if (!residualBuffer!!.hasRemaining()) {
                    residualBuffer = null
                }
            } else {
                // Fetch next frame
                val frame = source.getNextFrame(10000)
                if (frame != null) {
                    if (frame.isEndOfStream) {
                        isEos = true
                    }
                    if (frame.buffer != null && frame.buffer.remaining() > 0) {
                        val inputBytes = frame.buffer.remaining()
                        val arr = ByteArray(inputBytes)
                        frame.buffer.get(arr)
                        
                        val isMono = channelCount == 1

                        if (isMono) {
                            val cap = inputBytes * 2
                            residualBuffer = ByteBuffer.allocate(cap)
                            residualBuffer!!.order(ByteOrder.nativeOrder())
                            val stereoArr = ByteArray(cap)
                            // Duplicate samples: each 16-bit sample becomes two 16-bit samples
                            for (i in 0 until inputBytes step 2) {
                                if (i + 1 < inputBytes) {
                                    val b0 = arr[i]
                                    val b1 = arr[i + 1]
                                    val dstIdx = i * 2
                                    stereoArr[dstIdx] = b0
                                    stereoArr[dstIdx + 1] = b1
                                    stereoArr[dstIdx + 2] = b0
                                    stereoArr[dstIdx + 3] = b1
                                }
                            }
                            residualBuffer!!.put(stereoArr)
                        } else {
                            val cap = inputBytes
                            residualBuffer = ByteBuffer.allocate(cap)
                            residualBuffer!!.order(ByteOrder.nativeOrder())
                            residualBuffer!!.put(arr)
                        }
                        residualBuffer!!.flip()
                    }
                    source.releaseFrame(frame.bufferIndex)
                } else {
                    // Timeout or error, treat as EOS or silence
                    break
                }
            }
        }
        return bytesFilled
    }

    fun release() {
        source.release()
    }
}
