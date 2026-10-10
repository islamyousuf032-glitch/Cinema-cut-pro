package com.example.ui.colorgrade

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

class ColorWheelRenderer {
    var activeMode: ColorWheelRenderMode = ColorWheelRenderMode.COMPOSE_SWEEP_GRADIENT
        private set
        
    var fallbackReason: String = "None"
        private set

    init {
        determineBestMode()
    }

    private fun determineBestMode() {
        if (DeviceRenderDiagnostics.isSweepGradientBuggy()) {
            fallbackReason = "Device known for SweepGradient bugs"
            switchToBitmapMode()
        }
    }

    private fun switchToBitmapMode() {
        // Try CPU first based on priority 3
        activeMode = ColorWheelRenderMode.CPU_BITMAP
        try {
            val bitmap = ColorWheelBitmapGenerator.generateCpuBitmap(128)
            if (validateBitmap(bitmap)) {
                return // CPU works
            } else {
                fallbackReason = "CPU bitmap validation failed"
            }
        } catch (e: Exception) {
            fallbackReason = "CPU generator crashed: ${e.message}"
            Log.e("ColorWheelRenderer", "CPU fallback failed", e)
        }
        
        // Try Native next based on priority 4
        activeMode = ColorWheelRenderMode.NATIVE_BITMAP
        try {
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            NativeColorWheelGenerator.nativeGenerateColorWheelBitmap(bitmap, 128, 128, 0)
            if (validateBitmap(bitmap)) {
                return // Native works
            } else {
                fallbackReason = "Native bitmap validation failed (pixels black)"
            }
        } catch (e: Exception) {
            fallbackReason = "Native generator crashed: ${e.message}"
            Log.e("ColorWheelRenderer", "Native fallback failed", e)
        } catch (e: UnsatisfiedLinkError) {
            fallbackReason = "Native library not loaded"
            Log.e("ColorWheelRenderer", "Native fallback failed", e)
        }

        // If all fail, we just stay on CPU and hope for the best
        activeMode = ColorWheelRenderMode.CPU_BITMAP
    }

    private fun validateBitmap(bitmap: Bitmap): Boolean {
        val centerColor = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val edgeColor = bitmap.getPixel(bitmap.width - 2, bitmap.height / 2)
        
        val centerA = Color.alpha(centerColor)
        val edgeA = Color.alpha(edgeColor)
        
        // If it's completely transparent or completely black, it failed.
        if (centerA == 0 && edgeA == 0) return false
        
        val centerSum = Color.red(centerColor) + Color.green(centerColor) + Color.blue(centerColor)
        val edgeSum = Color.red(edgeColor) + Color.green(edgeColor) + Color.blue(edgeColor)
        
        if (centerSum == 0 && edgeSum == 0) return false
        
        return true
    }

    fun generateBitmap(size: Int): Bitmap {
        // In case SweepGradient was selected but we are asked for bitmap
        val modeToRun = if (activeMode == ColorWheelRenderMode.COMPOSE_SWEEP_GRADIENT) {
            ColorWheelRenderMode.NATIVE_BITMAP
        } else {
            activeMode
        }

        return if (modeToRun == ColorWheelRenderMode.NATIVE_BITMAP) {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                NativeColorWheelGenerator.nativeGenerateColorWheelBitmap(bmp, size, size, 0)
                bmp
            } catch (e: Throwable) {
                ColorWheelBitmapGenerator.generateCpuBitmap(size)
            }
        } else {
            ColorWheelBitmapGenerator.generateCpuBitmap(size)
        }
    }
}
