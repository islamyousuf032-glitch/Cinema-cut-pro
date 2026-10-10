#include "blend_mode_processor.h"
#include <algorithm>
#include <cmath>
#include <cstdlib>

namespace transform {

// Helper to clamp values to 0-255
inline uint8_t clampToUint8(int value) {
    if (value < 0) return 0;
    if (value > 255) return 255;
    return static_cast<uint8_t>(value);
}

void BlendModeProcessor::Process(BlendMode mode, uint8_t* basePixels, const uint8_t* topPixels, int width, int height, float opacity) {
    if (!basePixels || !topPixels || width <= 0 || height <= 0) return;
    opacity = std::isfinite(opacity) ? std::clamp(opacity, 0.0f, 1.0f) : 0.0f;
    int totalPixels = width * height;
    
    // We assume RGBA (4 channels)
    int totalBytes = totalPixels * 4;

    for (int i = 0; i < totalBytes; i += 4) {
        float alpha = (topPixels[i + 3] / 255.0f) * opacity;
        
        if (alpha <= 0.001f) {
            continue; // Fully transparent top pixel, do nothing
        }

        uint8_t rBase = basePixels[i];
        uint8_t gBase = basePixels[i + 1];
        uint8_t bBase = basePixels[i + 2];
        
        uint8_t rTop = topPixels[i];
        uint8_t gTop = topPixels[i + 1];
        uint8_t bTop = topPixels[i + 2];

        int outR = rBase, outG = gBase, outB = bBase;

        switch (mode) {
            case BlendMode::NORMAL:
                outR = rTop; outG = gTop; outB = bTop;
                break;
            case BlendMode::MULTIPLY:
                outR = (rBase * rTop) / 255;
                outG = (gBase * gTop) / 255;
                outB = (bBase * bTop) / 255;
                break;
            case BlendMode::SCREEN:
                outR = 255 - ((255 - rBase) * (255 - rTop)) / 255;
                outG = 255 - ((255 - gBase) * (255 - gTop)) / 255;
                outB = 255 - ((255 - bBase) * (255 - bTop)) / 255;
                break;
            case BlendMode::OVERLAY:
                outR = (rBase < 128) ? (2 * rBase * rTop / 255) : (255 - 2 * (255 - rBase) * (255 - rTop) / 255);
                outG = (gBase < 128) ? (2 * gBase * gTop / 255) : (255 - 2 * (255 - gBase) * (255 - gTop) / 255);
                outB = (bBase < 128) ? (2 * bBase * bTop / 255) : (255 - 2 * (255 - bBase) * (255 - bTop) / 255);
                break;
            case BlendMode::ADD:
                outR = std::min(rBase + rTop, 255);
                outG = std::min(gBase + gTop, 255);
                outB = std::min(bBase + bTop, 255);
                break;
            case BlendMode::SUBTRACT:
                outR = std::max(rBase - rTop, 0);
                outG = std::max(gBase - gTop, 0);
                outB = std::max(bBase - bTop, 0);
                break;
            case BlendMode::DARKEN:
                outR = std::min(rBase, rTop);
                outG = std::min(gBase, gTop);
                outB = std::min(bBase, bTop);
                break;
            case BlendMode::LIGHTEN:
                outR = std::max(rBase, rTop);
                outG = std::max(gBase, gTop);
                outB = std::max(bBase, bTop);
                break;
            case BlendMode::DIFFERENCE:
                outR = std::abs(rBase - rTop);
                outG = std::abs(gBase - gTop);
                outB = std::abs(bBase - bTop);
                break;
            case BlendMode::SOFT_LIGHT:
                // Simplified soft light
                outR = (rTop < 128) ? (rBase - (255 - 2 * rTop) * rBase * (255 - rBase) / 65025) 
                                    : (rBase + (2 * rTop - 255) * (std::sqrt(rBase / 255.0f) * 255 - rBase) / 255);
                outG = (gTop < 128) ? (gBase - (255 - 2 * gTop) * gBase * (255 - gBase) / 65025) 
                                    : (gBase + (2 * gTop - 255) * (std::sqrt(gBase / 255.0f) * 255 - gBase) / 255);
                outB = (bTop < 128) ? (bBase - (255 - 2 * bTop) * bBase * (255 - bBase) / 65025) 
                                    : (bBase + (2 * bTop - 255) * (std::sqrt(bBase / 255.0f) * 255 - bBase) / 255);
                break;
        }

        // Apply alpha blending
        basePixels[i] = static_cast<uint8_t>((outR * alpha) + (rBase * (1.0f - alpha)));
        basePixels[i + 1] = static_cast<uint8_t>((outG * alpha) + (gBase * (1.0f - alpha)));
        basePixels[i + 2] = static_cast<uint8_t>((outB * alpha) + (bBase * (1.0f - alpha)));
        
        // Base alpha remains same or increases?
        // Let's assume typical alpha compositing for base logic.
        // newAlpha = alphaTop + alphaBase * (1-alphaTop).
        float topA = alpha;
        float baseA = basePixels[i + 3] / 255.0f;
        basePixels[i + 3] = clampToUint8(static_cast<int>((topA + baseA * (1.0f - topA)) * 255));
    }
}

} // namespace transform
