#include "TimeStretcher.h"
#include <algorithm>
#include <cmath>

namespace pulsemix {

namespace {
constexpr float PI_F = 3.14159265358979323846f;

inline int64_t wrapFrame(int64_t idx, int64_t totalFrames, bool loopActive, int64_t loopStart, int64_t loopEnd) {
    if (loopActive && loopEnd > loopStart) {
        const int64_t loopLen = loopEnd - loopStart;
        if (idx >= loopEnd) {
            idx = loopStart + ((idx - loopStart) % loopLen);
        } else if (idx < loopStart) {
            const int64_t offset = (loopStart - idx) % loopLen;
            idx = (offset == 0) ? loopStart : (loopEnd - offset);
        }
    }
    return std::max<int64_t>(0, std::min(totalFrames - 1, idx));
}
} // namespace

TimeStretcher::TimeStretcher() {
    for (int i = 0; i < GRAIN_SIZE; ++i) {
        window_[i] = 0.5f * (1.0f - std::cos(2.0f * PI_F * static_cast<float>(i) / static_cast<float>(GRAIN_SIZE)));
    }
    grainA_.fill(0.0f);
    grainB_.fill(0.0f);
}

void TimeStretcher::reset(double startFrame) {
    phaseOut_ = 0;
    readPosA_ = startFrame;
    readPosB_ = startFrame;
    grainA_.fill(0.0f);
    grainB_.fill(0.0f);
}

void TimeStretcher::fillGrain(
    const float* pcmData,
    int64_t totalFrames,
    double startPos,
    bool loopActive,
    int64_t loopStartFrame,
    int64_t loopEndFrame,
    std::array<float, GRAIN_SIZE * 2>& dest
) {
    if (!pcmData || totalFrames <= 0) {
        dest.fill(0.0f);
        return;
    }
    const int64_t base = static_cast<int64_t>(std::floor(startPos));
    const float frac = static_cast<float>(startPos - static_cast<double>(base));

    for (int i = 0; i < GRAIN_SIZE; ++i) {
        const int64_t raw0 = base + i;
        if (!loopActive && (raw0 < 0 || raw0 >= totalFrames - 1)) {
            dest[i * 2] = 0.0f;
            dest[i * 2 + 1] = 0.0f;
            continue;
        }
        const int64_t f0 = wrapFrame(raw0, totalFrames, loopActive, loopStartFrame, loopEndFrame);
        const int64_t f1 = wrapFrame(raw0 + 1, totalFrames, loopActive, loopStartFrame, loopEndFrame);

        const float l0 = pcmData[f0 * 2];
        const float r0 = pcmData[f0 * 2 + 1];
        const float l1 = pcmData[f1 * 2];
        const float r1 = pcmData[f1 * 2 + 1];

        dest[i * 2] = l0 + (l1 - l0) * frac;
        dest[i * 2 + 1] = r0 + (r1 - r0) * frac;
    }
}

void TimeStretcher::process(
    const float* pcmData,
    int64_t totalFrames,
    double& trackCursor,
    double tempoRate,
    bool loopActive,
    int64_t loopStartFrame,
    int64_t loopEndFrame,
    float* outInterleaved,
    int numFrames
) {
    if (!pcmData || totalFrames <= 1) {
        std::fill(outInterleaved, outInterleaved + numFrames * 2, 0.0f);
        return;
    }

    for (int i = 0; i < numFrames; ++i) {
        if (phaseOut_ == 0) {
            fillGrain(pcmData, totalFrames, trackCursor, loopActive, loopStartFrame, loopEndFrame, grainA_);
        } else if (phaseOut_ == HOP_OUT) {
            fillGrain(pcmData, totalFrames, trackCursor, loopActive, loopStartFrame, loopEndFrame, grainB_);
        }

        const int idxA = phaseOut_;
        const int idxB = (phaseOut_ + HOP_OUT) % GRAIN_SIZE;
        const float wA = window_[idxA];
        const float wB = window_[idxB];

        outInterleaved[i * 2] = grainA_[idxA * 2] * wA + grainB_[idxB * 2] * wB;
        outInterleaved[i * 2 + 1] = grainA_[idxA * 2 + 1] * wA + grainB_[idxB * 2 + 1] * wB;

        phaseOut_ = (phaseOut_ + 1) % GRAIN_SIZE;
        trackCursor += tempoRate;

        if (loopActive && loopEndFrame > loopStartFrame) {
            const double loopLen = static_cast<double>(loopEndFrame - loopStartFrame);
            if (trackCursor >= static_cast<double>(loopEndFrame)) {
                trackCursor -= loopLen;
            } else if (trackCursor < static_cast<double>(loopStartFrame)) {
                trackCursor += loopLen;
            }
        } else {
            if (trackCursor >= static_cast<double>(totalFrames)) {
                trackCursor = static_cast<double>(totalFrames - 1);
            } else if (trackCursor < 0.0) {
                trackCursor = 0.0;
            }
        }
    }
}

} // namespace pulsemix
