package com.example.timeline.engine.performance

import android.util.LruCache
import com.example.timeline.core.transform.ClipTransform
import com.example.timeline.engine.NativeTransformMatrix

object NativeTransformCache {
    // Cache evaluations by frame and transform hash.
    // A more advanced system would use dirty flags or dependency tracking.
    
    private data class CacheKey(
        val frame: Long,
        val transformHash: Int
    )

    private val matrixCache = object : LruCache<CacheKey, NativeTransformMatrix>(200) {
        override fun sizeOf(key: CacheKey, value: NativeTransformMatrix): Int {
            return 1 // Each is small, measure by item count
        }
    }

    fun get(frame: Long, transform: ClipTransform): NativeTransformMatrix? {
        return matrixCache.get(CacheKey(frame, transform.hashCode()))
    }

    fun put(frame: Long, transform: ClipTransform, matrix: NativeTransformMatrix) {
        matrixCache.put(CacheKey(frame, transform.hashCode()), matrix)
    }
    
    fun clear() {
        matrixCache.evictAll()
    }
}
