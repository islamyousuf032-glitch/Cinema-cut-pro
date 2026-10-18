package com.example.timeline.engine.native.advanced

data class NativeClipLayoutRect(
    val id: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class NativeTrackLayoutRect(
    val trackIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class NativeRulerTick(
    val x: Float,
    val type: Int
)
