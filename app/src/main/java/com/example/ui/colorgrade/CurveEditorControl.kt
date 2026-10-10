package com.example.ui.colorgrade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.model.colorgrade.CurvePoint

@Composable
fun CurveEditorControl(
    points: List<CurvePoint>,
    onPointsChanged: (List<CurvePoint>) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    lineColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    var draggedPointIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color(0xFF1E1F22))
            .pointerInput(points) {
                detectTapGestures(
                    onTap = { offset ->
                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                        val y = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        
                        val mutablePoints = points.toMutableList()
                        mutablePoints.add(CurvePoint(x, y))
                        mutablePoints.sortBy { it.x }
                        onPointsChanged(mutablePoints)
                        onDragEnd()
                    },
                    onLongPress = { offset ->
                        // Find closest point to delete
                        val x = (offset.x / size.width)
                        val y = 1f - (offset.y / size.height)
                        
                        var closestIdx = -1
                        var minDist = Float.MAX_VALUE
                        for (i in 1 until points.size - 1) { // don't delete endpoints
                            val dist = Math.hypot((points[i].x - x).toDouble(), (points[i].y - y).toDouble()).toFloat()
                            if (dist < 0.1f && dist < minDist) {
                                minDist = dist
                                closestIdx = i
                            }
                        }
                        
                        if (closestIdx != -1) {
                            val mutablePoints = points.toMutableList()
                            mutablePoints.removeAt(closestIdx)
                            onPointsChanged(mutablePoints)
                            onDragEnd()
                        }
                    }
                )
            }
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onDragStart()
                        val x = (offset.x / size.width)
                        val y = 1f - (offset.y / size.height)
                        
                        // Find point to drag
                        var closestIdx = -1
                        var minDist = Float.MAX_VALUE
                        for (i in points.indices) {
                            val dist = Math.hypot((points[i].x - x).toDouble(), (points[i].y - y).toDouble()).toFloat()
                            if (dist < 0.15f && dist < minDist) {
                                minDist = dist
                                closestIdx = i
                            }
                        }
                        draggedPointIndex = if (closestIdx != -1) closestIdx else null
                    },
                    onDragEnd = {
                        draggedPointIndex = null
                        onDragEnd()
                    },
                    onDragCancel = {
                        draggedPointIndex = null
                        onDragEnd()
                    }
                ) { change, _ ->
                    change.consume()
                    val idx = draggedPointIndex ?: return@detectDragGestures
                    
                    val newX = (change.position.x / size.width).coerceIn(0f, 1f)
                    val newY = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    
                    val mutablePoints = points.toMutableList()
                    
                    // Constraints: keeping x sorted
                    var constrainedX = newX
                    if (idx == 0) {
                        constrainedX = 0f // First point stays at x=0
                    } else if (idx == points.size - 1) {
                        constrainedX = 1f // Last point stays at x=1
                    } else {
                        val minX = points[idx - 1].x + 0.01f
                        val maxX = points[idx + 1].x - 0.01f
                        constrainedX = constrainedX.coerceIn(minX, maxX)
                    }

                    mutablePoints[idx] = CurvePoint(constrainedX, newY)
                    onPointsChanged(mutablePoints)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Grid
            for (i in 1..3) {
                val pos = i * width / 4
                drawLine(Color.DarkGray, Offset(pos, 0f), Offset(pos, height), 1.dp.toPx())
                drawLine(Color.DarkGray, Offset(0f, pos), Offset(width, pos), 1.dp.toPx())
            }

            // Curve Line
            if (points.isNotEmpty()) {
                val path = Path()
                path.moveTo(points[0].x * width, (1f - points[0].y) * height)
                for (i in 1 until points.size) {
                    val p = points[i]
                    path.lineTo(p.x * width, (1f - p.y) * height)
                }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
            }

            // Points
            for (p in points) {
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(p.x * width, (1f - p.y) * height)
                )
            }
        }
    }
}
