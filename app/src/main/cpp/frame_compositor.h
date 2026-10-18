#ifndef FRAME_COMPOSITOR_H
#define FRAME_COMPOSITOR_H

#include "render_frame.h"

class FrameCompositor {
public:
    // Blend RGBA foreground pixels over the RGBA background. blendMode uses the stable
    // transform::BlendMode ordinal values declared in blend_mode_processor.h.
    static void compositeFrames(RenderFrame& bgFrame, const RenderFrame& fgFrame, int blendMode);

    // Convert packed RGBA8888 to planar I420 (Y, then U, then V), limited-range BT.601.
    // The output frame must have even dimensions.
    static void convertRgbaToYuv420(const RenderFrame& rgbaFrame, RenderFrame& yuvFrame);

    // Composite a same-sized RGBA watermark into an I420 destination frame.
    static void blendRgbaWatermarkIntoYuv420(RenderFrame& yuvFrame, const RenderFrame& watermarkFrame);
};

#endif // FRAME_COMPOSITOR_H
