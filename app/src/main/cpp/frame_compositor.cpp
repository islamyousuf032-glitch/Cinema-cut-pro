#include "frame_compositor.h"

#include "blend_mode_processor.h"

#include <algorithm>
#include <cmath>
#include <cstdint>

namespace {

bool isValidRgba(const RenderFrame& frame) {
    return frame.buffer != nullptr && frame.format == 0 && frame.width > 0 && frame.height > 0 &&
           frame.stride >= frame.width * 4;
}

bool isValidI420(const RenderFrame& frame) {
    return frame.buffer != nullptr && frame.format == 1 && frame.width > 0 && frame.height > 0 &&
           (frame.width % 2) == 0 && (frame.height % 2) == 0 && frame.stride >= frame.width;
}

uint8_t clampByte(float value) {
    if (!std::isfinite(value)) return 0;
    return static_cast<uint8_t>(std::lround(std::clamp(value, 0.0f, 255.0f)));
}

struct YuvPixel {
    float y;
    float u;
    float v;
};

YuvPixel rgbToLimitedBt601(float red, float green, float blue) {
    return {
        16.0f + 0.256788f * red + 0.504129f * green + 0.097906f * blue,
        128.0f - 0.148223f * red - 0.290993f * green + 0.439216f * blue,
        128.0f + 0.439216f * red - 0.367788f * green - 0.071427f * blue
    };
}

} // namespace

void FrameCompositor::compositeFrames(RenderFrame& bgFrame, const RenderFrame& fgFrame, int blendMode) {
    if (!isValidRgba(bgFrame) || !isValidRgba(fgFrame) ||
        bgFrame.width != fgFrame.width || bgFrame.height != fgFrame.height) {
        return;
    }

    const int lastMode = static_cast<int>(transform::BlendMode::SOFT_LIGHT);
    const auto mode = blendMode >= 0 && blendMode <= lastMode
        ? static_cast<transform::BlendMode>(blendMode)
        : transform::BlendMode::NORMAL;

    for (int y = 0; y < bgFrame.height; ++y) {
        transform::BlendModeProcessor::Process(
            mode,
            bgFrame.buffer + static_cast<size_t>(y) * static_cast<size_t>(bgFrame.stride),
            fgFrame.buffer + static_cast<size_t>(y) * static_cast<size_t>(fgFrame.stride),
            bgFrame.width,
            1,
            1.0f);
    }
}

void FrameCompositor::convertRgbaToYuv420(const RenderFrame& rgbaFrame, RenderFrame& yuvFrame) {
    if (!isValidRgba(rgbaFrame) || !isValidI420(yuvFrame) ||
        rgbaFrame.width != yuvFrame.width || rgbaFrame.height != yuvFrame.height) {
        return;
    }

    const int width = rgbaFrame.width;
    const int height = rgbaFrame.height;
    const size_t yPlaneSize = static_cast<size_t>(yuvFrame.stride) * static_cast<size_t>(height);
    const size_t chromaWidth = static_cast<size_t>(width / 2);
    const size_t chromaHeight = static_cast<size_t>(height / 2);
    uint8_t* yPlane = yuvFrame.buffer;
    uint8_t* uPlane = yuvFrame.buffer + yPlaneSize;
    uint8_t* vPlane = uPlane + chromaWidth * chromaHeight;

    for (int y = 0; y < height; ++y) {
        const uint8_t* sourceRow = rgbaFrame.buffer + static_cast<size_t>(y) * static_cast<size_t>(rgbaFrame.stride);
        uint8_t* destinationRow = yPlane + static_cast<size_t>(y) * static_cast<size_t>(yuvFrame.stride);
        for (int x = 0; x < width; ++x) {
            const uint8_t* pixel = sourceRow + static_cast<size_t>(x) * 4U;
            destinationRow[x] = clampByte(rgbToLimitedBt601(pixel[0], pixel[1], pixel[2]).y);
        }
    }

    for (int y = 0; y < height; y += 2) {
        for (int x = 0; x < width; x += 2) {
            float red = 0.0f;
            float green = 0.0f;
            float blue = 0.0f;
            for (int dy = 0; dy < 2; ++dy) {
                const uint8_t* row = rgbaFrame.buffer +
                    static_cast<size_t>(y + dy) * static_cast<size_t>(rgbaFrame.stride);
                for (int dx = 0; dx < 2; ++dx) {
                    const uint8_t* pixel = row + static_cast<size_t>(x + dx) * 4U;
                    red += pixel[0];
                    green += pixel[1];
                    blue += pixel[2];
                }
            }
            const YuvPixel chroma = rgbToLimitedBt601(red * 0.25f, green * 0.25f, blue * 0.25f);
            const size_t index = static_cast<size_t>(y / 2) * chromaWidth + static_cast<size_t>(x / 2);
            uPlane[index] = clampByte(chroma.u);
            vPlane[index] = clampByte(chroma.v);
        }
    }
}

void FrameCompositor::blendRgbaWatermarkIntoYuv420(
    RenderFrame& yuvFrame,
    const RenderFrame& watermarkFrame) {
    if (!isValidI420(yuvFrame) || !isValidRgba(watermarkFrame) ||
        yuvFrame.width != watermarkFrame.width || yuvFrame.height != watermarkFrame.height) {
        return;
    }

    const int width = yuvFrame.width;
    const int height = yuvFrame.height;
    const size_t yPlaneSize = static_cast<size_t>(yuvFrame.stride) * static_cast<size_t>(height);
    const size_t chromaWidth = static_cast<size_t>(width / 2);
    const size_t chromaHeight = static_cast<size_t>(height / 2);
    uint8_t* yPlane = yuvFrame.buffer;
    uint8_t* uPlane = yuvFrame.buffer + yPlaneSize;
    uint8_t* vPlane = uPlane + chromaWidth * chromaHeight;

    for (int y = 0; y < height; ++y) {
        const uint8_t* overlayRow = watermarkFrame.buffer + static_cast<size_t>(y) * static_cast<size_t>(watermarkFrame.stride);
        uint8_t* destinationRow = yPlane + static_cast<size_t>(y) * static_cast<size_t>(yuvFrame.stride);
        for (int x = 0; x < width; ++x) {
            const uint8_t* pixel = overlayRow + static_cast<size_t>(x) * 4U;
            const float alpha = pixel[3] / 255.0f;
            if (alpha <= 0.0f) continue;
            const float overlayY = rgbToLimitedBt601(pixel[0], pixel[1], pixel[2]).y;
            destinationRow[x] = clampByte(destinationRow[x] * (1.0f - alpha) + overlayY * alpha);
        }
    }

    for (int y = 0; y < height; y += 2) {
        for (int x = 0; x < width; x += 2) {
            float alphaSum = 0.0f;
            float redPremultiplied = 0.0f;
            float greenPremultiplied = 0.0f;
            float bluePremultiplied = 0.0f;
            for (int dy = 0; dy < 2; ++dy) {
                const uint8_t* row = watermarkFrame.buffer +
                    static_cast<size_t>(y + dy) * static_cast<size_t>(watermarkFrame.stride);
                for (int dx = 0; dx < 2; ++dx) {
                    const uint8_t* pixel = row + static_cast<size_t>(x + dx) * 4U;
                    const float alpha = pixel[3] / 255.0f;
                    alphaSum += alpha;
                    redPremultiplied += pixel[0] * alpha;
                    greenPremultiplied += pixel[1] * alpha;
                    bluePremultiplied += pixel[2] * alpha;
                }
            }
            if (alphaSum <= 0.0f) continue;

            const float alpha = alphaSum * 0.25f;
            const YuvPixel overlay = rgbToLimitedBt601(
                redPremultiplied / alphaSum,
                greenPremultiplied / alphaSum,
                bluePremultiplied / alphaSum);
            const size_t index = static_cast<size_t>(y / 2) * chromaWidth + static_cast<size_t>(x / 2);
            uPlane[index] = clampByte(uPlane[index] * (1.0f - alpha) + overlay.u * alpha);
            vPlane[index] = clampByte(vPlane[index] * (1.0f - alpha) + overlay.v * alpha);
        }
    }
}
