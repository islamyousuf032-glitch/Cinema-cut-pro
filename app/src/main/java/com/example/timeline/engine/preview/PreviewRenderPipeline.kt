package com.example.timeline.engine.preview

import android.graphics.Bitmap
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.adjustments.engine.CpuFrameProcessor
import com.example.timeline.core.TimelineProject
import com.example.timeline.engine.GradeEvaluationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders grades onto standalone preview frames for UI thumbnails or scopes.
 */
object PreviewRenderPipeline {

    suspend fun renderPreviewFrame(
        project: TimelineProject,
        timelineFrame: Long,
        rawBitmap: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        // 4, 5, 6: Evaluate full grade stack
        val evaluatedParams = GradeEvaluationEngine.evaluateFrame(project, timelineFrame)
        
        // 7, 8, 9, 10: Apply transforms and grade using CPU reference
        // (Since this is an isolated frame preview outside Media3 pipeline)
        CpuFrameProcessor.process(rawBitmap, evaluatedParams)
    }
}
