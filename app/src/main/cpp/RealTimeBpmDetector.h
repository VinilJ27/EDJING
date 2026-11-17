#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <vector>

namespace pulsemix {

struct RealtimeBpmResult {
    float detectedBpm = 0.0f;
    float confidence = 0.0f;
    float beatPhase = 0.0f;
    bool fromLiveStream = false;
    int analyzedHops = 0;
};

/**
 * Lock-free, zero-allocation real-time onset detector for the Oboe audio callback thread,
 * paired with a control-thread autocorrelation BPM estimator.
 */
class RealTimeBpmDetector {
public:
    static constexpr int RING_SIZE = 1024;      // ~10.24s @ 100 Hz onset hop rate
    static constexpr int LIVE_MIN_HOPS = 280;   // ~2.8s of live audio before live autocorrelation
    static constexpr int ANALYSIS_WINDOW_HOPS = 800; // 8.0s rolling window

    RealTimeBpmDetector();

    void setSampleRate(float sampleRate);
    void reset();

    /**
     * Called ONLY from the Oboe audio thread inside DeckProcessor::process.
     * Performs zero allocations and zero locks.
     */
    void pushFrames(const float* stereoPcm, int numFrames);

    /**
     * Builds a 100 Hz onset envelope vector for an entire loaded track on the control thread.
     */
    static std::vector<float> buildTrackOnsetEnvelope(
        const float* stereoPcm,
        int64_t totalFrames,
        float sampleRate
    );

    /**
     * Runs BPM autocorrelation on the live Oboe ring buffer (if >= LIVE_MIN_HOPS have streamed)
     * or falls back to a local window around cursorFrame in trackOnsetEnvelope.
     */
    RealtimeBpmResult analyze(
        const std::vector<float>& trackOnsetEnvelope,
        double cursorFrame,
        double playbackRate,
        float fallbackBaseBpm,
        int64_t firstBeatFrame
    ) const;

    uint64_t getTotalHopsWritten() const {
        return totalHopsWritten_.load(std::memory_order_acquire);
    }

private:
    float sampleRate_ = 48000.0f;
    int hopFrames_ = 480;

    // Audio-thread onset accumulator state
    float lpState_ = 0.0f;
    float accLowEnergy_ = 0.0f;
    float accFullEnergy_ = 0.0f;
    float prevLowEnergy_ = 0.0f;
    float prevFullEnergy_ = 0.0f;
    int sampleInHop_ = 0;

    // Pre-allocated SPSC ring buffer written by audio thread, read by analysis service thread
    std::array<float, RING_SIZE> onsetRing_{};
    std::atomic<uint64_t> totalHopsWritten_{0};

    static void estimateBpmFromOnsets(
        const float* onsets,
        int count,
        float hopRateHz,
        float& outBpm,
        float& outConfidence
    );
};

} // namespace pulsemix
