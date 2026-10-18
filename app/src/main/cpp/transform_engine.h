#pragma once

#include "matrix_math.h"
#include "keyframe_interpolator.h"
#include "crop_perspective.h"
#include "motion_path.h"

namespace transform {

class TransformEngine {
public:
    TransformEngine() = default;
    ~TransformEngine() = default;

    // We can hold state or cache here if needed to avoid per-frame allocations
};

} // namespace transform
