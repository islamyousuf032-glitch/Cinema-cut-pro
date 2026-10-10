package com.example.model.colorgrade

import kotlinx.serialization.Serializable

@Serializable
enum class ColorLayerType {
    PRIMARY_CORRECTION,
    CURVES,
    HSL,
    LUT,
    LOOK,
    MASKED_CORRECTION,
    ADJUSTMENT_LAYER
}
