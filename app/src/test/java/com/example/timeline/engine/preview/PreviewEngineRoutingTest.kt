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
}
