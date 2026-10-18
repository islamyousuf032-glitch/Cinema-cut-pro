#pragma once
#include <vector>
#include <array>
#include "keyframe_interpolator.h"

namespace transform {

class MotionPath {
public:
    static std::vector<std::array<float, 2>> BuildMotionPath(
        const std::vector<Keyframe>& xKeyframes,
        const std::vector<Keyframe>& yKeyframes,
        long long startFrame,
        long long endFrame
    );
};

} // namespace transform
