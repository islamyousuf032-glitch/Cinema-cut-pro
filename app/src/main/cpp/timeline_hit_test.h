#ifndef TIMELINE_HIT_TEST_H
#define TIMELINE_HIT_TEST_H

#include "timeline_layout.h"
#include <string>

struct HitTestResult {
    bool hit;
    int32_t clipIndex;
    int32_t trackIndex;
};

struct TrimHandleHitResult {
    bool hit;
    bool isStartHandle;
};

class TimelineHitTest {
public:
    static HitTestResult hitTestClip(float pointerX, float pointerY, const std::vector<ClipRect>& clipRects);
    static TrimHandleHitResult hitTestTrimHandle(float pointerX, float pointerY, const ClipRect& selectedClipRect, float handleWidth);
};


#include "timeline_layout_engine.h"

struct AdvHitTestResult {
    bool hit;
    int32_t clipId;
    int32_t trackIndex;
};

struct AdvTrimHandleHitResult {
    bool hit;
    bool isStartHandle;
};

struct AdvEditPointHitResult {
    bool hit;
    int32_t leftClipId;
    int32_t rightClipId;
};

class AdvancedTimelineHitTest {
public:
    static AdvHitTestResult hitTestClip(float pointerX, float pointerY, const std::vector<ClipLayoutRect>& clipRects);
    static AdvTrimHandleHitResult hitTestTrimHandle(float pointerX, float pointerY, const ClipLayoutRect& selectedClipRect, float handleWidth);
    static AdvEditPointHitResult hitTestEditPoint(float pointerX, float pointerY, const std::vector<ClipLayoutRect>& clipRects, float threshold);
};
#endif // TIMELINE_HIT_TEST_H
