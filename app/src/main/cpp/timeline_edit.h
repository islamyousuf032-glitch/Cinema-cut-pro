#ifndef TIMELINE_EDIT_H
#define TIMELINE_EDIT_H

#include <vector>
#include <cstdint>

struct EditClipInput {
    int32_t id;
    int64_t startFrame;
    int64_t durationFrames;
    int64_t sourceIn;
    int64_t sourceOut;
    int64_t maxSourceDuration; // -1 if unlimited
};

struct EditClipOutput {
    int32_t id;
    int64_t startFrame;
    int64_t durationFrames;
    int64_t sourceIn;
    int64_t sourceOut;
};

class TimelineEdit {
public:
    static std::vector<EditClipOutput> rippleEdit(const std::vector<EditClipInput>& trackClips, int32_t targetId, bool isStart, int64_t deltaFrames);
    static std::vector<EditClipOutput> rollEdit(const std::vector<EditClipInput>& trackClips, int32_t leftId, int32_t rightId, int64_t deltaFrames);
    static std::vector<EditClipOutput> slipEdit(const EditClipInput& clip, int64_t deltaFrames);
    static std::vector<EditClipOutput> slideEdit(const std::vector<EditClipInput>& trackClips, int32_t targetId, int64_t deltaFrames);
};

#endif // TIMELINE_EDIT_H
