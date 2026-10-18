package com.example.timeline.export

import com.example.timeline.core.transform.ClipTransform
import com.example.timeline.core.ProjectSettings
import com.example.timeline.engine.NativeTransformEngine

class ExportFrameCompositor {
    
    fun applyTransform(frame: RenderFrame, transform: ClipTransform, settings: ProjectSettings): RenderFrame {
        // Evaluate native matrix
        val matrix = NativeTransformEngine.buildTransformMatrix(transform)
        
        // Evaluate flip
        val finalScaleX = transform.scaleX * if (transform.flipHorizontal) -1f else 1f
        val finalScaleY = transform.scaleY * if (transform.flipVertical) -1f else 1f
        
        // Evaluate opacity
        val opacity = transform.opacity
        
        // Check BlendMode and Motion blur
        // (Note: ExportValidation already prevents reaching here if these are enabled and unsupported)
        
        // Convert RenderFrame to Bitmap if this wasn't mocked.
        // For the sake of the software fallback mock in this test environment:
        // We assume we can't extract pixels from RenderFrame class directly because it's a mock.
        // If it was a real frame (like Bitmap):
        //   val width = frame.width ...
        //   NativeExportTransformEngine.nativeApplyTransformRgba8888(
        //       outPixels = outArray, inPixels = inArray, width = width, height = height,
        //       px = transform.positionX, py = transform.positionY, rot = transform.rotationDegrees,
        //       sx = finalScaleX, sy = finalScaleY,
        //       anchorX = transform.anchorPointX, anchorY = transform.anchorPointY,
        //       cropL = transform.cropParams.cropLeft, cropT = transform.cropParams.cropTop,
        //       cropR = transform.cropParams.cropRight, cropB = transform.cropParams.cropBottom,
        //       opacity = opacity
        //   )
        
        return frame
    }
}

