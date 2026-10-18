#include "timeline_layout_engine.h"
#include "advanced_timeline_core.h"
#include <algorithm>

std::vector<ClipLayoutRect> TimelineLayoutEngine::layoutClipRects(const std::vector<ClipLayoutInputData>& inputs, float trackHeight, float trackPadding, float topOffset, float pixelsPerFrame, float scrollX) {
    std::vector<ClipLayoutRect> result;
    result.reserve(inputs.size());
    for (const auto& input : inputs) {
        float left = AdvancedTimelineCore::frameToX(input.startFrame, pixelsPerFrame) - scrollX;
        float right = AdvancedTimelineCore::frameToX(input.startFrame + input.durationFrames, pixelsPerFrame) - scrollX;
        float top = topOffset + static_cast<float>(input.trackIndex) * (trackHeight + trackPadding);
        float bottom = top + trackHeight;
        result.push_back({input.id, left, top, right, bottom});
    }
    return result;
}

std::vector<TrackLayoutRect> TimelineLayoutEngine::layoutTrackRects(int32_t numTracks, float viewportWidth, float trackHeight, float trackPadding, float topOffset) {
    std::vector<TrackLayoutRect> result;
    result.reserve(numTracks);
    for (int32_t i = 0; i < numTracks; ++i) {
        float top = topOffset + static_cast<float>(i) * (trackHeight + trackPadding);
        float bottom = top + trackHeight;
        result.push_back({i, 0.0f, top, viewportWidth, bottom});
    }
    return result;
}

std::vector<AdvancedRulerTick> TimelineLayoutEngine::calculateTimelineRulerTicks(float scrollX, int32_t viewportWidth, float pixelsPerFrame, int32_t frameRate) {
    std::vector<AdvancedRulerTick> result;
    if (pixelsPerFrame <= 0.0f || frameRate <= 0) return result;

    float startX = std::max(0.0f, scrollX);
    float endX = startX + static_cast<float>(viewportWidth);
    int64_t startFrame = static_cast<int64_t>(startX / pixelsPerFrame);
    int64_t endFrame = static_cast<int64_t>(endX / pixelsPerFrame) + 1;

    int32_t stepFrames = frameRate;
    if (pixelsPerFrame * frameRate < 20.0f) {
        stepFrames = frameRate * 5;
    }
    if (pixelsPerFrame * frameRate < 5.0f) {
        stepFrames = frameRate * 10;
    }

    startFrame = (startFrame / stepFrames) * stepFrames;

    for (int64_t f = startFrame; f <= endFrame; f += stepFrames) {
        float x = AdvancedTimelineCore::frameToX(f, pixelsPerFrame) - scrollX;
        int32_t type = (f % (frameRate * 10) == 0) ? 1 : 0;
        result.push_back({x, type});
    }
    return result;
}
