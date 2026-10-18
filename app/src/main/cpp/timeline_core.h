#ifndef TIMELINE_CORE_H
#define TIMELINE_CORE_H

#include <cstdint>

struct TimelineContext {
    float pixelsPerFrame;
    int32_t trackHeight;
    int32_t viewportWidth;
    float scrollX;
};

class TimelineCore {
public:
    static float frameToX(int64_t frame, float pixelsPerFrame);
    static int64_t xToFrame(float x, float pixelsPerFrame);
    static void calculateVisibleFrameRange(float scrollX, int32_t viewportWidth, float pixelsPerFrame, int64_t& outStartFrame, int64_t& outEndFrame);
    static float calculatePlayheadX(int64_t currentFrame, float scrollX, float pixelsPerFrame);
};

#endif // TIMELINE_CORE_H
