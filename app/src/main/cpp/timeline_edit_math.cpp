#include "timeline_edit_math.h"
#include <algorithm>

int64_t TimelineEditMath::calculateRippleShift(int64_t originalDuration, int64_t newDuration) {
    return newDuration - originalDuration;
}

int64_t TimelineEditMath::calculateRollEditDelta(int64_t deltaFrames, const AdvEditClipData& leftClip, const AdvEditClipData& rightClip) {
    int64_t actualDelta = deltaFrames;
    
    if (leftClip.durationFrames + actualDelta < 1) {
        actualDelta = -(leftClip.durationFrames - 1);
    }
    if (rightClip.durationFrames - actualDelta < 1) {
        actualDelta = rightClip.durationFrames - 1;
    }
    
    if (leftClip.maxSourceDuration > 0 && leftClip.sourceOut + actualDelta > leftClip.maxSourceDuration) {
        actualDelta = leftClip.maxSourceDuration - leftClip.sourceOut;
    }
    if (rightClip.sourceIn + actualDelta < 0) {
        actualDelta = -rightClip.sourceIn;
    }
    
    return actualDelta;
}

int64_t TimelineEditMath::calculateSlipSourceRange(int64_t deltaFrames, const AdvEditClipData& clip) {
    int64_t actualDelta = deltaFrames;
    if (clip.sourceIn + actualDelta < 0) {
        actualDelta = -clip.sourceIn;
    }
    if (clip.maxSourceDuration > 0 && clip.sourceOut + actualDelta > clip.maxSourceDuration) {
        actualDelta = clip.maxSourceDuration - clip.sourceOut;
    }
    return actualDelta;
}

std::vector<AdvEditClipData> TimelineEditMath::calculateSlideNeighborEdits(int64_t deltaFrames, int32_t targetId, const std::vector<AdvEditClipData>& trackClips) {
    std::vector<AdvEditClipData> result = trackClips;
    // Just a stub for slide math that returns modified clips
    return result;
}

int64_t TimelineEditMath::calculateNudgeFrame(int64_t currentFrame, int64_t nudgeFrames) {
    return currentFrame + nudgeFrames;
}
