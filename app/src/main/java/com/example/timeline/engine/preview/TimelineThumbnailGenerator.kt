package com.example.timeline.engine.preview

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
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
        height: Int
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
                    val bmp = extractFrame(context, uriString, timeUs, width, height)
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
    
    private suspend fun extractFrame(context: Context, uriString: String, timeUs: Long, targetWidth: Int, targetHeight: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            extractionSemaphore.withPermit {
                var mmr: MediaMetadataRetriever? = null
                try {
                    mmr = MediaMetadataRetriever()
                    val path = if (uriString.startsWith("file://")) uriString.substring(7) else uriString
                    mmr.setDataSource(context, Uri.parse(path))
                    val frame = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        val aspectRatio = frame.width.toFloat() / frame.height.toFloat()
                        var w = targetWidth
                        var h = targetHeight
                        if (targetWidth == 0 || targetHeight == 0) {
                            w = 120
                            h = (120 / aspectRatio).toInt()
                        }
                        Bitmap.createScaledBitmap(frame, w, h, true)
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } finally {
                    mmr?.release()
                }
            }
        }
    }
}
