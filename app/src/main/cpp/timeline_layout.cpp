#include "timeline_layout.h"
#include "timeline_core.h"
#include <cmath>

std::vector<ClipRect> TimelineLayout::layoutClips(const std::vector<ClipLayoutInput>& inputs, float trackHeight, float trackPadding, float topOffset, float pixelsPerFrame, float scrollX) {
    std::vector<ClipRect> results;
    results.reserve(inputs.size());
    
    for (const auto& input : inputs) {
        ClipRect rect;
        rect.trackIndex = input.trackIndex;
        
        float left = TimelineCore::frameToX(input.startFrame, pixelsPerFrame) - scrollX;
        float right = TimelineCore::frameToX(input.startFrame + input.durationFrames, pixelsPerFrame) - scrollX;
        
        float top = static_cast<float>(input.trackIndex) * (trackHeight + trackPadding) + topOffset;
        float bottom = top + trackHeight;
        
        rect.left = left;
        rect.top = top;
        rect.right = right;
        rect.bottom = bottom;
        
        results.push_back(rect);
    }
    
    return results;
}

std::vector<ThumbnailCell> TimelineLayout::calculateThumbnailCellsForClip(int64_t startFrame, int64_t durationFrames, float cellWidth, float pixelsPerFrame, int64_t sourceInFrame) {
    std::vector<ThumbnailCell> cells;
    if (cellWidth <= 0.0f || pixelsPerFrame <= 0.0f || durationFrames <= 0) {
        return cells;
    }
    
    float clipWidth = static_cast<float>(durationFrames) * pixelsPerFrame;
    int32_t numCells = static_cast<int32_t>(std::ceil(clipWidth / cellWidth));
    
    float framesPerCell = cellWidth / pixelsPerFrame;
    
    for (int32_t i = 0; i < numCells; ++i) {
        ThumbnailCell cell;
        cell.cellIndex = i;
        cell.startX = static_cast<float>(i) * cellWidth;
        
        // Calculate remaining width for the last cell
        float remainingWidth = clipWidth - cell.startX;
        cell.width = std::min(cellWidth, remainingWidth);
        
        // The frame time relative to source
        int64_t offsetFrames = static_cast<int64_t>(std::round(static_cast<float>(i) * framesPerCell));
        cell.frameTime = sourceInFrame + offsetFrames;
        
        cells.push_back(cell);
    }
    
    return cells;
}

std::vector<RulerTick> TimelineLayout::calculateTimelineRulerTicks(float scrollX, int32_t viewportWidth, float pixelsPerFrame, int32_t frameRate) {
    std::vector<RulerTick> ticks;
    if (pixelsPerFrame <= 0.0f || frameRate <= 0) return ticks;
    
    float startX = std::max(0.0f, scrollX);
    float endX = startX + static_cast<float>(viewportWidth);
    
    int64_t startFrame = static_cast<int64_t>(std::floor(startX / pixelsPerFrame));
    int64_t endFrame = static_cast<int64_t>(std::ceil(endX / pixelsPerFrame));
    
    int64_t minorInterval = 1;
    int64_t majorInterval = frameRate;
    
    if (pixelsPerFrame > 20.0f) {
        minorInterval = 1;
        majorInterval = frameRate;
    } else if (pixelsPerFrame > 5.0f) {
        minorInterval = std::max(int64_t(1), (int64_t)frameRate / 2);
        majorInterval = frameRate;
    } else if (pixelsPerFrame > 1.0f) {
        minorInterval = frameRate;
        majorInterval = frameRate * 10;
    } else if (pixelsPerFrame > 0.1f) {
        minorInterval = frameRate * 10;
        majorInterval = frameRate * 60;
    } else {
        minorInterval = frameRate * 60;
        majorInterval = frameRate * 300;
    }
    
    int64_t firstTick = (startFrame / minorInterval) * minorInterval;
    
    for (int64_t f = firstTick; f <= endFrame; f += minorInterval) {
        RulerTick tick;
        tick.frame = f;
        tick.x = TimelineCore::frameToX(f, pixelsPerFrame) - scrollX;
        tick.isMajor = (f % majorInterval == 0);
        ticks.push_back(tick);
    }
    
    return ticks;
}
