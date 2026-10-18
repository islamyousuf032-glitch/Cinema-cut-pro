package com.example.model.adjustments

import kotlinx.serialization.Serializable

@Serializable
data class AdjustmentPreset(
    val presetId: String,
    val name: String,
    val category: String,
    val params: VideoAdjustmentParams,
    val createdByUser: Boolean = false,
    val thumbnailPreview: String? = null
)
