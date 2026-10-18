#include "color_eval.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <vector>

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

void ColorEval::applyClipAdjustments(RenderFrame& frame, const ClipColorAdjustments& adjustments) {
    if (frame.buffer == nullptr || frame.format != 0 || frame.width <= 0 || frame.height <= 0 ||
        frame.stride < frame.width * 4) {
        return;
    }

    const float exposure = std::clamp(finiteOr(adjustments.exposureStops, 0.0f), -5.0f, 5.0f);
    const float exposureMultiplier = std::exp2(exposure);
    const float brightness = std::clamp(finiteOr(adjustments.brightness, 0.0f), -1.0f, 1.0f);
    const float contrast = std::clamp(finiteOr(adjustments.contrast, 0.0f), -1.0f, 1.0f);
    const float saturation = std::clamp(finiteOr(adjustments.saturation, 1.0f), 0.0f, 2.0f);
    const float vibrance = std::clamp(finiteOr(adjustments.vibrance, 0.0f), -1.0f, 1.0f);
    const float temperature = std::clamp(finiteOr(adjustments.temperature, 0.0f), -1.0f, 1.0f);
    const float tint = std::clamp(finiteOr(adjustments.tint, 0.0f), -1.0f, 1.0f);
    const float detailAmount = std::clamp(finiteOr(adjustments.detailAmount, 0.0f), -1.0f, 3.0f);

    const float temperatureRed = temperature > 0.0f ? 1.0f + temperature * 0.2f : 1.0f;
    const float temperatureBlue = temperature < 0.0f ? 1.0f - temperature * 0.2f : 1.0f;
    const float tintGreen = tint > 0.0f ? 1.0f + tint * 0.2f : 1.0f;
    const float tintRed = tint < 0.0f ? 1.0f - tint * 0.2f : 1.0f;
    const float contrastFactor = std::max(0.0f, 1.0f + contrast);

    for (int y = 0; y < frame.height; ++y) {
        uint8_t* row = frame.buffer + static_cast<size_t>(y) * static_cast<size_t>(frame.stride);
        for (int x = 0; x < frame.width; ++x) {
            uint8_t* pixel = row + static_cast<size_t>(x) * 4U;
            float red = pixel[0] / 255.0f;
            float green = pixel[1] / 255.0f;
            float blue = pixel[2] / 255.0f;

            red *= temperatureRed * tintRed * exposureMultiplier;
            green *= tintGreen * exposureMultiplier;
            blue *= temperatureBlue * exposureMultiplier;
            red += brightness * 0.2f;
            green += brightness * 0.2f;
            blue += brightness * 0.2f;

            if (contrast != 0.0f) {
                red = 0.5f + (red - 0.5f) * contrastFactor;
                green = 0.5f + (green - 0.5f) * contrastFactor;
                blue = 0.5f + (blue - 0.5f) * contrastFactor;
            }

            const float luma = 0.2126f * red + 0.7152f * green + 0.0722f * blue;
            red = luma + (red - luma) * saturation;
            green = luma + (green - luma) * saturation;
            blue = luma + (blue - luma) * saturation;

            if (vibrance != 0.0f) {
                const float chroma = std::max({red, green, blue}) - std::min({red, green, blue});
                const float vibranceMultiplier = std::max(0.0f, 1.0f + vibrance * (1.0f - chroma));
                red = luma + (red - luma) * vibranceMultiplier;
                green = luma + (green - luma) * vibranceMultiplier;
                blue = luma + (blue - luma) * vibranceMultiplier;
            }

            pixel[0] = toByte(red);
            pixel[1] = toByte(green);
            pixel[2] = toByte(blue);
            // Alpha is preserved exactly.
        }
    }

    if (detailAmount == 0.0f || frame.width < 3 || frame.height < 3) return;

    const size_t stride = static_cast<size_t>(frame.stride);
    std::vector<uint8_t> previousRow(stride);
    std::vector<uint8_t> currentRow(stride);
    std::vector<uint8_t> nextRow(stride);
    std::copy_n(frame.buffer, stride, previousRow.data());
    std::copy_n(frame.buffer + stride, stride, currentRow.data());
    std::copy_n(frame.buffer + 2U * stride, stride, nextRow.data());

    for (int y = 1; y < frame.height - 1; ++y) {
        uint8_t* destinationRow = frame.buffer + static_cast<size_t>(y) * stride;
        for (int x = 1; x < frame.width - 1; ++x) {
            const size_t pixelOffset = static_cast<size_t>(x) * 4U;
            for (int channel = 0; channel < 3; ++channel) {
                float neighborhoodSum = 0.0f;
                for (int dy = -1; dy <= 1; ++dy) {
                    const uint8_t* sourceRow = dy < 0 ? previousRow.data() : (dy > 0 ? nextRow.data() : currentRow.data());
                    for (int dx = -1; dx <= 1; ++dx) {
                        const size_t sampleOffset = static_cast<size_t>(x + dx) * 4U +
                                                    static_cast<size_t>(channel);
                        neighborhoodSum += sourceRow[sampleOffset] / 255.0f;
                    }
                }
                const float current = currentRow[pixelOffset + static_cast<size_t>(channel)] / 255.0f;
                const float blurred = neighborhoodSum / 9.0f;
                destinationRow[pixelOffset + static_cast<size_t>(channel)] =
                    toByte(current + (current - blurred) * detailAmount);
            }
        }

        if (y + 2 < frame.height) {
            previousRow.swap(currentRow);
            currentRow.swap(nextRow);
            const uint8_t* incomingRow = frame.buffer + static_cast<size_t>(y + 2) * stride;
            std::copy_n(incomingRow, stride, nextRow.data());
        }
    }
}
