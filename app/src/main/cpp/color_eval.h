#ifndef COLOR_EVAL_H
#define COLOR_EVAL_H

#include "render_frame.h"

struct ClipColorAdjustments {
    float exposureStops = 0.0f;
    float brightness = 0.0f;
    float contrast = 0.0f;
    float saturation = 1.0f;
    float vibrance = 0.0f;
    float temperature = 0.0f;
    float tint = 0.0f;
    float detailAmount = 0.0f;
};

class ColorEval {
public:
    // Applies exposure in stops, brightness as an encoded [0,1] offset, contrast as a
    // multiplier around 0.5, and saturation as a multiplier around Rec.709 luma.
    static void applyColorAdjustments(RenderFrame& frame, float exposure, float brightness, float contrast, float saturation);

    // Applies the static basic corrections supported by the Media3 single-clip export path.
    // Its SDR GL frames are linear Rec.709 RGBA, so these values are processed in that working space.
    static void applyClipAdjustments(RenderFrame& frame, const ClipColorAdjustments& adjustments);
};

#endif // COLOR_EVAL_H
