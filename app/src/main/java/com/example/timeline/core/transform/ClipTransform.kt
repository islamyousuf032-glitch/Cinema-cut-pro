package com.example.timeline.core.transform

import kotlinx.serialization.Serializable

@Serializable
data class PairFloat(val first: Float, val second: Float)

@Serializable
data class ClipTransform(
    val transformParams: TransformParams = TransformParams(),
    val cropParams: CropParams = CropParams(),
    val perspectiveParams: PerspectiveParams = PerspectiveParams(),
    val motionBlurParams: MotionBlurParams = MotionBlurParams(),
    val fitMode: TransformFitMode = TransformFitMode.FIT,
    val blendMode: BlendMode = BlendMode.NORMAL,
    
    // Tracks
    val positionTrack: TransformKeyframeTrack<PairFloat> = TransformKeyframeTrack("position"),
    val scaleTrack: TransformKeyframeTrack<PairFloat> = TransformKeyframeTrack("scale"),
    val rotationTrack: TransformKeyframeTrack<Float> = TransformKeyframeTrack("rotation"),
    val anchorTrack: TransformKeyframeTrack<PairFloat> = TransformKeyframeTrack("anchor"),
    val opacityTrack: TransformKeyframeTrack<Float> = TransformKeyframeTrack("opacity"),
    val cropTrack: TransformKeyframeTrack<CropParams> = TransformKeyframeTrack("crop"),
    val perspectiveTrack: TransformKeyframeTrack<PerspectiveParams> = TransformKeyframeTrack("perspective"),
    val motionBlurStrengthTrack: TransformKeyframeTrack<Float> = TransformKeyframeTrack("motionBlurStrength")
) {

    companion object {
        fun defaultTransform(): ClipTransform = ClipTransform()
    }

    val positionX: Float get() = transformParams.positionX
    val positionY: Float get() = transformParams.positionY
    val scaleX: Float get() = transformParams.scaleX
    val scaleY: Float get() = transformParams.scaleY
    val rotationDegrees: Float get() = transformParams.rotationDegrees
    val flipHorizontal: Boolean get() = transformParams.flipHorizontal
    val flipVertical: Boolean get() = transformParams.flipVertical
    val opacity: Float get() = transformParams.opacity
    val anchorPointX: Float get() = transformParams.anchorX
    val anchorPointY: Float get() = transformParams.anchorY

    fun resetTransform() = defaultTransform()
    
    fun resetPosition() = copy(
        transformParams = transformParams.copy(positionX = 0f, positionY = 0f), 
        positionTrack = TransformKeyframeTrack("position")
    )
    
    fun resetScale() = copy(
        transformParams = transformParams.copy(scaleX = 1f, scaleY = 1f), 
        scaleTrack = TransformKeyframeTrack("scale")
    )
    
    fun resetRotation() = copy(
        transformParams = transformParams.copy(rotationDegrees = 0f), 
        rotationTrack = TransformKeyframeTrack("rotation")
    )
    
    fun resetCrop() = copy(
        cropParams = CropParams(), 
        cropTrack = TransformKeyframeTrack("crop")
    )
    
    fun resetPerspective() = copy(
        perspectiveParams = PerspectiveParams(), 
        perspectiveTrack = TransformKeyframeTrack("perspective")
    )
    
    fun hasKeyframes(): Boolean {
        return positionTrack.hasKeyframes() ||
               scaleTrack.hasKeyframes() ||
               rotationTrack.hasKeyframes() ||
               anchorTrack.hasKeyframes() ||
               opacityTrack.hasKeyframes() ||
               cropTrack.hasKeyframes() ||
               perspectiveTrack.hasKeyframes() ||
               motionBlurStrengthTrack.hasKeyframes()
    }

    fun addPositionKeyframe(frame: Long, x: Float, y: Float) = copy(positionTrack = positionTrack.addKeyframe(TransformKeyframe(parameterId = "position", frame = frame, value = PairFloat(x, y))))
    fun addScaleKeyframe(frame: Long, x: Float, y: Float) = copy(scaleTrack = scaleTrack.addKeyframe(TransformKeyframe(parameterId = "scale", frame = frame, value = PairFloat(x, y))))
    fun addRotationKeyframe(frame: Long, rotation: Float) = copy(rotationTrack = rotationTrack.addKeyframe(TransformKeyframe(parameterId = "rotation", frame = frame, value = rotation)))
    fun addOpacityKeyframe(frame: Long, opacity: Float) = copy(opacityTrack = opacityTrack.addKeyframe(TransformKeyframe(parameterId = "opacity", frame = frame, value = opacity)))
    fun addAnchorKeyframe(frame: Long, x: Float, y: Float) = copy(anchorTrack = anchorTrack.addKeyframe(TransformKeyframe(parameterId = "anchor", frame = frame, value = PairFloat(x, y))))
    
    fun copyTransform(): ClipTransform = this.copy()
    
    fun pasteTransform(other: ClipTransform): ClipTransform = other.copy()
    
    fun evaluateTransformAtFrame(frame: Long): ClipTransform {
        if (!hasKeyframes()) return this
        
        val pos = positionTrack.evaluate(frame, PairFloat(positionX, positionY))
        val sc = scaleTrack.evaluate(frame, PairFloat(scaleX, scaleY))
        val rot = rotationTrack.evaluate(frame, rotationDegrees)
        val anc = anchorTrack.evaluate(frame, PairFloat(anchorPointX, anchorPointY))
        val op = opacityTrack.evaluate(frame, opacity)
        val crp = cropTrack.evaluate(frame, cropParams)
        val pers = perspectiveTrack.evaluate(frame, perspectiveParams)
        val mblurStr = motionBlurStrengthTrack.evaluate(frame, motionBlurParams.strength)
        
        return copy(
            transformParams = transformParams.copy(
                positionX = pos.first,
                positionY = pos.second,
                scaleX = sc.first,
                scaleY = sc.second,
                rotationDegrees = rot,
                anchorX = anc.first,
                anchorY = anc.second,
                opacity = op
            ),
            cropParams = crp,
            perspectiveParams = pers,
            motionBlurParams = motionBlurParams.copy(strength = mblurStr)
        )
    }
}
