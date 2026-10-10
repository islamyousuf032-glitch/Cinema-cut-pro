package com.example.timeline.engine.preview

import android.content.Context
import android.os.Build

object PreviewEngineFactory {
    fun createEngine(context: Context, type: PreviewEngineType = PreviewEngineType.AUTO): PreviewEngine {
        val targetType = if (type == PreviewEngineType.AUTO) PreviewEngineType.MEDIA3_FALLBACK else type
        
        when (targetType) {
            PreviewEngineType.VLC_NATIVE -> {
                try {
                    Class.forName("org.videolan.libvlc.LibVLC")
                    return com.example.timeline.engine.preview.native.NativeVlcPreviewEngine(context)
                } catch (e: Exception) {
                    android.util.Log.e("PreviewEngineFactory", "LibVLC not available: ${e.message}")
                }
            }
            PreviewEngineType.MEDIA3_FALLBACK -> {
                return com.example.timeline.engine.preview.Media3FallbackPreviewEngine(context)
            }
            PreviewEngineType.STILL_FRAME -> {
                return StillFramePreviewEngine(context)
            }
            PreviewEngineType.BROWSER -> {
                return com.example.timeline.engine.preview.browser.BrowserPreviewEngine(context)
            }
            PreviewEngineType.NATIVE_CPP -> {
                return com.example.timeline.engine.preview.native.NativeCppPreviewEngine()
            }
            else -> {}
        }
        
        return StillFramePreviewEngine(context)
    }
}
