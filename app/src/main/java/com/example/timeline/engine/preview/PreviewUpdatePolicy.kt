package com.example.timeline.engine.preview

import com.example.timeline.core.Rational
import com.example.timeline.core.TimelineClip

/**
 * A project edit can update the live grade without seeking only when the same timeline frame still
 * maps to the same source timestamp and the selected decoder/source are unchanged.
 */
internal fun canUpdatePreviewGradeWithoutSeek(
    previousClip: TimelineClip,
    updatedClip: TimelineClip,
    previousSource: PreviewMediaSource?,
    updatedSource: PreviewMediaSource?,
    previousFrameRate: Rational,
    updatedFrameRate: Rational,
    engineType: PreviewEngineType,
    updatedAssetIsMcraw: Boolean
): Boolean {
    if (previousClip.id != updatedClip.id || previousClip.mediaId != updatedClip.mediaId) return false
    if (previousClip.sourceIn != updatedClip.sourceIn || previousClip.timelineStart != updatedClip.timelineStart) return false
    if (previousSource == null || previousSource != updatedSource) return false
    if (previousFrameRate != updatedFrameRate) return false
    return (engineType == PreviewEngineType.MOTIONCAM_RAW) == updatedAssetIsMcraw
}
