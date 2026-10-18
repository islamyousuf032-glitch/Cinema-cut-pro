package com.example.timeline.engine.performance

import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

object FrameTimeProfiler : Choreographer.FrameCallback {
    private var lastFrameTimeNanos: Long = 0
    private var frameCount = 0
    private var droppedFramesTotal = 0
    private var lastFpsCalculationTime: Long = 0
    
    private val _stats = MutableStateFlow(UiFrameStats())
    val stats: StateFlow<UiFrameStats> = _stats.asStateFlow()

    private val _nativeStats = MutableStateFlow(NativePerformanceStats())
    val nativeStats: StateFlow<NativePerformanceStats> = _nativeStats.asStateFlow()


    private var isRunning = false
    private val formatRate = 1000L * 1000L * 1000L // 1 second in nanos

    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameTimeNanos = System.nanoTime()
        lastFpsCalculationTime = lastFrameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        val frameTimeDiff = frameTimeNanos - lastFrameTimeNanos
        val frameTimeMs = frameTimeDiff / 1_000_000f

        // roughly > 16.6ms is a dropped frame if we target 60, > 8.3ms for 120
        // standard android often targets 60, but let's count > 17ms
        if (frameTimeMs > 17f) {
            droppedFramesTotal++
        }

        frameCount++
        
        val elapsedSinceLastFps = frameTimeNanos - lastFpsCalculationTime
        if (elapsedSinceLastFps >= formatRate) {
            val fps = (frameCount * formatRate).toFloat() / elapsedSinceLastFps
            
            val runtime = Runtime.getRuntime()
            val memUsageMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)

            _stats.value = _stats.value.copy(
                uiFps = fps,
                frameTimeMs = frameTimeMs,
                droppedFrames = droppedFramesTotal,
                memoryUsageMb = memUsageMb
            )

            frameCount = 0
            lastFpsCalculationTime = frameTimeNanos
        }

        lastFrameTimeNanos = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun updateNativeTime(timeMs: Float) {
        _nativeStats.value = _nativeStats.value.copy(nativeTransformTimeMs = timeMs)
    }
    
    fun updateActiveEngine(engine: String) {
        _stats.value = _stats.value.copy(activeEngine = engine)
    }
}
