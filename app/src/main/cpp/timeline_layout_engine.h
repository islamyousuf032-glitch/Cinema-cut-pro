#ifndef TIMELINE_LAYOUT_ENGINE_H
#define TIMELINE_LAYOUT_ENGINE_H

#include <vector>
#include <cstdint>

struct ClipLayoutInputData {
    int32_t id;
    int32_t trackIndex;
    int64_t startFrame;
    int64_t durationFrames;
};

struct ClipLayoutRect {
    int32_t id;
    float left;
    float top;
    float right;
    float bottom;
};

struct TrackLayoutRect {
    int32_t trackIndex;
    float left;
    float top;
    float right;
    float bottom;
};

struct AdvancedRulerTick {
    float x;
    int32_t type; // 0 = minor, 1 = major
};

class TimelineLayoutEngine {
public:
    static std::vector<ClipLayoutRect> layoutClipRects(const std::vector<ClipLayoutInputData>& inputs, float trackHeight, float trackPadding, float topOffset, float pixelsPerFrame, float scrollX);
    static std::vector<TrackLayoutRect> layoutTrackRects(int32_t numTracks, float viewportWidth, float trackHeight, float trackPadding, float topOffset);
    static std::vector<AdvancedRulerTick> calculateTimelineRulerTicks(float scrollX, int32_t viewportWidth, float pixelsPerFrame, int32_t frameRate);
};

#endif // TIMELINE_LAYOUT_ENGINE_H
