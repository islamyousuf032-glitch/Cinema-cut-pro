#include "timeline_math.h"
#include <cmath>
#include <algorithm>

float TimelineMath::frameToPixel(long frame, float pixelsPerFrame) {
    return static_cast<float>(frame) * pixelsPerFrame;
}

long TimelineMath::pixelToFrame(float pixel, float pixelsPerFrame) {
    if (pixelsPerFrame <= 0.0f) return 0;
    return static_cast<long>(std::round(pixel / pixelsPerFrame));
}

bool TimelineMath::isFrameVisible(long frame, long startVisibleFrame, long endVisibleFrame) {
    return frame >= startVisibleFrame && frame <= endVisibleFrame;
}

ClipRect TimelineMath::calculateClipRect(long clipIdHash, long timelineStart, long duration, int trackIndex, int trackHeight, float pixelsPerFrame) {
    ClipRect rect;
    rect.clipIdHash = clipIdHash;
    rect.left = frameToPixel(timelineStart, pixelsPerFrame);
    rect.top = static_cast<float>(trackIndex * trackHeight);
    rect.right = frameToPixel(timelineStart + duration, pixelsPerFrame);
    rect.bottom = static_cast<float>((trackIndex + 1) * trackHeight);
    return rect;
}

long TimelineMath::calculateSnapPoint(long dragFrame, const std::vector<long>& snapPoints, long thresholdFrames) {
    long bestSnap = dragFrame;
    long minDiff = thresholdFrames + 1;

    for (long snapPoint : snapPoints) {
        long diff = std::abs(dragFrame - snapPoint);
        if (diff <= thresholdFrames && diff < minDiff) {
            minDiff = diff;
            bestSnap = snapPoint;
        }
    }
    return bestSnap;
}
