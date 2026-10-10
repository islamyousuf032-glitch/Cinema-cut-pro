#pragma once
#include <array>

namespace transform {

struct TransformParams {
    float positionX;
    float positionY;
    float scaleX;
    float scaleY;
    float rotationDegrees;
    float anchorX;
    float anchorY;
    bool flipHorizontal;
    bool flipVertical;
};

class TransformMatrix {
public:
    static std::array<float, 16> Build4x4(const TransformParams& params);
    static std::array<float, 9> Build3x3(const TransformParams& params);
};

} // namespace transform
