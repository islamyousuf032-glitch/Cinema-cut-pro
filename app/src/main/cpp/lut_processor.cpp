#include "lut_processor.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace {

struct AxisSample {
    int low;
    int high;
    float weight;
};

AxisSample sampleAxis(uint8_t channel, int size) {
    const float coordinate = (channel / 255.0f) * static_cast<float>(size - 1);
    const int low = static_cast<int>(std::floor(coordinate));
    const int high = std::min(low + 1, size - 1);
    return {low, high, coordinate - static_cast<float>(low)};
}

float lutChannel(const uint8_t* lut, int size, int red, int green, int blue, int channel) {
    const size_t index = ((static_cast<size_t>(blue) * static_cast<size_t>(size) +
                           static_cast<size_t>(green)) * static_cast<size_t>(size) +
                          static_cast<size_t>(red)) * 3U + static_cast<size_t>(channel);
    return lut[index] / 255.0f;
}

float trilinearChannel(
    const uint8_t* lut,
    int size,
    const AxisSample& red,
    const AxisSample& green,
    const AxisSample& blue,
    int channel) {
    const float c000 = lutChannel(lut, size, red.low, green.low, blue.low, channel);
    const float c100 = lutChannel(lut, size, red.high, green.low, blue.low, channel);
    const float c010 = lutChannel(lut, size, red.low, green.high, blue.low, channel);
    const float c110 = lutChannel(lut, size, red.high, green.high, blue.low, channel);
    const float c001 = lutChannel(lut, size, red.low, green.low, blue.high, channel);
    const float c101 = lutChannel(lut, size, red.high, green.low, blue.high, channel);
    const float c011 = lutChannel(lut, size, red.low, green.high, blue.high, channel);
    const float c111 = lutChannel(lut, size, red.high, green.high, blue.high, channel);

    const float c00 = c000 + (c100 - c000) * red.weight;
    const float c10 = c010 + (c110 - c010) * red.weight;
    const float c01 = c001 + (c101 - c001) * red.weight;
    const float c11 = c011 + (c111 - c011) * red.weight;
    const float c0 = c00 + (c10 - c00) * green.weight;
    const float c1 = c01 + (c11 - c01) * green.weight;
    return c0 + (c1 - c0) * blue.weight;
}

} // namespace

void LUTProcessor::applyLUT(RenderFrame& frame, const uint8_t* lutData, int lutSize) {
    if (frame.buffer == nullptr || frame.format != 0 || lutData == nullptr || lutSize < 2 || lutSize > 64 ||
        frame.width <= 0 || frame.height <= 0 || frame.stride < frame.width * 4) {
        return;
    }

    for (int y = 0; y < frame.height; ++y) {
        uint8_t* row = frame.buffer + static_cast<size_t>(y) * static_cast<size_t>(frame.stride);
        for (int x = 0; x < frame.width; ++x) {
            uint8_t* pixel = row + static_cast<size_t>(x) * 4U;
            const AxisSample red = sampleAxis(pixel[0], lutSize);
            const AxisSample green = sampleAxis(pixel[1], lutSize);
            const AxisSample blue = sampleAxis(pixel[2], lutSize);
            pixel[0] = static_cast<uint8_t>(std::lround(std::clamp(trilinearChannel(lutData, lutSize, red, green, blue, 0), 0.0f, 1.0f) * 255.0f));
            pixel[1] = static_cast<uint8_t>(std::lround(std::clamp(trilinearChannel(lutData, lutSize, red, green, blue, 1), 0.0f, 1.0f) * 255.0f));
            pixel[2] = static_cast<uint8_t>(std::lround(std::clamp(trilinearChannel(lutData, lutSize, red, green, blue, 2), 0.0f, 1.0f) * 255.0f));
            // Alpha is intentionally untouched.
        }
    }
}
