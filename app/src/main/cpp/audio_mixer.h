#ifndef AUDIO_MIXER_H
#define AUDIO_MIXER_H

#include <cstdint>

class AudioMixer {
public:
    static void mixAudio(int16_t* dest, const int16_t* src, int numSamples, float volume);
};

#endif // AUDIO_MIXER_H
