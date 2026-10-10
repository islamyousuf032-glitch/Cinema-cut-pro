package com.example.timeline.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.timeline.media.MediaAsset
import com.example.timeline.core.TimelineClip
import com.example.timeline.engine.native.NativeTimelineCore
import com.example.timeline.engine.preview.TimelineThumbnailGenerator

@Composable
fun FilmStripThumbnailRow(
    clip: TimelineClip,
    mediaAsset: MediaAsset,
    pixelsPerFrame: Float,
    scrollX: Float,
    viewportWidth: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    val heightPx = with(density) { 56.dp.toPx() }
    val widthPx = heightPx * (mediaAsset.metadata.width.toFloat() / mediaAsset.metadata.height.toFloat().coerceAtLeast(1f))
    
    val durationFrames = clip.timelineEnd - clip.timelineStart
    val thumbnailPlans = remember(clip.id, durationFrames, pixelsPerFrame, clip.sourceIn) {
        val rawPlans = NativeTimelineCore.nativeCalculateThumbnailCells(
            startFrame = clip.timelineStart,
            durationFrames = durationFrames,
            cellWidth = widthPx,
            pixelsPerFrame = pixelsPerFrame,
            sourceInFrame = clip.sourceIn
        )
        val numCells = rawPlans.size / 4
        val plans = mutableListOf<ThumbnailCellPlan>()
        for (i in 0 until numCells) {
            plans.add(
                ThumbnailCellPlan(
                    cellIndex = rawPlans[i * 4].toInt(),
                    sourceFrame = rawPlans[i * 4 + 1].toLong(),
                    startX = rawPlans[i * 4 + 2],
                    width = rawPlans[i * 4 + 3]
                )
            )
        }
        plans
    }

    val clipStartX = NativeTimelineCore.frameToX(clip.timelineStart, pixelsPerFrame)
    val localVisibleStart = scrollX - clipStartX
    val localVisibleEnd = localVisibleStart + viewportWidth

    Box(modifier = modifier.fillMaxSize().padding(vertical = 12.dp)) {
        thumbnailPlans.forEach { plan ->
            val isVisible = plan.startX < localVisibleEnd && (plan.startX + plan.width) > localVisibleStart
            if (isVisible) {
                ThumbnailCellView(
                    plan = plan,
                    mediaAsset = mediaAsset,
                    context = android.content.ContextWrapper(context),
                    targetWidth = widthPx.toInt(),
                    targetHeight = heightPx.toInt()
                )
            }
        }
        
        // Dark overlay for text readability
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
    }
}

@Composable
fun ThumbnailCellView(
    plan: ThumbnailCellPlan,
    mediaAsset: MediaAsset,
    context: android.content.Context,
    targetWidth: Int,
    targetHeight: Int
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val fps = mediaAsset.metadata.estimatedFrameRate?.fpsAsFloat ?: 30f
    val timeUs = (plan.sourceFrame * 1_000_000L / fps).toLong()
    val uri = mediaAsset.localOriginalUriString ?: mediaAsset.originalUriString

    LaunchedEffect(plan.sourceFrame, uri) {
        val bmp = TimelineThumbnailGenerator.getOrGenerateThumbnail(
            context = context,
            assetId = mediaAsset.assetId,
            uriString = uri,
            timeUs = timeUs,
            width = targetWidth,
            height = targetHeight
        )
        bitmap = bmp
    }

    val dpStartX = with(LocalDensity.current) { plan.startX.toDp() }
    val dpWidth = with(LocalDensity.current) { plan.width.toDp() }

    Box(
        modifier = Modifier
            .offset(x = dpStartX)
            .width(dpWidth)
            .fillMaxHeight()
            .background(Color.DarkGray)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
