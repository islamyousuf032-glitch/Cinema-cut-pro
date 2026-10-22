#pragma once

#include <array>
#include <cstdint>
#include <string>
#include <vector>

namespace cinema::mcraw {

struct BayerPreviewMetadata {
    // Row-major 2x2 CFA order, for example "rggb" or "bggr".
    std::string cfaPattern = "rggb";
    std::array<double, 4> blackLevel{{0.0, 0.0, 0.0, 0.0}};
    double whiteLevel = 65535.0;
    std::array<double, 3> whiteBalanceGain{{1.0, 1.0, 1.0}};
    std::array<double, 9> forwardMatrix1{{1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0}};
    bool hasForwardMatrix1 = false;
};

struct RgbaPreview {
    int width = 0;
    int height = 0;
    // Android ARGB_8888 word order: 0xAARRGGBB.
    std::vector<uint32_t> pixels;
};

/** Supported primary RAW preview adjustments, applied directly during Bayer rendering. */
struct PreviewGrade {
    double exposureStops = 0.0;
    double temperature = 0.0;
    double tint = 0.0;
    std::array<double, 3> lift{{0.0, 0.0, 0.0}};
    std::array<double, 3> gamma{{1.0, 1.0, 1.0}};
    std::array<double, 3> gain{{1.0, 1.0, 1.0}};
    std::array<double, 3> offset{{0.0, 0.0, 0.0}};
};

/**
 * Builds a bounded, display-ready preview from one 16-bit Bayer frame.
 * This is a simple bilinear/demosaic preview path, not a full camera ISP.
 */
bool renderBayerPreview(
    const uint16_t* raw,
    int sourceWidth,
    int sourceHeight,
    const BayerPreviewMetadata& metadata,
    int maximumEdge,
    RgbaPreview& output,
    std::string* error = nullptr,
    const PreviewGrade& grade = {},
    int rotationDegrees = 0);

}  // namespace cinema::mcraw
