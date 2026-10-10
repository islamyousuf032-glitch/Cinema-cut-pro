#include "native_motion_blur.h"
#include <cmath>
#include <algorithm>

namespace transform {

std::vector<float> NativeMotionBlur::computeMotionVector(
        float prevX, float prevY,
        float currX, float currY,
        float nextX, float nextY) {
    // Simple central difference
    float dx = (nextX - prevX) / 2.0f;
    float dy = (nextY - prevY) / 2.0f;
    return {dx, dy};
}

std::vector<float> NativeMotionBlur::computeMotionBlurSamples(
        int sampleCount,
        float shutterAngle,
        float strength) {
    
    std::vector<float> weights(sampleCount, 0.0f);
    // Simple box blur filter over sample count
    for (int i = 0; i < sampleCount; ++i) {
        weights[i] = 1.0f / sampleCount; // Normalization
    }
    return weights;
}

static inline void applyTransformToPoint(float& x, float& y, float tx, float ty, float rot, float sx, float sy, float ax, float ay) {
    // Subtract anchor (assuming normalized coords or pixel? Let's assume normalized)
    x -= ax;
    y -= ay;

    // Scale
    x *= sx;
    y *= sy;

    // Rotate
    float rad = rot * M_PI / 180.0f;
    float cosR = std::cos(rad);
    float sinR = std::sin(rad);
    float nx = x * cosR - y * sinR;
    float ny = x * sinR + y * cosR;

    // Translate
    x = nx + ax + tx;
    y = ny + ay + ty;
}

void NativeMotionBlur::applyTransformMotionBlurRgba8888(
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
        float strength) {
        
    // Calculate directional velocity
    float diffX = (nextX - prevX) * 0.5f;
    float diffY = (nextY - prevY) * 0.5f;
    float diffRot = (nextRot - prevRot) * 0.5f;
    float diffSx = (nextScaleX - prevScaleX) * 0.5f;
    float diffSy = (nextScaleY - prevScaleY) * 0.5f;

    // If no motion, just copy
    if (std::abs(diffX) < 1e-3 && std::abs(diffY) < 1e-3 && 
        std::abs(diffRot) < 1e-3 && 
        std::abs(diffSx) < 1e-3 && std::abs(diffSy) < 1e-3) {
        std::copy(inPixels, inPixels + (width * height * 4), outPixels);
        return;
    }

    // Shutter scale: shutterAngle = 0 to 360, strength = 0 to 1
    float activeStrength = (shutterAngle / 360.0f) * strength;
    float halfExtents = activeStrength * 0.5f;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float normX = static_cast<float>(x) / width;
            float normY = static_cast<float>(y) / height;

            // Base point (reverse mapped? No, we're building an output image from input)
            // Wait, standard way is backward mapping (from out to in).
            // Let's implement a very simple multi-sample accumulation.
            float rAcc = 0, gAcc = 0, bAcc = 0, aAcc = 0;

            for (int s = 0; s < sampleCount; ++s) {
                // time offset from -0.5 to 0.5
                float t = (static_cast<float>(s) / std::max(1, sampleCount - 1)) - 0.5f;
                t *= activeStrength; // scale by shutter / strength
                
                // Interpolated transform parameters at t
                float pX = currX + t * diffX;
                float pY = currY + t * diffY;
                float pRot = currRot + t * diffRot;
                float pSx = currScaleX + t * diffSx;
                float pSy = currScaleY + t * diffSy;

                // We want to know where the OUTPUT pixel (x,y) maps to in the INPUT image.
                // Forward map: in -> out. Backward map: out -> in.
                float inNx = normX;
                float inNy = normY;
                
                // Inverse transform to find source pixel
                inNx -= (anchorX + pX);
                inNy -= (anchorY + pY);
                
                float rad = -pRot * M_PI / 180.0f;
                float cosR = std::cos(rad);
                float sinR = std::sin(rad);
                
                float rx = inNx * cosR - inNy * sinR;
                float ry = inNx * sinR + inNy * cosR;
                
                inNx = rx / (pSx != 0 ? pSx : 1.0f) + anchorX;
                inNy = ry / (pSy != 0 ? pSy : 1.0f) + anchorY;

                int srcX = std::round(inNx * width);
                int srcY = std::round(inNy * height);

                if (srcX >= 0 && srcX < width && srcY >= 0 && srcY < height) {
                    int srcIdx = (srcY * width + srcX) * 4;
                    rAcc += inPixels[srcIdx];
                    gAcc += inPixels[srcIdx + 1];
                    bAcc += inPixels[srcIdx + 2];
                    aAcc += inPixels[srcIdx + 3];
                }
            }

            int outIdx = (y * width + x) * 4;
            outPixels[outIdx]     = static_cast<uint8_t>(std::min(255.0f, rAcc / sampleCount));
            outPixels[outIdx + 1] = static_cast<uint8_t>(std::min(255.0f, gAcc / sampleCount));
            outPixels[outIdx + 2] = static_cast<uint8_t>(std::min(255.0f, bAcc / sampleCount));
            outPixels[outIdx + 3] = static_cast<uint8_t>(std::min(255.0f, aAcc / sampleCount));
        }
    }
}

} // namespace transform
