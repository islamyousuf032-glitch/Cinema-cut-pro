package com.example.model.colorgrade.engine

import android.graphics.Bitmap
import com.example.model.colorgrade.ColorGradeStack

object CpuColorGradeReferenceProcessor {


    fun processLayers(bitmap: Bitmap, layerStack: com.example.model.colorgrade.ColorGradeLayerStack): Bitmap {
        if (!layerStack.enabled) return bitmap
        var currentBitmap = bitmap
        for (layer in layerStack.layers) {
            if (layer.enabled) {
                // Apply the layer's grade to the bitmap
                val newBitmap = process(currentBitmap, layer.grade)
                // TODO: Blend using layer.opacity
                currentBitmap = newBitmap
            }
        }
        return currentBitmap
    }

    fun process(bitmap: Bitmap, stack: ColorGradeStack): Bitmap {
        if (!stack.enabled) return bitmap

        val width = bitmap.width
        val height = bitmap.height
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val rgb = FloatArray(3)
        val rgbBeforeSkin = FloatArray(3)
        
        for (i in pixels.indices) {
            val color = pixels[i]
            rgb[0] = ((color shr 16) and 0xFF) / 255f
            rgb[1] = ((color shr 8) and 0xFF) / 255f
            rgb[2] = (color and 0xFF) / 255f
            
            // Input Transform
            ColorSpaceTransformEngine.applyInputTransform(rgb, stack.inputTransform)
            
            // Save state for skin tone protection if enabled
            if (stack.skinToneProtection.enabled) {
                rgbBeforeSkin[0] = rgb[0]
                rgbBeforeSkin[1] = rgb[1]
                rgbBeforeSkin[2] = rgb[2]
            }

            // Primary Corrections
            PrimaryColorCorrector.process(rgb, stack.primaryCorrections)

            // Curves
            CurveProcessor.process(rgb, stack.curves)

            // HSL & Selective Color (executed inside qualifier mask if needed)
            val qualifierMask = HslQualifierProcessor.getMask(rgb, stack.hslAdjustments)
            if (stack.hslAdjustments.showMatte) {
                val m = Math.round(ColorMath.clamp(qualifierMask) * 255f)
                pixels[i] = (0xFF shl 24) or (m shl 16) or (m shl 8) or m
                continue
            }
            if (qualifierMask > 0f && stack.hslAdjustments.enabled) {
                HslAdjustmentProcessor.process(rgb, qualifierMask, stack.hslAdjustments)
            }
            
            SelectiveColorProcessor.process(rgb, stack.selectiveColor)

            // Skin Tone Protection
            if (stack.skinToneProtection.enabled) {
                SkinToneProtectionProcessor.process(rgbBeforeSkin, rgb, stack.skinToneProtection)
            }

            // LUT Stack
            LutProcessor.process(rgb, stack.lutStack)

            // HDR Tone Mapping / Output Clamp
            HdrToneMappingProcessor.process(rgb, stack.hdrToneMapping)

            val rInt = Math.round(ColorMath.clamp(rgb[0]) * 255f)
            val gInt = Math.round(ColorMath.clamp(rgb[1]) * 255f)
            val bInt = Math.round(ColorMath.clamp(rgb[2]) * 255f)
            
            pixels[i] = (0xFF shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
        }
        
        outputBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outputBitmap
    }
}

interface GpuColorGradeRendererInterface {
    fun setGradeParameters(stack: ColorGradeStack)
    // Methods for shader injection / texture passing
}
