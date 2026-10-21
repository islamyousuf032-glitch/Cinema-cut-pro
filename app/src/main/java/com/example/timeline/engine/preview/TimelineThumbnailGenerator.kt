package com.example.timeline.engine.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object TimelineThumbnailGenerator {
    
    private val activeExtractions = mutableMapOf<String, Deferred<Bitmap?>>()
    private val mutex = Mutex()
    private val extractionSemaphore = Semaphore(4) // Max 4 concurrent extractions
    
    suspend fun getOrGenerateThumbnail(
        context: Context, 
        assetId: String, 
        uriString: String, 
        timeUs: Long, 
        width: Int, 
        height: Int,
        mimeType: String? = null
    ): Bitmap? = coroutineScope {
        val key = TimelineThumbnailCache.generateKey(assetId, timeUs, width)
        val cached = TimelineThumbnailCache.get(key)
        if (cached != null) {
            return@coroutineScope cached
        }
        
        val deferred = mutex.withLock {
            var active = activeExtractions[key]
            if (active == null) {
                active = async(Dispatchers.IO) {
                    val bmp = extractFrame(context, uriString, timeUs, width, height, mimeType)
                    if (bmp != null) {
                        TimelineThumbnailCache.put(key, bmp)
                    }
                    mutex.withLock {
                        activeExtractions.remove(key)
                    }
                    bmp
                }
                activeExtractions[key] = active
            }
            active
        }
        
        deferred.await()
    }
    
    private suspend fun extractFrame(
        context: Context,
        uriString: String,
        timeUs: Long,
        targetWidth: Int,
        targetHeight: Int,
        mimeType: String?
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            extractionSemaphore.withPermit {
                var mcrawHandle = 0L
                var mmr: MediaMetadataRetriever? = null
                try {
                    val uri = Uri.parse(uriString)
                    if (com.example.timeline.media.MotionCamRawFormat.isCandidate(context, mimeType, uri)) {
                        mcrawHandle = com.example.timeline.media.NativeMcrawDecoder.open(context, uri)
                        val frameCount = com.example.timeline.media.NativeMcrawDecoder.frameCount(mcrawHandle)
                        if (frameCount <= 0) return@withPermit null
                        val targetNs = timeUs.coerceAtLeast(0L).coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
                        val rawTimestamps = LongArray(frameCount) { index ->
                            com.example.timeline.media.NativeMcrawDecoder.frameTimestampNs(mcrawHandle, index)
                        }
                        val timestampOrigin = rawTimestamps.firstOrNull() ?: 0L
                        val timestamps = LongArray(frameCount) { index ->
                            (rawTimestamps[index] - timestampOrigin).coerceAtLeast(0L)
                        }
                        var low = 0
                        var high = frameCount - 1
                        while (low < high) {
                            val middle = (low + high) ushr 1
                            if (timestamps[middle] < targetNs) low = middle + 1 else high = middle
                        }
                        val next = low
                        val previous = (next - 1).coerceAtLeast(0)
                        val selected = if (kotlin.math.abs(targetNs - timestamps[previous]) <= kotlin.math.abs(timestamps[next] - targetNs)) previous else next
                        val edge = maxOf(targetWidth, targetHeight, 64)
                        val nativeBitmap = com.example.timeline.media.NativeMcrawDecoder.decodeFrame(mcrawHandle, selected, edge)
                        val rotation = runCatching {
                            JSONObject(com.example.timeline.media.NativeMcrawDecoder.frameMetadataJson(mcrawHandle, selected))
                                .optInt("rotationDegrees", 0)
                                .let { ((it % 360) + 360) % 360 }
                        }.getOrDefault(0)
                        val decoded = if (rotation == 0) nativeBitmap else Bitmap.createBitmap(
                            nativeBitmap,
                            0,
                            0,
                            nativeBitmap.width,
                            nativeBitmap.height,
                            Matrix().apply { postRotate(rotation.toFloat()) },
                            true
                        )
                        val aspectRatio = decoded.width.toFloat() / decoded.height.toFloat().coerceAtLeast(1f)
                        val width: Int
                        val height: Int
                        if (targetWidth > 0 && targetHeight > 0) {
                            width = targetWidth
                            height = targetHeight
                        } else {
                            width = 120
                            height = (120 / aspectRatio).toInt().coerceAtLeast(1)
                        }
                        if (decoded.width == width && decoded.height == height) decoded
                        else Bitmap.createScaledBitmap(decoded, width, height, true)
                    } else {
                        mmr = MediaMetadataRetriever()
                        val path = if (uriString.startsWith("file://")) uriString.substring(7) else uriString
                        mmr.setDataSource(context, Uri.parse(path))
                        val frame = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (frame != null) {
                            val aspectRatio = frame.width.toFloat() / frame.height.toFloat()
                            var width = targetWidth
                            var height = targetHeight
                            if (targetWidth == 0 || targetHeight == 0) {
                                width = 120
                                height = (120 / aspectRatio).toInt()
                            }
                            Bitmap.createScaledBitmap(frame, width, height, true)
                        } else null
                    }
                } catch (e: Exception) {
                    if (com.example.BuildConfig.DEBUG) e.printStackTrace()
                    null
                } finally {
                    if (mcrawHandle != 0L) com.example.timeline.media.NativeMcrawDecoder.close(mcrawHandle)
                    mmr?.release()
                }
            }
        }
    }
}
