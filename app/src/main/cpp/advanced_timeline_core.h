#ifndef ADVANCED_TIMELINE_CORE_H
#define ADVANCED_TIMELINE_CORE_H

#include <cstdint>

class AdvancedTimelineCore {
public:
    static float frameToX(int64_t frame, float pixelsPerFrame);
    static int64_t xToFrame(float x, float pixelsPerFrame);
    static void calculateVisibleFrameRange(float scrollX, int32_t viewportWidth, float pixelsPerFrame, int64_t& outStartFrame, int64_t& outEndFrame);
    static float calculatePlayheadX(int64_t currentFrame, float scrollX, float pixelsPerFrame);
};

#endif // ADVANCED_TIMELINE_CORE_H
