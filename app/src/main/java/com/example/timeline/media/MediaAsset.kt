package com.example.timeline.media

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ProxyInfo(
    val uriString: String,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val isProxyGenerated: Boolean,
    val proxyFormat: String,
    val proxyMimeType: String
)

@Serializable
data class MediaAsset(
    val assetId: String = UUID.randomUUID().toString(),
    val projectId: String,
    val displayName: String,
    val originalUriString: String,
    val localOriginalUriString: String? = null,
    val persistedUriPermission: Boolean,
    val mimeType: String,
    val fileSizeBytes: Long,
    val importedAt: Long,
    val modifiedAt: Long,
    val assetType: MediaAssetType,
    val previewStatus: PreviewStatus = PreviewStatus.NOT_TESTED,
    val proxyStatus: ProxyStatus = ProxyStatus.NONE,
    val mediaErrorStatus: MediaErrorStatus = MediaErrorStatus.NONE,
    val mediaAvailabilityStatus: MediaAvailabilityStatus = MediaAvailabilityStatus.AVAILABLE_ORIGINAL_URI,
    val metadata: MediaMetadata,
    val proxyInfo: ProxyInfo? = null,
    val optimizedMediaInfo: ProxyInfo? = null,
    val thumbnailUri: String? = null,
    val waveformUri: String? = null,
    val userAssignedLogProfile: String? = null,
    val colorManagementMode: String? = null,
    val relinkFingerprint: String? = null,
    val errorMessage: String? = null,
    val technicalReport: ImportTechnicalReport? = null
)
