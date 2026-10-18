package com.example.timeline.engine.preview

import com.example.timeline.media.MediaAsset
import com.example.timeline.media.PreviewStatus
import com.example.timeline.media.ProxyStatus

object PreviewSourceResolver {

    fun resolvePlayableUri(asset: MediaAsset, preferProxy: Boolean): Pair<String?, Boolean> {
        val hasProxy = asset.proxyInfo != null && asset.proxyStatus == ProxyStatus.READY
        val isDirectFailed = asset.previewStatus == PreviewStatus.DIRECT_FAILED_RUNTIME
        val useProxy = hasProxy && (preferProxy || isDirectFailed)

        if (useProxy) {
            return Pair(asset.proxyInfo!!.uriString, true)
        }

        val localOriginalUriString = asset.localOriginalUriString
        val originalUriString = asset.originalUriString

        val uriToUse = if (!localOriginalUriString.isNullOrBlank()) {
            localOriginalUriString
        } else {
            originalUriString
        }

        return Pair(uriToUse, false)
    }

    fun resolveSource(asset: MediaAsset, preferProxy: Boolean): PreviewMediaSource? {
        val (uri, useProxy) = resolvePlayableUri(asset, preferProxy)
        if (uri.isNullOrBlank()) return null

        val mimeType = if (useProxy) {
            asset.proxyInfo?.proxyMimeType
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
