package com.example.ui.colorgrade

import android.os.Build

object DeviceRenderDiagnostics {
    val deviceModel: String = Build.MODEL ?: "Unknown"
    val deviceManufacturer: String = Build.MANUFACTURER ?: "Unknown"
    val androidVersion: String = Build.VERSION.RELEASE ?: "Unknown"
    val sdkInt: Int = Build.VERSION.SDK_INT
    
    // Samsung Galaxy F23 has issues with SweepGradient
    fun isSweepGradientBuggy(): Boolean {
        val model = deviceModel.uppercase()
        return model.contains("SM-E236") || model.contains("F23")
    }
}
