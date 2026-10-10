package com.example.model.colorgrade.engine

import java.nio.ByteBuffer

object NativeColorGradeEngine {
    init {
        System.loadLibrary("transform_engine")
    }

    external fun nativeEvaluateColorLayerStack(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        layerData: FloatArray,
        numLayers: Int
    )

    external fun nativeApplyBasicColorLayer(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        exposure: Float,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        opacity: Float
    )

    external fun nativeBuildCurveLut(
        points: FloatArray,
        numPoints: Int,
        outLut: ByteArray
    )

    external fun nativeApplyExposure(pixels: IntArray, width: Int, height: Int, exposure: Float)
    external fun nativeApplyContrast(pixels: IntArray, width: Int, height: Int, contrast: Float)
    external fun nativeApplySaturation(pixels: IntArray, width: Int, height: Int, saturation: Float)
    external fun nativeApplyTemperatureTint(pixels: IntArray, width: Int, height: Int, temperature: Float, tint: Float)
    external fun nativeApplyLiftGammaGain(pixels: IntArray, width: Int, height: Int, liftR: Float, liftG: Float, liftB: Float, gammaR: Float, gammaG: Float, gammaB: Float, gainR: Float, gainG: Float, gainB: Float)
}
