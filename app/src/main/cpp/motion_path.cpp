#include "motion_path.h"

namespace transform {

std::vector<std::array<float, 2>> MotionPath::BuildMotionPath(
    const std::vector<Keyframe>& xKeyframes,
    const std::vector<Keyframe>& yKeyframes,
    long long startFrame,
    long long endFrame
) {
    std::vector<std::array<float, 2>> path;
    if (startFrame > endFrame) return path;

    path.reserve(endFrame - startFrame + 1);

    for (long long frame = startFrame; frame <= endFrame; ++frame) {
        float x = KeyframeInterpolator::InterpolateArray(frame, xKeyframes, 0.f);
        float y = KeyframeInterpolator::InterpolateArray(frame, yKeyframes, 0.f);
        path.push_back({x, y});
    }

    return path;
}

} // namespace transform
