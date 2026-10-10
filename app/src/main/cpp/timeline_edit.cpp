#include "timeline_edit.h"
#include <algorithm>

std::vector<EditClipOutput> TimelineEdit::rippleEdit(const std::vector<EditClipInput>& trackClips, int32_t targetId, bool isStart, int64_t deltaFrames) {
    std::vector<EditClipOutput> result;
    result.reserve(trackClips.size());

    int64_t actualDelta = deltaFrames;
    
    // Find target
    EditClipInput targetClip;
    bool found = false;
    for (const auto& c : trackClips) {
        if (c.id == targetId) {
            targetClip = c;
            found = true;
            break;
        }
    }
    
    if (!found) {
        // Return original if target not found
        for (const auto& c : trackClips) {
            result.push_back({c.id, c.startFrame, c.durationFrames, c.sourceIn, c.sourceOut});
        }
        return result;
    }

    if (isStart) {
        // Trimming start
        if (targetClip.durationFrames - actualDelta < 1) {
            actualDelta = targetClip.durationFrames - 1;
        }
        if (targetClip.sourceIn + actualDelta < 0) {
            actualDelta = -targetClip.sourceIn;
        }
    } else {
        // Trimming end
        if (targetClip.durationFrames + actualDelta < 1) {
            actualDelta = -(targetClip.durationFrames - 1);
        }
        if (targetClip.maxSourceDuration > 0 && targetClip.sourceOut + actualDelta > targetClip.maxSourceDuration) {
            actualDelta = targetClip.maxSourceDuration - targetClip.sourceOut;
        }
    }

    if (actualDelta == 0) {
        for (const auto& c : trackClips) {
            result.push_back({c.id, c.startFrame, c.durationFrames, c.sourceIn, c.sourceOut});
        }
        return result;
    }

    for (const auto& c : trackClips) {
        EditClipOutput out = {c.id, c.startFrame, c.durationFrames, c.sourceIn, c.sourceOut};
        
        if (c.id == targetId) {
            if (isStart) {
                out.startFrame += actualDelta;
                out.durationFrames -= actualDelta;
                out.sourceIn += actualDelta;
            } else {
                out.durationFrames += actualDelta;
                out.sourceOut += actualDelta;
            }
        } else {
            // Ripple other clips
            if (isStart) {
                if (c.startFrame >= targetClip.startFrame) {
                    if (c.id != targetId) {
                        out.startFrame += actualDelta;
                    }
                }
            } else {
                if (c.startFrame >= targetClip.startFrame + targetClip.durationFrames) {
                    out.startFrame += actualDelta;
                }
            }
        }
        
        // Prevent negative start frames
        if (out.startFrame < 0) out.startFrame = 0;
        
        result.push_back(out);
    }

    return result;
}

std::vector<EditClipOutput> TimelineEdit::rollEdit(const std::vector<EditClipInput>& trackClips, int32_t leftId, int32_t rightId, int64_t deltaFrames) {
    std::vector<EditClipOutput> result;
    result.reserve(trackClips.size());

    int64_t actualDelta = deltaFrames;
    
    EditClipInput leftClip, rightClip;
    bool foundLeft = false, foundRight = false;
    for (const auto& c : trackClips) {
        if (c.id == leftId) { leftClip = c; foundLeft = true; }
        if (c.id == rightId) { rightClip = c; foundRight = true; }
    }
    
    if (!foundLeft || !foundRight) {
        for (const auto& c : trackClips) {
            result.push_back({c.id, c.startFrame, c.durationFrames, c.sourceIn, c.sourceOut});
        }
        return result;
    }

    // Constraints check
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

    for (const auto& c : trackClips) {
        EditClipOutput out = {c.id, c.startFrame, c.durationFrames, c.sourceIn, c.sourceOut};
        
        if (c.id == leftId) {
            out.durationFrames += actualDelta;
            out.sourceOut += actualDelta;
        } else if (c.id == rightId) {
            out.startFrame += actualDelta;
            out.durationFrames -= actualDelta;
            out.sourceIn += actualDelta;
        }
        
        result.push_back(out);
    }
    
    return result;
}

std::vector<EditClipOutput> TimelineEdit::slipEdit(const EditClipInput& clip, int64_t deltaFrames) {
    std::vector<EditClipOutput> result;
    
    int64_t actualDelta = deltaFrames;
    if (clip.sourceIn + actualDelta < 0) {
        actualDelta = -clip.sourceIn;
    }
    if (clip.maxSourceDuration > 0 && clip.sourceOut + actualDelta > clip.maxSourceDuration) {
        actualDelta = clip.maxSourceDuration - clip.sourceOut;
    }
    
    EditClipOutput out = {clip.id, clip.startFrame, clip.durationFrames, clip.sourceIn + actualDelta, clip.sourceOut + actualDelta};
    result.push_back(out);
    
    return result;
}

std::vector<EditClipOutput> TimelineEdit::slideEdit(const std::vector<EditClipInput>& trackClips, int32_t targetId, int64_t deltaFrames) {
    std::vector<EditClipOutput> result;
    result.reserve(trackClips.size());

    int64_t actualDelta = deltaFrames;
    
    int targetIndex = -1;
    for (size_t i = 0; i < trackClips.size(); ++i) {
        if (trackClips[i].id == targetId) {
            targetIndex = i;
            break;
        }
    }
    
    if (targetIndex < 0) {
        for (const auto& c : trackClips) {
            result.push_back({c.id, c.startFrame, c.durationFrames, c.sourceIn, c.sourceOut});
        }
        return result;
    }
    
    // Sort track clips by startFrame to find adjacent ones properly, but for simplicity here we assume they are sorted
    
    bool hasLeft = (targetIndex > 0);
    bool hasRight = (targetIndex < (int)trackClips.size() - 1);
    
    EditClipInput targetClip = trackClips[targetIndex];
    EditClipInput leftClip = hasLeft ? trackClips[targetIndex - 1] : EditClipInput{0,0,0,0,0,-1};
    EditClipInput rightClip = hasRight ? trackClips[targetIndex + 1] : EditClipInput{0,0,0,0,0,-1};

    // Constraints
    if (hasLeft) {
        if (leftClip.durationFrames + actualDelta < 1) {
            actualDelta = -(leftClip.durationFrames - 1);
        }
        if (leftClip.maxSourceDuration > 0 && leftClip.sourceOut + actualDelta > leftClip.maxSourceDuration) {
            actualDelta = leftClip.maxSourceDuration - leftClip.sourceOut;
        }
    } else if (actualDelta < 0) {
        // Can't slide left if no clip on left to absorb it, unless we just let it shift start?
        // Slide usually requires 3 clips. If missing one side, it just ripples?
        // Standard NLE slide: changes dur of adjacent clips. 
        if (targetClip.startFrame + actualDelta < 0) {
            actualDelta = -targetClip.startFrame;
        }
    }
    
    if (hasRight) {
        if (rightClip.durationFrames - actualDelta < 1) {
            actualDelta = rightClip.durationFrames - 1;
        }
        if (rightClip.sourceIn + actualDelta < 0) {
            actualDelta = -rightClip.sourceIn;
        }
    }

    for (size_t i = 0; i < trackClips.size(); ++i) {
        const auto& c = trackClips[i];
        EditClipOutput out = {c.id, c.startFrame, c.durationFrames, c.sourceIn, c.sourceOut};
        
        if (i == targetIndex) {
            out.startFrame += actualDelta;
        } else if (hasLeft && i == targetIndex - 1) {
            out.durationFrames += actualDelta;
            out.sourceOut += actualDelta;
        } else if (hasRight && i == targetIndex + 1) {
            out.startFrame += actualDelta;
            out.durationFrames -= actualDelta;
            out.sourceIn += actualDelta;
        }
        
        result.push_back(out);
    }
    
    return result;
}
