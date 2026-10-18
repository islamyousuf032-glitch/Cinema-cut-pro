#ifndef TIMELINE_SNAP_H
#define TIMELINE_SNAP_H

#include <cstdint>
#include <vector>

struct SnapTarget {
    int64_t frame;
    int32_t type; // 0 = playhead, 1 = clip edge, 2 = marker
};

struct SnapResult {
    bool snapped;
    int64_t snappedFrame;
    int32_t targetType;
    float distancePixels;
};

class TimelineSnap {
public:
    static SnapResult snapFrame(int64_t inputFrame, const std::vector<SnapTarget>& targets, float pixelsPerFrame, float snapThresholdPixels);
};

#endif // TIMELINE_SNAP_H
