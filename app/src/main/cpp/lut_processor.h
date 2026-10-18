#ifndef LUT_PROCESSOR_H
#define LUT_PROCESSOR_H

#include "render_frame.h"
#include <cstdint>

class LUTProcessor {
public:
    // Applies an RGB uint8 3D LUT with R as the fastest-varying cube index, using trilinear
    // interpolation. The cube contains lutSize^3 RGB triplets.
    static void applyLUT(RenderFrame& frame, const uint8_t* lutData, int lutSize);
};

#endif // LUT_PROCESSOR_H
