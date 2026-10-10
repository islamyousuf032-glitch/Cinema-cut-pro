package com.example.timeline.ui

import com.example.timeline.core.Rational

object TimecodeFormatter {
    fun formatTimecode(frame: Long, fpsRational: Rational): String {
        val fps = fpsRational.toFloat()
        if (fps <= 0f) return "00:00:00:00"
        
        val framesPerSecond = Math.round(fps).toLong().coerceAtLeast(1)
        val totalSeconds = frame / framesPerSecond
        val f = frame % framesPerSecond
        
        val s = totalSeconds % 60
        val m = (totalSeconds / 60) % 60
        val h = (totalSeconds / 3600)
        
        return String.format("%02d:%02d:%02d:%02d", h, m, s, f)
    }
}
