#include "mcraw_preview_renderer.h"

#include <algorithm>
#include <cmath>
#include <limits>

namespace cinema::mcraw {
namespace {

constexpr double kD50ToD65[9] = {
    0.9555766, -0.0230393, 0.0631636,
    -0.0282895, 1.0099416, 0.0210077,
    0.0122982, -0.0204830, 1.3299098,
};
constexpr double kXyzD65ToSrgb[9] = {
    3.2406, -1.5372, -0.4986,
    -0.9689, 1.8758, 0.0415,
    0.0557, -0.2040, 1.0570,
};

bool fail(const char* message, std::string* error) {
    if (error != nullptr) *error = message;
    return false;
}

int channelFor(const std::string& pattern, int x, int y) {
    const char value = pattern[static_cast<size_t>((y & 1) * 2 + (x & 1))];
    if (value == 'r' || value == 'R') return 0;
    if (value == 'g' || value == 'G') return 1;
    if (value == 'b' || value == 'B') return 2;
    return -1;
}

double srgbEncode(double linear) {
    linear = std::clamp(linear, 0.0, 1.0);
    if (linear <= 0.0031308) return 12.92 * linear;
    return 1.055 * std::pow(linear, 1.0 / 2.4) - 0.055;
}

uint8_t toByte(double value) {
    const double normalized = std::clamp(value, 0.0, 1.0);
    return static_cast<uint8_t>(std::lround(normalized * 255.0));
}

}  // namespace

bool renderBayerPreview(
    const uint16_t* raw,
    const int sourceWidth,
    const int sourceHeight,
    const BayerPreviewMetadata& metadata,
    const int maximumEdge,
    RgbaPreview& output,
    std::string* error) {
    output = {};
    if (raw == nullptr) return fail("RAW frame buffer is null", error);
    if (sourceWidth < 2 || sourceHeight < 2) return fail("RAW frame dimensions are invalid", error);
    if (metadata.cfaPattern.size() != 4 ||
        channelFor(metadata.cfaPattern, 0, 0) < 0 ||
        channelFor(metadata.cfaPattern, 1, 0) < 0 ||
        channelFor(metadata.cfaPattern, 0, 1) < 0 ||
        channelFor(metadata.cfaPattern, 1, 1) < 0) {
        return fail("Unsupported Bayer CFA pattern", error);
    }
    if (maximumEdge <= 0) return fail("Preview edge must be positive", error);

    const double scale = std::min(1.0, static_cast<double>(maximumEdge) /
        static_cast<double>(std::max(sourceWidth, sourceHeight)));
    const int outputWidth = std::max(2, static_cast<int>(std::lround(sourceWidth * scale)));
    const int outputHeight = std::max(2, static_cast<int>(std::lround(sourceHeight * scale)));
    const uint64_t outputPixels = static_cast<uint64_t>(outputWidth) * static_cast<uint64_t>(outputHeight);
    if (outputPixels > static_cast<uint64_t>(std::numeric_limits<size_t>::max() / sizeof(uint32_t))) {
        return fail("Preview dimensions overflow", error);
    }

    output.width = outputWidth;
    output.height = outputHeight;
    output.pixels.resize(static_cast<size_t>(outputPixels));

    const double white = std::isfinite(metadata.whiteLevel) && metadata.whiteLevel > 0.0
        ? metadata.whiteLevel : 65535.0;
    auto normalizedRaw = [&](const int x, const int y) {
        const int boundedX = std::clamp(x, 0, sourceWidth - 1);
        const int boundedY = std::clamp(y, 0, sourceHeight - 1);
        const size_t pixelIndex = static_cast<size_t>(boundedY) * static_cast<size_t>(sourceWidth) +
            static_cast<size_t>(boundedX);
        const size_t cfaIndex = static_cast<size_t>((boundedY & 1) * 2 + (boundedX & 1));
        const double black = std::isfinite(metadata.blackLevel[cfaIndex])
            ? metadata.blackLevel[cfaIndex] : 0.0;
        const double range = std::max(1.0, white - black);
        return std::clamp((static_cast<double>(raw[pixelIndex]) - black) / range, 0.0, 1.0);
    };

    auto interpolateChannel = [&](const int x, const int y, const int wantedChannel) {
        if (channelFor(metadata.cfaPattern, x, y) == wantedChannel) {
            return normalizedRaw(x, y);
        }
        double weightedSum = 0.0;
        double weightTotal = 0.0;
        for (int dy = -1; dy <= 1; ++dy) {
            for (int dx = -1; dx <= 1; ++dx) {
                if (dx == 0 && dy == 0) continue;
                const int nx = std::clamp(x + dx, 0, sourceWidth - 1);
                const int ny = std::clamp(y + dy, 0, sourceHeight - 1);
                if (channelFor(metadata.cfaPattern, nx, ny) != wantedChannel) continue;
                const double weight = 1.0 / static_cast<double>(dx * dx + dy * dy);
                weightedSum += normalizedRaw(nx, ny) * weight;
                weightTotal += weight;
            }
        }
        if (weightTotal > 0.0) return weightedSum / weightTotal;
        return normalizedRaw(x, y);
    };

    auto applyForwardMatrix = [&](double& red, double& green, double& blue) {
        if (!metadata.hasForwardMatrix1) return;
        const std::array<double, 3> camera{{red, green, blue}};
        std::array<double, 3> xyzD50{};
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                xyzD50[static_cast<size_t>(row)] +=
                    metadata.forwardMatrix1[static_cast<size_t>(row * 3 + col)] * camera[static_cast<size_t>(col)];
            }
        }
        std::array<double, 3> xyzD65{};
        std::array<double, 3> srgb{};
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                xyzD65[static_cast<size_t>(row)] += kD50ToD65[row * 3 + col] * xyzD50[static_cast<size_t>(col)];
            }
        }
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                srgb[static_cast<size_t>(row)] += kXyzD65ToSrgb[row * 3 + col] * xyzD65[static_cast<size_t>(col)];
            }
        }
        if (std::isfinite(srgb[0]) && std::isfinite(srgb[1]) && std::isfinite(srgb[2])) {
            red = srgb[0];
            green = srgb[1];
            blue = srgb[2];
        }
    };

    for (int y = 0; y < outputHeight; ++y) {
        const int sourceY = std::min(sourceHeight - 1,
            static_cast<int>((static_cast<int64_t>(2 * y + 1) * sourceHeight) / (2 * outputHeight)));
        for (int x = 0; x < outputWidth; ++x) {
            const int sourceX = std::min(sourceWidth - 1,
                static_cast<int>((static_cast<int64_t>(2 * x + 1) * sourceWidth) / (2 * outputWidth)));
            double red = interpolateChannel(sourceX, sourceY, 0) * metadata.whiteBalanceGain[0];
            double green = interpolateChannel(sourceX, sourceY, 1) * metadata.whiteBalanceGain[1];
            double blue = interpolateChannel(sourceX, sourceY, 2) * metadata.whiteBalanceGain[2];
            applyForwardMatrix(red, green, blue);
            const uint32_t pixel = 0xff000000u |
                (static_cast<uint32_t>(toByte(srgbEncode(red))) << 16u) |
                (static_cast<uint32_t>(toByte(srgbEncode(green))) << 8u) |
                static_cast<uint32_t>(toByte(srgbEncode(blue)));
            output.pixels[static_cast<size_t>(y) * static_cast<size_t>(outputWidth) + static_cast<size_t>(x)] = pixel;
        }
    }
    if (error != nullptr) error->clear();
    return true;
}

}  // namespace cinema::mcraw
