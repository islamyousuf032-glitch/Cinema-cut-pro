#include "transform_eval.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace {

float finiteOr(float value, float fallback) {
    return std::isfinite(value) ? value : fallback;
}

uint8_t sampleBilinear(const uint8_t* source, int stride, int width, int height, float x, float y, int channel) {
    if (x < 0.0f || y < 0.0f || x > static_cast<float>(width - 1) || y > static_cast<float>(height - 1)) {
        return 0;
    }

    const int x0 = static_cast<int>(std::floor(x));
    const int y0 = static_cast<int>(std::floor(y));
    const int x1 = std::min(x0 + 1, width - 1);
    const int y1 = std::min(y0 + 1, height - 1);
    const float wx = x - static_cast<float>(x0);
    const float wy = y - static_cast<float>(y0);

    const auto valueAt = [source, stride, channel](int sx, int sy) {
        return static_cast<float>(source[static_cast<size_t>(sy) * static_cast<size_t>(stride) +
                                         static_cast<size_t>(sx) * 4U + static_cast<size_t>(channel)]);
    };
    const float top = valueAt(x0, y0) + (valueAt(x1, y0) - valueAt(x0, y0)) * wx;
    const float bottom = valueAt(x0, y1) + (valueAt(x1, y1) - valueAt(x0, y1)) * wx;
    return static_cast<uint8_t>(std::lround(std::clamp(top + (bottom - top) * wy, 0.0f, 255.0f)));
}

} // namespace

void TransformEval::applyTransform(
    RenderFrame& frame,
    float scaleX,
    float scaleY,
    float rotation,
    float posX,
    float posY,
    float opacity) {
    if (frame.buffer == nullptr || frame.format != 0 || frame.width <= 0 || frame.height <= 0 ||
        frame.stride < frame.width * 4) {
        return;
    }

    scaleX = finiteOr(scaleX, 0.0f);
    scaleY = finiteOr(scaleY, 0.0f);
    rotation = finiteOr(rotation, 0.0f);
    posX = finiteOr(posX, 0.0f);
    posY = finiteOr(posY, 0.0f);
    opacity = std::clamp(finiteOr(opacity, 0.0f), 0.0f, 1.0f);

    if (scaleX == 1.0f && scaleY == 1.0f && rotation == 0.0f && posX == 0.0f && posY == 0.0f && opacity == 1.0f) {
        return;
    }

    const size_t bufferSize = static_cast<size_t>(frame.stride) * static_cast<size_t>(frame.height);
    std::vector<uint8_t> source(frame.buffer, frame.buffer + bufferSize);
    std::fill(frame.buffer, frame.buffer + bufferSize, 0);

    if (scaleX == 0.0f || scaleY == 0.0f || opacity == 0.0f) return;

    const float radians = rotation * 0.01745329251994329577f;
    const float cosine = std::cos(radians);
    const float sine = std::sin(radians);
    const float outputCenterX = frame.width * 0.5f + posX * frame.width;
    const float outputCenterY = frame.height * 0.5f + posY * frame.height;
    const float sourceCenterX = frame.width * 0.5f;
    const float sourceCenterY = frame.height * 0.5f;

    for (int y = 0; y < frame.height; ++y) {
        for (int x = 0; x < frame.width; ++x) {
            const float dx = (static_cast<float>(x) + 0.5f) - outputCenterX;
            const float dy = (static_cast<float>(y) + 0.5f) - outputCenterY;
            // Inverse of translate -> rotate -> scale around the image center.
            const float sourceOffsetX = (cosine * dx + sine * dy) / scaleX;
            const float sourceOffsetY = (-sine * dx + cosine * dy) / scaleY;
            const float sourceX = sourceCenterX + sourceOffsetX - 0.5f;
            const float sourceY = sourceCenterY + sourceOffsetY - 0.5f;
            uint8_t* destination = frame.buffer + static_cast<size_t>(y) * static_cast<size_t>(frame.stride) +
                                   static_cast<size_t>(x) * 4U;
            for (int channel = 0; channel < 4; ++channel) {
                const uint8_t sampled = sampleBilinear(source.data(), frame.stride, frame.width, frame.height,
                                                       sourceX, sourceY, channel);
                if (channel == 3) {
                    destination[channel] = static_cast<uint8_t>(std::lround(sampled * opacity));
                } else {
                    destination[channel] = sampled;
                }
            }
        }
    }
}
