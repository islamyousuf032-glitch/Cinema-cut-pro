#ifndef TRANSFORM_EVAL_H
#define TRANSFORM_EVAL_H

#include "render_frame.h"

class TransformEval {
public:
    // In-place RGBA8888 affine transform with bilinear sampling. position is normalized to frame
    // width/height, rotation is in degrees, and the anchor is the frame center.
    static void applyTransform(RenderFrame& frame, float scaleX, float scaleY, float rotation, float posX, float posY, float opacity);
};

#endif // TRANSFORM_EVAL_H
