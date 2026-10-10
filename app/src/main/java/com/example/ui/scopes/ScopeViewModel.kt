package com.example.ui.scopes

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.scopes.HistogramAnalyzer
import com.example.model.scopes.ScopeData
import com.example.model.scopes.VectorscopeAnalyzer
import com.example.model.scopes.WaveformAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScopeViewModel : ViewModel() {

    private val _scopeData = kotlinx.coroutines.flow.MutableStateFlow(ScopeData())
    val scopeData = _scopeData.asStateFlow()

    private val _isAnalyzing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()

    private val histogramAnalyzer = HistogramAnalyzer()
    private val waveformAnalyzer = WaveformAnalyzer()
    private val vectorscopeAnalyzer = VectorscopeAnalyzer()

    // A single worker serializes access to this reusable buffer. New requests replace a pending
    // frame, rather than being dropped while an older frame is still being analyzed.
    private val analysisLock = Any()
    private var pendingRequest: AnalysisRequest? = null
    private var workerRunning = false
    private val sharedScopeData = ScopeData()

    private val targetWidth = 256
    private val targetHeight = 256

    /** Takes ownership of [bitmap] and recycles it after analysis. */
    fun analyzeFrame(
        bitmap: Bitmap,
        params: VideoAdjustmentParams?,
        colorSettings: com.example.model.adjustments.ColorPipelineSettings?
    ) {
        val request = AnalysisRequest(bitmap, params, colorSettings)
        var replaced: AnalysisRequest? = null
        var shouldStartWorker = false
        synchronized(analysisLock) {
            replaced = pendingRequest
            pendingRequest = request
            if (!workerRunning) {
                workerRunning = true
                shouldStartWorker = true
            }
            _isAnalyzing.value = true
        }
        replaced?.bitmap?.takeIf { it !== bitmap }?.recycleSafely()

        if (shouldStartWorker) {
            viewModelScope.launch(Dispatchers.Default) {
                processPendingRequests()
            }
        }
    }

    private fun processPendingRequests() {
        while (true) {
            val request = synchronized(analysisLock) {
                val next = pendingRequest
                if (next == null) {
                    workerRunning = false
                    _isAnalyzing.value = false
                    return
                }
                pendingRequest = null
                next
            }

            try {
                analyzeRequest(request)
            } catch (cancelled: CancellationException) {
                request.bitmap.recycleSafely()
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("ScopeViewModel", "Scope analysis failed", error)
                request.bitmap.recycleSafely()
            }
        }
    }

    private fun analyzeRequest(request: AnalysisRequest) {
        val original = request.bitmap
        val scaled = if (original.width > targetWidth || original.height > targetHeight) {
            Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
        } else {
            original
        }
        var analyzedBitmap = scaled

        try {
            if (request.params != null && request.colorSettings != null) {
                val processor = com.example.model.adjustments.engine.CpuFrameProcessor()
                val engine = com.example.model.adjustments.engine.ColorTransformEngine(request.colorSettings)
                val rawPixels = IntArray(scaled.width * scaled.height)
                scaled.getPixels(rawPixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                val floatPixels = FloatArray(rawPixels.size * 4)
                for (index in rawPixels.indices) {
                    floatPixels[index * 4] = android.graphics.Color.red(rawPixels[index]) / 255f
                    floatPixels[index * 4 + 1] = android.graphics.Color.green(rawPixels[index]) / 255f
                    floatPixels[index * 4 + 2] = android.graphics.Color.blue(rawPixels[index]) / 255f
                    floatPixels[index * 4 + 3] = android.graphics.Color.alpha(rawPixels[index]) / 255f
                }
                val frame = com.example.model.adjustments.engine.AdjustmentPreviewFrame(
                    scaled.width,
                    scaled.height,
                    floatPixels
                )
                val processed = processor.processFrame(frame, request.params, engine, 0L)
                val gradedPixels = IntArray(rawPixels.size)
                for (index in rawPixels.indices) {
                    val r = (processed.pixels[index * 4] * 255f).toInt().coerceIn(0, 255)
                    val g = (processed.pixels[index * 4 + 1] * 255f).toInt().coerceIn(0, 255)
                    val b = (processed.pixels[index * 4 + 2] * 255f).toInt().coerceIn(0, 255)
                    val a = (processed.pixels[index * 4 + 3] * 255f).toInt().coerceIn(0, 255)
                    gradedPixels[index] = android.graphics.Color.argb(a, r, g, b)
                }
                analyzedBitmap = Bitmap.createBitmap(
                    gradedPixels,
                    scaled.width,
                    scaled.height,
                    Bitmap.Config.ARGB_8888
                )
            }

            histogramAnalyzer.analyze(analyzedBitmap, sharedScopeData)
            waveformAnalyzer.analyze(analyzedBitmap, sharedScopeData)
            vectorscopeAnalyzer.analyze(analyzedBitmap, sharedScopeData)

            _scopeData.value = sharedScopeData.copy(
                histogramR = sharedScopeData.histogramR.clone(),
                histogramG = sharedScopeData.histogramG.clone(),
                histogramB = sharedScopeData.histogramB.clone(),
                histogramLuma = sharedScopeData.histogramLuma.clone(),
                waveformLuma = sharedScopeData.waveformLuma.clone(),
                paradeR = sharedScopeData.paradeR.clone(),
                paradeG = sharedScopeData.paradeG.clone(),
                paradeB = sharedScopeData.paradeB.clone(),
                vectorscope = sharedScopeData.vectorscope.clone()
            )
        } finally {
            if (analyzedBitmap !== scaled && analyzedBitmap !== original) analyzedBitmap.recycleSafely()
            if (scaled !== original) scaled.recycleSafely()
            original.recycleSafely()
        }
    }

    override fun onCleared() {
        synchronized(analysisLock) {
            pendingRequest?.bitmap?.recycleSafely()
            pendingRequest = null
        }
        super.onCleared()
    }

    private data class AnalysisRequest(
        val bitmap: Bitmap,
        val params: VideoAdjustmentParams?,
        val colorSettings: com.example.model.adjustments.ColorPipelineSettings?
    )

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }
}
