#pragma once
#include <array>

namespace transform {

struct CropParams {
    float left;
    float right;
    float top;
    float bottom;
    float feather;
};

struct PerspectiveParams {
    float topLeftX;
    float topLeftY;
    float topRightX;
    float topRightY;
    float bottomLeftX;
    float bottomLeftY;
    float bottomRightX;
    float bottomRightY;
    bool enabled;
};

class CropPerspective {
public:
    static std::array<float, 4> CalculateCropRect(const CropParams& params, float width, float height);
    static std::array<float, 8> CalculatePerspectiveQuad(const PerspectiveParams& params, float width, float height);
};

} // namespace transform
