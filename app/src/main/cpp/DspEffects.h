#pragma once

#include <array>
#include <cmath>
#include <vector>

namespace pulsemix {

enum class EffectType : int {
    NONE = 0,
    ECHO = 1,
    REVERB = 2,
    FLANGER = 3,
    PHASER = 4,
    DELAY = 5,
    FILTER = 6,
    BRAKE = 7,
    REVERSE = 8,
    BITCRUSHER = 9
};

class StereoBiquad {
public:
    void setIdentity();
    void setLowShelf(float sampleRate, float freqHz, float gainDb, float slope = 0.85f);
    void setPeaking(float sampleRate, float freqHz, float gainDb, float q = 0.85f);
    void setHighShelf(float sampleRate, float freqHz, float gainDb, float slope = 0.85f);
    void setLowPass(float sampleRate, float freqHz, float q = 0.85f);
    void setHighPass(float sampleRate, float freqHz, float q = 0.85f);
    void setBandPass(float sampleRate, float freqHz, float q = 1.6f);

    inline void process(float& left, float& right) {
        const float outL = b0 * left + z1L;
        z1L = b1 * left - a1 * outL + z2L;
        z2L = b2 * left - a2 * outL;
        left = outL;

        const float outR = b0 * right + z1R;
        z1R = b1 * right - a1 * outR + z2R;
        z2R = b2 * right - a2 * outR;
        right = outR;
    }

    void resetState();

private:
    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f;
    float a1 = 0.0f, a2 = 0.0f;
    float z1L = 0.0f, z2L = 0.0f;
    float z1R = 0.0f, z2R = 0.0f;
};

class DeckEffectsChain {
public:
    explicit DeckEffectsChain(int maxSampleRate = 96000);

    void setSampleRate(float sampleRate);
    void setEffect(EffectType type, float wetDry, float param);
    void setBpm(float bpm);
    void reset();

    void processStereo(float* interleaved, int numFrames);

    // For BRAKE effect speed modulation (1.0 normal -> 0.0 stopped)
    float getBrakeSpeedMultiplier() const { return brakeSpeed_; }
    bool isReverseEffectActive() const { return effectType_ == EffectType::REVERSE; }

private:
    float sampleRate_ = 48000.0f;
    float bpm_ = 128.0f;
    EffectType effectType_ = EffectType::NONE;
    float wetDry_ = 0.5f;
    float param_ = 0.5f;

    // Pre-allocated delay buffer (2 seconds stereo)
    std::vector<float> delayBufferL_;
    std::vector<float> delayBufferR_;
    int delayWriteIdx_ = 0;
    float echoLpL_ = 0.0f;
    float echoLpR_ = 0.0f;

    // Pre-allocated Reverb comb & allpass buffers
    static constexpr int NUM_COMBS = 4;
    static constexpr int NUM_ALLPASS = 2;
    std::array<std::vector<float>, NUM_COMBS> combBufL_;
    std::array<std::vector<float>, NUM_COMBS> combBufR_;
    std::array<int, NUM_COMBS> combIdxL_{};
    std::array<int, NUM_COMBS> combIdxR_{};
    std::array<float, NUM_COMBS> combLpL_{};
    std::array<float, NUM_COMBS> combLpR_{};

    std::array<std::vector<float>, NUM_ALLPASS> apBufL_;
    std::array<std::vector<float>, NUM_ALLPASS> apBufR_;
    std::array<int, NUM_ALLPASS> apIdxL_{};
    std::array<int, NUM_ALLPASS> apIdxR_{};

    // Flanger / Phaser LFO state
    float lfoPhase_ = 0.0f;
    std::array<float, 4> phaserZ1L_{};
    std::array<float, 4> phaserZ1R_{};
    float phaserFbL_ = 0.0f;
    float phaserFbR_ = 0.0f;

    // FX Filter
    StereoBiquad fxFilter_;

    // Brake state
    float brakeSpeed_ = 1.0f;

    // Bitcrusher state
    int crushCounter_ = 0;
    float crushHoldL_ = 0.0f;
    float crushHoldR_ = 0.0f;
};

} // namespace pulsemix
