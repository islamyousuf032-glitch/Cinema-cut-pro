package com.example.timeline.export.native

import java.nio.ByteBuffer

class NativeExportFrame(
    val buffer: ByteBuffer,
    val width: Int,
    val height: Int,
    val format: Int // 0: RGBA8888, 1: planar I420 (Y, U, V)
)
