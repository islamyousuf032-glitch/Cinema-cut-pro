package com.example.ui.colorgrade

import android.graphics.Bitmap

object NativeColorWheelGenerator {
    init {
        System.loadLibrary("transform_engine")
    }
    
    external fun nativeGenerateColorWheelBitmap(bitmap: Bitmap, width: Int, height: Int, mode: Int)
}
