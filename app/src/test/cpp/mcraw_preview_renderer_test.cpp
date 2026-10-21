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
