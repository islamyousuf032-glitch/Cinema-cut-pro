package com.example.timeline.engine.preview

import com.example.timeline.media.MediaAsset
import com.example.timeline.media.PreviewStatus
import com.example.timeline.media.ProxyStatus

object PreviewSourceResolver {

    fun resolvePlayableUri(
        asset: MediaAsset,
        preferProxy: Boolean,
        forceOriginal: Boolean = false
    ): Pair<String?, Boolean> {
        val hasProxy = asset.proxyInfo != null &&
            asset.proxyStatus == ProxyStatus.READY &&
            asset.proxyPlaybackFailure.isNullOrBlank()
        val isDirectFailed = asset.previewStatus == PreviewStatus.DIRECT_FAILED_RUNTIME
        val useProxy = !forceOriginal && hasProxy && (preferProxy || isDirectFailed)

        if (useProxy) {
            return Pair(asset.proxyInfo!!.uriString, true)
        }

        val localOriginalUriString = asset.localOriginalUriString
        val originalUriString = asset.originalUriString
        val uriToUse = if (!localOriginalUriString.isNullOrBlank()) localOriginalUriString else originalUriString
        return Pair(uriToUse, false)
    }

    fun resolveSource(
        asset: MediaAsset,
        preferProxy: Boolean,
        forceOriginal: Boolean = false
    ): PreviewMediaSource? {
        val (uri, useProxy) = resolvePlayableUri(asset, preferProxy, forceOriginal)
        if (uri.isNullOrBlank()) return null

        val mimeType = if (useProxy) {
            val proxyInfo = asset.proxyInfo
            val storedMimeType = proxyInfo?.proxyMimeType?.substringBefore(';')?.trim()
            // Older project files stored the elementary-stream codec (video/avc) in this field.
            // Media3 expects the container MIME for progressive MP4 sources.
            if (proxyInfo?.proxyFormat?.equals("mp4", ignoreCase = true) == true ||
                proxyInfo?.uriString?.substringBefore('?')?.endsWith(".mp4", ignoreCase = true) == true
            ) {
                "video/mp4"
            } else {
                storedMimeType
            }
        } else {
            asset.mimeType
        }

        return PreviewMediaSource(
            uri = uri,
            mimeType = mimeType,
            isProxy = useProxy
        )
    }
}
