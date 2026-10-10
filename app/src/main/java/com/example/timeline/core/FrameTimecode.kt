package com.example.timeline.core

object FrameTimecode {
    fun frameToTimecode(frame: Long, fps: Int): String {
        val f = frame % fps
        val totalSeconds = frame / fps
        val s = totalSeconds % 60
        val m = (totalSeconds / 60) % 60
        val h = totalSeconds / 3600
        
        return String.format("%02d:%02d:%02d:%02d", h, m, s, f)
    }

    fun timecodeToFrame(timecode: String, fps: Int): Long {
        val parts = timecode.split(":")
        require(parts.size == 4) { "Invalid timecode format, expected HH:MM:SS:FF" }
        
        val h = parts[0].toLong()
        val m = parts[1].toLong()
        val s = parts[2].toLong()
        val f = parts[3].toLong()
        
        return (h * 3600 * fps) + (m * 60 * fps) + (s * fps) + f
    }
}
