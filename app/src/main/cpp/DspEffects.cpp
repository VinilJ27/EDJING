#include "DspEffects.h"
#include <algorithm>
#include <cmath>

namespace pulsemix {

namespace {
constexpr float PI_F = 3.14159265358979323846f;
constexpr float TWO_PI_F = 6.28318530717958647692f;

inline float clampf(float v, float lo, float hi) {
    return std::max(lo, std::min(hi, v));
}
} // namespace

void StereoBiquad::setIdentity() {
    b0 = 1.0f; b1 = 0.0f; b2 = 0.0f;
    a1 = 0.0f; a2 = 0.0f;
}

void StereoBiquad::resetState() {
    z1L = z2L = z1R = z2R = 0.0f;
}

void StereoBiquad::setLowShelf(float sampleRate, float freqHz, float gainDb, float slope) {
    if (std::abs(gainDb) < 0.05f) {
        setIdentity();
        return;
    }
    const float A = std::pow(10.0f, gainDb / 40.0f);
    const float w0 = TWO_PI_F * clampf(freqHz, 20.0f, sampleRate * 0.45f) / sampleRate;
    const float cosW0 = std::cos(w0);
    const float sinW0 = std::sin(w0);
    const float alpha = sinW0 / 2.0f * std::sqrt((A + 1.0f / A) * (1.0f / slope - 1.0f) + 2.0f);
    const float twoSqrtAAlpha = 2.0f * std::sqrt(A) * alpha;

    const float a0 = (A + 1.0f) + (A - 1.0f) * cosW0 + twoSqrtAAlpha;
    b0 = (A * ((A + 1.0f) - (A - 1.0f) * cosW0 + twoSqrtAAlpha)) / a0;
    b1 = (2.0f * A * ((A - 1.0f) - (A + 1.0f) * cosW0)) / a0;
    b2 = (A * ((A + 1.0f) - (A - 1.0f) * cosW0 - twoSqrtAAlpha)) / a0;
    a1 = (-2.0f * ((A - 1.0f) + (A + 1.0f) * cosW0)) / a0;
    a2 = ((A + 1.0f) + (A - 1.0f) * cosW0 - twoSqrtAAlpha) / a0;
}

void StereoBiquad::setPeaking(float sampleRate, float freqHz, float gainDb, float q) {
    if (std::abs(gainDb) < 0.05f) {
        setIdentity();
        return;
    }
    const float A = std::pow(10.0f, gainDb / 40.0f);
    const float w0 = TWO_PI_F * clampf(freqHz, 20.0f, sampleRate * 0.45f) / sampleRate;
    const float cosW0 = std::cos(w0);
    const float alpha = std::sin(w0) / (2.0f * q);

    const float a0 = 1.0f + alpha / A;
    b0 = (1.0f + alpha * A) / a0;
    b1 = (-2.0f * cosW0) / a0;
    b2 = (1.0f - alpha * A) / a0;
    a1 = (-2.0f * cosW0) / a0;
    a2 = (1.0f - alpha / A) / a0;
}

void StereoBiquad::setHighShelf(float sampleRate, float freqHz, float gainDb, float slope) {
    if (std::abs(gainDb) < 0.05f) {
        setIdentity();
        return;
    }
    const float A = std::pow(10.0f, gainDb / 40.0f);
    const float w0 = TWO_PI_F * clampf(freqHz, 20.0f, sampleRate * 0.45f) / sampleRate;
    const float cosW0 = std::cos(w0);
    const float sinW0 = std::sin(w0);
    const float alpha = sinW0 / 2.0f * std::sqrt((A + 1.0f / A) * (1.0f / slope - 1.0f) + 2.0f);
    const float twoSqrtAAlpha = 2.0f * std::sqrt(A) * alpha;

    const float a0 = (A + 1.0f) - (A - 1.0f) * cosW0 + twoSqrtAAlpha;
    b0 = (A * ((A + 1.0f) + (A - 1.0f) * cosW0 + twoSqrtAAlpha)) / a0;
    b1 = (-2.0f * A * ((A - 1.0f) + (A + 1.0f) * cosW0)) / a0;
    b2 = (A * ((A + 1.0f) + (A - 1.0f) * cosW0 - twoSqrtAAlpha)) / a0;
    a1 = (2.0f * ((A - 1.0f) - (A + 1.0f) * cosW0)) / a0;
    a2 = ((A + 1.0f) - (A - 1.0f) * cosW0 - twoSqrtAAlpha) / a0;
}

void StereoBiquad::setLowPass(float sampleRate, float freqHz, float q) {
    const float w0 = TWO_PI_F * clampf(freqHz, 40.0f, sampleRate * 0.46f) / sampleRate;
    const float cosW0 = std::cos(w0);
    const float alpha = std::sin(w0) / (2.0f * q);
    const float a0 = 1.0f + alpha;

    b0 = ((1.0f - cosW0) * 0.5f) / a0;
    b1 = (1.0f - cosW0) / a0;
    b2 = b0;
    a1 = (-2.0f * cosW0) / a0;
    a2 = (1.0f - alpha) / a0;
}

void StereoBiquad::setHighPass(float sampleRate, float freqHz, float q) {
    const float w0 = TWO_PI_F * clampf(freqHz, 20.0f, sampleRate * 0.46f) / sampleRate;
    const float cosW0 = std::cos(w0);
    const float alpha = std::sin(w0) / (2.0f * q);
    const float a0 = 1.0f + alpha;

    b0 = ((1.0f + cosW0) * 0.5f) / a0;
    b1 = (-(1.0f + cosW0)) / a0;
    b2 = b0;
    a1 = (-2.0f * cosW0) / a0;
    a2 = (1.0f - alpha) / a0;
}

void StereoBiquad::setBandPass(float sampleRate, float freqHz, float q) {
    const float w0 = TWO_PI_F * clampf(freqHz, 60.0f, sampleRate * 0.45f) / sampleRate;
    const float cosW0 = std::cos(w0);
    const float alpha = std::sin(w0) / (2.0f * q);
    const float a0 = 1.0f + alpha;

    b0 = alpha / a0;
    b1 = 0.0f;
    b2 = -alpha / a0;
    a1 = (-2.0f * cosW0) / a0;
    a2 = (1.0f - alpha) / a0;
}

DeckEffectsChain::DeckEffectsChain(int maxSampleRate) {
    const int delayLen = maxSampleRate * 2;
    delayBufferL_.assign(delayLen, 0.0f);
    delayBufferR_.assign(delayLen, 0.0f);

    static constexpr int combLengths[NUM_COMBS] = {1557, 1617, 1491, 1422};
    static constexpr int apLengths[NUM_ALLPASS] = {556, 441};

    for (int i = 0; i < NUM_COMBS; ++i) {
        combBufL_[i].assign(combLengths[i] * 2 + 64, 0.0f);
        combBufR_[i].assign(combLengths[i] * 2 + 96, 0.0f);
    }
    for (int i = 0; i < NUM_ALLPASS; ++i) {
        apBufL_[i].assign(apLengths[i] * 2 + 32, 0.0f);
        apBufR_[i].assign(apLengths[i] * 2 + 48, 0.0f);
    }
}

void DeckEffectsChain::setSampleRate(float sampleRate) {
    sampleRate_ = std::max(22050.0f, sampleRate);
}

void DeckEffectsChain::setBpm(float bpm) {
    bpm_ = clampf(bpm, 40.0f, 240.0f);
}

void DeckEffectsChain::setEffect(EffectType type, float wetDry, float param) {
    effectType_ = type;
    wetDry_ = clampf(wetDry, 0.0f, 1.0f);
    param_ = clampf(param, 0.0f, 1.0f);
}

void DeckEffectsChain::reset() {
    std::fill(delayBufferL_.begin(), delayBufferL_.end(), 0.0f);
    std::fill(delayBufferR_.begin(), delayBufferR_.end(), 0.0f);
    delayWriteIdx_ = 0;
    echoLpL_ = echoLpR_ = 0.0f;
    for (int i = 0; i < NUM_COMBS; ++i) {
        std::fill(combBufL_[i].begin(), combBufL_[i].end(), 0.0f);
        std::fill(combBufR_[i].begin(), combBufR_[i].end(), 0.0f);
        combIdxL_[i] = combIdxR_[i] = 0;
        combLpL_[i] = combLpR_[i] = 0.0f;
    }
    for (int i = 0; i < NUM_ALLPASS; ++i) {
        std::fill(apBufL_[i].begin(), apBufL_[i].end(), 0.0f);
        std::fill(apBufR_[i].begin(), apBufR_[i].end(), 0.0f);
        apIdxL_[i] = apIdxR_[i] = 0;
    }
    phaserZ1L_.fill(0.0f);
    phaserZ1R_.fill(0.0f);
    phaserFbL_ = phaserFbR_ = 0.0f;
    fxFilter_.resetState();
    brakeSpeed_ = 1.0f;
    crushCounter_ = 0;
}

void DeckEffectsChain::processStereo(float* interleaved, int numFrames) {
    // Update brake envelope even if dry
    const float brakeTarget = (effectType_ == EffectType::BRAKE) ? 0.0f : 1.0f;
    const float brakeRate = (effectType_ == EffectType::BRAKE)
        ? (1.0f / (sampleRate_ * (0.4f + (1.0f - param_) * 1.4f)))
        : (1.0f / (sampleRate_ * 0.25f));

    for (int i = 0; i < numFrames; ++i) {
        if (brakeSpeed_ > brakeTarget) {
            brakeSpeed_ = std::max(0.0f, brakeSpeed_ - brakeRate);
        } else if (brakeSpeed_ < brakeTarget) {
            brakeSpeed_ = std::min(1.0f, brakeSpeed_ + brakeRate);
        }
    }

    if (effectType_ == EffectType::NONE || effectType_ == EffectType::BRAKE || effectType_ == EffectType::REVERSE) {
        return;
    }

    const int delayCap = static_cast<int>(delayBufferL_.size());

    switch (effectType_) {
        case EffectType::ECHO: {
            // Beat-synced echo (1/4 to 1 beat) with warm LP damping
            const float beatSec = 60.0f / bpm_;
            const float subdivision = 0.25f + std::round(param_ * 3.0f) * 0.25f;
            const int delaySamples = std::max(64, std::min(delayCap - 2, static_cast<int>(beatSec * subdivision * sampleRate_)));
            const float feedback = 0.35f + param_ * 0.45f;
            for (int i = 0; i < numFrames; ++i) {
                const float inL = interleaved[i * 2];
                const float inR = interleaved[i * 2 + 1];
                int readIdx = delayWriteIdx_ - delaySamples;
                if (readIdx < 0) readIdx += delayCap;

                const float delL = delayBufferL_[readIdx];
                const float delR = delayBufferR_[readIdx];
                echoLpL_ = echoLpL_ * 0.35f + delL * 0.65f;
                echoLpR_ = echoLpR_ * 0.35f + delR * 0.65f;

                delayBufferL_[delayWriteIdx_] = inL + echoLpL_ * feedback;
                delayBufferR_[delayWriteIdx_] = inR + echoLpR_ * feedback;

                interleaved[i * 2] = inL * (1.0f - wetDry_ * 0.4f) + delL * wetDry_;
                interleaved[i * 2 + 1] = inR * (1.0f - wetDry_ * 0.4f) + delR * wetDry_;

                delayWriteIdx_ = (delayWriteIdx_ + 1) % delayCap;
            }
            break;
        }
        case EffectType::DELAY: {
            // Stereo ping-pong delay
            const float beatSec = 60.0f / bpm_;
            const float mult = (param_ < 0.33f) ? 0.375f : ((param_ < 0.66f) ? 0.5f : 0.75f);
            const int delaySamples = std::max(64, std::min(delayCap - 2, static_cast<int>(beatSec * mult * sampleRate_)));
            const float feedback = 0.25f + param_ * 0.5f;
            for (int i = 0; i < numFrames; ++i) {
                const float inL = interleaved[i * 2];
                const float inR = interleaved[i * 2 + 1];
                int readIdx = delayWriteIdx_ - delaySamples;
                if (readIdx < 0) readIdx += delayCap;

                const float delL = delayBufferL_[readIdx];
                const float delR = delayBufferR_[readIdx];

                delayBufferL_[delayWriteIdx_] = inL + delR * feedback;
                delayBufferR_[delayWriteIdx_] = inR + delL * feedback;

                interleaved[i * 2] = inL + delL * wetDry_ * 0.85f;
                interleaved[i * 2 + 1] = inR + delR * wetDry_ * 0.85f;

                delayWriteIdx_ = (delayWriteIdx_ + 1) % delayCap;
            }
            break;
        }
        case EffectType::REVERB: {
            const float roomFeedback = 0.72f + param_ * 0.22f;
            const float damp = 0.25f;
            for (int i = 0; i < numFrames; ++i) {
                const float inL = interleaved[i * 2];
                const float inR = interleaved[i * 2 + 1];
                const float monoIn = (inL + inR) * 0.25f;

                float outL = 0.0f;
                float outR = 0.0f;
                for (int c = 0; c < NUM_COMBS; ++c) {
                    const int lenL = static_cast<int>(combBufL_[c].size());
                    const float bufOutL = combBufL_[c][combIdxL_[c]];
                    combLpL_[c] = bufOutL * (1.0f - damp) + combLpL_[c] * damp;
                    combBufL_[c][combIdxL_[c]] = monoIn + combLpL_[c] * roomFeedback;
                    combIdxL_[c] = (combIdxL_[c] + 1) % lenL;
                    outL += bufOutL;

                    const int lenR = static_cast<int>(combBufR_[c].size());
                    const float bufOutR = combBufR_[c][combIdxR_[c]];
                    combLpR_[c] = bufOutR * (1.0f - damp) + combLpR_[c] * damp;
                    combBufR_[c][combIdxR_[c]] = monoIn + combLpR_[c] * roomFeedback;
                    combIdxR_[c] = (combIdxR_[c] + 1) % lenR;
                    outR += bufOutR;
                }
                for (int a = 0; a < NUM_ALLPASS; ++a) {
                    const int lenL = static_cast<int>(apBufL_[a].size());
                    const float bufL = apBufL_[a][apIdxL_[a]];
                    const float tmpL = -outL + bufL;
                    apBufL_[a][apIdxL_[a]] = outL + bufL * 0.5f;
                    apIdxL_[a] = (apIdxL_[a] + 1) % lenL;
                    outL = tmpL;

                    const int lenR = static_cast<int>(apBufR_[a].size());
                    const float bufR = apBufR_[a][apIdxR_[a]];
                    const float tmpR = -outR + bufR;
                    apBufR_[a][apIdxR_[a]] = outR + bufR * 0.5f;
                    apIdxR_[a] = (apIdxR_[a] + 1) % lenR;
                    outR = tmpR;
                }
                interleaved[i * 2] = inL * (1.0f - wetDry_ * 0.35f) + outL * wetDry_ * 0.65f;
                interleaved[i * 2 + 1] = inR * (1.0f - wetDry_ * 0.35f) + outR * wetDry_ * 0.65f;
            }
            break;
        }
        case EffectType::FLANGER: {
            const float lfoRate = 0.25f + param_ * 2.5f;
            const float lfoInc = TWO_PI_F * lfoRate / sampleRate_;
            const float maxDepthSamples = sampleRate_ * 0.0055f;
            const float minDelaySamples = sampleRate_ * 0.0007f;
            for (int i = 0; i < numFrames; ++i) {
                const float inL = interleaved[i * 2];
                const float inR = interleaved[i * 2 + 1];
                const float lfo = 0.5f * (1.0f + std::sin(lfoPhase_));
                const float delaySamples = minDelaySamples + lfo * maxDepthSamples;
                const int d0 = static_cast<int>(delaySamples);
                const float frac = delaySamples - static_cast<float>(d0);

                int idx0 = delayWriteIdx_ - d0;
                if (idx0 < 0) idx0 += delayCap;
                int idx1 = idx0 - 1;
                if (idx1 < 0) idx1 += delayCap;

                const float delL = delayBufferL_[idx0] * (1.0f - frac) + delayBufferL_[idx1] * frac;
                const float delR = delayBufferR_[idx0] * (1.0f - frac) + delayBufferR_[idx1] * frac;

                delayBufferL_[delayWriteIdx_] = inL + delL * 0.55f;
                delayBufferR_[delayWriteIdx_] = inR + delR * 0.55f;

                interleaved[i * 2] = inL * (1.0f - wetDry_ * 0.5f) + (inL + delL) * 0.5f * wetDry_;
                interleaved[i * 2 + 1] = inR * (1.0f - wetDry_ * 0.5f) + (inR + delR) * 0.5f * wetDry_;

                delayWriteIdx_ = (delayWriteIdx_ + 1) % delayCap;
                lfoPhase_ += lfoInc;
                if (lfoPhase_ >= TWO_PI_F) lfoPhase_ -= TWO_PI_F;
            }
            break;
        }
        case EffectType::PHASER: {
            const float lfoRate = 0.3f + param_ * 3.5f;
            const float lfoInc = TWO_PI_F * lfoRate / sampleRate_;
            for (int i = 0; i < numFrames; ++i) {
                const float lfo = 0.5f * (1.0f + std::sin(lfoPhase_));
                const float sweepFreq = 300.0f + lfo * 3200.0f;
                const float k = std::tan(PI_F * sweepFreq / sampleRate_);
                const float a1 = (1.0f - k) / (1.0f + k);

                float xL = interleaved[i * 2] + phaserFbL_ * 0.45f;
                float xR = interleaved[i * 2 + 1] + phaserFbR_ * 0.45f;
                for (int s = 0; s < 4; ++s) {
                    const float yL = -a1 * xL + phaserZ1L_[s];
                    phaserZ1L_[s] = xL + a1 * yL;
                    xL = yL;

                    const float yR = -a1 * xR + phaserZ1R_[s];
                    phaserZ1R_[s] = xR + a1 * yR;
                    xR = yR;
                }
                phaserFbL_ = clampf(xL, -1.0f, 1.0f);
                phaserFbR_ = clampf(xR, -1.0f, 1.0f);

                interleaved[i * 2] = interleaved[i * 2] * (1.0f - wetDry_ * 0.5f) + xL * wetDry_ * 0.65f;
                interleaved[i * 2 + 1] = interleaved[i * 2 + 1] * (1.0f - wetDry_ * 0.5f) + xR * wetDry_ * 0.65f;

                lfoPhase_ += lfoInc;
                if (lfoPhase_ >= TWO_PI_F) lfoPhase_ -= TWO_PI_F;
            }
            break;
        }
        case EffectType::FILTER: {
            const float freq = 180.0f * std::pow(55.0f, param_);
            fxFilter_.setBandPass(sampleRate_, freq, 2.2f);
            for (int i = 0; i < numFrames; ++i) {
                const float dryL = interleaved[i * 2];
                const float dryR = interleaved[i * 2 + 1];
                float wL = dryL * 1.4f;
                float wR = dryR * 1.4f;
                fxFilter_.process(wL, wR);
                interleaved[i * 2] = dryL * (1.0f - wetDry_) + wL * wetDry_;
                interleaved[i * 2 + 1] = dryR * (1.0f - wetDry_) + wR * wetDry_;
            }
            break;
        }
        case EffectType::BITCRUSHER: {
            const int downsample = 1 + static_cast<int>(param_ * 20.0f);
            const float levels = std::pow(2.0f, 10.0f - param_ * 7.0f);
            for (int i = 0; i < numFrames; ++i) {
                const float dryL = interleaved[i * 2];
                const float dryR = interleaved[i * 2 + 1];
                if (crushCounter_ == 0) {
                    crushHoldL_ = std::round(dryL * levels) / levels;
                    crushHoldR_ = std::round(dryR * levels) / levels;
                }
                crushCounter_ = (crushCounter_ + 1) % downsample;
                interleaved[i * 2] = dryL * (1.0f - wetDry_) + crushHoldL_ * wetDry_;
                interleaved[i * 2 + 1] = dryR * (1.0f - wetDry_) + crushHoldR_ * wetDry_;
            }
            break;
        }
        default:
            break;
    }
}

} // namespace pulsemix
