#include "keyframe_interpolator.h"
#include <cmath>
#include <algorithm>

namespace transform {

float KeyframeInterpolator::EaseIn(float t) {
    return t * t * t;
}

float KeyframeInterpolator::EaseOut(float t) {
    float f = t - 1.0f;
    return f * f * f + 1.0f;
}

float KeyframeInterpolator::EaseInOut(float t) {
    if (t < 0.5f) {
        return 4.0f * t * t * t;
    } else {
        float f = ((2.0f * t) - 2.0f);
        return 0.5f * f * f * f + 1.0f;
    }
}

float KeyframeInterpolator::Bezier(float t, float p0, float p1, float p2, float p3) {
    float u = 1.0f - t;
    float tt = t * t;
    float uu = u * u;
    float uuu = uu * u;
    float ttt = tt * t;
    
    return uuu * p0 + 3.0f * uu * t * p1 + 3.0f * u * tt * p2 + ttt * p3;
}

float KeyframeInterpolator::Interpolate(long long currentFrame, const Keyframe& kf1, const Keyframe& kf2) {
    if (currentFrame <= kf1.frame) return kf1.value;
    if (currentFrame >= kf2.frame) return kf2.value;

    if (kf1.interpolation == InterpolationType::HOLD) {
        return kf1.value;
    }

    float t = static_cast<float>(currentFrame - kf1.frame) / static_cast<float>(kf2.frame - kf1.frame);

    switch (kf1.interpolation) {
        case InterpolationType::LINEAR:
            return kf1.value + t * (kf2.value - kf1.value);
        case InterpolationType::EASE_IN:
            return kf1.value + EaseIn(t) * (kf2.value - kf1.value);
        case InterpolationType::EASE_OUT:
            return kf1.value + EaseOut(t) * (kf2.value - kf1.value);
        case InterpolationType::EASE_IN_OUT:
            return kf1.value + EaseInOut(t) * (kf2.value - kf1.value);
        case InterpolationType::BEZIER:
            // Assuming bezierHandleLeft/Right are control points in 1D equivalent (simplified)
            // A full implementation would find the t where bezier_x(t) = target_time, then compute bezier_y(t)
            return Bezier(t, kf1.value, kf1.value + kf1.bezierHandleRight, kf2.value + kf2.bezierHandleLeft, kf2.value);
        default:
            return kf1.value + t * (kf2.value - kf1.value);
    }
}

float KeyframeInterpolator::InterpolateArray(long long currentFrame, const std::vector<Keyframe>& keyframes, float defaultValue) {
    if (keyframes.empty()) return defaultValue;
    if (keyframes.size() == 1) return keyframes[0].value;
    
    if (currentFrame <= keyframes.front().frame) return keyframes.front().value;
    if (currentFrame >= keyframes.back().frame) return keyframes.back().value;

    for (size_t i = 0; i < keyframes.size() - 1; ++i) {
        if (currentFrame >= keyframes[i].frame && currentFrame < keyframes[i+1].frame) {
            return Interpolate(currentFrame, keyframes[i], keyframes[i+1]);
        }
    }
    return defaultValue;
}

} // namespace transform
