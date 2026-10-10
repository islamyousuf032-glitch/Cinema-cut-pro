package com.example.ui.colorgrade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.model.colorgrade.CurvePoint
import kotlin.math.hypot

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
    val latestPoints = rememberUpdatedState(points)
    val latestOnPointsChanged = rememberUpdatedState(onPointsChanged)
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color(0xFF1E1F22))
            // Keep pointer input alive while the model changes on every drag frame. Keying these
            // handlers by `points` cancels the active gesture as soon as Compose recomposes.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val current = latestPoints.value
                        if (current.size < 2) return@detectTapGestures

                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                        val y = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        val nearestDistance = current.minOfOrNull { point ->
                            hypot((point.x - x).toDouble(), (point.y - y).toDouble()).toFloat()
                        } ?: Float.MAX_VALUE
                        // Taps on an existing control point select it; they should not stack
                        // duplicate or almost-identical points underneath it.
                        if (nearestDistance <= POINT_HIT_RADIUS || x <= current.first().x || x >= current.last().x) {
                            return@detectTapGestures
                        }

                        val insertAfter = current.indexOfLast { it.x < x }.coerceIn(0, current.lastIndex - 1)
                        val minX = current[insertAfter].x + MIN_POINT_GAP
                        val maxX = current[insertAfter + 1].x - MIN_POINT_GAP
                        if (minX > maxX) return@detectTapGestures

                        val updated = current.toMutableList().apply {
                            add(insertAfter + 1, CurvePoint(x.coerceIn(minX, maxX), y))
                        }
                        latestOnDragStart.value()
                        latestOnPointsChanged.value(updated)
                        latestOnDragEnd.value()
                    },
                    onLongPress = { offset ->
                        val current = latestPoints.value
                        if (current.size <= 2) return@detectTapGestures
                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                        val y = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        val closestIdx = (1 until current.lastIndex).minByOrNull { index ->
                            hypot(
                                (current[index].x - x).toDouble(),
                                (current[index].y - y).toDouble()
                            )
                        }
                        if (closestIdx != null) {
                            val distance = hypot(
                                (current[closestIdx].x - x).toDouble(),
                                (current[closestIdx].y - y).toDouble()
                            ).toFloat()
                            if (distance <= DELETE_HIT_RADIUS) {
                                latestOnDragStart.value()
                                latestOnPointsChanged.value(current.toMutableList().apply { removeAt(closestIdx) })
                                latestOnDragEnd.value()
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val current = latestPoints.value
                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                        val y = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        val closestIdx = current.indices.minByOrNull { index ->
                            hypot(
                                (current[index].x - x).toDouble(),
                                (current[index].y - y).toDouble()
                            )
                        }
                        draggedPointIndex = closestIdx?.takeIf { index ->
                            hypot(
                                (current[index].x - x).toDouble(),
                                (current[index].y - y).toDouble()
                            ) <= POINT_HIT_RADIUS
                        }
                        latestOnDragStart.value()
                    },
                    onDragEnd = {
                        draggedPointIndex = null
                        latestOnDragEnd.value()
                    },
                    onDragCancel = {
                        draggedPointIndex = null
                        latestOnDragEnd.value()
                    }
                ) { change, _ ->
                    change.consume()
                    val idx = draggedPointIndex ?: return@detectDragGestures
                    val current = latestPoints.value
                    if (idx !in current.indices) return@detectDragGestures

                    val newX = (change.position.x / size.width).coerceIn(0f, 1f)
                    val newY = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    var constrainedX = newX
                    if (idx == 0) {
                        constrainedX = 0f
                    } else if (idx == current.lastIndex) {
                        constrainedX = 1f
                    } else {
                        val minX = current[idx - 1].x + MIN_POINT_GAP
                        val maxX = current[idx + 1].x - MIN_POINT_GAP
                        if (minX <= maxX) constrainedX = constrainedX.coerceIn(minX, maxX)
                    }

                    latestOnPointsChanged.value(current.toMutableList().apply {
                        this[idx] = CurvePoint(constrainedX, newY)
                    })
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            for (i in 1..3) {
                val pos = i * width / 4
                drawLine(Color.DarkGray, Offset(pos, 0f), Offset(pos, height), 1.dp.toPx())
                drawLine(Color.DarkGray, Offset(0f, pos), Offset(width, pos), 1.dp.toPx())
            }

            if (points.isNotEmpty()) {
                val path = Path()
                path.moveTo(points[0].x * width, (1f - points[0].y) * height)
                for (i in 1 until points.size) {
                    val point = points[i]
                    path.lineTo(point.x * width, (1f - point.y) * height)
                }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
            }

            points.forEach { point ->
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(point.x * width, (1f - point.y) * height)
                )
            }
        }
    }
}

private const val POINT_HIT_RADIUS = 0.09f
private const val DELETE_HIT_RADIUS = 0.09f
private const val MIN_POINT_GAP = 0.01f
