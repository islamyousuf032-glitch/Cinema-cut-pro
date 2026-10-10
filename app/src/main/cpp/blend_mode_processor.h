#pragma once

#include <vector>
#include <cstdint>

namespace transform {

enum class BlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    ADD,
    SUBTRACT,
    DARKEN,
    LIGHTEN,
    DIFFERENCE,
    SOFT_LIGHT
};

class BlendModeProcessor {
public:
    static void Process(BlendMode mode, 
                        uint8_t* basePixels, 
                        const uint8_t* topPixels, 
                        int width, 
                        int height, 
                        float opacity);
};

} // namespace transform
