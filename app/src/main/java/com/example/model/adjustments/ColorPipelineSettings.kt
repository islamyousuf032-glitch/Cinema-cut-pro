package com.example.model.adjustments

import kotlinx.serialization.Serializable

@Serializable
data class ColorPipelineSettings(
    val inputColorSpace: ColorSpaceProfile = ColorSpaceProfile.REC_709,
    val workingColorSpace: ColorSpaceProfile = ColorSpaceProfile.REC_2020_LINEAR,
    val outputColorSpace: ColorSpaceProfile = ColorSpaceProfile.REC_709,
    val inputLogProfile: LogProfile = LogProfile.NONE,
    val toneMappingMode: String = "HABLE", // None, Reinhard, Hable, ACES
    val hdrMode: Boolean = false,
    val preserveHDRMetadata: Boolean = true,
    val clampMode: String = "LUMINANCE" // Luminance, RGB, None
)
