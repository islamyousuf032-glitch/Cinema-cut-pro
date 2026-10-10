package com.example.timeline.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.timeline.core.TimelineClip
import com.example.timeline.engine.TransformKeyframeEngine

@Composable
fun MotionPathRenderer(
    clip: TimelineClip?,
    modifier: Modifier = Modifier
) {
    if (clip == null) return

    val startFrame = clip.timelineStart
    val duration = clip.duration
    val endFrame = startFrame + duration

    val positionTrack = clip.transform.positionTrack
    if (!positionTrack.hasKeyframes()) return

    // Simple evaluation wrapper. It's safe to run in a remember block or just every composition.
    // For large tracks it should be cached, but for now we extract from engine.
    
    val xTrackFrames = LongArray(positionTrack.keyframes.size)
    val xTrackValues = FloatArray(positionTrack.keyframes.size)
    val xInterps = IntArray(positionTrack.keyframes.size)
    
    val yTrackFrames = LongArray(positionTrack.keyframes.size)
    val yTrackValues = FloatArray(positionTrack.keyframes.size)
    val yInterps = IntArray(positionTrack.keyframes.size)
    
    for (i in positionTrack.keyframes.indices) {
        val kf = positionTrack.keyframes[i]
        xTrackFrames[i] = kf.frame
        xTrackValues[i] = kf.value.first
        xInterps[i] = kf.interpolation.ordinal
        
        yTrackFrames[i] = kf.frame
        yTrackValues[i] = kf.value.second
        yInterps[i] = kf.interpolation.ordinal
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val pathPairs = TransformKeyframeEngine.generateMotionPath(
            startFrame, endFrame,
            com.example.timeline.core.transform.TransformKeyframeTrack("x", positionTrack.keyframes.map { com.example.timeline.core.transform.TransformKeyframe(it.id, "x", it.frame, it.value.first, it.interpolation) }),
            com.example.timeline.core.transform.TransformKeyframeTrack("y", positionTrack.keyframes.map { com.example.timeline.core.transform.TransformKeyframe(it.id, "y", it.frame, it.value.second, it.interpolation) })
        )
        
        if (pathPairs.isEmpty()) return@Canvas

        val baseWidth = 1920f
        val baseHeight = 1080f
        
        val scale = minOf(size.width / baseWidth, size.height / baseHeight)
        val viewportOffsetX = (size.width - baseWidth * scale) / 2f
        val viewportOffsetY = (size.height - baseHeight * scale) / 2f
        
        val path = Path()
        
        pathPairs.forEachIndexed { index, (x, y) ->
            val screenX = viewportOffsetX + baseWidth * scale / 2f + x * scale
            val screenY = viewportOffsetY + baseHeight * scale / 2f + y * scale
            
            if (index == 0) {
                path.moveTo(screenX, screenY)
            } else {
                path.lineTo(screenX, screenY)
            }
        }
        
        drawPath(
            path = path,
            color = Color.Red.copy(alpha = 0.6f),
            style = Stroke(width = 2f)
        )
        
        // Draw keyframe diamonds on path
        positionTrack.keyframes.forEach { kf ->
            val kfX = viewportOffsetX + baseWidth * scale / 2f + kf.value.first * scale
            val kfY = viewportOffsetY + baseHeight * scale / 2f + kf.value.second * scale
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = Offset(kfX, kfY)
            )
            drawCircle(
                color = Color.Red,
                radius = 3f,
                center = Offset(kfX, kfY)
            )
        }
    }
}
