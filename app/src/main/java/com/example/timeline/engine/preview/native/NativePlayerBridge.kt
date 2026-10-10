package com.example.timeline.engine.preview.native

object NativePlayerBridge {
    init {
        try {
            System.loadLibrary("native_player")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("NativePlayerBridge", "Failed to load native_player", e)
        }
    }

    external fun nativeCreate(surface: android.view.Surface?): Long
    external fun nativeOpen(handle: Long, path: String): Int
    external fun nativePlay(handle: Long)
    external fun nativePause(handle: Long)
    external fun nativeSeek(handle: Long, timeUs: Long)
    external fun nativeSetPreviewMaxSize(handle: Long, maxWidth: Int, maxHeight: Int)
    external fun nativeSetFitMode(handle: Long, fitMode: Int)
    external fun nativeGetDurationUs(handle: Long): Long
    external fun nativeGetPositionUs(handle: Long): Long
    external fun nativeGetVideoWidth(handle: Long): Int
    external fun nativeGetVideoHeight(handle: Long): Int
    external fun nativeGetLastError(handle: Long): String
    external fun nativeRelease(handle: Long)
}
