package com.example.timeline.export.resolver

import com.example.timeline.core.TimelineProject
import com.example.timeline.export.model.ExportSettings

class AudioRenderResolver(
    private val project: TimelineProject,
    private val settings: ExportSettings
) {
    fun getActiveAudioClipsAtTime(presentationTimeUs: Long): List<RenderClipInstance> {
        val resolver = TimelineRenderResolver(project, settings)
        // Convert time to a frame index
        val fps = settings.frameRate.floatValue
        val projectFpsRational = project.settings.getFpsRational()
        val projectFps = projectFpsRational.toFloat()
        
        val projectFrame = ExportFrameTimeMapper.timeUsToProjectFrame(presentationTimeUs, projectFpsRational)
        val outputFrameIndex = ExportFrameTimeMapper.timeUsToProjectFrame(presentationTimeUs, com.example.timeline.core.Rational(fps.toInt(), 1))

        val plan = resolver.createPlanForFrame(outputFrameIndex)
        return plan.activeAudioLayers
    }
}
