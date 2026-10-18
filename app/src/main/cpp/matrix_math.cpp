#include "matrix_math.h"
#include <cmath>

namespace transform {

static const float PI = 3.14159265358979323846f;

std::array<float, 16> TransformMatrix::Build4x4(const TransformParams& params) {
    std::array<float, 16> matrix = {
        1.f, 0.f, 0.f, 0.f,
        0.f, 1.f, 0.f, 0.f,
        0.f, 0.f, 1.f, 0.f,
        0.f, 0.f, 0.f, 1.f
    };

    float rad = params.rotationDegrees * PI / 180.0f;
    float cosA = std::cos(rad);
    float sinA = std::sin(rad);

    float sx = params.scaleX * (params.flipHorizontal ? -1.f : 1.f);
    float sy = params.scaleY * (params.flipVertical ? -1.f : 1.f);

    float tx = params.positionX;
    float ty = params.positionY;

    // A simplified 2D affine transform mapped into a 4x4 matrix
    matrix[0] = cosA * sx;
    matrix[1] = sinA * sx;
    matrix[4] = -sinA * sy;
    matrix[5] = cosA * sy;
    matrix[12] = tx;
    matrix[13] = ty;

    return matrix;
}

std::array<float, 9> TransformMatrix::Build3x3(const TransformParams& params) {
    std::array<float, 9> matrix = {
        1.f, 0.f, 0.f,
        0.f, 1.f, 0.f,
        0.f, 0.f, 1.f
    };
    
    float rad = params.rotationDegrees * PI / 180.0f;
    float cosA = std::cos(rad);
    float sinA = std::sin(rad);

    float sx = params.scaleX * (params.flipHorizontal ? -1.f : 1.f);
    float sy = params.scaleY * (params.flipVertical ? -1.f : 1.f);

    float tx = params.positionX;
    float ty = params.positionY;

    matrix[0] = cosA * sx;
    matrix[1] = -sinA * sy;
    matrix[2] = tx;

    matrix[3] = sinA * sx;
    matrix[4] = cosA * sy;
    matrix[5] = ty;

    return matrix;
}

} // namespace transform
