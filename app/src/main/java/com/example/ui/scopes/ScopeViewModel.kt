package com.example.ui.scopes

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.adjustments.VideoAdjustmentParams
import com.example.model.scopes.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScopeViewModel : ViewModel() {

    private val _scopeData = MutableStateFlow(ScopeData())
    val scopeData: StateFlow<ScopeData> = _scopeData.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val histogramAnalyzer = HistogramAnalyzer()
    private val waveformAnalyzer = WaveformAnalyzer()
    private val vectorscopeAnalyzer = VectorscopeAnalyzer()

    // Using a shared Data object to avoid continuous allocations
    private val sharedScopeData = ScopeData()

    // Downscale target for fast scopes calculation
    private val targetWidth = 256
    private val targetHeight = 256

    fun analyzeFrame(bitmap: Bitmap, params: VideoAdjustmentParams?, colorSettings: com.example.model.adjustments.ColorPipelineSettings?) {
        if (_isAnalyzing.value) return
        
        viewModelScope.launch(Dispatchers.Default) {
            _isAnalyzing.value = true
            
            try {
                // Resize for fast scopes analysis if needed, or analyze directly
                val scaledMap = if (bitmap.width > targetWidth || bitmap.height > targetHeight) {
                     Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                } else {
                     bitmap
                }

                // If params present, apply color transformation
                val mapToAnalyze = if (params != null && colorSettings != null) {
                    val processor = com.example.model.adjustments.engine.CpuFrameProcessor()
                    val engine = com.example.model.adjustments.engine.ColorTransformEngine(colorSettings)
                    val rawPixels = IntArray(scaledMap.width * scaledMap.height)
                    scaledMap.getPixels(rawPixels, 0, scaledMap.width, 0, 0, scaledMap.width, scaledMap.height)
                    val floatPixels = FloatArray(rawPixels.size * 4)
                    for (i in rawPixels.indices) {
                        floatPixels[i * 4] = android.graphics.Color.red(rawPixels[i]) / 255f
                        floatPixels[i * 4 + 1] = android.graphics.Color.green(rawPixels[i]) / 255f
                        floatPixels[i * 4 + 2] = android.graphics.Color.blue(rawPixels[i]) / 255f
                        floatPixels[i * 4 + 3] = android.graphics.Color.alpha(rawPixels[i]) / 255f
                    }
                    val frame = com.example.model.adjustments.engine.AdjustmentPreviewFrame(scaledMap.width, scaledMap.height, floatPixels)
                    val processed = processor.processFrame(frame, params, engine, 0L)
                    val gradedPixels = IntArray(rawPixels.size)
                    for (i in rawPixels.indices) {
                        val r = (processed.pixels[i * 4] * 255f).toInt().coerceIn(0, 255)
                        val g = (processed.pixels[i * 4 + 1] * 255f).toInt().coerceIn(0, 255)
                        val b = (processed.pixels[i * 4 + 2] * 255f).toInt().coerceIn(0, 255)
                        val a = (processed.pixels[i * 4 + 3] * 255f).toInt().coerceIn(0, 255)
                        gradedPixels[i] = android.graphics.Color.argb(a, r, g, b)
                    }
                    Bitmap.createBitmap(gradedPixels, scaledMap.width, scaledMap.height, Bitmap.Config.ARGB_8888).also {
                        if (scaledMap !== bitmap) scaledMap.recycle()
                    }
                } else {
                    scaledMap
                }

                // Analyze
                histogramAnalyzer.analyze(mapToAnalyze, sharedScopeData)
                waveformAnalyzer.analyze(mapToAnalyze, sharedScopeData)
                vectorscopeAnalyzer.analyze(mapToAnalyze, sharedScopeData)

                if (mapToAnalyze !== bitmap) {
                    mapToAnalyze.recycle()
                }

                // Push update
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
                _isAnalyzing.value = false
            }
        }
    }
}
