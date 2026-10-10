#include "audio_mixer.h"
#include "color_eval.h"
#include "frame_compositor.h"
#include "lut_processor.h"
#include "transform_eval.h"

#include <array>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

void expect(bool condition, const std::string& message) {
    if (!condition) throw std::runtime_error(message);
}

void testRgbaCompositing() {
    std::array<uint8_t, 4> background{20, 40, 60, 255};
    const std::array<uint8_t, 4> foreground{220, 120, 20, 128};
    RenderFrame bg{background.data(), 1, 1, 4, 0};
    const RenderFrame fg{const_cast<uint8_t*>(foreground.data()), 1, 1, 4, 0};
    FrameCompositor::compositeFrames(bg, fg, 0);
    expect(std::abs(static_cast<int>(background[0]) - 120) <= 1, "normal blend red channel");
    expect(std::abs(static_cast<int>(background[1]) - 80) <= 1, "normal blend green channel");
    expect(std::abs(static_cast<int>(background[2]) - 40) <= 1, "normal blend blue channel");
    expect(background[3] == 255, "source-over composite alpha");

    std::array<uint8_t, 4> multiplyBackground{100, 100, 100, 255};
    const std::array<uint8_t, 4> multiplyForeground{128, 128, 128, 255};
    RenderFrame multiplyBg{multiplyBackground.data(), 1, 1, 4, 0};
    const RenderFrame multiplyFg{const_cast<uint8_t*>(multiplyForeground.data()), 1, 1, 4, 0};
    FrameCompositor::compositeFrames(multiplyBg, multiplyFg, 1);
    expect(multiplyBackground[0] == 50, "multiply blend mode");
}

void testRgbaToI420AndWatermark() {
    std::array<uint8_t, 16> rgba{};
    std::array<uint8_t, 6> yuv{};
    RenderFrame rgbaFrame{rgba.data(), 2, 2, 8, 0};
    RenderFrame yuvFrame{yuv.data(), 2, 2, 2, 1};
    FrameCompositor::convertRgbaToYuv420(rgbaFrame, yuvFrame);
    expect(yuv[0] == 16 && yuv[1] == 16 && yuv[2] == 16 && yuv[3] == 16, "limited-range black luma");
    expect(yuv[4] == 128 && yuv[5] == 128, "neutral black chroma");

    rgba.fill(255);
    FrameCompositor::convertRgbaToYuv420(rgbaFrame, yuvFrame);
    expect(yuv[0] == 235 && yuv[1] == 235 && yuv[2] == 235 && yuv[3] == 235, "limited-range white luma");
    expect(yuv[4] == 128 && yuv[5] == 128, "neutral white chroma");

    rgba.fill(0);
    yuv.fill(0);
    FrameCompositor::convertRgbaToYuv420(rgbaFrame, yuvFrame);
    rgba = {255, 0, 0, 255, 255, 0, 0, 255,
            255, 0, 0, 255, 255, 0, 0, 255};
    FrameCompositor::blendRgbaWatermarkIntoYuv420(yuvFrame, rgbaFrame);
    expect(yuv[0] >= 81 && yuv[0] <= 83, "opaque red watermark luma");
    expect(yuv[4] >= 89 && yuv[4] <= 92, "opaque red watermark U chroma");
    expect(yuv[5] >= 239 && yuv[5] <= 241, "opaque red watermark V chroma");
}

void testColorAndLut() {
    std::array<uint8_t, 4> pixel{64, 128, 192, 77};
    RenderFrame frame{pixel.data(), 1, 1, 4, 0};
    ColorEval::applyColorAdjustments(frame, 1.0f, 0.0f, 1.0f, 1.0f);
    expect(pixel[0] >= 88 && pixel[0] <= 92, "one-stop exposure on red");
    expect(pixel[1] >= 173 && pixel[1] <= 177, "one-stop exposure on green");
    expect(pixel[2] == 255, "one-stop exposure clips blue to white");
    expect(pixel[3] == 77, "color correction preserves alpha");

    std::vector<uint8_t> identityLut(2U * 2U * 2U * 3U);
    for (int blue = 0; blue < 2; ++blue) {
        for (int green = 0; green < 2; ++green) {
            for (int red = 0; red < 2; ++red) {
                const size_t index = ((static_cast<size_t>(blue) * 2U + static_cast<size_t>(green)) * 2U +
                                      static_cast<size_t>(red)) * 3U;
                identityLut[index] = static_cast<uint8_t>(red * 255);
                identityLut[index + 1] = static_cast<uint8_t>(green * 255);
                identityLut[index + 2] = static_cast<uint8_t>(blue * 255);
            }
        }
    }
    std::array<uint8_t, 4> lutPixel{64, 127, 230, 44};
    RenderFrame lutFrame{lutPixel.data(), 1, 1, 4, 0};
    LUTProcessor::applyLUT(lutFrame, identityLut.data(), 2);
    expect(std::abs(static_cast<int>(lutPixel[0]) - 64) <= 1, "identity LUT red");
    expect(std::abs(static_cast<int>(lutPixel[1]) - 127) <= 1, "identity LUT green");
    expect(std::abs(static_cast<int>(lutPixel[2]) - 230) <= 1, "identity LUT blue");
    expect(lutPixel[3] == 44, "LUT preserves alpha");
}

void testAffineTransform() {
    std::array<uint8_t, 12> pixels{
        10, 0, 0, 255,
        20, 0, 0, 255,
        30, 0, 0, 255
    };
    RenderFrame frame{pixels.data(), 3, 1, 12, 0};
    TransformEval::applyTransform(frame, 1.0f, 1.0f, 0.0f, 1.0f / 3.0f, 0.0f, 1.0f);
    expect(pixels[3] == 0, "translated frame exposes transparent edge");
    expect(pixels[4] == 10 && pixels[8] == 20, "translation samples source pixels in place");
}

void testAudioMixing() {
    std::array<int16_t, 3> destination{30000, -30000, 0};
    const std::array<int16_t, 3> source{10000, -10000, 200};
    AudioMixer::mixAudio(destination.data(), source.data(), 3, 1.0f);
    expect(destination[0] == 32767, "positive PCM saturation");
    expect(destination[1] == -32768, "negative PCM saturation");
    expect(destination[2] == 200, "PCM accumulation");
}

} // namespace

int main() {
    try {
        testRgbaCompositing();
        testRgbaToI420AndWatermark();
        testColorAndLut();
        testAffineTransform();
        testAudioMixing();
        std::cout << "Native export engine tests passed.\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "Native export engine test failure: " << error.what() << '\n';
        return 1;
    }
}
