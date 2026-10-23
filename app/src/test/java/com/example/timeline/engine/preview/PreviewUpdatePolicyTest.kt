package com.example.timeline.engine.preview

import com.example.timeline.core.Rational
import com.example.timeline.core.TimelineClip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewUpdatePolicyTest {
    private fun clip(sourceIn: Long = 0L, timelineStart: Long = 0L) = TimelineClip(
        id = "clip",
        trackId = "video",
        mediaId = "asset",
        name = "clip.mp4",
        sourceIn = sourceIn,
        sourceOut = 100L,
        timelineStart = timelineStart
    )

    private val source = PreviewMediaSource("file:///source.mp4", "video/mp4", isProxy = false)
    private val fps = Rational(30, 1)

    @Test
    fun gradeOnlyProjectUpdateKeepsCurrentSourceAndDoesNotNeedASeek() {
        assertTrue(
            canUpdatePreviewGradeWithoutSeek(
                previousClip = clip(),
                updatedClip = clip(),
                previousSource = source,
                updatedSource = source,
                previousFrameRate = fps,
                updatedFrameRate = fps,
                engineType = PreviewEngineType.MEDIA3_FALLBACK,
                updatedAssetIsMcraw = false
            )
        )
    }

    @Test
    fun sourceMappingOrDecoderChangesRequireASeekOrReload() {
        val sameMapButDifferentSourceIn = canUpdatePreviewGradeWithoutSeek(
            previousClip = clip(sourceIn = 0L),
            updatedClip = clip(sourceIn = 10L),
            previousSource = source,
            updatedSource = source,
            previousFrameRate = fps,
            updatedFrameRate = fps,
            engineType = PreviewEngineType.MEDIA3_FALLBACK,
            updatedAssetIsMcraw = false
        )
        val rawDecoderMismatch = canUpdatePreviewGradeWithoutSeek(
            previousClip = clip(),
            updatedClip = clip(),
            previousSource = source,
            updatedSource = source,
            previousFrameRate = fps,
            updatedFrameRate = fps,
            engineType = PreviewEngineType.MOTIONCAM_RAW,
            updatedAssetIsMcraw = false
        )

        assertFalse(sameMapButDifferentSourceIn)
        assertFalse(rawDecoderMismatch)
    }
}
