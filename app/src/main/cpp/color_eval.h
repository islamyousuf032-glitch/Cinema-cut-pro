#ifndef COLOR_EVAL_H
#define COLOR_EVAL_H

#include "render_frame.h"

class ColorEval {
public:
    // Applies exposure in stops, brightness as an encoded [0,1] offset, contrast as a
    // multiplier around 0.5, and saturation as a multiplier around Rec.709 luma.
    static void applyColorAdjustments(RenderFrame& frame, float exposure, float brightness, float contrast, float saturation);
};

#endif // COLOR_EVAL_H
