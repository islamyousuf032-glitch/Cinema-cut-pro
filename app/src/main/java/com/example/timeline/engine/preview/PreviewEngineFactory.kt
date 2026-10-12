package com.example.timeline.engine.preview

import android.content.Context

object PreviewEngineFactory {
    fun createEngine(context: Context, type: PreviewEngineType = PreviewEngineType.AUTO): PreviewEngine {
        val targetType = if (type == PreviewEngineType.AUTO) PreviewEngineType.FFMPEG_NATIVE else type

        when (targetType) {
            PreviewEngineType.FFMPEG_NATIVE,
            PreviewEngineType.VLC_NATIVE -> {
                if (isRunningUnderRobolectric(context)) {
                    if (com.example.BuildConfig.DEBUG) android.util.Log.d("PreviewEngineFactory", "Using Media3 in Robolectric; LibVLC is not loaded in JVM tests")
                    return com.example.timeline.engine.preview.Media3FallbackPreviewEngine(context)
                }
                val nativeEngine = createLibVlcEngine(context, targetType)
                if (nativeEngine != null) return nativeEngine
                if (com.example.BuildConfig.DEBUG) {
                    android.util.Log.d("PreviewEngineFactory", "LibVLC unavailable; falling back to Media3")
                }
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

    private fun isRunningUnderRobolectric(context: Context): Boolean = runCatching {
        Class.forName("org.robolectric.RobolectricTestRunner", false, context.classLoader)
    }.isSuccess

    private fun createLibVlcEngine(context: Context, type: PreviewEngineType): PreviewEngine? {
        val engine = runCatching {
            Class.forName("org.videolan.libvlc.LibVLC")
            com.example.timeline.engine.preview.native.NativeVlcPreviewEngine(context, type)
        }.onFailure { error ->
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("PreviewEngineFactory", "LibVLC initialization failed", error)
            }
        }.getOrNull() ?: return null

        if (engine.currentState.value == PreviewState.ERROR) {
            engine.release()
            return null
        }
        return engine
    }
}
