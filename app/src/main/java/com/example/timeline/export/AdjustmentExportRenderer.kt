package com.example.timeline.export

import com.example.model.adjustments.AdjustmentStack
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.timeline.core.ProjectSettings

class AdjustmentExportRenderer(
    private val colorSettings: ExportColorRenderer
) {

    /**
     * Renders adjustments to the provided frame.
     * Applies clip-level params first, followed by adjustment layers, 
     * then finally applies track/global & color management.
     */
    fun renderFrame(
        baseFrame: RenderFrame,
        clipParams: VideoAdjustmentParams,
        layerStacks: List<AdjustmentStack>,
        settings: ProjectSettings,
        timelineFrame: Long
    ): RenderFrame {
        // Evaluate all layer adjustments at the current frame using keyframes
        val evaluatedLayerParams = layerStacks.map { it.evaluateParamsAtFrame(timelineFrame) }
        
        // Merge them sequentially
        var compositeParams = clipParams
        for (layerParams in layerStacks) {
            compositeParams = AdjustmentStack(
                stackId = "temp", targetType = com.example.model.adjustments.TargetType.CLIP, targetId = "temp",
                params = compositeParams
            ).mergeWithAdjustmentLayer(
                layerParams.copy(params = layerParams.evaluateParamsAtFrame(timelineFrame))
            )
        }
        
        // In a real rendering pipeline, we would apply compositeParams through a GlShaderProgram here.
        // For the software mock, we just apply output management
        return applyColorManagement(baseFrame, compositeParams, settings)
    }

    private fun applyColorManagement(
        frame: RenderFrame,
        params: VideoAdjustmentParams,
        settings: ProjectSettings
    ): RenderFrame {
        // Applies output color space configuration
        colorSettings.applyOutputSettings(frame, settings)
        return frame
    }
}
