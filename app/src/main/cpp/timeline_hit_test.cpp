#include "timeline_hit_test.h"

HitTestResult TimelineHitTest::hitTestClip(float pointerX, float pointerY, const std::vector<ClipRect>& clipRects) {
    HitTestResult result = {false, -1, -1};
    
    // Reverse iteration to hit test top-most clips first (if they overlap, though they shouldn't in tracks)
    for (int32_t i = static_cast<int32_t>(clipRects.size()) - 1; i >= 0; --i) {
        const ClipRect& rect = clipRects[i];
        if (pointerX >= rect.left && pointerX <= rect.right &&
            pointerY >= rect.top && pointerY <= rect.bottom) {
            result.hit = true;
            result.clipIndex = i;
            result.trackIndex = rect.trackIndex;
            break;
        }
    }
    
    return result;
}

TrimHandleHitResult TimelineHitTest::hitTestTrimHandle(float pointerX, float pointerY, const ClipRect& selectedClipRect, float handleWidth) {
    TrimHandleHitResult result = {false, false};
    
    // Check if within vertical bounds of the clip
    if (pointerY >= selectedClipRect.top && pointerY <= selectedClipRect.bottom) {
        // Check start handle
        if (pointerX >= (selectedClipRect.left - handleWidth) && pointerX <= (selectedClipRect.left + handleWidth)) {
            result.hit = true;
            result.isStartHandle = true;
            return result;
        }
        
        // Check end handle
        if (pointerX >= (selectedClipRect.right - handleWidth) && pointerX <= (selectedClipRect.right + handleWidth)) {
            result.hit = true;
            result.isStartHandle = false;
            return result;
        }
    }
    
    return result;
}

AdvHitTestResult AdvancedTimelineHitTest::hitTestClip(float pointerX, float pointerY, const std::vector<ClipLayoutRect>& clipRects) {
    for (auto it = clipRects.rbegin(); it != clipRects.rend(); ++it) {
        if (pointerX >= it->left && pointerX <= it->right &&
            pointerY >= it->top && pointerY <= it->bottom) {
            return {true, it->id, 0}; // trackIndex could be found if needed, or included in ClipLayoutRect
        }
    }
    return {false, -1, -1};
}

AdvTrimHandleHitResult AdvancedTimelineHitTest::hitTestTrimHandle(float pointerX, float pointerY, const ClipLayoutRect& selectedClipRect, float handleWidth) {
    if (pointerY < selectedClipRect.top || pointerY > selectedClipRect.bottom) {
        return {false, false};
    }
    
    if (pointerX >= selectedClipRect.left && pointerX <= selectedClipRect.left + handleWidth) {
        return {true, true};
    }
    
    if (pointerX >= selectedClipRect.right - handleWidth && pointerX <= selectedClipRect.right) {
        return {true, false};
    }
    
    return {false, false};
}

AdvEditPointHitResult AdvancedTimelineHitTest::hitTestEditPoint(float pointerX, float pointerY, const std::vector<ClipLayoutRect>& clipRects, float threshold) {
    for (const auto& clip : clipRects) {
        if (pointerY >= clip.top && pointerY <= clip.bottom) {
            if (pointerX >= clip.left - threshold && pointerX <= clip.left + threshold) {
                // Left edge hit
                return {true, -1, clip.id}; // simplifying for now
            }
            if (pointerX >= clip.right - threshold && pointerX <= clip.right + threshold) {
                // Right edge hit
                return {true, clip.id, -1};
            }
        }
    }
    return {false, -1, -1};
}
