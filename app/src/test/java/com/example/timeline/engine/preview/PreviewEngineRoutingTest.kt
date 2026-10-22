package com.example.timeline.engine.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewEngineRoutingTest {
    @Test
    fun automaticSelectionUsesMedia3AsPrimary() {
        assertEquals(
            PreviewEngineType.MEDIA3_FALLBACK,
            resolveRequestedPreviewEngine(PreviewEngineType.AUTO)
        )
    }

    @Test
    fun motionCamRawHasDedicatedNativePreviewRoute() {
        assertEquals(
            PreviewEngineType.MOTIONCAM_RAW,
            resolveRequestedPreviewEngine(PreviewEngineType.MOTIONCAM_RAW)
        )
    }

    @Test
    fun explicitLegacyLibVlcPreferenceRemainsAvailable() {
        assertEquals(
            PreviewEngineType.FFMPEG_NATIVE,
            resolveRequestedPreviewEngine(PreviewEngineType.FFMPEG_NATIVE)
        )
        assertEquals(
            PreviewEngineType.VLC_NATIVE,
            resolveRequestedPreviewEngine(PreviewEngineType.VLC_NATIVE)
        )
    }

    @Test
    fun liveGradeFallbackUsesTheRequestedEngineNotTheTemporaryActiveEngine() {
        assertEquals(true, requiresMedia3ForLiveGrade(PreviewEngineType.VLC_NATIVE, isMotionCamRaw = false))
        assertEquals(true, requiresMedia3ForLiveGrade(PreviewEngineType.FFMPEG_NATIVE, isMotionCamRaw = false))
        assertEquals(true, requiresMedia3ForLiveGrade(PreviewEngineType.BROWSER, isMotionCamRaw = false))
        assertEquals(false, requiresMedia3ForLiveGrade(PreviewEngineType.AUTO, isMotionCamRaw = false))
        assertEquals(false, requiresMedia3ForLiveGrade(PreviewEngineType.MEDIA3_FALLBACK, isMotionCamRaw = false))
        assertEquals(false, requiresMedia3ForLiveGrade(PreviewEngineType.VLC_NATIVE, isMotionCamRaw = true))
    }
}
