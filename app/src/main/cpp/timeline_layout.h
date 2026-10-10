#ifndef TIMELINE_LAYOUT_H
#define TIMELINE_LAYOUT_H

#include <cstdint>
#include <vector>

struct ClipRect {
    int32_t trackIndex;
    float left;
    float top;
    float right;
    float bottom;
};

struct ThumbnailCell {
    int32_t cellIndex;
    int64_t frameTime;
    float startX;
    float width;
};

struct RulerTick {
    int64_t frame;
    float x;
    bool isMajor;
};

struct ClipLayoutInput {
    int32_t trackIndex;
    int64_t startFrame;
    int64_t durationFrames;
};

class TimelineLayout {
public:
    static std::vector<ClipRect> layoutClips(const std::vector<ClipLayoutInput>& inputs, float trackHeight, float trackPadding, float topOffset, float pixelsPerFrame, float scrollX);
    static std::vector<ThumbnailCell> calculateThumbnailCellsForClip(int64_t startFrame, int64_t durationFrames, float cellWidth, float pixelsPerFrame, int64_t sourceInFrame);
    static std::vector<RulerTick> calculateTimelineRulerTicks(float scrollX, int32_t viewportWidth, float pixelsPerFrame, int32_t frameRate);
};

#endif // TIMELINE_LAYOUT_H
