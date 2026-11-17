#pragma once

#include <array>
#include <cstdint>

namespace pulsemix {

/**
 * Lock-free, allocation-free WSOLA Overlap-Add time-stretcher
 * for Key-Lock (pitch-preserving tempo changes) on stereo float PCM.
 */
class TimeStretcher {
public:
    static constexpr int GRAIN_SIZE = 1024;
    static constexpr int HOP_OUT = GRAIN_SIZE / 2;

    TimeStretcher();
    void reset(double startFrame);

    /**
     * Reads `numFrames` stereo samples from `pcmData` at `totalFrames`,
     * advancing the track cursor by `tempoRate` per output frame while keeping
     * musical pitch unchanged via overlapping Hann grains.
     * Updates `trackCursor` in place.
     */
    void process(
        const float* pcmData,
        int64_t totalFrames,
        double& trackCursor,
        double tempoRate,
        bool loopActive,
        int64_t loopStartFrame,
        int64_t loopEndFrame,
        float* outInterleaved,
        int numFrames
    );

private:
    std::array<float, GRAIN_SIZE> window_{};
    std::array<float, GRAIN_SIZE * 2> grainA_{};
    std::array<float, GRAIN_SIZE * 2> grainB_{};
    int phaseOut_ = 0;
    double readPosA_ = 0.0;
    double readPosB_ = 0.0;

    void fillGrain(
        const float* pcmData,
        int64_t totalFrames,
        double startPos,
        bool loopActive,
        int64_t loopStartFrame,
        int64_t loopEndFrame,
        std::array<float, GRAIN_SIZE * 2>& dest
    );
};

} // namespace pulsemix
