#include "mcraw_preview_renderer.h"

#include <cstdint>
#include <iostream>
#include <vector>

int main() {
    // Deterministic RGGB mosaic: red samples are brightest, then green, then blue.
    constexpr int width = 8;
    constexpr int height = 8;
    std::vector<uint16_t> raw(width * height);
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const char color = ((y & 1) == 0)
                ? (((x & 1) == 0) ? 'r' : 'g')
                : (((x & 1) == 0) ? 'g' : 'b');
            raw[static_cast<size_t>(y * width + x)] =
                color == 'r' ? 900 : (color == 'g' ? 500 : 100);
        }
    }

    cinema::mcraw::BayerPreviewMetadata metadata;
    metadata.cfaPattern = "rggb";
    metadata.whiteLevel = 1000.0;
    metadata.blackLevel = {{0.0, 0.0, 0.0, 0.0}};

    cinema::mcraw::RgbaPreview preview;
    std::string error;
    if (!cinema::mcraw::renderBayerPreview(
            raw.data(), width, height, metadata, 8, preview, &error)) {
        std::cerr << "Renderer failed: " << error << '\n';
        return 1;
    }
    if (preview.width != width || preview.height != height || preview.pixels.size() != raw.size()) {
        std::cerr << "Unexpected preview dimensions\n";
        return 1;
    }

    const uint32_t center = preview.pixels[3u * width + 3u];
    const uint32_t red = (center >> 16u) & 0xffu;
    const uint32_t green = (center >> 8u) & 0xffu;
    const uint32_t blue = center & 0xffu;
    if (!(red > green && green > blue)) {
        std::cerr << "Demosaic channel order is wrong: " << red << ',' << green << ',' << blue << '\n';
        return 1;
    }
    if ((center >> 24u) != 0xffu) {
        std::cerr << "Preview alpha is not opaque\n";
        return 1;
    }
    const std::vector<uint32_t> neutralPixels = preview.pixels;

    cinema::mcraw::PreviewGrade exposureGrade;
    exposureGrade.exposureStops = 1.0;
    if (!cinema::mcraw::renderBayerPreview(
            raw.data(), width, height, metadata, 8, preview, &error, exposureGrade)) {
        std::cerr << "Graded renderer failed: " << error << '\n';
        return 1;
    }
    const uint32_t brightCenter = preview.pixels[3u * width + 3u];
    if (((brightCenter >> 16u) & 0xffu) <= ((neutralPixels[3u * width + 3u] >> 16u) & 0xffu)) {
        std::cerr << "Exposure grade did not increase the red channel\n";
        return 1;
    }

    constexpr int gradientWidth = 8;
    constexpr int gradientHeight = 6;
    std::vector<uint16_t> gradient(gradientWidth * gradientHeight);
    for (int y = 0; y < gradientHeight; ++y) {
        for (int x = 0; x < gradientWidth; ++x) {
            gradient[static_cast<size_t>(y * gradientWidth + x)] =
                static_cast<uint16_t>(100 + x * 90 + y * 40);
        }
    }
    cinema::mcraw::RgbaPreview unrotated;
    cinema::mcraw::RgbaPreview rotated;
    if (!cinema::mcraw::renderBayerPreview(
            gradient.data(), gradientWidth, gradientHeight, metadata, 16, unrotated, &error) ||
        !cinema::mcraw::renderBayerPreview(
            gradient.data(), gradientWidth, gradientHeight, metadata, 16, rotated, &error,
            cinema::mcraw::PreviewGrade{}, 90)) {
        std::cerr << "Rotated preview render failed: " << error << '\n';
        return 1;
    }
    if (rotated.width != gradientHeight || rotated.height != gradientWidth) {
        std::cerr << "90-degree rotation returned the wrong dimensions\n";
        return 1;
    }
    for (int y = 0; y < rotated.height; ++y) {
        for (int x = 0; x < rotated.width; ++x) {
            const int sourceX = y;
            const int sourceY = gradientHeight - 1 - x;
            if (rotated.pixels[static_cast<size_t>(y * rotated.width + x)] !=
                unrotated.pixels[static_cast<size_t>(sourceY * unrotated.width + sourceX)]) {
                std::cerr << "90-degree rotation changed sampled pixel content\n";
                return 1;
            }
        }
    }

    if (cinema::mcraw::renderBayerPreview(raw.data(), 1, height, metadata, 4, preview, &error)) {
        std::cerr << "Invalid source dimensions were accepted\n";
        return 1;
    }
    if (!preview.pixels.empty()) {
        std::cerr << "Failed rendering must clear any stale output\n";
        return 1;
    }

    std::cout << "mcraw preview renderer test passed\n";
    return 0;
}
