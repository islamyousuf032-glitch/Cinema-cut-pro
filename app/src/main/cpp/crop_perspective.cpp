#include "crop_perspective.h"

namespace transform {

std::array<float, 4> CropPerspective::CalculateCropRect(const CropParams& params, float width, float height) {
    std::array<float, 4> rect;
    // Normalized 0..1 to pixels
    rect[0] = params.left * width;
    rect[1] = params.top * height;
    rect[2] = width - (params.right * width);
    rect[3] = height - (params.bottom * height);
    return rect;
}

std::array<float, 8> CropPerspective::CalculatePerspectiveQuad(const PerspectiveParams& params, float width, float height) {
    std::array<float, 8> quad;
    if (params.enabled) {
        quad[0] = params.topLeftX * width;
        quad[1] = params.topLeftY * height;
        quad[2] = params.topRightX * width;
        quad[3] = params.topRightY * height;
        quad[4] = params.bottomRightX * width;
        quad[5] = params.bottomRightY * height;
        quad[6] = params.bottomLeftX * width;
        quad[7] = params.bottomLeftY * height;
    } else {
        quad[0] = 0.f;     quad[1] = 0.f;
        quad[2] = width;   quad[3] = 0.f;
        quad[4] = width;   quad[5] = height;
        quad[6] = 0.f;     quad[7] = height;
    }
    return quad;
}

} // namespace transform
