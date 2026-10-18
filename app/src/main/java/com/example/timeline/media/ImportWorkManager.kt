package com.example.timeline.media

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import androidx.work.WorkRequest
import java.io.File
import java.util.UUID

class ImportWorkManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun startProxyJob(asset: MediaAsset): UUID {
        val cacheDir = File(context.filesDir, "proxy_cache").apply { mkdirs() }
        val outputFile = File(cacheDir, "proxy_${asset.assetId}.mp4")
        
        val sourceUri = asset.localOriginalUriString ?: asset.originalUriString
        
        val inputData = Data.Builder()
            .putString(ProxyGenerationWorker.KEY_ASSET_ID, asset.assetId)
            .putString(ProxyGenerationWorker.KEY_SOURCE_URI, sourceUri)
            .putString(ProxyGenerationWorker.KEY_OUTPUT_URI, "file://" + outputFile.absolutePath)
            .putInt(ProxyGenerationWorker.KEY_SOURCE_WIDTH, asset.metadata.width)
            .putInt(ProxyGenerationWorker.KEY_SOURCE_HEIGHT, asset.metadata.height)
            .putBoolean(ProxyGenerationWorker.KEY_HAS_AUDIO, asset.metadata.hasAudio)
            .build()
            
        val request = OneTimeWorkRequestBuilder<ProxyGenerationWorker>()
            .setInputData(inputData)
            .build()
            
        workManager.enqueue(request)
        return request.id
    }
}
