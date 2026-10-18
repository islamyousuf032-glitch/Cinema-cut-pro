package com.example.timeline.engine.preview

import com.example.timeline.core.ProjectSettings
import com.example.timeline.core.transform.ClipTransform
import com.example.timeline.core.transform.TransformFitMode
import com.example.timeline.media.MediaAsset

object ViewportTransformEngine {

    data class TransformResult(
        val canvasWidth: Float,
        val canvasHeight: Float,
        val displayWidth: Float,
        val displayHeight: Float,
        val scaleX: Float,
        val scaleY: Float,
        val offsetX: Float,
        val offsetY: Float,
        val rotation: Float,
        val opacity: Float
    )

    fun computeTransform(
        projectSettings: ProjectSettings,
        mediaAsset: MediaAsset,
        clipTransform: ClipTransform
    ): TransformResult {
        val projW = projectSettings.resolutionWidth.toFloat()
        val projH = projectSettings.resolutionHeight.toFloat()
        
        val sourceW = mediaAsset.metadata.width.toFloat().takeIf { it > 0 } ?: projW
        val sourceH = mediaAsset.metadata.height.toFloat().takeIf { it > 0 } ?: projH
        
        var displayW = sourceW
        var displayH = sourceH
        
        val projRatio = projW / projH
        val sourceRatio = sourceW / sourceH
        
        var baseScaleX = 1f
        var baseScaleY = 1f
        
        when (clipTransform.fitMode) {
            TransformFitMode.FIT -> {
                if (sourceRatio > projRatio) {
                    displayW = projW
                    displayH = projW / sourceRatio
                } else {
                    displayH = projH
                    displayW = projH * sourceRatio
                }
            }
            TransformFitMode.FILL -> {
                if (sourceRatio > projRatio) {
                    displayH = projH
                    displayW = projH * sourceRatio
                } else {
                    displayW = projW
                    displayH = projW / sourceRatio
                }
            }
            TransformFitMode.STRETCH -> {
                displayW = projW
                displayH = projH
            }
            TransformFitMode.ORIGINAL_SIZE -> {
                displayW = sourceW
                displayH = sourceH
            }
        }
        
        val finalScaleX = baseScaleX * clipTransform.scaleX
        val finalScaleY = baseScaleY * clipTransform.scaleY
        
        // Offset
        val finalOffsetX = clipTransform.positionX
        val finalOffsetY = clipTransform.positionY

        return TransformResult(
            canvasWidth = projW,
            canvasHeight = projH,
            displayWidth = displayW,
            displayHeight = displayH,
            scaleX = finalScaleX,
            scaleY = finalScaleY,
            offsetX = finalOffsetX,
            offsetY = finalOffsetY,
            rotation = clipTransform.rotationDegrees,
            opacity = clipTransform.opacity
        )
    }
}
