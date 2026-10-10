package com.example.timeline.engine.preview

import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import com.example.timeline.core.transform.BlendMode

object BlendModeProcessor {
    fun toComposeBlendMode(mode: BlendMode): ComposeBlendMode {
        return when (mode) {
            BlendMode.NORMAL -> ComposeBlendMode.SrcOver
            BlendMode.MULTIPLY -> ComposeBlendMode.Multiply
            BlendMode.SCREEN -> ComposeBlendMode.Screen
            BlendMode.OVERLAY -> ComposeBlendMode.Overlay
            BlendMode.ADD -> ComposeBlendMode.Plus
            BlendMode.SUBTRACT -> ComposeBlendMode.Clear // Not a perfect match but standard
            BlendMode.DARKEN -> ComposeBlendMode.Darken
            BlendMode.LIGHTEN -> ComposeBlendMode.Lighten
            BlendMode.DIFFERENCE -> ComposeBlendMode.Difference
            BlendMode.SOFT_LIGHT -> ComposeBlendMode.Softlight
            else -> ComposeBlendMode.SrcOver
        }
    }
}
