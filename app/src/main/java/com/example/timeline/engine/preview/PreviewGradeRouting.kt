package com.example.timeline.engine.preview

/**
 * Returns whether the selected non-RAW preview route needs the Media3 GPU graph for live grading.
 * This decision is based on the user's requested engine, not the engine currently active after a
 * temporary fallback; otherwise the Media3 handoff would flip the decision and immediately undo
 * itself when Compose re-runs its effect.
 */
internal fun requiresMedia3ForLiveGrade(
    engineToRestore: PreviewEngineType,
    isMotionCamRaw: Boolean
): Boolean {
    if (isMotionCamRaw) return false
    return engineToRestore !in setOf(
        PreviewEngineType.AUTO,
        PreviewEngineType.MEDIA3_FALLBACK,
        PreviewEngineType.PROXY_PREVIEW
    )
}
