#ifndef RENDER_FRAME_H
#define RENDER_FRAME_H

#include <cstdint>

struct RenderFrame {
    uint8_t* buffer;
    int width;
    int height;
    int stride; // Byte stride of the packed RGBA or I420 Y plane.
    int format; // 0: packed RGBA8888, 1: planar I420 (Y, U, V).
};

#endif // RENDER_FRAME_H
