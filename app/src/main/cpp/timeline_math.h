#ifndef TIMELINE_MATH_H
#define TIMELINE_MATH_H

#include <vector>

struct ClipRect {
    long clipIdHash;
    float left;
    float top;
    float right;
    float bottom;
};

class TimelineMath {
public:
    static float frameToPixel(long frame, float pixelsPerFrame);
    static long pixelToFrame(float pixel, float pixelsPerFrame);
    static bool isFrameVisible(long frame, long startVisibleFrame, long endVisibleFrame);
    static ClipRect calculateClipRect(long clipIdHash, long timelineStart, long duration, int trackIndex, int trackHeight, float pixelsPerFrame);
    static long calculateSnapPoint(long dragFrame, const std::vector<long>& snapPoints, long thresholdFrames);
};

#endif
