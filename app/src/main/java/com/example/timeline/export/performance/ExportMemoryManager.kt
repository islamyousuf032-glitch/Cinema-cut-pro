package com.example.timeline.export.performance

import android.util.Log
import java.nio.ByteBuffer

object ExportMemoryManager {
    private const val TAG = "ExportMemoryManager"
    
    // We can keep a pool of ByteBuffers to avoid allocation overhead during export
    private val bufferPool = mutableListOf<ByteBuffer>()
    private val maxPoolSize = 5 
    
    @Synchronized
    fun obtainBuffer(capacity: Int): ByteBuffer {
        val iterator = bufferPool.iterator()
        while (iterator.hasNext()) {
            val buffer = iterator.next()
            if (buffer.capacity() >= capacity) {
                iterator.remove()
                buffer.clear()
                return buffer
            }
        }
        
        // If not found in pool, allocate new
        Log.d(TAG, "Allocating new ByteBuffer, capacity: \$capacity")
        return ByteBuffer.allocateDirect(capacity)
    }
    
    @Synchronized
    fun releaseBuffer(buffer: ByteBuffer) {
        if (bufferPool.size < maxPoolSize) {
            buffer.clear()
            bufferPool.add(buffer)
        }
        // If pool is full, let GC handle it
    }
    
    @Synchronized
    fun clearPool() {
        bufferPool.clear()
        System.gc() // Suggest GC run since we're dropping direct buffers
    }
}
