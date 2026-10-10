package com.example.timeline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeline.core.TimelineClip
import com.example.timeline.media.MediaAsset

@Composable
fun FilmStripClipView(
    clip: TimelineClip,
    mediaAsset: MediaAsset? = null,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    pixelsPerFrame: Float,
    scrollX: Float,
    viewportWidth: Float
) {
    val durationFrames = clip.timelineEnd - clip.timelineStart
    val widthPx = durationFrames * pixelsPerFrame
    val durationSec = durationFrames / 30f // Assumes 30fps for display
    
    val clipColor = if (clip.type == com.example.timeline.core.ClipType.MEDIA) Color(0xFF2B5B84) else Color(0xFF2E634A)
    val borderColor = if (isSelected) Color(0xFFFFC107) else Color.Black.copy(alpha = 0.5f)
    
    Box(
        modifier = modifier.width(with(androidx.compose.ui.platform.LocalDensity.current) { widthPx.toDp() }).height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(clipColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
    ) {
        if (mediaAsset != null && clip.type == com.example.timeline.core.ClipType.MEDIA) {
            FilmStripThumbnailRow(
                clip = clip,
                mediaAsset = mediaAsset,
                pixelsPerFrame = pixelsPerFrame,
                scrollX = scrollX,
                viewportWidth = viewportWidth,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Draw sprocket holes and frame divisions
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val w = size.width
            
            if (clip.type == com.example.timeline.core.ClipType.MEDIA) {
                // Top and bottom sprocket bands
                val bandHeight = 12f
                drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, 0f), size = Size(w, bandHeight))
                drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, h - bandHeight), size = Size(w, bandHeight))
                
                // Sprocket holes
                val holeWidth = 6f
                val holeHeight = 6f
                val holeSpacing = 20f
                var x = 5f
                while (x < w) {
                    drawRoundRect(Color.Black, topLeft = Offset(x, 3f), size = Size(holeWidth, holeHeight), cornerRadius = CornerRadius(2f))
                    drawRoundRect(Color.Black, topLeft = Offset(x, h - bandHeight + 3f), size = Size(holeWidth, holeHeight), cornerRadius = CornerRadius(2f))
                    x += holeSpacing
                }
                
                // Frames
                val frameWidth = h * 1.5f
                var fx = frameWidth
                while (fx < w) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.8f),
                        start = Offset(fx, bandHeight),
                        end = Offset(fx, h - bandHeight),
                        strokeWidth = 2f
                    )
                    fx += frameWidth
                }
            } else {
                // Audio waveform abstract representation
                val midY = h / 2f
                val step = 10f
                var ax = 5f
                while (ax < w) {
                    val amp = (Math.random() * (h * 0.6)).toFloat()
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(ax, midY - amp / 2),
                        end = Offset(ax, midY + amp / 2),
                        strokeWidth = 3f
                    )
                    ax += step
                }
            }
        }
        
        // Text overlay
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = clip.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = String.format("%.1fs", durationSec),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Trim Handles if selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(16.dp)
                    .fillMaxHeight()
                    .background(Color(0x88FFC107))
            ) {
                Box(modifier = Modifier.align(Alignment.Center).width(4.dp).height(24.dp).background(Color.White))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(16.dp)
                    .fillMaxHeight()
                    .background(Color(0x88FFC107))
            ) {
                Box(modifier = Modifier.align(Alignment.Center).width(4.dp).height(24.dp).background(Color.White))
            }
        }
    }
}
