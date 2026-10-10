package com.example.timeline.export.resolver

import com.example.timeline.export.model.ExportSettings

object ExportFrameTimeMapper {
    fun outputFrameToTimeUs(outputFrameIndex: Long, settings: ExportSettings): Long {
        val fps = settings.frameRate.floatValue
        if (fps <= 0f) return 0L
        return ((outputFrameIndex * 1000000.0) / fps).toLong()
    }

    fun timeUsToProjectFrame(timeUs: Long, fpsRational: com.example.timeline.core.Rational): Long {
        val fps = fpsRational.toFloat()
        if (fps <= 0f) return 0L
        return ((timeUs / 1000000.0) * fps).toLong()
    }
    
    fun projectFrameToTimeUs(projectFrame: Long, fpsRational: com.example.timeline.core.Rational): Long {
        val fps = fpsRational.toFloat()
        if (fps <= 0f) return 0L
        return ((projectFrame / fps) * 1000000.0).toLong()
    }
}
