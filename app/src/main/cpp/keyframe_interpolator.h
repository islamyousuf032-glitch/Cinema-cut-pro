#pragma once
#include <vector>

namespace transform {

enum class InterpolationType {
    HOLD = 0,
    LINEAR = 1,
    EASE_IN = 2,
    EASE_OUT = 3,
    EASE_IN_OUT = 4,
    BEZIER = 5
};

struct Keyframe {
    long long frame;
    float value;
    InterpolationType interpolation;
    float bezierHandleLeft;
    float bezierHandleRight;
};

class KeyframeInterpolator {
public:
    static float Interpolate(long long currentFrame, const Keyframe& kf1, const Keyframe& kf2);
    static float InterpolateArray(long long currentFrame, const std::vector<Keyframe>& keyframes, float defaultValue);
private:
    static float EaseIn(float t);
    static float EaseOut(float t);
    static float EaseInOut(float t);
    static float Bezier(float t, float p0, float p1, float p2, float p3);
};

} // namespace transform
