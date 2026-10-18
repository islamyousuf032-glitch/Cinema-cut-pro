package com.example.timeline.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.toSize
import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.transform.ClipTransform

import com.example.timeline.ui.TimelineViewModel

enum class DragMode {
    NONE, POSITION, SCALE_TL, SCALE_TR, SCALE_BL, SCALE_BR, ROTATE, ANCHOR, CROP_L, CROP_R, CROP_T, CROP_B
}

@Composable
fun TransformHandleOverlay(
    clip: TimelineClip?,
    currentFrame: Long,
    timelineViewModel: TimelineViewModel,
    onUpdateClipTransform: (TimelineClip, ClipTransform) -> Unit,
    onLiveTransformUpdate: (ClipTransform) -> Unit = {},
    modifier: Modifier = Modifier,
    activeOverlayMode: String = "BoundingBox" // Can be "BoundingBox", "Crop", "Perspective", etc.
) {
    if (clip == null) return
    
    val transform = clip.transform
    val evaluatedTransform = transform.evaluateTransformAtFrame(currentFrame)
    
    // Default project resolutions
    val baseWidth = 1920f
    val baseHeight = 1080f
    
    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var dragStartTransform by remember { mutableStateOf(clip.transform) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStartProject by remember { mutableStateOf(timelineViewModel.uiState.value.project) }

    fun mapToCanvas(offset: Offset, size: Size, evalTrans: ClipTransform): Offset {
        val scale = minOf(size.width / baseWidth, size.height / baseHeight)
        val viewportOffsetX = (size.width - baseWidth * scale) / 2f
        val viewportOffsetY = (size.height - baseHeight * scale) / 2f
        
        // This calculates normalized projection points
        val projX = (offset.x - viewportOffsetX - baseWidth * scale / 2f) / scale
        val projY = (offset.y - viewportOffsetY - baseHeight * scale / 2f) / scale
        
        // Android graphics Matrix for inverse map
        val matrix = android.graphics.Matrix()
        matrix.postTranslate(evalTrans.positionX, evalTrans.positionY)
        matrix.postRotate(evalTrans.rotationDegrees, evalTrans.anchorPointX, evalTrans.anchorPointY)
        matrix.postScale(evalTrans.scaleX, evalTrans.scaleY, evalTrans.anchorPointX, evalTrans.anchorPointY)
        
        val inverse = android.graphics.Matrix()
        matrix.invert(inverse)
        
        val pts = floatArrayOf(projX, projY)
        inverse.mapPoints(pts)
        
        return Offset(pts[0], pts[1])
    }
    
    fun mapRawViewportToProjectCenter(offset: Offset, size: Size): Offset {
        val scale = minOf(size.width / baseWidth, size.height / baseHeight)
        val viewportOffsetX = (size.width - baseWidth * scale) / 2f
        val viewportOffsetY = (size.height - baseHeight * scale) / 2f
        
        val projX = (offset.x - viewportOffsetX - baseWidth * scale / 2f) / scale
        val projY = (offset.y - viewportOffsetY - baseHeight * scale / 2f) / scale
        
        return Offset(projX, projY)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(clip.id) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartTransform = clip.transform
                        dragStartProject = timelineViewModel.uiState.value.project
                        val projPos = mapRawViewportToProjectCenter(offset, size.toSize())
                        val localPos = mapToCanvas(offset, size.toSize(), evaluatedTransform)
                        
                        dragStartOffset = projPos
                        
                        val scale = minOf(size.width / baseWidth, size.height / baseHeight)
                        val hw = baseWidth / 2f
                        val hh = baseHeight / 2f
                        
                        // Simple Hit Test for Crop
                        if (activeOverlayMode == "Crop") {
                            val cp = evaluatedTransform.cropParams
                            val left = -hw + cp.cropLeft * baseWidth
                            val right = hw - cp.cropRight * baseWidth
                            val top = -hh + cp.cropTop * baseHeight
                            val bottom = hh - cp.cropBottom * baseHeight
                            
                            val tX = localPos.x / scale
                            val tY = localPos.y / scale

                            if (kotlin.math.abs(tX - left) < (40f / scale)) dragMode = DragMode.CROP_L
                            else if (kotlin.math.abs(tX - right) < (40f / scale)) dragMode = DragMode.CROP_R
                            else if (kotlin.math.abs(tY - top) < (40f / scale)) dragMode = DragMode.CROP_T
                            else if (kotlin.math.abs(tY - bottom) < (40f / scale)) dragMode = DragMode.CROP_B
                            else dragMode = DragMode.POSITION
                        } else {
                            dragMode = if (localPos.x > -hw && localPos.x < hw && localPos.y > -hh && localPos.y < hh) {
                                DragMode.POSITION
                            } else {
                                DragMode.POSITION // Default fallback for preview
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val currentProjPos = mapRawViewportToProjectCenter(change.position, size.toSize())
                        val deltaX = currentProjPos.x - dragStartOffset.x
                        val deltaY = currentProjPos.y - dragStartOffset.y
                        
                        val scale = minOf(size.width / baseWidth, size.height / baseHeight)
                        
                        when (dragMode) {
                            DragMode.POSITION -> {
                                val newX = dragStartTransform.transformParams.positionX + deltaX
                                val newY = dragStartTransform.transformParams.positionY + deltaY
                                onLiveTransformUpdate(
                                    dragStartTransform.copy(
                                        transformParams = dragStartTransform.transformParams.copy(
                                            positionX = newX,
                                            positionY = newY
                                        )
                                    )
                                )
                            }
                            DragMode.CROP_L -> {
                                val localDeltaX = mapToCanvas(change.position, size.toSize(), evaluatedTransform).x / scale - mapToCanvas(change.previousPosition, size.toSize(), evaluatedTransform).x / scale
                                val cropChange = localDeltaX / baseWidth
                                val newCropL = (dragStartTransform.cropParams.cropLeft + cropChange).coerceIn(0f, 1f - dragStartTransform.cropParams.cropRight)
                                dragStartTransform = dragStartTransform.copy(cropParams = dragStartTransform.cropParams.copy(cropLeft = newCropL))
                                onLiveTransformUpdate(dragStartTransform)
                            }
                            DragMode.CROP_R -> {
                                val localDeltaX = mapToCanvas(change.position, size.toSize(), evaluatedTransform).x / scale - mapToCanvas(change.previousPosition, size.toSize(), evaluatedTransform).x / scale
                                val cropChange = -localDeltaX / baseWidth
                                val newCropR = (dragStartTransform.cropParams.cropRight + cropChange).coerceIn(0f, 1f - dragStartTransform.cropParams.cropLeft)
                                dragStartTransform = dragStartTransform.copy(cropParams = dragStartTransform.cropParams.copy(cropRight = newCropR))
                                onLiveTransformUpdate(dragStartTransform)
                            }
                            DragMode.CROP_T -> {
                                val localDeltaY = mapToCanvas(change.position, size.toSize(), evaluatedTransform).y / scale - mapToCanvas(change.previousPosition, size.toSize(), evaluatedTransform).y / scale
                                val cropChange = localDeltaY / baseHeight
                                val newCropT = (dragStartTransform.cropParams.cropTop + cropChange).coerceIn(0f, 1f - dragStartTransform.cropParams.cropBottom)
                                dragStartTransform = dragStartTransform.copy(cropParams = dragStartTransform.cropParams.copy(cropTop = newCropT))
                                onLiveTransformUpdate(dragStartTransform)
                            }
                            DragMode.CROP_B -> {
                                val localDeltaY = mapToCanvas(change.position, size.toSize(), evaluatedTransform).y / scale - mapToCanvas(change.previousPosition, size.toSize(), evaluatedTransform).y / scale
                                val cropChange = -localDeltaY / baseHeight
                                val newCropB = (dragStartTransform.cropParams.cropBottom + cropChange).coerceIn(0f, 1f - dragStartTransform.cropParams.cropTop)
                                dragStartTransform = dragStartTransform.copy(cropParams = dragStartTransform.cropParams.copy(cropBottom = newCropB))
                                onLiveTransformUpdate(dragStartTransform)
                            }
                            else -> {}
                        }
                    },
                    onDragEnd = {
                        dragMode = DragMode.NONE
                        onUpdateClipTransform(clip, dragStartTransform)
                        onLiveTransformUpdate(dragStartTransform)
                    }
                )
            }
    ) {
        val scale = minOf(size.width / baseWidth, size.height / baseHeight)
        val viewportOffsetX = (size.width - baseWidth * scale) / 2f
        val viewportOffsetY = (size.height - baseHeight * scale) / 2f
        
        val matrix = Matrix()
        matrix.translate(viewportOffsetX + baseWidth * scale / 2f, viewportOffsetY + baseHeight * scale / 2f)
        matrix.translate(evaluatedTransform.positionX * scale, evaluatedTransform.positionY * scale)
        // Matrix in compose graphics applies around origin. We must account for anchor point.
        matrix.translate(evaluatedTransform.anchorPointX * scale, evaluatedTransform.anchorPointY * scale)
        matrix.rotateZ(evaluatedTransform.rotationDegrees)
        matrix.scale(evaluatedTransform.scaleX, evaluatedTransform.scaleY)
        matrix.translate(-evaluatedTransform.anchorPointX * scale, -evaluatedTransform.anchorPointY * scale)
        
        withTransform({
            transform(matrix)
        }) {
            when (activeOverlayMode) {
                "Crop" -> drawCropOverlay(baseWidth * scale, baseHeight * scale, evaluatedTransform.cropParams)
                "Perspective" -> drawPerspectiveOverlay(baseWidth * scale, baseHeight * scale, evaluatedTransform.perspectiveParams)
                else -> {
                    drawBoundingBoxOverlay(baseWidth * scale, baseHeight * scale)
                    drawAnchorPointOverlay(evaluatedTransform.anchorPointX * scale, evaluatedTransform.anchorPointY * scale)
                }
            }
        }
    }
}
