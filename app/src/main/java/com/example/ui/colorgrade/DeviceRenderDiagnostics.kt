package com.example.ui.colorgrade

import android.os.Build

object DeviceRenderDiagnostics {
    val deviceModel: String = Build.MODEL ?: "Unknown"
    val deviceManufacturer: String = Build.MANUFACTURER ?: "Unknown"
    val androidVersion: String = Build.VERSION.RELEASE ?: "Unknown"
    val sdkInt: Int = Build.VERSION.SDK_INT
    
    // Provisional model-name workaround only; this has not been verified on the SM-E236B/API 34 target.
    fun isSweepGradientBuggy(): Boolean {
        val model = deviceModel.uppercase()
        return model.contains("SM-E236") || model.contains("F23")
    }
}
