package com.example.timeline.engine.preview

import android.content.Context

object PreviewEngineFactory {
    fun createEngine(context: Context, type: PreviewEngineType = PreviewEngineType.AUTO): PreviewEngine {
        val targetType = if (type == PreviewEngineType.AUTO) PreviewEngineType.FFMPEG_NATIVE else type

        when (targetType) {
            PreviewEngineType.FFMPEG_NATIVE,
            PreviewEngineType.VLC_NATIVE -> {
                val nativeEngine = createLibVlcEngine(context, targetType)
                if (nativeEngine != null) return nativeEngine
                android.util.Log.w("PreviewEngineFactory", "FFmpeg/LibVLC unavailable; falling back to Media3")
                return com.example.timeline.engine.preview.Media3FallbackPreviewEngine(context)
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

    private fun createLibVlcEngine(context: Context, type: PreviewEngineType): PreviewEngine? {
        val engine = runCatching {
            Class.forName("org.videolan.libvlc.LibVLC")
            com.example.timeline.engine.preview.native.NativeVlcPreviewEngine(context, type)
        }.onFailure { error ->
            android.util.Log.e("PreviewEngineFactory", "FFmpeg/LibVLC initialization failed", error)
        }.getOrNull() ?: return null

        if (engine.currentState.value == PreviewState.ERROR) {
            engine.release()
            return null
        }
        return engine
    }
}
