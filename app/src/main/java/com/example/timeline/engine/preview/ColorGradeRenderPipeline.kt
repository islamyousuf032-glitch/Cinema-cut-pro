package com.example.timeline.engine.preview

import com.example.model.adjustments.VideoAdjustmentParams
import com.example.timeline.core.TimelineProject
import com.example.timeline.engine.GradeEvaluationEngine
import com.example.model.adjustments.engine.VideoEffectGraph

/**
 * Handles the application of color grades to the real-time playback pipeline.
 */
class ColorGradeRenderPipeline(
    private val effectGraph: VideoEffectGraph
) {
    fun updateViewportGrade(project: TimelineProject, presentationTimeUs: Long, timelineFrame: Long) {
        val params = GradeEvaluationEngine.evaluateFrame(project, timelineFrame)
        effectGraph.setParams(params)
    }
}
