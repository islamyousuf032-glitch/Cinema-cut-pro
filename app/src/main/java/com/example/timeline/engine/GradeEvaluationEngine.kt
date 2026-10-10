package com.example.timeline.engine

import com.example.model.adjustments.AdjustmentStack
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.timeline.core.TimelineProject
import com.example.timeline.engine.preview.TimelineFrameResolver

object GradeEvaluationEngine {
    
    /**
     * Evaluates the complete grade at the given timeline frame.
     * Merges the clip grade with any applicable adjustment layers above it.
     */
    fun evaluateFrame(project: TimelineProject, frame: Long): VideoAdjustmentParams {
        val topmost = GradingFrameResolver.resolve(project, frame)
        if (topmost == null) {
            return VideoAdjustmentParams.default()
        }
        val (clip, trackIndex) = topmost
        
        // 4. Evaluate clip grade at current frame
        var currentParams = clip.adjustments.evaluateParamsAtFrame(frame)
        
        // 5. Evaluate adjustment layers above clip
        val adjustmentLayers = AdjustmentLayerResolver.resolveLayers(project, frame, trackIndex)
        
        for (layer in adjustmentLayers) {
            // Evaluated layer params
            val layerParams = layer.adjustments.evaluateParamsAtFrame(frame)
            
            // Create a temp stack to use merge method
            val baseStack = AdjustmentStack(
                stackId = "base",
                targetType = com.example.model.adjustments.TargetType.CLIP,
                targetId = "base",
                params = currentParams
            )
            
            val layerStack = layer.adjustments.copy(params = layerParams)
            
            // Merge rules: The base stack applies the layer stack
            currentParams = baseStack.mergeWithAdjustmentLayer(layerStack)
        }
        
        return currentParams
    }
}
