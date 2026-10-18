#include "timeline_snap_engine.h"
#include <cmath>
#include <cstdlib>

std::vector<int64_t> TimelineSnapEngine::collectSnapTargets(const std::vector<int64_t>& clipEdges, int64_t playheadFrame, const std::vector<int64_t>& markers) {
    std::vector<int64_t> targets;
    targets.reserve(clipEdges.size() + markers.size() + 1);
    
    targets.push_back(playheadFrame);
    targets.insert(targets.end(), clipEdges.begin(), clipEdges.end());
    targets.insert(targets.end(), markers.begin(), markers.end());
    
    return targets;
}

int64_t TimelineSnapEngine::snapFrame(int64_t inputFrame, const std::vector<int64_t>& snapTargets, int64_t thresholdFrames) {
    int64_t bestFrame = inputFrame;
    int64_t minDiff = thresholdFrames + 1;
    
    for (int64_t target : snapTargets) {
        int64_t diff = std::abs(target - inputFrame);
        if (diff < minDiff && diff <= thresholdFrames) {
            minDiff = diff;
            bestFrame = target;
        }
    }
    
    return bestFrame;
}
