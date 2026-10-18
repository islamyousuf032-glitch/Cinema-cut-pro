package com.example.timeline.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MediaAssetRepository {
    private val _assets = MutableStateFlow<List<MediaAsset>>(emptyList())
    val assets: StateFlow<List<MediaAsset>> = _assets.asStateFlow()

    fun addAsset(asset: MediaAsset) {
        _assets.update { it + asset }
    }

    fun updateAsset(asset: MediaAsset) {
        _assets.update { list ->
            list.map { if (it.assetId == asset.assetId) asset else it }
        }
    }

    fun removeAsset(assetId: String) {
        _assets.update { list ->
            list.filter { it.assetId != assetId }
        }
    }

    fun getAssetById(assetId: String): MediaAsset? {
        return _assets.value.find { it.assetId == assetId }
    }

    fun listAssets(): List<MediaAsset> {
        return _assets.value
    }

    fun updateProxyInfo(assetId: String, proxyInfo: ProxyInfo?) {
        val asset = getAssetById(assetId) ?: return
        updateAsset(asset.copy(proxyInfo = proxyInfo))
    }
}
