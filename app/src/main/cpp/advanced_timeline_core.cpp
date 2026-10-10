#include "advanced_timeline_core.h"
#include <algorithm>

float AdvancedTimelineCore::frameToX(int64_t frame, float pixelsPerFrame) {
    return static_cast<float>(frame) * pixelsPerFrame;
}

int64_t AdvancedTimelineCore::xToFrame(float x, float pixelsPerFrame) {
    if (pixelsPerFrame <= 0.0f) return 0;
    return static_cast<int64_t>(x / pixelsPerFrame);
}

void AdvancedTimelineCore::calculateVisibleFrameRange(float scrollX, int32_t viewportWidth, float pixelsPerFrame, int64_t& outStartFrame, int64_t& outEndFrame) {
    if (pixelsPerFrame <= 0.0f) {
        outStartFrame = 0;
        outEndFrame = 0;
        return;
    }
    float startX = std::max(0.0f, scrollX);
    float endX = startX + static_cast<float>(viewportWidth);
    
    outStartFrame = static_cast<int64_t>(startX / pixelsPerFrame);
    outEndFrame = static_cast<int64_t>(endX / pixelsPerFrame) + 1;
}

float AdvancedTimelineCore::calculatePlayheadX(int64_t currentFrame, float scrollX, float pixelsPerFrame) {
    return (static_cast<float>(currentFrame) * pixelsPerFrame) - scrollX;
}
