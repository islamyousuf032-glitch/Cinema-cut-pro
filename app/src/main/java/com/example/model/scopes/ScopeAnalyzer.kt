package com.example.model.scopes

import android.graphics.Bitmap

interface ScopeAnalyzer {
    fun analyze(bitmap: Bitmap, output: ScopeData)
}
