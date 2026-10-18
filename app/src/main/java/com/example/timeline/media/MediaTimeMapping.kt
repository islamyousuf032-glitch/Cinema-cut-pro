package com.example.timeline.media

object MediaTimeMapping {
    fun sourceFrameToProjectFrame(sourceFrame: Long, sourceFps: FrameRate, projectFps: com.example.timeline.core.Rational): Long {
        val num = sourceFrame * sourceFps.denominator.toLong() * projectFps.numerator.toLong()
        val den = sourceFps.numerator.toLong() * projectFps.denominator.toLong()
        return num / den
    }

    fun projectFrameToSourceFrame(projectFrame: Long, projectFps: com.example.timeline.core.Rational, sourceFps: FrameRate): Long {
        val num = projectFrame * projectFps.denominator.toLong() * sourceFps.numerator.toLong()
        val den = projectFps.numerator.toLong() * sourceFps.denominator.toLong()
        return num / den
    }
    
    fun microsecondsToSourceFrame(micros: Long, sourceFps: FrameRate): Long {
        val secondsNum = micros
        val secondsDen = 1_000_000L
        val num = secondsNum * sourceFps.numerator.toLong()
        val den = secondsDen * sourceFps.denominator.toLong()
        return num / den
    }

    fun frameToTimecode(frame: Long, fps: FrameRate): String {
        val fpsFloat = fps.fpsAsFloat
        val fpsRounded = Math.round(fpsFloat)
        if (fpsRounded == 0) return "00:00:00:00"
        val totalSeconds = frame / fpsRounded
        val frames = frame % fpsRounded
        
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
    }
    
    fun timecodeToFrame(timecode: String, fps: FrameRate): Long {
        val parts = timecode.split(":")
        if (parts.size != 4) return 0L
        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        val seconds = parts[2].toLongOrNull() ?: 0L
        val frames = parts[3].toLongOrNull() ?: 0L
        
        val totalSeconds = hours * 3600L + minutes * 60L + seconds
        val fpsRounded = Math.round(fps.fpsAsFloat)
        
        return totalSeconds * fpsRounded + frames
    }
}
