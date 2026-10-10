#pragma once

#include <vector>
#include <cstdint>

namespace transform {

class NativeMotionBlur {
public:
    static std::vector<float> computeMotionVector(
        float prevX, float prevY,
        float currX, float currY,
        float nextX, float nextY);
        
    static std::vector<float> computeMotionBlurSamples(
        int sampleCount,
        float shutterAngle,
        float strength);

    static void applyTransformMotionBlurRgba8888(
        uint8_t* outPixels,
        const uint8_t* inPixels,
        int width,
        int height,
        float currX, float currY, float currRot, float currScaleX, float currScaleY,
        float prevX, float prevY, float prevRot, float prevScaleX, float prevScaleY,
        float nextX, float nextY, float nextRot, float nextScaleX, float nextScaleY,
        float anchorX, float anchorY,
        float shutterAngle,
        int sampleCount,
        float strength);
};

} // namespace transform
