package com.example.timeline.media

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.UUID

class ImportWorkManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    /** Builds the request separately so its id can be saved with the project before enqueueing. */
    fun createProxyRequest(asset: MediaAsset): OneTimeWorkRequest {
        val cacheDir = File(context.filesDir, "proxy_cache")
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) {
            throw IllegalStateException("Proxy output directory could not be created.")
        }
        val outputFile = File(cacheDir, "proxy_${asset.assetId}.mp4")
        val sourceUri = resolveSourceUri(asset)

        val inputData = Data.Builder()
            .putString(ProxyGenerationWorker.KEY_ASSET_ID, asset.assetId)
            .putString(ProxyGenerationWorker.KEY_SOURCE_URI, sourceUri)
            .putString(ProxyGenerationWorker.KEY_OUTPUT_URI, Uri.fromFile(outputFile).toString())
            .putInt(ProxyGenerationWorker.KEY_SOURCE_WIDTH, asset.metadata.width)
            .putInt(ProxyGenerationWorker.KEY_SOURCE_HEIGHT, asset.metadata.height)
            .putBoolean(ProxyGenerationWorker.KEY_HAS_AUDIO, asset.metadata.hasAudio)
            .putBoolean(ProxyGenerationWorker.KEY_SOURCE_HDR, asset.metadata.videoStreams.any { it.isHdr })
            .build()

        return OneTimeWorkRequestBuilder<ProxyGenerationWorker>()
            .setInputData(inputData)
            .addTag(proxyWorkTag(asset.projectId, asset.assetId))
            .build()
    }

    fun enqueueProxyRequest(request: OneTimeWorkRequest) {
        workManager.enqueue(request)
    }

    /** Kept as a convenience for callers that do not need to persist the request id first. */
    fun startProxyJob(asset: MediaAsset): UUID {
        val request = createProxyRequest(asset)
        enqueueProxyRequest(request)
        return request.id
    }

    private fun resolveSourceUri(asset: MediaAsset): String =
        ProxySourceUriResolver.resolve(
            asset.localOriginalUriString,
            asset.originalUriString
        ) { path ->
            val file = File(path)
            file.isFile && file.length() > 0L
        }

    companion object {
        fun proxyWorkTag(projectId: String, assetId: String): String =
            "proxy:$projectId:$assetId"
    }
}
