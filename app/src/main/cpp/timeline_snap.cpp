#include "timeline_snap.h"
#include <cmath>
#include <limits>
#include <algorithm>

SnapResult TimelineSnap::snapFrame(int64_t inputFrame, const std::vector<SnapTarget>& targets, float pixelsPerFrame, float snapThresholdPixels) {
    SnapResult result = {false, inputFrame, -1, 0.0f};
    
    if (pixelsPerFrame <= 0.0f || snapThresholdPixels <= 0.0f || targets.empty()) {
        return result;
    }
    
    float inputX = static_cast<float>(inputFrame) * pixelsPerFrame;
    float closestDist = std::numeric_limits<float>::max();
    int64_t bestFrame = inputFrame;
    int32_t bestType = -1;
    bool found = false;
    
    for (const auto& target : targets) {
        float targetX = static_cast<float>(target.frame) * pixelsPerFrame;
        float dist = std::abs(targetX - inputX);
        
        if (dist <= snapThresholdPixels && dist < closestDist) {
            closestDist = dist;
            bestFrame = target.frame;
            bestType = target.type;
            found = true;
        }
    }
    
    if (found) {
        result.snapped = true;
        result.snappedFrame = bestFrame;
        result.targetType = bestType;
        result.distancePixels = closestDist;
    }
    
    return result;
}
