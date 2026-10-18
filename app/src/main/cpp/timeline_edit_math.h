#ifndef TIMELINE_EDIT_MATH_H
#define TIMELINE_EDIT_MATH_H

#include <cstdint>
#include <vector>

struct AdvEditClipData {
    int32_t id;
    int64_t startFrame;
    int64_t durationFrames;
    int64_t sourceIn;
    int64_t sourceOut;
    int64_t maxSourceDuration;
};

class TimelineEditMath {
public:
    static int64_t calculateRippleShift(int64_t originalDuration, int64_t newDuration);
    static int64_t calculateRollEditDelta(int64_t deltaFrames, const AdvEditClipData& leftClip, const AdvEditClipData& rightClip);
    static int64_t calculateSlipSourceRange(int64_t deltaFrames, const AdvEditClipData& clip);
    static std::vector<AdvEditClipData> calculateSlideNeighborEdits(int64_t deltaFrames, int32_t targetId, const std::vector<AdvEditClipData>& trackClips);
    static int64_t calculateNudgeFrame(int64_t currentFrame, int64_t nudgeFrames);
};

#endif // TIMELINE_EDIT_MATH_H
