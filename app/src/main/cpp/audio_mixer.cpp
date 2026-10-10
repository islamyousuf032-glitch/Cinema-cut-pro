#include "audio_mixer.h"

#include <algorithm>
#include <cmath>
#include <cstdint>

void AudioMixer::mixAudio(int16_t* dest, const int16_t* src, int numSamples, float volume) {
    if (dest == nullptr || src == nullptr || numSamples <= 0) return;
    if (!std::isfinite(volume)) volume = 0.0f;

    for (int i = 0; i < numSamples; ++i) {
        const float mixed = static_cast<float>(dest[i]) + static_cast<float>(src[i]) * volume;
        const long rounded = std::lround(mixed);
        dest[i] = static_cast<int16_t>(std::clamp(rounded, -32768L, 32767L));
    }
}
