package com.example.timeline.export.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportColorSettings(
    val colorOutput: ColorOutput = ColorOutput.Rec709,
    val toneMappingMode: ToneMappingMode = ToneMappingMode.STANDARD
) {
    enum class ColorOutput {
        Rec709,
        Rec2020,
        HLG,
        PQ
    }

    enum class ToneMappingMode {
        STANDARD,
        HDR_TO_SDR,
        NONE
    }
}
