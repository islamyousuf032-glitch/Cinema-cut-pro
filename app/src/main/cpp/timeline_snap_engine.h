#ifndef TIMELINE_SNAP_ENGINE_H
#define TIMELINE_SNAP_ENGINE_H

#include <vector>
#include <cstdint>

class TimelineSnapEngine {
public:
    static std::vector<int64_t> collectSnapTargets(const std::vector<int64_t>& clipEdges, int64_t playheadFrame, const std::vector<int64_t>& markers);
    static int64_t snapFrame(int64_t inputFrame, const std::vector<int64_t>& snapTargets, int64_t thresholdFrames);
};

#endif // TIMELINE_SNAP_ENGINE_H
