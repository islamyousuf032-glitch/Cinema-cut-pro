#include "mcraw_preview_renderer.h"

#include <algorithm>
#include <array>
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

uint8_t toSrgbByte(double linear) {
    // The same 16-bit lookup table is reused for every output frame. It replaces three pow()
    // calls per preview pixel with table reads; 16-bit indexing keeps quantization well below
    // one 8-bit output code value.
    static const std::array<uint8_t, 65'536> encodeTable = [] {
        std::array<uint8_t, 65'536> table{};
        for (size_t index = 0; index < table.size(); ++index) {
            const double value = static_cast<double>(index) / 65'535.0;
            const double encoded = value <= 0.0031308
                ? 12.92 * value
                : 1.055 * std::pow(value, 1.0 / 2.4) - 0.055;
            table[index] = static_cast<uint8_t>(std::lround(encoded * 255.0));
        }
        return table;
    }();
    const double normalized = std::clamp(linear, 0.0, 1.0);
    const size_t index = static_cast<size_t>(std::lround(normalized * 65'535.0));
    return encodeTable[index];
}

struct GradeLookup {
    bool enabled = false;
    std::array<std::array<uint8_t, 256>, 3> channels{};
};

GradeLookup makeGradeLookup(const PreviewGrade& grade) {
    GradeLookup lookup;
    const double exposure = std::isfinite(grade.exposureStops)
        ? std::exp2(std::clamp(grade.exposureStops, -16.0, 16.0)) : 1.0;
    const double temperature = std::isfinite(grade.temperature)
        ? std::clamp(grade.temperature, -1.0, 1.0) : 0.0;
    const double tint = std::isfinite(grade.tint)
        ? std::clamp(grade.tint, -1.0, 1.0) : 0.0;
    const std::array<double, 3> temperatureTint{{
        1.0 + temperature + tint,
        1.0 + tint,
        1.0 - temperature,
    }};
    const auto& temp = temperatureTint;
    const bool active = std::abs(exposure - 1.0) > 1e-9 ||
        std::abs(temperature) > 1e-9 || std::abs(tint) > 1e-9 ||
        std::any_of(grade.lift.begin(), grade.lift.end(), [](double value) { return std::abs(value) > 1e-9; }) ||
        std::any_of(grade.gamma.begin(), grade.gamma.end(), [](double value) { return std::abs(value - 1.0) > 1e-9; }) ||
        std::any_of(grade.gain.begin(), grade.gain.end(), [](double value) { return std::abs(value - 1.0) > 1e-9; }) ||
        std::any_of(grade.offset.begin(), grade.offset.end(), [](double value) { return std::abs(value) > 1e-9; });
    if (!active) return lookup;

    lookup.enabled = true;
    for (size_t channel = 0; channel < lookup.channels.size(); ++channel) {
        const double lift = std::isfinite(grade.lift[channel])
            ? std::clamp(grade.lift[channel], -1.0, 1.0) : 0.0;
        const double gamma = std::isfinite(grade.gamma[channel])
            ? std::max(grade.gamma[channel], 0.001) : 1.0;
        const double gain = std::isfinite(grade.gain[channel])
            ? std::clamp(grade.gain[channel], 0.0, 64.0) : 1.0;
        const double offset = std::isfinite(grade.offset[channel])
            ? std::clamp(grade.offset[channel], -4.0, 4.0) : 0.0;
        const double tempMultiplier = std::isfinite(temp[channel]) ? temp[channel] : 1.0;
        for (size_t input = 0; input < lookup.channels[channel].size(); ++input) {
            int value = static_cast<int>(input);
            value = std::min(255, static_cast<int>(value * exposure));
            value = std::clamp(static_cast<int>(value * tempMultiplier), 0, 255);
            value = std::clamp(static_cast<int>(value + offset * 255.0), 0, 255);
            double normalized = static_cast<double>(value) / 255.0;
            normalized += lift * (1.0 - normalized);
            if (gamma != 1.0 && normalized > 0.0) {
                normalized = std::pow(normalized, 1.0 / gamma);
            }
            normalized *= gain;
            lookup.channels[channel][input] = static_cast<uint8_t>(
                std::clamp(static_cast<int>(normalized * 255.0), 0, 255));
        }
    }
    return lookup;
}

}  // namespace

bool renderBayerPreview(
    const uint16_t* raw,
    const int sourceWidth,
    const int sourceHeight,
    const BayerPreviewMetadata& metadata,
    const int maximumEdge,
    RgbaPreview& output,
    std::string* error,
    const PreviewGrade& grade,
    int rotationDegrees) {
    output.width = 0;
    output.height = 0;
    output.pixels.clear();
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
    rotationDegrees = ((rotationDegrees % 360) + 360) % 360;
    if (rotationDegrees % 90 != 0) return fail("Only right-angle MotionCam rotations are supported", error);

    const double scale = std::min(1.0, static_cast<double>(maximumEdge) /
        static_cast<double>(std::max(sourceWidth, sourceHeight)));
    const int sampledWidth = std::max(2, static_cast<int>(std::lround(sourceWidth * scale)));
    const int sampledHeight = std::max(2, static_cast<int>(std::lround(sourceHeight * scale)));
    const bool swapsDimensions = rotationDegrees == 90 || rotationDegrees == 270;
    const int outputWidth = swapsDimensions ? sampledHeight : sampledWidth;
    const int outputHeight = swapsDimensions ? sampledWidth : sampledHeight;
    const uint64_t outputPixels = static_cast<uint64_t>(outputWidth) * static_cast<uint64_t>(outputHeight);
    if (outputPixels > static_cast<uint64_t>(std::numeric_limits<size_t>::max() / sizeof(uint32_t))) {
        return fail("Preview dimensions overflow", error);
    }

    output.width = outputWidth;
    output.height = outputHeight;
    output.pixels.resize(static_cast<size_t>(outputPixels));
    const GradeLookup gradeLookup = makeGradeLookup(grade);

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

    auto interpolateChannels = [&](const int x, const int y) {
        std::array<double, 3> channels{{0.0, 0.0, 0.0}};
        std::array<double, 3> weightedSums{{0.0, 0.0, 0.0}};
        std::array<double, 3> weightTotals{{0.0, 0.0, 0.0}};
        std::array<bool, 3> hasCenterSample{{false, false, false}};
        const double centerValue = normalizedRaw(x, y);
        const int centerChannel = channelFor(metadata.cfaPattern, x, y);
        if (centerChannel >= 0) {
            channels[static_cast<size_t>(centerChannel)] = centerValue;
            hasCenterSample[static_cast<size_t>(centerChannel)] = true;
        }

        // Visit the 3x3 Bayer neighborhood once for all three channels. The previous code
        // repeated this walk and raw normalization independently for R, G, and B.
        for (int dy = -1; dy <= 1; ++dy) {
            for (int dx = -1; dx <= 1; ++dx) {
                if (dx == 0 && dy == 0) continue;
                const int nx = std::clamp(x + dx, 0, sourceWidth - 1);
                const int ny = std::clamp(y + dy, 0, sourceHeight - 1);
                const int channel = channelFor(metadata.cfaPattern, nx, ny);
                if (channel < 0 || hasCenterSample[static_cast<size_t>(channel)]) continue;
                const double weight = 1.0 / static_cast<double>(dx * dx + dy * dy);
                weightedSums[static_cast<size_t>(channel)] += normalizedRaw(nx, ny) * weight;
                weightTotals[static_cast<size_t>(channel)] += weight;
            }
        }

        for (size_t channel = 0; channel < channels.size(); ++channel) {
            if (hasCenterSample[channel]) continue;
            channels[channel] = weightTotals[channel] > 0.0
                ? weightedSums[channel] / weightTotals[channel]
                : centerValue;
        }
        return channels;
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
        for (int x = 0; x < outputWidth; ++x) {
            int sampledX = x;
            int sampledY = y;
            switch (rotationDegrees) {
                case 90:
                    sampledX = y;
                    sampledY = sampledHeight - 1 - x;
                    break;
                case 180:
                    sampledX = sampledWidth - 1 - x;
                    sampledY = sampledHeight - 1 - y;
                    break;
                case 270:
                    sampledX = sampledWidth - 1 - y;
                    sampledY = x;
                    break;
                default:
                    break;
            }
            const int sourceX = std::min(sourceWidth - 1,
                static_cast<int>((static_cast<int64_t>(2 * sampledX + 1) * sourceWidth) / (2 * sampledWidth)));
            const int sourceY = std::min(sourceHeight - 1,
                static_cast<int>((static_cast<int64_t>(2 * sampledY + 1) * sourceHeight) / (2 * sampledHeight)));
            const auto camera = interpolateChannels(sourceX, sourceY);
            double red = camera[0] * metadata.whiteBalanceGain[0];
            double green = camera[1] * metadata.whiteBalanceGain[1];
            double blue = camera[2] * metadata.whiteBalanceGain[2];
            applyForwardMatrix(red, green, blue);
            uint8_t redByte = toSrgbByte(red);
            uint8_t greenByte = toSrgbByte(green);
            uint8_t blueByte = toSrgbByte(blue);
            if (gradeLookup.enabled) {
                redByte = gradeLookup.channels[0][redByte];
                greenByte = gradeLookup.channels[1][greenByte];
                blueByte = gradeLookup.channels[2][blueByte];
            }
            const uint32_t pixel = 0xff000000u |
                (static_cast<uint32_t>(redByte) << 16u) |
                (static_cast<uint32_t>(greenByte) << 8u) |
                static_cast<uint32_t>(blueByte);
            output.pixels[static_cast<size_t>(y) * static_cast<size_t>(outputWidth) + static_cast<size_t>(x)] = pixel;
        }
    }
    if (error != nullptr) error->clear();
    return true;
}

}  // namespace cinema::mcraw
