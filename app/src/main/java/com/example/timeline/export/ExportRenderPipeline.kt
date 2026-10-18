package com.example.timeline.export

import com.example.timeline.core.TimelineProject
import com.example.timeline.core.TimelineClip
import com.example.timeline.core.ProjectSettings
import com.example.timeline.engine.preview.TimelineFrameResolver
import com.example.timeline.media.MediaAssetRepository
import com.example.model.adjustments.AdjustmentStack
import com.example.timeline.media.MediaAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Placeholder for raw/decoded frame representation
class RenderFrame

sealed class ExportProgress {
    data class Rendering(val frame: Long, val totalFrames: Long) : ExportProgress()
    data class Warning(val message: String) : ExportProgress()
    data class Error(val exception: Exception) : ExportProgress()
    object Complete : ExportProgress()
}

class ExportRenderPipeline(
    private val project: TimelineProject,
    private val settings: ProjectSettings,
    private val mediaRepository: MediaAssetRepository,
    private val adjustmentRenderer: AdjustmentExportRenderer
) {
    fun renderExport(): Flow<ExportProgress> = flow {
        
        val totalFrames = calculateTotalFrames(project)
        val transformCompositor = ExportFrameCompositor()
        
        for (frame in 0 until totalFrames) {
            // determine timeline clip at frame
            val trackClipPair = TimelineFrameResolver.getTopmostVisibleVideoClipWithIndex(project, frame)
            if (trackClipPair == null) {
                // Render black/empty frame if no video clip exists
                val emptyFrame = RenderFrame()
                encodeFrame(emptyFrame)
                emit(ExportProgress.Rendering(frame, totalFrames))
                continue
            }
            val (clip, trackIndex) = trackClipPair
            
            // get MediaAsset
            val asset = mediaRepository.getAssetById(clip.mediaId)
            if (asset == null) {
                emit(ExportProgress.Error(Exception("Missing media asset: ${clip.mediaId}")))
                return@flow
            }
            
            // decode source frame
            val sourceFrameNum = TimelineFrameResolver.resolveSourceFrame(frame, clip)
            var rawFrame = decodeSourceFrame(asset, sourceFrameNum, false)
            
            if (rawFrame == null) {
                 // If original decode fails and optimized media exists, export may use optimized media with warning.
                 if (asset.proxyInfo != null || asset.optimizedMediaInfo != null) {
                      emit(ExportProgress.Warning("Original decode failed, using optimized/proxy media for clip ${clip.id}"))
                      rawFrame = decodeSourceFrame(asset, sourceFrameNum, true)
                 }
                 if (rawFrame == null) {
                      emit(ExportProgress.Error(Exception("Failed to decode frame $sourceFrameNum for clip ${clip.id}")))
                      return@flow
                 }
            }
            
            // Evaluate transform for clip at current frame
            val evaluatedTransform = clip.transform.evaluateTransformAtFrame(frame)
            
            // Apply transform 
            val transformedFrame = transformCompositor.applyTransform(rawFrame, evaluatedTransform, settings)
            
            // evaluate clip adjustment params at timeline frame
            val evaluatedParams = com.example.timeline.engine.GradeEvaluationEngine.evaluateFrame(project, frame)
            
            // apply adjustments and color management
            val finalFrame = adjustmentRenderer.renderFrame(
                 transformedFrame,
                 evaluatedParams,
                 emptyList(), // merged internally now
                 settings,
                 frame
            )
            
            // render/encode final frame
            encodeFrame(finalFrame)
            
            emit(ExportProgress.Rendering(frame, totalFrames))
        }
        emit(ExportProgress.Complete)
    }
    
    private fun calculateTotalFrames(project: TimelineProject): Long {
        var maxEnd = 0L
        project.tracks.forEach { track ->
            track.clips.forEach { clip ->
                if (clip.timelineEnd > maxEnd) {
                    maxEnd = clip.timelineEnd
                }
            }
        }
        return maxEnd
    }
    
    private fun decodeSourceFrame(asset: MediaAsset, frame: Long, canUseOptimized: Boolean): RenderFrame? {
        // Mock decoding functionality
        // In real app, we'd use MediaCodec or ffmpeg to decode the exact frame
        return RenderFrame()
    }
    
    private fun encodeFrame(frame: RenderFrame) {
        // Mock encode functionality
    }
}
