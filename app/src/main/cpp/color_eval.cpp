#include "color_eval.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace {

float finiteOr(float value, float fallback) {
    return std::isfinite(value) ? value : fallback;
}

float clamp01(float value) {
    return std::clamp(value, 0.0f, 1.0f);
}

float srgbToLinear(float value) {
    return value <= 0.04045f ? value / 12.92f : std::pow((value + 0.055f) / 1.055f, 2.4f);
}

float linearToSrgb(float value) {
    value = clamp01(value);
    return value <= 0.0031308f ? value * 12.92f : 1.055f * std::pow(value, 1.0f / 2.4f) - 0.055f;
}

uint8_t toByte(float value) {
    return static_cast<uint8_t>(std::lround(clamp01(value) * 255.0f));
}

} // namespace

void ColorEval::applyColorAdjustments(
    RenderFrame& frame,
    float exposure,
    float brightness,
    float contrast,
    float saturation) {
    if (frame.buffer == nullptr || frame.format != 0 || frame.width <= 0 || frame.height <= 0 ||
        frame.stride < frame.width * 4) {
        return;
    }

    const float safeExposure = std::clamp(finiteOr(exposure, 0.0f), -16.0f, 16.0f);
    const float exposureMultiplier = std::exp2(safeExposure);
    const float safeBrightness = std::clamp(finiteOr(brightness, 0.0f), -1.0f, 1.0f);
    const float safeContrast = std::clamp(finiteOr(contrast, 1.0f), 0.0f, 4.0f);
    const float safeSaturation = std::clamp(finiteOr(saturation, 1.0f), 0.0f, 4.0f);

    for (int y = 0; y < frame.height; ++y) {
        uint8_t* row = frame.buffer + static_cast<size_t>(y) * static_cast<size_t>(frame.stride);
        for (int x = 0; x < frame.width; ++x) {
            uint8_t* pixel = row + static_cast<size_t>(x) * 4U;
            float red = linearToSrgb(srgbToLinear(pixel[0] / 255.0f) * exposureMultiplier);
            float green = linearToSrgb(srgbToLinear(pixel[1] / 255.0f) * exposureMultiplier);
            float blue = linearToSrgb(srgbToLinear(pixel[2] / 255.0f) * exposureMultiplier);

            red = (red - 0.5f) * safeContrast + 0.5f + safeBrightness;
            green = (green - 0.5f) * safeContrast + 0.5f + safeBrightness;
            blue = (blue - 0.5f) * safeContrast + 0.5f + safeBrightness;

            const float luma = 0.2126f * red + 0.7152f * green + 0.0722f * blue;
            red = luma + safeSaturation * (red - luma);
            green = luma + safeSaturation * (green - luma);
            blue = luma + safeSaturation * (blue - luma);

            pixel[0] = toByte(red);
            pixel[1] = toByte(green);
            pixel[2] = toByte(blue);
            // Alpha is preserved exactly.
        }
    }
}
