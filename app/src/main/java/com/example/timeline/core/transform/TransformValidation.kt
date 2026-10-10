package com.example.timeline.core.transform

object TransformValidation {
    fun validateTransform(transform: ClipTransform): ClipTransform {
        val validOpacity = transform.opacity.coerceIn(0f, 1f)
        val validCropLeft = transform.cropParams.cropLeft.coerceIn(0f, 1f)
        val validCropRight = transform.cropParams.cropRight.coerceIn(0f, 1f)
        val validCropTop = transform.cropParams.cropTop.coerceIn(0f, 1f)
        val validCropBottom = transform.cropParams.cropBottom.coerceIn(0f, 1f)
        val validCropFeather = transform.cropParams.cropFeather.coerceIn(0f, 1f)
        
        val validScaleX = if (transform.scaleX.isNaN()) 1f else transform.scaleX
        val validScaleY = if (transform.scaleY.isNaN()) 1f else transform.scaleY

        val validAnchorX = transform.anchorPointX.coerceIn(0f, 1f)
        val validAnchorY = transform.anchorPointY.coerceIn(0f, 1f)
        
        val cleanedTransformParams = transform.transformParams.copy(
            opacity = validOpacity,
            scaleX = validScaleX,
            scaleY = validScaleY,
            anchorX = validAnchorX,
            anchorY = validAnchorY
        )
        
        val cleanedCropParams = transform.cropParams.copy(
            cropLeft = validCropLeft,
            cropRight = validCropRight,
            cropTop = validCropTop,
            cropBottom = validCropBottom,
            cropFeather = validCropFeather
        )
        
        return transform.copy(
            transformParams = cleanedTransformParams,
            cropParams = cleanedCropParams
        )
    }
}
