package com.example.timeline.engine.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout

@Composable
fun TransformCompositor(
    renderState: TransformRenderState,
    modifier: Modifier = Modifier,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
    content: @Composable () -> Unit
) {
    MotionBlurPreviewRenderer(renderState, modifier) {
        val evaluatedTransform = renderState.transform

        val hasPerspective = evaluatedTransform.perspectiveParams.enabled
        
        val cropShape = CropProcessor(evaluatedTransform.cropParams)
        val composeBlendMode = BlendModeProcessor.toComposeBlendMode(evaluatedTransform.blendMode)

        val transformModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
            scaleX = evaluatedTransform.scaleX * (if (evaluatedTransform.flipHorizontal) -1f else 1f)
            scaleY = evaluatedTransform.scaleY * (if (evaluatedTransform.flipVertical) -1f else 1f)
            translationX = evaluatedTransform.positionX
            translationY = evaluatedTransform.positionY
            rotationZ = evaluatedTransform.rotationDegrees
            alpha = evaluatedTransform.opacity
            transformOrigin = TransformOrigin(evaluatedTransform.anchorPointX, evaluatedTransform.anchorPointY)
            clip = true
            shape = cropShape
            // 'blendMode' may not exist directly on old graphicsLayer
            // We'll apply it via compose drawing if needed, but for now we set it natively later or skip if unsupported on API level.
        }
        .drawWithContent {
            if (composeBlendMode != androidx.compose.ui.graphics.BlendMode.SrcOver || colorFilter != null) {
                drawIntoCanvas { canvas ->
                    val paint = Paint()
                    paint.blendMode = composeBlendMode
                    if (colorFilter != null) {
                        paint.colorFilter = colorFilter
                    }
                    canvas.saveLayer(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, size), paint)
                    drawContent()
                    canvas.restore()
                }
            } else {
                drawContent()
            }
        }

    Box(modifier = transformModifier) {
        if (hasPerspective && !renderState.requiresGpuCompositor) {
            Layout(
                content = content,
                modifier = Modifier.drawWithContent {
                   drawIntoCanvas { canvas ->
                       val aCanvas = canvas.nativeCanvas
                       val w = size.width
                       val h = size.height
                       val androidMatrix = android.graphics.Matrix()
                       val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
                       val dst = PerspectiveProcessor.calculateBounds(evaluatedTransform.perspectiveParams, w, h)
                       androidMatrix.setPolyToPoly(src, 0, dst, 0, 4)
                       
                       aCanvas.save()
                       aCanvas.concat(androidMatrix)
                       drawContent()
                       aCanvas.restore()
                   }
                }
            ) { measurables, constraints ->
                val placeable = measurables.firstOrNull()?.measure(constraints)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable?.placeRelative(0, 0)
                }
            }
        } else {
            content()
        }
    }
    }
}
